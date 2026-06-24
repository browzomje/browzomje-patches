package app.template.patches.pinterest

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/WallpaperUtils;"

@Suppress("unused")
val pinterestAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes sponsored (promoted) pins from the feed by stripping them from each feed page.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // Hook the feed-page constructor (o12.e) and, once the items ArrayList is populated,
        // hand `this` to the extension which removes every promoted pin from the stored list.
        // Done at the end of <init> so this.f109367a is already assigned.
        val method = PinterestAdsFingerprint.method
        val returnIndex = method.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_VOID
        }
        val insertIndex = if (returnIndex != -1) {
            returnIndex
        } else {
            method.implementation!!.instructions.size - 1
        }

        val registerCount = method.implementation!!.registerCount
        val parameterRegisterCount = method.parameters.size + 1 // +1 for `this`
        val p0RegisterIndex = registerCount - parameterRegisterCount

        val instructions = InlineSmaliCompiler.compile(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                "$EXTENSION_CLASS->filterSponsoredPinsFromFeed(Ljava/lang/Object;)V",
            "",
            registerCount,
            true,
        )
        method.addInstructions(insertIndex, instructions)
    }
}
