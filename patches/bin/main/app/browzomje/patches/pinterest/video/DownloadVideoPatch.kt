package app.browzomje.patches.pinterest.video

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Download video"

@Suppress("unused")
val downloadVideoPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Adds a 'Download video' option to the pin menu for video pins, saving the clip to the Downloads folder.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) Cattura del pin video corrente. Metodo statico: i registri dei parametri partono
        //    da registerCount - numeroParametri (non c'è "this").
        val captureMethod = VideoTracksBuilderFingerprint.method
        val captureImpl = captureMethod.implementation
            ?: throw Exception("Il builder delle tracce video non ha implementazione")
        val captureRegisterCount = captureImpl.registerCount
        val pinRegister = captureRegisterCount - captureMethod.parameters.size

        captureMethod.addInstructions(
            0,
            "invoke-static/range { v$pinRegister .. v$pinRegister }, " +
                "$EXTENSION_CLASS->setCurrentVideoPin(Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, captureMethod, "cattura del pin video")

        // 2) Cattura della mappa dei formati (uid, formati) — i primi due parametri.
        val captureMethod2 = VideoTracksBuilderFingerprint2.method
        val captureImpl2 = captureMethod2.implementation
            ?: throw Exception("Il builder delle tracce video (8 parametri) non ha implementazione")
        val captureRegisterCount2 = captureImpl2.registerCount
        val uidRegister = captureRegisterCount2 - captureMethod2.parameters.size
        val listRegister = uidRegister + 1

        captureMethod2.addInstructions(
            0,
            "invoke-static/range { v$uidRegister .. v$listRegister }, " +
                "$EXTENSION_CLASS->setCurrentVideoTracks(Ljava/lang/String;Ljava/util/Map;)V",
        )
        PatchLog.hooked(PATCH_NAME, captureMethod2, "cattura dei formati video")

        // 3) La voce di menu vera e propria.
        val menuMethod = VideoOverflowMenuBuilderFingerprint.method
        val menuRegisterCount = menuMethod.implementation!!.registerCount
        val p0RegisterIndex = menuRegisterCount - (menuMethod.parameters.size + 1)

        val menuExits = menuMethod.addInstructionsBeforeEveryReturn(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                "$EXTENSION_CLASS->addDownloadVideoOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, menuMethod, "voce nel menu del pin, $menuExits uscite")
    }
}
