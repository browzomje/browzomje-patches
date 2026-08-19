package app.browzomje.extension.pinterest;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/** Preferenze runtime di Morphe, lette/scritte dalla schermata impostazioni e da tutte le patch. */
public final class MorpheSettingsStore {

    private static final String TAG = "MorpheSettings";
    private static final String PREFS_NAME = "morphe_settings";

    public static final String KEY_DISABLE_ADS = "disable_ads";
    public static final String KEY_HIDE_SHOPPING_PINS = "hide_shopping_pins";
    public static final String KEY_HIDE_SEARCH_HISTORY = "hide_search_history";
    public static final String KEY_HIDE_SEARCH_BUTTON = "hide_search_button";
    public static final String KEY_HIDE_CREATE_BUTTON = "hide_create_button";
    public static final String KEY_HIDE_NOTIFICATIONS_BUTTON = "hide_notifications_button";
    public static final String KEY_HIDE_PROFILE_BUTTON = "hide_profile_button";
    public static final String KEY_DISABLE_EMAIL_CONFIRM_DIALOG = "disable_email_confirm_dialog";
    public static final String KEY_VERBOSE_LOGGING = "verbose_logging";
    public static final String KEY_BOARD_DOWNLOAD = "board_download";
    public static final String KEY_HIDE_SEARCH_BOARD_MODULES = "hide_search_board_modules";
    public static final String KEY_DISABLE_SCREENSHOT_SHARE = "disable_screenshot_share";
    public static final String KEY_SANITIZE_LINKS = "sanitize_links";
    public static final String KEY_SHARE_LINK_ONLY = "share_link_only";

    /**
     * Le impostazioni che si esportano e si importano, nell'ordine in cui compaiono a schermo.
     *
     * <p>{@link #KEY_HIDE_PROFILE_BUTTON} non c'è: non è più onorata (vedi
     * {@link #isProfileButtonHidden()}), quindi salvarla in un backup vorrebbe dire promettere
     * qualcosa che al ripristino non succede.
     */
    public static final String[] KEYS = {
        KEY_DISABLE_ADS,
        KEY_HIDE_SHOPPING_PINS,
        KEY_HIDE_SEARCH_BOARD_MODULES,
        KEY_HIDE_SEARCH_HISTORY,
        KEY_DISABLE_SCREENSHOT_SHARE,
        KEY_SANITIZE_LINKS,
        KEY_SHARE_LINK_ONLY,
        KEY_DISABLE_EMAIL_CONFIRM_DIALOG,
        KEY_BOARD_DOWNLOAD,
        KEY_HIDE_SEARCH_BUTTON,
        KEY_HIDE_CREATE_BUTTON,
        KEY_HIDE_NOTIFICATIONS_BUTTON,
        KEY_VERBOSE_LOGGING,
    };

    private MorpheSettingsStore() {}

    /**
     * Cache delle SharedPreferences. Il filtro pubblicità gira su ogni pagina del feed:
     * ricavare l'Application via reflection a ogni pin costerebbe troppo.
     */
    private static volatile SharedPreferences cachedPrefs;

