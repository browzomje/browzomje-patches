package app.browzomje.extension.pinterest;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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

    // region Espressioni regolari: dove cercare i link dentro un testo libero

    /** {@code https://pin.it/<slug>}. */
    private static final Pattern PIN_IT_PATTERN =
            Pattern.compile("https?://pin\\.it/[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);

    /**
     * Accorciatore lato server: {@code https://<qualcosa>pinterest.<tld>/url_shortener/<slug>/redirect/}.
     * Porta un'impronta per-condivisione nello slug, esattamente come {@code pin.it}.
     */
    private static final Pattern URL_SHORTENER_PATTERN = Pattern.compile(
            "https?://[a-z0-9.-]*pinterest\\.[a-z.]{2,10}/url_shortener/[A-Za-z0-9_-]+/redirect/?",
            Pattern.CASE_INSENSITIVE);

    /**
     * Qualunque URL http(s), non solo di Pinterest. Serve a {@link #stripToUrls(String)}: il testo
     * di condivisione può contenere il link al sito d'origine del pin, che va tenuto.
     */
    private static final Pattern ANY_URL_PATTERN =
            Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    /** Qualunque URL pinterest.* (qualsiasi TLD, sottodominio, percorso). */
    private static final Pattern PINTEREST_URL_PATTERN =
            Pattern.compile("https?://[a-z0-9.-]*pinterest\\.[a-z.]{2,10}/[^\\s]*",
                    Pattern.CASE_INSENSITIVE);

    // endregion

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

    /**
     * L'accorciatore {@code /url_shortener/<slug>/redirect/} risponde {@code 200 OK} e fa il
     * redirect vero via JavaScript quando lo si interroga su {@code www.pinterest.com}: con una
     * HEAD non si arriva da nessuna parte. Sullo stesso percorso ma sull'host {@code api.} il
     * server risponde invece con un {@code 302} il cui header {@code Location} è già l'URL del pin.
     * Prima di partire riscriviamo quindi l'host.
     */
    private static final Pattern URL_SHORTENER_REWRITE_TARGET = Pattern.compile(
            "^(https?://)([a-z0-9.-]*)(pinterest\\.[a-z.]{2,10}/url_shortener/[A-Za-z0-9_-]+/redirect/?)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Prefisso {@code /pin/<id>} nel percorso risolto. Pinterest ci accoda spesso {@code /sent/} e
     * una query ({@code ?invite_code=…&sender=…&sfo=1}) che sono impronte per-condivisione: si
     * butta via tutto quello che viene dopo l'id.
     */
    private static final Pattern PIN_PATH_PATTERN =
            Pattern.compile("^(/pin/[0-9]+)/?", Pattern.CASE_INSENSITIVE);

    private static final ExecutorService RESOLVER = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable, "morphe-url-resolver");
        t.setDaemon(true);
        return t;
    });

    /**
     * Segue la catena di redirect dello short link con richieste {@code HEAD} successive, finché non
     * arriva una risposta non-3xx o finché non si esauriscono i {@link #MAX_REDIRECT_HOPS} salti.
     *
     * @return il link canonico {@code https://pinterest.com/pin/<id>/} se ci si è arrivati, oppure
     *     lo short link originale in ogni altro caso (rete assente, timeout, destinazione che non è
     *     un pin, …).
     */
    private static String resolveShortUrl(final String shortUrl) {
        String cached = RESOLVED_CACHE.get(shortUrl);
        if (cached != null) {
            return cached;
        }

        String resolved = shortUrl;
        try {
            Future<String> future = RESOLVER.submit(new Callable<String>() {
                @Override
                public String call() {
                    String current = shortUrl;
                    Matcher rewrite = URL_SHORTENER_REWRITE_TARGET.matcher(current);
                    if (rewrite.matches()) {
                        current = rewrite.group(1) + "api." + rewrite.group(3);
                    }

                    for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
                        HttpURLConnection conn = null;
                        try {
                            conn = (HttpURLConnection) new URL(current).openConnection();
                            conn.setInstanceFollowRedirects(false);
                            conn.setConnectTimeout(RESOLVE_TIMEOUT_MS);
                            conn.setReadTimeout(RESOLVE_TIMEOUT_MS);
                            conn.setRequestMethod("HEAD");
                            // Uno user agent da browser normale: con quello di un'app Android
                            // Pinterest risponde con un deep link che aprirebbe l'app sul telefono
                            // di chi riceve, invece della pagina web.
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                            conn.setRequestProperty("Accept", "*/*");

                            int status = conn.getResponseCode();
                            if (status >= 300 && status < 400) {
                                String location = conn.getHeaderField("Location");
                                if (location == null || location.isEmpty()) {
                                    break;
                                }
                                current = location;
                                continue;
                            }
                            break;
                        } catch (Throwable ignored) {
                            break;
                        } finally {
                            if (conn != null) {
                                try {
                                    conn.disconnect();
                                } catch (Throwable ignored) {
                                    // niente da fare
                                }
                            }
                        }
                    }

                    try {
                        Uri terminal = Uri.parse(current);
                        String host = terminal.getHost();
                        if (host == null || !host.toLowerCase(java.util.Locale.ROOT).contains("pinterest")) {
                            return shortUrl;
                        }
                        String path = terminal.getEncodedPath();
                        if (path == null) {
                            return shortUrl;
                        }
                        Matcher pinMatch = PIN_PATH_PATTERN.matcher(path);
                        if (!pinMatch.find()) {
                            return shortUrl;
                        }
                        return "https://pinterest.com" + pinMatch.group(1) + "/";
                    } catch (Throwable ignored) {
                        return shortUrl;
                    }
                }
            });

            resolved = future.get(TOTAL_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            // Timeout o errore: si tiene il link originale e non si mette nulla in cache, così il
            // prossimo tentativo (magari con la rete tornata) ci riprova.
            MorpheLog.d(MorpheLog.COPY_LINK, "short link non risolto, resta l'originale: " + t);
            return shortUrl;
        }

        if (RESOLVED_CACHE.size() < CACHE_MAX_ENTRIES) {
            RESOLVED_CACHE.put(shortUrl, resolved);
        }
        return resolved;
    }

    // endregion

    // region Sanificatori

    /** @return {@code url} senza i parametri di tracciamento. Gli altri parametri restano. */
    public static String stripTrackingParams(String url) {
        if (url == null || url.isEmpty() || url.indexOf('?') < 0) {
            return url;
        }
        try {
            Uri uri = Uri.parse(url);
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) {
                return url;
            }

            Uri.Builder builder = uri.buildUpon().clearQuery();
            boolean modified = false;

            for (String key : uri.getQueryParameterNames()) {
                if (TRACKING_PARAMS.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                    modified = true;
                    continue;
                }
                List<String> values = uri.getQueryParameters(key);
                if (values.isEmpty()) {
                    builder.appendQueryParameter(key, "");
                } else {
                    for (String value : values) {
                        builder.appendQueryParameter(key, value);
                    }
                }
            }

            return modified ? builder.build().toString() : url;
        } catch (Throwable ignored) {
            return url;
        }
    }

    /**
     * Ripulisce un testo libero, che può contenere o meno dei link.
     *
     * <ol>
     *   <li>ogni short link trovato viene risolto nella sua forma canonica;
     *   <li>a ogni URL pinterest.* rimasto vengono tolti i parametri di tracciamento.
     * </ol>
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Gli short link si deduplicano prima di risolverli: se lo stesso slug compare due volte
        // nel testo, la richiesta di rete resta una sola.
        Set<String> shortUrls = new HashSet<>();
        Matcher pinItMatcher = PIN_IT_PATTERN.matcher(text);
        while (pinItMatcher.find()) {
            shortUrls.add(pinItMatcher.group());
        }
        Matcher shortenerMatcher = URL_SHORTENER_PATTERN.matcher(text);
        while (shortenerMatcher.find()) {
            shortUrls.add(shortenerMatcher.group());
        }
        for (String shortUrl : shortUrls) {
            String resolved = resolveShortUrl(shortUrl);
            if (!resolved.equals(shortUrl)) {
                result = result.replace(shortUrl, resolved);
            }
        }

        Set<String> pinterestUrls = new HashSet<>();
        Matcher pinterestMatcher = PINTEREST_URL_PATTERN.matcher(result);
        while (pinterestMatcher.find()) {
            pinterestUrls.add(pinterestMatcher.group());
        }
        for (String pinterestUrl : pinterestUrls) {
            String cleaned = stripTrackingParams(pinterestUrl);
            if (!cleaned.equals(pinterestUrl)) {
                result = result.replace(pinterestUrl, cleaned);
            }
        }

        return result;
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
        if (data == null || data.getItemCount() == 0 || !MorpheSettingsStore.isLinkSanitizerEnabled()) {
            return data;
        }
        try {
            ClipData.Item item = data.getItemAt(0);
            CharSequence text = item == null ? null : item.getText();
            if (text == null || text.length() == 0) {
                return data;
            }
            String original = text.toString();
            String cleaned = sanitizeText(original);
            if (cleaned.equals(original)) {
                return data;
            }

            String label = data.getDescription() != null && data.getDescription().getLabel() != null
                    ? data.getDescription().getLabel().toString()
                    : "Text";
            MorpheLog.hookFired(MorpheLog.SHARE_LINK, "link copiato ripulito");
            return ClipData.newPlainText(label, cleaned);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SHARE_LINK, "sanificazione degli appunti non riuscita", t);
            return data;
        }
    }

    // endregion
}
