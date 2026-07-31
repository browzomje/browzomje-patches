package app.browzomje.extension.pinterest;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Esportazione e importazione delle impostazioni Morphe come file JSON.
 *
 * <p>Serve a non dover rimettere a mano tutti gli interruttori dopo ogni ripatch: l'APK patchato
 * si reinstalla da capo a ogni nuova build, e con esso se ne vanno le SharedPreferences.
 *
 * <p>Il formato è leggibile e modificabile a mano di proposito — è un file di configurazione, non
 * un dump binario:
 *
 * <pre>
 * {
 *   "morphe": { "format": 1, "exported_at": "…", "app_version": "14.28.0" },
 *   "settings": { "disable_ads": true, "hide_search_history": true, … }
 * }
 * </pre>
 *
 * <p>All'importazione conta solo l'oggetto {@code settings}, e di quello solo le chiavi note:
 * un file scritto da una versione più vecchia (o più nuova) di Morphe si applica comunque, per
 * la parte che questa versione conosce, invece di essere rifiutato in blocco.
 */
final class MorpheSettingsBackup {

    /** Versione del formato. Da alzare solo se cambia il significato di una chiave già usata. */
    private static final int FORMAT = 1;

    private static final String OBJECT_HEADER = "morphe";
    private static final String OBJECT_SETTINGS = "settings";

    private MorpheSettingsBackup() {}

    /**
     * @return il nome proposto per il file esportato, es. {@code pinterest_morphe_2026-07-30.json}.
     *     La data è quella odierna, così più export finiscono in file diversi senza sovrascriversi
     *     e si riconosce a colpo d'occhio quale sia il più recente.
     */
    static String fileName() {
        return "pinterest_morphe_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                + ".json";
    }

    /**
     * @return il contenuto del file da scrivere: tutte le impostazioni di
     *     {@link MorpheSettingsStore#KEYS} col valore in vigore, comprese quelle mai toccate
     *     dall'utente, che valgono il proprio default.
     */
    static String export(Context context) throws JSONException {
        JSONObject settings = new JSONObject();
        for (String key : MorpheSettingsStore.KEYS) {
            settings.put(key, MorpheSettingsStore.effective(key));
        }

        JSONObject header = new JSONObject();
        header.put("format", FORMAT);
        header.put("exported_at",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
        header.put("app_version", appVersion(context));

        JSONObject root = new JSONObject();
        root.put(OBJECT_HEADER, header);
        root.put(OBJECT_SETTINGS, settings);
        return root.toString(2);
    }

    /**
     * Applica un file esportato in precedenza.
     *
     * <p>Le chiavi sconosciute vengono ignorate in silenzio, ma un file che non contiene
     * <em>nessuna</em> chiave nota viene rifiutato: quasi sempre significa che è stato scelto il
     * file sbagliato, e applicarlo lasciando tutto com'è sarebbe un successo apparente.
     *
     * @return quante impostazioni sono state applicate.
     * @throws JSONException se il file non è JSON o non ha la forma attesa.
     */
    static int apply(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject settings = root.optJSONObject(OBJECT_SETTINGS);
        if (settings == null) {
            throw new JSONException("no \"" + OBJECT_SETTINGS + "\" object: "
                    + "this is not a Morphe settings file");
        }

        int applied = 0;
        for (String key : MorpheSettingsStore.KEYS) {
            if (!settings.has(key)) {
                continue;
            }
            MorpheSettingsStore.set(key, settings.getBoolean(key));
            applied++;
        }

        if (applied == 0) {
            throw new JSONException("no known setting in the file");
        }
        MorpheLog.ok(MorpheLog.SETTINGS_ENTRY, "imported " + applied + " settings out of "
                + MorpheSettingsStore.KEYS.length);
        return applied;
    }

    /** Legge tutto lo stream in una stringa. I file sono di poche centinaia di byte. */
    static String readAll(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("file not readable");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }

    /** @return la versione di Pinterest, o {@code "?"} se non leggibile: è solo informativa. */
    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "?" : info.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
}