    // Application corrente senza doverla passare esplicitamente dal bytecode:
    // ActivityThread.currentApplication() è API interna stabile, usata comunemente da
    // strumenti di instrumentation per questo stesso scopo.
    static Application appContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object app = activityThreadClass.getMethod("currentApplication").invoke(null);
            return (Application) app;
        } catch (Throwable t) {
            Log.e(TAG, "could not get the current Application", t);
            return null;
        }
    }

    private static SharedPreferences prefs() {
        SharedPreferences cached = cachedPrefs;
        if (cached != null) {
            return cached;
        }
        Application app = appContext();
        if (app == null) {
            return null;
        }
        cached = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedPrefs = cached;
        return cached;
    }

    public static boolean get(String key, boolean defaultValue) {
        SharedPreferences sp = prefs();
        return sp != null ? sp.getBoolean(key, defaultValue) : defaultValue;
    }

    public static void set(String key, boolean value) {
        SharedPreferences sp = prefs();
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply();
            Log.i(MorpheLog.TAG, "[settings] " + key + " = " + value);
        } else {
            Log.e(MorpheLog.TAG, "[settings] could not save " + key
                    + ": SharedPreferences not available");
        }
    }

    /**
     * Valore predefinito di una chiave, cioè quello che vale finché l'utente non tocca il
     * relativo interruttore.
     *
     * <p>Sta tutto qui, in un punto solo, perché lo stesso default serve sia ai metodi
     * {@code is…()} sia all'esportazione, che deve scrivere nel file il valore effettivo anche
     * delle opzioni mai toccate.
     */
    static boolean defaultOf(String key) {
        switch (key) {
            case KEY_DISABLE_ADS:
            case KEY_HIDE_SHOPPING_PINS:
            case KEY_DISABLE_EMAIL_CONFIRM_DIALOG:
            case KEY_BOARD_DOWNLOAD:
            case KEY_VERBOSE_LOGGING:
            case KEY_HIDE_SEARCH_BOARD_MODULES:
            case KEY_DISABLE_SCREENSHOT_SHARE:
            case KEY_SANITIZE_LINKS:
            case KEY_SHARE_LINK_ONLY:
                return true;
            default:
                return false;
        }
    }

    /** @return il valore in vigore per questa chiave, salvato o predefinito che sia. */
    public static boolean effective(String key) {
        return get(key, defaultOf(key));
    }

    public static boolean isAdsDisabled() {
        return effective(KEY_DISABLE_ADS);
    }

    /**
     * Pin di catalogo dei negozi (nome del commerciante + titolo del prodotto).
     *
     * <p>Resta un interruttore a sé rispetto a {@link #isAdsDisabled()} anche se entrambi sono
     * accesi per default: quei pin non sono annunci — Pinterest non li marca come promossi e non
     * ci mette l'etichetta "Promoted" — quindi chi usa Pinterest per comprare deve poterli
     * riavere senza rinunciare al blocco della pubblicità vera.
     */
    public static boolean isShoppingPinsHidden() {
        return effective(KEY_HIDE_SHOPPING_PINS);
    }

    public static boolean isSearchHistoryHidden() {
        return effective(KEY_HIDE_SEARCH_HISTORY);
    }

    /**
     * Carosello sponsorizzato e bacheche in evidenza nella schermata di ricerca (issue #30).
     *
     * <p>Interruttore separato da {@link #isAdsDisabled()} perché tocca solo la ricerca e perché
     * la regola sulle bacheche in evidenza è euristica: chi la trovasse troppo aggressiva deve
     * poterla spegnere senza rinunciare al blocco della pubblicità.
     */
    public static boolean isSearchBoardModulesHidden() {
        return effective(KEY_HIDE_SEARCH_BOARD_MODULES);
    }

    /**
     * Pannello "Share screenshot" che Pinterest apre dopo uno screenshot, e la rilevazione che lo
     * precede (issue #32).
     */
    public static boolean isScreenshotShareDisabled() {
        return effective(KEY_DISABLE_SCREENSHOT_SHARE);
    }

    /**
     * Ripulitura dei link che escono dall'app: parametri di tracciamento tolti dalla query e short
     * link {@code pin.it} risolti nel link canonico del pin.
     *
     * <p>È un interruttore e non una scelta fatta una volta per tutte in fase di patch perché
     * risolvere uno short link costa una richiesta di rete mentre l'utente aspetta: chi ha una
     * connessione lenta e preferisce la condivisione istantanea deve poterlo spegnere. Vedi
     * {@link UrlSanitizer}.
     */
    public static boolean isLinkSanitizerEnabled() {
        return effective(KEY_SANITIZE_LINKS);
    }

    /**
     * Condivide il solo link, senza la frase promozionale che Pinterest ci antepone ("Take a
     * look at this Pin! ➡️").
     *
     * <p>Interruttore separato da {@link #isLinkSanitizerEnabled()} perché è una decisione diversa:
     * quello toglie il tracciamento, questo toglie del testo che qualcuno potrebbe volere. Vedi
     * {@link UrlSanitizer#stripToUrls(String)}.
     */
    public static boolean isShareLinkOnlyEnabled() {
        return effective(KEY_SHARE_LINK_ONLY);
    }

    public static boolean isSearchButtonHidden() {
        return effective(KEY_HIDE_SEARCH_BUTTON);
    }

    public static boolean isCreateButtonHidden() {
        return effective(KEY_HIDE_CREATE_BUTTON);
    }

    public static boolean isNotificationsButtonHidden() {
        return effective(KEY_HIDE_NOTIFICATIONS_BUTTON);
    }

    /**
     * Sempre false: il tasto Profilo non è nascondibile, vedi {@link #isNavTabHidden(int)}.
     * Ignora anche una preferenza a true salvata da una versione precedente della patch.
     */
    public static boolean isProfileButtonHidden() {
        return false;
    }

    public static boolean isEmailConfirmDialogDisabled() {
        return effective(KEY_DISABLE_EMAIL_CONFIRM_DIALOG);
    }

    /**
     * Voce di download nel menu "…" della bacheca. Quando è attiva, i pin delle bacheche
     * visitate vengono tenuti in memoria per poterli scaricare in blocco.
     */
    public static boolean isBoardDownloadEnabled() {
        return effective(KEY_BOARD_DOWNLOAD);
    }

    /**
     * Log dettagliato in logcat ({@code adb logcat -s Morphe:V}). Acceso per default finché le
     * patch sono in collaudo su 14.28.0: senza, quando qualcosa non funziona non si capisce
     * quale hook non sia scattato.
     */
    public static boolean isVerboseLoggingEnabled() {
        return effective(KEY_VERBOSE_LOGGING);
    }

    /**
     * @param tabOrdinal ordinale nell'enum dei tab di Pinterest: 1=CREATE, 2=NOTIFICATIONS,
     *     3=PROFILE, 4=SEARCH.
     * @return true se quel tasto della barra di navigazione va nascosto.
     *
     * <p>HOME (0) e PROFILE (3) non sono nascondibili: la schermata Morphe si raggiunge solo da
     * Profilo → Impostazioni → Morphe, quindi nascondere Profilo chiuderebbe fuori dall'opzione
     * stessa per rimetterlo, lasciando come sola via d'uscita
     * {@code adb shell am start -d morphe://settings}. HOME resta l'unico modo garantito di
     * tornare al feed.
     */
    public static boolean isNavTabHidden(int tabOrdinal) {
        switch (tabOrdinal) {
            case 1:
                return isCreateButtonHidden();
            case 2:
                return isNotificationsButtonHidden();
            case 4:
                return isSearchButtonHidden();
            default:
                return false;
        }
    }
}
