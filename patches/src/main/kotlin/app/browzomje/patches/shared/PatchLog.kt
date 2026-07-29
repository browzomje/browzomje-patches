package app.browzomje.patches.shared

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

/**
 * Log di *build*, stampato mentre le patch vengono applicate all'APK.
 *
 * Serve a distinguere due categorie di problema che, viste dal telefono, sembrano identiche
 * ("la funzione non va"):
 *
 * 1. **il fingerprint non ha trovato il metodo** → si vede qui, in fase di build;
 * 2. **il metodo è stato agganciato ma l'hook non scatta / fallisce a runtime** → si vede in
 *    `adb logcat -s Morphe:V`, grazie a `MorpheLog` nell'extension.
 *
 * Ogni patch stampa quindi su quale classe/metodo offuscato si è agganciata: quando esce una
 * nuova versione di Pinterest, il diff di questo output dice subito cosa è cambiato.
 */
object PatchLog {

    private const val PREFIX = "[morphe]"

    fun info(patch: String, message: String) {
        println("$PREFIX $patch: $message")
    }

    fun warn(patch: String, message: String) {
        println("$PREFIX $patch: WARNING — $message")
    }

    /** Log which obfuscated method a patch hooked into. */
    fun hooked(patch: String, method: MutableMethod, detail: String = "") {
        val where = "${method.definingClass}->${method.name}"
        val suffix = if (detail.isEmpty()) "" else " ($detail)"
        info(patch, "hooked to $where$suffix")
    }
}
