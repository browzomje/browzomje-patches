package app.browzomje.patches.pinterest.share.system_share_sheet

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturnUsingParameters
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/SystemShareSheet;"

private const val PATCH_NAME = "Use the system share sheet"

/**
 * Apre il foglio di condivisione di sistema al posto di quello interno di Pinterest (issue #38).
 *
 * L'hook è **sempre** iniettato (`default = true`) e l'interruttore della schermata Morphe è
 * acceso: il foglio di sistema arriva a tutte le app installate, mentre quello interno mostra una
 * manciata di app scelte da Pinterest. Resta un interruttore perché chi usa i messaggi diretti
 * dell'app perderebbe la strada più breve per mandare un pin a un contatto.
 *
 * ### Dove ci si aggancia, e perché lì
 *
 * 1. Ai costruttori di `SendableObject`, l'oggetto Parcelable che Pinterest crea all'inizio di
 *    ogni flusso di condivisione (pin, bacheca, profilo). Da lì si estrae l'id e il tipo dell'oggetto.
 * 2. Al costruttore di `SharesheetModalAppListView`, che è **dentro** il foglio interno: quando
 *    questo viene istanziato, `SystemShareSheet` ha già l'id e il tipo dell'oggetto e può aprire
 *    il foglio di sistema Android (`ACTION_SEND`) al posto di quello interno.
 */
@Suppress("unused")
val systemShareSheetPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Opens the Android system share sheet instead of Pinterest's in-app share sheet, " +
        "when the matching Morphe setting is enabled.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Intercetta la creazione di SendableObject per catturare id e tipo
        val sendableMatches = SendableObjectConstructorFingerprint.matchAllOrNull()
        if (!sendableMatches.isNullOrEmpty()) {
            var sendableHooked = 0
            for (match in sendableMatches) {
                val method = match.method
                try {
                    method.addInstructionsBeforeEveryReturnUsingParameters(
                        "invoke-static/range { p0 .. p0 }, " +
                            "$EXTENSION_CLASS->recordSendableObject(Ljava/lang/Object;)V",
                    )
                    sendableHooked++
                } catch (e: Exception) {
                    PatchLog.warn(PATCH_NAME, "could not hook SendableObject constructor: ${e.message}")
                }
            }
            PatchLog.info(PATCH_NAME, "hooked $sendableHooked SendableObject constructor(s)")
        } else {
            PatchLog.warn(PATCH_NAME, "SendableObject constructors not found")
        }

        // 2. Intercetta l'apertura del foglio di condivisione
        val matches = SharesheetAppListConstructorFingerprint.matchAllOrNull()
        if (matches.isNullOrEmpty()) {
            PatchLog.warn(
                PATCH_NAME,
                "SharesheetModalAppListView not found: the in-app share sheet is NOT replaced.",
            )
            return@execute
        }

        var hooked = 0
        for (match in matches) {
            val method = match.method
            try {
                method.addInstructionsAfterSuperConstructor(
                    "invoke-static/range { p0 .. p0 }, " +
                        "$EXTENSION_CLASS->replaceWithSystemSheet(Ljava/lang/Object;)V",
                )
                hooked++
                PatchLog.hooked(PATCH_NAME, method, "share sheet app list, after super()")
            } catch (e: IllegalStateException) {
                // Un costruttore che delega a un altro dello stesso oggetto non chiama `super()`.
                PatchLog.info(
                    PATCH_NAME,
                    "${method.definingClass}-><init> has no super() call: skipped",
                )
            }
        }
        PatchLog.info(PATCH_NAME, "hooked $hooked share sheet constructor(s)")
    }
}
