package app.browzomje.patches.pinterest.pin_menu.image_tools

import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val pinImageInfoPatch = bytecodePatch(
    name = "Show pin image information",
    description = "Adds optional image dimensions and format information to the pin menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute {
        val method = OverflowMenuBuilderFingerprint.method
        method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION->addPinImageInfo(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Show pin image information", method, "deferred menu option")
    }
}

@Suppress("unused")
val highestQualityImagePatch = bytecodePatch(
    name = "Download images in highest available quality",
    description = "Uses the largest image variant provided by Pinterest for the native Download image menu action.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute {
        val method = OverflowMenuBuilderFingerprint.method
        method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION->upgradePinImageDownload(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Download images in highest available quality", method, "native image action")
    }
}
