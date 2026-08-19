package app.browzomje.patches.pinterest.video

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Download video"

@Suppress("unused")
val downloadVideoPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Adds a \"Download video\" option to the pin menu, saving the clip to the Downloads folder.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) Cattura del pin video corrente.
        //
        //    I registri dei parametri si scrivono per nome (`p0`, `p1`, …) e non si calcolano:
        //    l'assemblatore sa già dove stanno, e sbagliare il conto non darebbe un errore in
        //    fase di patch ma un oggetto a caso passato all'extension. Vedi
        //    addInstructionsBeforeEveryReturnUsingParameters.
        val captureMethod = VideoTracksBuilderFingerprint.method
        captureMethod.addInstructions(
            0,
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->setCurrentVideoPin(Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, captureMethod, "video pin capture")

        // 2) Cattura della mappa dei formati (uid, formati) — i primi due parametri.
        val captureMethod2 = VideoTracksBuilderFingerprint2.method
        captureMethod2.addInstructions(
            0,
            "invoke-static/range { p0 .. p1 }, $EXTENSION_CLASS->setCurrentVideoTracks(Ljava/lang/String;Ljava/util/Map;)V",
        )
        PatchLog.hooked(PATCH_NAME, captureMethod2, "video formats capture")

        // 3) La voce di menu vera e propria.
        val menuMethod = OverflowMenuBuilderFingerprint.method
        val at = menuMethod.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->addDownloadVideoOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, menuMethod, "pin menu option, after super at $at")
    }
}
