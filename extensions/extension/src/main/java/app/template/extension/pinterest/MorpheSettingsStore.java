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
    public static final String KEY_HIDE_SEARCH_HISTORY = "hide_search_history";
    public static final String KEY_HIDE_SEARCH_BUTTON = "hide_search_button";
    public static final String KEY_HIDE_CREATE_BUTTON = "hide_create_button";
    public static final String KEY_HIDE_NOTIFICATIONS_BUTTON = "hide_notifications_button";
    public static final String KEY_HIDE_PROFILE_BUTTON = "hide_profile_button";
    public static final String KEY_DISABLE_EMAIL_CONFIRM_DIALOG = "disable_email_confirm_dialog";
    public static final String KEY_VERBOSE_LOGGING = "verbose_logging";
    public static final String KEY_BOARD_DOWNLOAD = "board_download";

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
            Log.e(TAG, "Impossibile ottenere l'Application corrente", t);
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
            Log.e(MorpheLog.TAG, "[settings] impossibile salvare " + key
                    + ": SharedPreferences non disponibili");
        }
    }

    public static boolean isAdsDisabled() {
        return get(KEY_DISABLE_ADS, true);
    }

    public static boolean isSearchHistoryHidden() {
        return get(KEY_HIDE_SEARCH_HISTORY, false);
    }

    public static boolean isSearchButtonHidden() {
        return get(KEY_HIDE_SEARCH_BUTTON, false);
    }

    public static boolean isCreateButtonHidden() {
        return get(KEY_HIDE_CREATE_BUTTON, false);
    }

    public static boolean isNotificationsButtonHidden() {
        return get(KEY_HIDE_NOTIFICATIONS_BUTTON, false);
    }

    /**
     * Sempre false: il tasto Profilo non è nascondibile, vedi {@link #isNavTabHidden(int)}.
     * Ignora anche una preferenza a true salvata da una versione precedente della patch.
     */
    public static boolean isProfileButtonHidden() {
        return false;
    }

    public static boolean isEmailConfirmDialogDisabled() {
        return get(KEY_DISABLE_EMAIL_CONFIRM_DIALOG, true);
    }

    /**
     * Voce di download nel menu "…" della bacheca. Quando è attiva, i pin delle bacheche
     * visitate vengono tenuti in memoria per poterli scaricare in blocco.
     */
    public static boolean isBoardDownloadEnabled() {
        return get(KEY_BOARD_DOWNLOAD, true);
    }

    /**
     * Log dettagliato in logcat ({@code adb logcat -s Morphe:V}). Acceso per default finché le
     * patch sono in collaudo su 14.28.0: senza, quando qualcosa non funziona non si capisce
     * quale hook non sia scattato.
     */
    public static boolean isVerboseLoggingEnabled() {
        return get(KEY_VERBOSE_LOGGING, true);
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
