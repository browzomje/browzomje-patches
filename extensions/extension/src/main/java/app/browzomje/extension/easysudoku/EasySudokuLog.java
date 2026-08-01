package app.browzomje.extension.easysudoku;

import android.util.Log;

/**
 * Logging delle patch Morphe per Easy Sudoku.
 *
 * <p>Stesso tag di Pinterest ({@value #TAG}) così che in fase di test basti sempre:
 *
 * <pre>adb logcat -s Morphe:V</pre>
 *
 * <p>È volutamente separato da {@code app.browzomje.extension.pinterest.MorpheLog}: quello
 * dipende da {@code MorpheSettingsStore} (la schermata impostazioni di Pinterest), che qui non
 * esiste. Tenere le due extension indipendenti evita di trascinare in Easy Sudoku codice che
 * non le appartiene.
 *
 * <p>Serve a distinguere due categorie di problema che, viste dal telefono, sembrano identiche
 * ("la patch non funziona"):
 *
 * <ol>
 *   <li><b>il fingerprint non ha trovato il metodo</b> → si vede in fase di build, nell'output
 *       di {@code easy_sudoku/build.sh} (righe {@code [morphe] <patch>: ...});
 *   <li><b>il metodo è stato agganciato ma l'hook non scatta a runtime</b> → si vede qui.
 * </ol>
 */
public final class EasySudokuLog {

    public static final String TAG = "Morphe";

    /** Canali (una costante per feature, per non sbagliare a scrivere le stringhe). */
    public static final String REWARDS = "es-rewards";

    private EasySudokuLog() {}

    public static void i(String channel, String message) {
        Log.i(TAG, "[" + channel + "] " + message);
    }

    public static void w(String channel, String message) {
        Log.w(TAG, "[" + channel + "] " + message);
    }

    public static void e(String channel, String message, Throwable t) {
        Log.e(TAG, "[" + channel + "] " + message, t);
    }
}
