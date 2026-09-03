package app.browzomje.extension.pinterest;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Apre nel browser predefinito del telefono i link che Pinterest aprirebbe nel proprio browser
 * interno (issue #35).
 *
 * <p><b>Dove si aggancia.</b> Al {@code onViewCreated} di
 * {@code com.pinterest.feature.browser.view.InAppBrowserFragment}, cioè il fragment del browser
 * interno. È un'ancora della categoria più solida che ci sia in questa app — una classe con il
 * nome vero, non offuscato — e infatti è l'unica cosa che non è cambiata fra 14.32.0 e 14.34.0 in
 * tutta questa zona: la classe che monta la sezione commenti è passata da {@code pt.v3} a
 * {@code st.x3}, quella che smista le condivisioni da {@code as2.g0} a {@code jt2.c0}, e
 * {@code InAppBrowserFragment} è rimasta {@code InAppBrowserFragment}.
 *
 * <p><b>Il prezzo dell'aggancio.</b> Intercettare qui vuol dire arrivare quando il fragment del
 * browser è già stato creato: si vede quindi un lampo della schermata interna prima che si apra il
 * browser di sistema. Agganciarsi prima avrebbe voluto dire riconoscere il punto di navigazione,
 * che è codice offuscato e sparso — decine di chiamanti diversi, uno per ogni tasto che porta a un
 * sito. Un lampo in cambio di un aggancio che non si rompe è uno scambio conveniente.
 *
 * <p><b>Come si trova l'URL.</b> Non si legge un campo per nome: nel fragment tutti i nomi sono
 * offuscati. Si cerca invece, fra i campi del fragment, l'oggetto di navigazione — riconosciuto
 * perché implementa {@code com.pinterest.navigation.Navigation}, interfaccia con il nome vero — e
 * dentro quello la prima stringa che sia un URL {@code http}/{@code https}. Su 14.34.0
 * {@code NavigationImpl} ha un solo campo di tipo {@code String} ed è proprio l'indirizzo.
 */
public final class ExternalBrowser {

    /**
     * L'interfaccia che marca l'oggetto di navigazione. Non è offuscata perché è dichiarata
     * pubblica e citata dai {@code ScreenLocation}, che il framework di navigazione di Pinterest
     * costruisce per nome.
     */
    private static final String NAVIGATION_INTERFACE = "com.pinterest.navigation.Navigation";

    /** Quanto in su si risale nella gerarchia di classi cercando i campi. */
    private static final int MAX_CLASS_DEPTH = 12;

    /**
     * Ultimo URL già dirottato e quando, per non rifarlo due volte di fila.
     *
     * <p>Serve al caso in cui la chiusura del browser interno non riesca: senza questo, il
     * fragment resterebbe in piedi, {@code onViewCreated} potrebbe scattare di nuovo e l'utente si
     * ritroverebbe il browser esterno riaperto a ripetizione. Con la finestra, il secondo tentativo
     * viene ignorato e al massimo resta aperto il browser interno — che è il comportamento
     * originale dell'app, cioè il modo giusto di fallire.
     */
    private static volatile String lastRedirectedUrl;
    private static volatile long lastRedirectAtMs;

    private static final long REDIRECT_DEBOUNCE_MS = 3000L;

    private ExternalBrowser() {}

    /**
     * Chiamata dal bytecode in testa a {@code InAppBrowserFragment.onViewCreated(View, Bundle)}.
     *
     * <p>I due parametri sono dichiarati {@code Object} e non {@code Fragment}/{@code View}: nel
     * bytecode i registri sorgente hanno il tipo statico offuscato del fragment, e dichiarare qui
     * un tipo più stretto farebbe fallire il verificatore Dalvik al caricamento della classe — cioè
     * un crash all'apertura di un link, lontano dalla patch. Il cast si fa a runtime.
     *
     * @param fragment il fragment del browser interno ({@code this} del metodo agganciato).
     * @param view la view appena creata, da cui si risale a Context e Activity.
     */
    public static void redirectToDefaultBrowser(Object fragment, Object view) {
        MorpheLog.hookFired(MorpheLog.EXTERNAL_BROWSER, "InAppBrowserFragment.onViewCreated, fragment = "
                + (fragment == null ? "null" : fragment.getClass().getName()));

        if (!MorpheSettingsStore.isExternalBrowserEnabled()) {
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "switch is off: keeping Pinterest's own browser");
            return;
        }
        if (!(view instanceof View)) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "hook received "
                    + (view == null ? "null" : view.getClass().getName())
                    + " instead of a View: nothing to do");
            return;
        }

        View root = (View) view;
        Context context = root.getContext();
        PinterestUtils.rememberActivityFrom(context);

        String url = findUrl(fragment);
        if (url == null) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "no http(s) URL found on the fragment: "
                    + "leaving the in-app browser alone");
            return;
        }
        MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "in-app browser was asked to open " + url);

        if (isPinterestOwnUrl(url)) {
            // Il browser interno serve anche a pagine di Pinterest — accesso, centro assistenza,
            // sondaggi — dove mandare fuori l'utente lo butterebbe fuori dal flusso in cui si
            // trova (e, per l'accesso, fuori dalla sessione). Quelle restano dentro.
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "this is a Pinterest page: it stays in-app");
            return;
        }

        if (isDuplicate(url)) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "already redirected " + url
                    + " a moment ago: not doing it again (did the in-app browser fail to close?)");
            return;
        }

        String finalUrl = url;
        if (MorpheSettingsStore.isLinkSanitizerEnabled()) {
            // Lo stesso trattamento che ricevono i link condivisi: il browser esterno non deve
            // ereditare i parametri di tracciamento che Pinterest attacca all'indirizzo.
            String stripped = UrlSanitizer.stripTrackingParams(url);
            if (stripped != null && !stripped.equals(url)) {
                MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "tracking parameters stripped: " + stripped);
                finalUrl = stripped;
            }
        }

        if (!open(context, finalUrl)) {
            return;
        }

        lastRedirectedUrl = url;
        lastRedirectAtMs = System.currentTimeMillis();
        MorpheLog.ok(MorpheLog.EXTERNAL_BROWSER, "opened in the default browser: " + finalUrl);

        // Il browser interno è già stato aperto quando arriviamo qui, quindi va richiuso, o
        // l'utente se lo ritrova sotto tornando indietro dal browser di sistema.
        PinterestUtils.pressBack(context, MorpheLog.EXTERNAL_BROWSER, "in-app browser");
    }

    /**
     * Intercetta la richiesta di apertura di una Chrome Custom Tab (CCT) per aprirla invece
     * nel browser predefinito esterno del sistema.
     *
     * @param cctManager l'oggetto gestore CCT (p0)
     * @param url l'URL da aprire (p1)
     * @return true se il link è stato reindirizzato con successo al browser esterno (il chiamante
     *         deve uscire immediatamente con return-void); false se CCT deve procedere normalmente.
     */
    public static boolean redirectCct(Object cctManager, String url) {
        MorpheLog.hookFired(MorpheLog.EXTERNAL_BROWSER, "ChromeCustomTabs requested, url = " + url);

        if (!MorpheSettingsStore.isExternalBrowserEnabled()) {
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "switch is off: keeping Pinterest's own browser/CCT");
            return false;
        }

        if (url == null || url.trim().isEmpty()) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "CCT URL is empty: ignoring");
            return false;
        }

        if (!isHttpUrl(url)) {
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "non-web link (" + url + "): keeping default handling");
            return false;
        }

        if (isPinterestOwnUrl(url)) {
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "this is a Pinterest page: keeping in-app CCT");
            return false;
        }

        Context context = findContextFrom(cctManager);
        if (context == null) {
            context = PinterestUtils.foregroundActivity();
        }
        if (context == null) {
            context = MorpheSettingsStore.appContext();
        }
        if (context == null) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "cannot redirect CCT: no Context or Activity found");
            return false;
        }
        PinterestUtils.rememberActivityFrom(context);

        if (isDuplicate(url)) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "already redirected CCT " + url
                    + " a moment ago: skipping to avoid duplicate launches");
            return true;
        }

        String finalUrl = url;
        if (MorpheSettingsStore.isLinkSanitizerEnabled()) {
            String stripped = UrlSanitizer.stripTrackingParams(url);
            if (stripped != null && !stripped.equals(url)) {
                MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "tracking parameters stripped: " + stripped);
                finalUrl = stripped;
            }
        }

        if (!open(context, finalUrl)) {
            return false;
        }

        lastRedirectedUrl = url;
        lastRedirectAtMs = System.currentTimeMillis();
        MorpheLog.ok(MorpheLog.EXTERNAL_BROWSER, "redirected CCT link to external browser: " + finalUrl);
        return true;
    }

    private static Context findContextFrom(Object target) {
        if (target == null) {
            return null;
        }
        if (target instanceof Context) {
            return (Context) target;
        }
        Class<?> type = target.getClass();
        for (int depth = 0; depth < MAX_CLASS_DEPTH && type != null && type != Object.class; depth++) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                type = type.getSuperclass();
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Context.class.isAssignableFrom(field.getType())) {
                    Object val = read(field, target);
                    if (val instanceof Context) {
                        return (Context) val;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /**
     * @return true se l'intent è partito. Un fallimento non è fatale: si lascia semplicemente
     *     aperto il browser interno, che è quello che l'app avrebbe fatto comunque.
     */
    private static boolean open(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            // NEW_TASK perché il browser deve vivere fuori dal task di Pinterest: senza, tornando
            // indietro dal browser si rientrerebbe nella pila di Pinterest invece di uscirne.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            // Non si usa resolveActivity() per decidere prima: da Android 11 la visibilità dei
            // pacchetti è filtrata e resolveActivity restituirebbe null anche con un browser
            // installato, a meno di dichiarare una <queries> nel manifest. Provare e raccogliere
            // l'eccezione dà la risposta giusta senza toccare il manifest.
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "no app could open " + url
                    + ": keeping Pinterest's own browser", t);
            return false;
        }
    }

    private static boolean isDuplicate(String url) {
        return url.equals(lastRedirectedUrl)
                && System.currentTimeMillis() - lastRedirectAtMs < REDIRECT_DEBOUNCE_MS;
    }

    /**
     * @return true se l'host è di Pinterest. Si guarda l'host e non l'URL intero perché
     *     {@code contains("pinterest")} direbbe di sì anche a
     *     {@code https://esempio.com/?ref=pinterest}, che è un sito qualunque.
     */
    private static boolean isPinterestOwnUrl(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(java.util.Locale.US);
            // pin.it non è qui: è lo short link di un pin, cioè esattamente il caso in cui il
            // link porta fuori.
            return host.equals("pinterest.com") || host.endsWith(".pinterest.com")
                    || host.contains(".pinterest.");
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "could not parse " + url + ": " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ ricerca dell'URL

    /**
     * Cerca l'indirizzo che il browser interno sta per aprire.
     *
     * <p>Due passi, entrambi loggati: prima l'oggetto di navigazione fra i campi del fragment, poi
     * la stringa-URL fra i campi di quell'oggetto. Se salta il primo passo il messaggio lo dice, e
     * si sa subito che a cambiare è stato il framework di navigazione e non il nome di un campo.
     */
    private static String findUrl(Object fragment) {
        if (fragment == null) {
            return null;
        }
        Object navigation = findNavigation(fragment);
        if (navigation == null) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "no field implementing " + NAVIGATION_INTERFACE
                    + " on " + fragment.getClass().getName()
                    + ": the navigation framework changed shape");
            return null;
        }
        MorpheLog.d(MorpheLog.EXTERNAL_BROWSER, "navigation object is "
                + navigation.getClass().getName());

        String url = firstUrlField(navigation);
        if (url == null) {
            MorpheLog.w(MorpheLog.EXTERNAL_BROWSER, "no String field holding an http(s) URL on "
                    + navigation.getClass().getName());
        }
        return url;
    }

    private static Object findNavigation(Object fragment) {
        Class<?> type = fragment.getClass();
        for (int depth = 0; depth < MAX_CLASS_DEPTH && type != null && type != Object.class; depth++) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                type = type.getSuperclass();
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = read(field, fragment);
                if (value != null && implementsInterface(value.getClass(), NAVIGATION_INTERFACE)) {
                    return value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String firstUrlField(Object navigation) {
        Class<?> type = navigation.getClass();
        for (int depth = 0; depth < MAX_CLASS_DEPTH && type != null && type != Object.class; depth++) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                type = type.getSuperclass();
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                Object value = read(field, navigation);
                if (value instanceof String && isHttpUrl((String) value)) {
                    return (String) value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /**
     * @return true se {@code type}, o una qualunque delle sue superclassi, implementa
     *     l'interfaccia di nome {@code interfaceName}.
     *
     * <p>Si confrontano i <b>nomi</b> invece di usare {@code Class.forName(...).isInstance(...)}
     * per non dipendere da quale ClassLoader abbia caricato l'interfaccia: il codice dell'extension
     * viene fuso nell'APK ma non è detto che veda le classi dell'app dallo stesso loader in ogni
     * situazione. Il nome, per un'interfaccia non offuscata, è un'ancora sufficiente.
     */
    private static boolean implementsInterface(Class<?> type, String interfaceName) {
        for (int depth = 0; depth < MAX_CLASS_DEPTH && type != null && type != Object.class; depth++) {
            for (Class<?> each : type.getInterfaces()) {
                if (interfaceName.equals(each.getName())
                        || implementsInterface(each, interfaceName)) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
