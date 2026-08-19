package app.browzomje.extension.pinterest;

import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logging unificato di tutte le patch Morphe.
 *
 * <p>Tutto passa da un unico tag, {@value #TAG}, così che in fase di test basti:
 *
 * <pre>adb logcat -s Morphe:V</pre>
 *
 * <p>Ogni messaggio è preceduto da un "canale" (il nome della feature: {@code ads},
 * {@code navbar}, …) per capire a colpo d'occhio da dove arriva.
 *
 * <p>Oltre al log, questa classe tiene un piccolo registro in memoria di quali hook sono
 * effettivamente scattati e con quale esito: la schermata Morphe lo mostra, così si vede
 * cosa funziona anche senza avere un PC collegato.
 */
public final class MorpheLog {

    public static final String TAG = "Morphe";

    /** Canali (una costante per feature, per non sbagliare a scrivere le stringhe). */
    public static final String ADS = "ads";
    public static final String NAVBAR = "navbar";
    /** Tasti in alto a destra nel feed (Crea, Posta/Notifiche), distinti da {@link #NAVBAR}. */
    public static final String HEADER = "header";
    public static final String SEARCH_HISTORY = "search-history";
    /** Moduli a bacheche della schermata di ricerca (issue #30), distinti da {@link #ADS}. */
    public static final String SEARCH_MODULES = "search-modules";
    /** Rilevamento screenshot e pannello di condivisione che ne segue (issue #32). */
    public static final String SCREENSHOT = "screenshot";
    public static final String SETTINGS_ENTRY = "settings-entry";
    public static final String EMAIL_DIALOG = "email-dialog";
    public static final String COPY_LINK = "copy-link";
    /**
     * Ripulitura dei link in uscita — foglio di condivisione e appunti — distinta da
     * {@link #COPY_LINK}, che è la voce "Copia link diretto" del menu del pin.
     */
    public static final String SHARE_LINK = "share-link";
    public static final String WALLPAPER = "wallpaper";
    public static final String VIDEO = "video";
    public static final String BOARD = "board";
    /** Voce di download nel menu "…" della bacheca, distinta da {@link #BOARD} che è la raccolta dei pin. */
    public static final String BOARD_MENU = "board-menu";
    public static final String REFLECTION = "reflection";

    /** canale -> ultimo esito registrato, in ordine di prima comparsa. */
    private static final Map<String, String> STATUS = new LinkedHashMap<>();

    /** canale -> quante volte l'hook è scattato. */
    private static final Map<String, Integer> HITS = new LinkedHashMap<>();

    private MorpheLog() {}

    public static void d(String channel, String message) {
        if (MorpheSettingsStore.isVerboseLoggingEnabled()) {
            Log.d(TAG, "[" + channel + "] " + message);
        }
    }

    public static void i(String channel, String message) {
        Log.i(TAG, "[" + channel + "] " + message);
    }

    public static void w(String channel, String message) {
        Log.w(TAG, "[" + channel + "] " + message);
    }

    public static void w(String channel, String message, Throwable t) {
        Log.w(TAG, "[" + channel + "] " + message, t);
    }

    public static void e(String channel, String message, Throwable t) {
        Log.e(TAG, "[" + channel + "] " + message, t);
        setStatus(channel, "ERROR: " + message + " (" + describe(t) + ")");
    }

    public static void e(String channel, String message) {
        Log.e(TAG, "[" + channel + "] " + message);
        setStatus(channel, "ERROR: " + message);
    }

    /**
     * Da chiamare all'ingresso di ogni hook: conta l'invocazione e, la prima volta, logga in
     * modo evidente che l'aggancio bytecode → extension ha funzionato. Se una feature non
     * compare mai in logcat, il problema è nel fingerprint (l'hook non è stato iniettato o il
     * metodo agganciato non viene mai eseguito), non nell'extension.
     */
    public static void hookFired(String channel, String detail) {
        Integer previous = HITS.get(channel);
        int count = previous == null ? 1 : previous + 1;
        HITS.put(channel, count);
        if (count == 1) {
            Log.i(TAG, "[" + channel + "] hook live (first call) — " + detail);
            setStatus(channel, "hook live");
        } else {
            d(channel, "hook #" + count + " — " + detail);
        }
    }

    public static void setStatus(String channel, String status) {
        STATUS.put(channel, status);
    }

    public static void ok(String channel, String detail) {
        setStatus(channel, "ok — " + detail);
        i(channel, detail);
    }

    /** Snapshot leggibile dello stato, mostrato dalla schermata Morphe. */
    public static String dumpStatus() {
        if (STATUS.isEmpty()) {
            return MorpheStrings.get(MorpheStrings.DIAGNOSTICS_EMPTY);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : STATUS.entrySet()) {
            Integer hits = HITS.get(entry.getKey());
            sb.append(entry.getKey())
                    .append(" (")
                    .append(hits == null ? 0 : hits)
                    .append("x): ")
                    .append(entry.getValue())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "null";
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
