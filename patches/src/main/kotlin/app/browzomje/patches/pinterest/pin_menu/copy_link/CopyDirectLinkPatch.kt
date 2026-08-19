package app.browzomje.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn
import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val copyDirectLinkPatch = bytecodePatch(
    name = "Copy direct link",
    description = "Adds a 'Copy direct link' option to the pin menu to copy a link under the direct CDN media format instead of the standard Pinterest web link.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val method = OverflowMenuBuilderFingerprint.method
        val registerCount = method.implementation!!.registerCount
        val p0RegisterIndex = registerCount - (method.parameters.size + 1)

        val exits = method.addInstructionsBeforeEveryReturn(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                "$EXTENSION_CLASS->addCopyLinkOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Copy direct link", method, "pin menu option, $exits exits")
    }
}
