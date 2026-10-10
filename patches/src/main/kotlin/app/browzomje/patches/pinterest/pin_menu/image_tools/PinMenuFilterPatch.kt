package app.browzomje.patches.pinterest.pin_menu.image_tools

import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val pinMenuFilterPatch = bytecodePatch(
    name = "Filter pin menu actions",
    description = "Hides selected native pin menu actions by their localized labels, one per line.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute {
        val method = OverflowMenuBuilderFingerprint.method
        method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, " +
                "Lapp/browzomje/extension/pinterest/PinterestUtils;->filterPinMenu(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Filter pin menu actions", method, "deferred native action filter")
    }
}
