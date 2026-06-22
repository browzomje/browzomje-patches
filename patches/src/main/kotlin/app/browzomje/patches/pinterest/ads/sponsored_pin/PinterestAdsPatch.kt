package app.template.patches.pinterest

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST

@Suppress("unused")
val pinterestAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes sponsored pins.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        PinterestAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )
    }
}