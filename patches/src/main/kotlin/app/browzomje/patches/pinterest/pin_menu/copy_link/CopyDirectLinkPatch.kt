package app.template.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/WallpaperUtils;"

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
        val returnVoidIndex = method.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_VOID
        }
        val insertIndex = if (returnVoidIndex != -1) returnVoidIndex else method.implementation!!.instructions.size - 1

        val registerCount = method.implementation!!.registerCount
        val parameterRegisterCount = method.parameters.size + 1
        val p0RegisterIndex = registerCount - parameterRegisterCount

        val instructions = InlineSmaliCompiler.compile(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, $EXTENSION_CLASS->addCopyLinkOption(Ljava/lang/Object;)V",
            "",
            registerCount,
            true
        )
        method.addInstructions(insertIndex, instructions)
    }
}
