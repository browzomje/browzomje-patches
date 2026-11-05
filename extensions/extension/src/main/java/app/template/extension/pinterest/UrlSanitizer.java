package app.browzomje.extension.pinterest;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ripulisce i link che Pinterest sta per mandare fuori dall'app — sul foglio di condivisione di
 * Android e negli appunti di sistema.
 *
 * <p>I problemi da risolvere sono due, e sono di natura diversa:
 *
 * <ol>
 *   <li><b>Parametri di tracciamento nella query</b> ({@code utm_*}, {@code fbclid}, {@code sender},
 *       …). Si tolgono in locale, riscrivendo la query: operazione offline, istantanea e sempre
 *       sicura.
 *   <li><b>Short link con impronta personale</b> ({@code pin.it/<slug>} e
 *       {@code pinterest.<tld>/url_shortener/<slug>/redirect/}). Qui l'identificatore sta nello
 *       <i>slug</i>, non nella query: togliere i parametri non serve a niente, bisogna risolvere il
 *       redirect e ricostruire il link canonico {@code pinterest.com/pin/<id>/}.
 * </ol>
 *
 * <p><b>Il punto delicato è il secondo</b>: risolvere lo short link vuol dire fare una richiesta di
 * rete mentre l'utente sta aspettando che si apra il foglio di condivisione. Tre accorgimenti
 * tengono il costo sotto controllo:
 *
 * <ul>
 *   <li>la richiesta gira su un thread a parte (niente {@code NetworkOnMainThreadException}) con un
 *       tetto complessivo di {@link #TOTAL_BUDGET_MS};
 *   <li>i risultati si tengono in {@link #RESOLVED_CACHE}: condividere due volte lo stesso pin paga
 *       una sola attesa;
 *   <li>se qualcosa va storto — rete assente, timeout, risposta inattesa — si restituisce il link
 *       originale. La condivisione non deve mai fallire per colpa nostra.
 * </ul>
 *
 * <p>Tutto questo vale solo se l'interruttore "Ripulisci i link condivisi" è acceso: vedi
 * {@link MorpheSettingsStore#isLinkSanitizerEnabled()}.
 */
@SuppressWarnings("unused")
public final class UrlSanitizer {

    private UrlSanitizer() {}

    // region Parametri di tracciamento

    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            // Tassonomia UTM (Google / generica)
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_content",
            "utm_term",
            "utm_id",
            "utm_creative_format",
            "utm_marketing_tactic",
            // Click id dei circuiti pubblicitari
            "fbclid",    // Facebook
            "gclid",     // Google Ads
            "gbraid",    // Google Ads Enhanced Conversions
            "wbraid",    // Google Ads Enhanced Conversions
            "yclid",     // Yandex
            "ttclid",    // TikTok
            "twclid",    // X/Twitter
            "li_fat_id", // LinkedIn
            "mc_cid",    // Mailchimp, campagna
            "mc_eid",    // Mailchimp, destinatario
            "igshid",    // Instagram
            // Specifici di Pinterest / install referrer
            "amp_client_id",
            "app_upsell_type",
            "invite_code",
            "sfo",
            "sender"
    ));

    // endregion

    private static final Set<String> AUTH_PARAMS = new HashSet<>(Arrays.asList(
            "signature", "sig", "token", "access_token", "auth", "authorization", "code",
            "id_token", "refresh_token", "state", "oauth_signature", "x-amz-signature", "x-goog-signature"));

    private static final Pattern ANY_URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PINTEREST_WEB_HOST = Pattern.compile(
            "(?:[a-z0-9-]+\\.)*pinterest\\.(?:com|co\\.uk|com\\.au|ca|de|es|fr|it|jp)");
    private static final Pattern PIN_IT_PATH = Pattern.compile("/[A-Za-z0-9_-]+/?");
    private static final Pattern SHORTENER_PATH = Pattern.compile("/url_shortener/[A-Za-z0-9_-]+/redirect/?");

    // region Risolutore HTTP

    /** Timeout della singola richiesta. */
    private static final int RESOLVE_TIMEOUT_MS = 2_500;

    /** Tetto complessivo per URL: oltre questo si rinuncia e si tiene il link originale. */
    private static final long TOTAL_BUDGET_MS = 4_000L;

    /** Tetto ai salti di redirect, contro le catene ostili o infinite. */
    private static final int MAX_REDIRECT_HOPS = 5;

    /**
     * Short link già risolti, {@code short → canonico}.
     *
     * <p>Serve a non far pagare l'attesa di rete due volte per lo stesso pin: capita di
     * continuo, perché "Copia link" e poi "Condividi" sullo stesso pin producono lo stesso short
     * link. È una cache di processo e non ha invalidazione: il link canonico di un pin non cambia.
     * Il tetto a {@link #CACHE_MAX_ENTRIES} evita che una sessione lunghissima la faccia crescere
     * senza limite.
     */
    private static final Map<String, String> RESOLVED_CACHE = new ConcurrentHashMap<>();

    private static final int CACHE_MAX_ENTRIES = 256;

    private static final Pattern PIN_PATH_PATTERN = Pattern.compile("(/pin/[0-9]+)(?:/sent)?/?");

    private static final ExecutorService RESOLVER = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable, "morphe-url-resolver");
        t.setDaemon(true);
        return t;
    });

    private static URI parse(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String trustedHost(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getHost() == null) return "";
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return "pin.it".equals(host) || "pinterest.com".equals(host)
                || "www.pinterest.com".equals(host) || "api.pinterest.com".equals(host) ? host : "";
    }

    static URI trustedRedirect(URI from, String location) {
        if (location == null || location.isEmpty()) return null;
        URI target = parse(location);
        if (target == null) return null;
        URI resolved = from.resolve(target);
        return trustedHost(resolved).isEmpty() || hasAuthQuery(resolved) ? null : resolved;
    }

    private static boolean isShortLink(URI uri, String host) {
        String path = uri.getRawPath();
        return path != null && ("pin.it".equals(host)
                ? PIN_IT_PATH.matcher(path).matches() : SHORTENER_PATH.matcher(path).matches());
    }

    private static String queryName(String pair) {
        int equals = pair.indexOf('=');
        return Uri.decode(equals < 0 ? pair : pair.substring(0, equals)).toLowerCase(Locale.ROOT);
    }

    private static boolean hasAuthQuery(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) return false;
        for (String pair : query.split("&", -1)) {
            if (AUTH_PARAMS.contains(queryName(pair))) return true;
        }
        return false;
    }

    private static String canonicalPin(URI uri, String host) {
        if (host.isEmpty() || "pin.it".equals(host) || uri.getRawPath() == null) return null;
        Matcher pin = PIN_PATH_PATTERN.matcher(uri.getRawPath());
        return pin.matches() ? "https://pinterest.com" + pin.group(1) + "/" : null;
    }

    private static String resolveShortUrl(URI source) {
        String original = source.toString();
        String cached = RESOLVED_CACHE.get(original);
        if (cached != null) return cached;

        Future<String> future = RESOLVER.submit(() -> {
            URI current = "pin.it".equals(trustedHost(source)) ? source
                    : URI.create("https://api.pinterest.com" + source.getRawPath()
                            + (source.getRawQuery() == null ? "" : "?" + source.getRawQuery()));
            for (int hop = 0; hop < MAX_REDIRECT_HOPS && !Thread.currentThread().isInterrupted(); hop++) {
                String host = trustedHost(current);
                String canonical = canonicalPin(current, host);
                if (canonical != null) return canonical;
                if (host.isEmpty() || !isShortLink(current, host)) return original;
                HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
                try {
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(RESOLVE_TIMEOUT_MS);
                    connection.setReadTimeout(RESOLVE_TIMEOUT_MS);
                    connection.setRequestMethod("HEAD");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int status = connection.getResponseCode();
                    if (status < 300 || status >= 400) return original;
                    current = trustedRedirect(current, connection.getHeaderField("Location"));
                    if (current == null) return original;
                } finally {
                    connection.disconnect();
                }
            }
            String canonical = canonicalPin(current, trustedHost(current));
            return canonical == null ? original : canonical;
        });
        try {
            String resolved = future.get(TOTAL_BUDGET_MS, TimeUnit.MILLISECONDS);
            if (!resolved.equals(original) && RESOLVED_CACHE.size() < CACHE_MAX_ENTRIES) {
                RESOLVED_CACHE.put(original, resolved);
            }
            return resolved;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
            // Preserve the source link if the lookup fails.
        } finally {
            future.cancel(true);
        }
        return original;
    }

    /** Strip known tracking keys while retaining the exact encoding and order of other pairs. */
    public static String stripTrackingParams(String url) {
        if (url == null || url.indexOf('?') < 0) return url;
        URI uri = parse(url);
        if (uri == null || uri.getRawQuery() == null || hasAuthQuery(uri)) return url;
        java.util.ArrayList<String> retained = new java.util.ArrayList<>();
        String[] pairs = uri.getRawQuery().split("&", -1);
        for (String pair : pairs) {
            if (!TRACKING_PARAMS.contains(queryName(pair))) retained.add(pair);
        }
        if (retained.size() == pairs.length) return url;
        String fragment = uri.getRawFragment();
        return url.substring(0, url.indexOf('?')) + (retained.isEmpty() ? "" : "?" + android.text.TextUtils.join("&", retained))
                + (fragment == null ? "" : "#" + fragment);
    }

    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher urls = ANY_URL_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        Map<String, String> shortLinks = new java.util.HashMap<>();
        while (urls.find()) {
            String original = urls.group();
            URI uri = parse(original);
            String host = trustedHost(uri);
            String replacement = original;
            if (uri != null && !hasAuthQuery(uri)) {
                if (!host.isEmpty() && isShortLink(uri, host)) {
                    replacement = shortLinks.get(original);
                    if (replacement == null) {
                        replacement = resolveShortUrl(uri);
                        shortLinks.put(original, replacement);
                    }
                } else if (uri.getHost() != null && uri.getRawUserInfo() == null && uri.getPort() == -1
                        && PINTEREST_WEB_HOST.matcher(uri.getHost().toLowerCase(Locale.ROOT)).matches()) {
                    replacement = stripTrackingParams(original);
                }
            }
            urls.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        return urls.appendTail(result).toString();
    }

    /**
     * Ripulisce il valore che sta per essere messo in un extra dell'intent, ma solo se l'extra è
     * {@link Intent#EXTRA_TEXT}. Per qualunque altra chiave il valore torna indietro identico.
     *
     * <p>È il punto d'aggancio universale della condivisione: invece di rincorrere le varie
     * fabbriche di intent dell'app — quella del testo, quella dell'immagine con didascalia, quella
     * dei deep link — si aggancia l'unico gesto che hanno in comune, cioè scrivere il testo
     * nell'extra. Il filtro sulla chiave sta qui a runtime e non nel patch perché nel bytecode la
     * chiave è un registro, non una costante leggibile al momento della patch.
     *
     * <p>Se l'opzione "Condividi solo il link" è accesa, al testo ripulito viene poi tolto anche il
     * contorno pubblicitario: vedi {@link #stripToUrls(String)}.
     *
     * @param key chiave dell'extra, così com'è nel registro al momento della chiamata
     * @param value valore dell'extra
     * @return il valore ripulito se la chiave è {@code EXTRA_TEXT} e c'era qualcosa da togliere,
     *     altrimenti {@code value} identico
     */
    public static String sanitizeExtra(String key, String value) {
        if (value == null || !Intent.EXTRA_TEXT.equals(key)) {
            return value;
        }

        String result = value;
        try {
            if (MorpheSettingsStore.isLinkSanitizerEnabled()) {
                result = sanitizeText(result);
            }
            if (MorpheSettingsStore.isShareLinkOnlyEnabled()) {
                result = stripToUrls(result);
            }
            if (!result.equals(value)) {
                MorpheLog.hookFired(MorpheLog.SHARE_LINK, "testo di condivisione ripulito");
            }
            return result;
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SHARE_LINK, "sanificazione non riuscita, condivido com'è", t);
            return value;
        }
    }

    /**
     * Riduce il testo di condivisione ai soli link che contiene, buttando via la prosa attorno.
     *
     * <p>Pinterest antepone al link una frase promozionale con emoji — "Take a look at this Pin!
     * ➡️", "Dai un'occhiata a questa idea" e simili. Non è una stringa dell'APK: arriva dal server
     * nel campo {@code message} della risposta di condivisione, quindi non si può togliere né dalle
     * risorse né in fase di patch. Va tolta qui, sul testo, subito prima che finisca nell'intent.
     *
     * <p>La regola è deliberatamente prudente: <b>se nel testo non c'è nessun URL, il testo torna
     * indietro intatto</b>. Meglio lasciare una frase di troppo che cancellare un messaggio che
     * l'utente aveva scritto di suo — questo hook vede tutti gli {@code EXTRA_TEXT} dell'app, non
     * solo quelli della condivisione di un pin.
     *
     * @return i soli URL trovati, separati da uno spazio; oppure {@code text} identico se non ce ne
     *     sono.
     */
    public static String stripToUrls(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder urls = new StringBuilder();
        Matcher matcher = ANY_URL_PATTERN.matcher(text);
        while (matcher.find()) {
            if (urls.length() > 0) {
                urls.append(' ');
            }
            urls.append(matcher.group());
        }

        return urls.length() == 0 ? text : urls.toString();
    }

    /**
     * Ripulisce il testo che sta per finire negli appunti di sistema.
     *
     * <p>Iniettato prima di ogni chiamata a {@code ClipboardManager.setPrimaryClip}. Il clip torna
     * indietro identico — stesso riferimento — quando non c'è niente da ripulire, così i tanti usi
     * non-URL degli appunti (testo di ricerca, bozza di commento, …) non pagano nulla.
     */
    public static ClipData sanitizeClipData(ClipData data) {
        if (data == null || data.getItemCount() != 1 || !MorpheSettingsStore.isLinkSanitizerEnabled()) {
            return data;
        }
        try {
            ClipData.Item item = data.getItemAt(0);
            CharSequence text = item == null ? null : item.getText();
            if (text == null || text.length() == 0 || item.getUri() != null
                    || item.getIntent() != null || item.getHtmlText() != null) {
                return data;
            }
            String original = text.toString();
            String cleaned = sanitizeText(original);
            if (cleaned.equals(original)) {
                return data;
            }

            MorpheLog.hookFired(MorpheLog.SHARE_LINK, "link copiato ripulito");
            return new ClipData(data.getDescription(), new ClipData.Item(cleaned));
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SHARE_LINK, "sanificazione degli appunti non riuscita", t);
            return data;
        }
    }

    // endregion
}
