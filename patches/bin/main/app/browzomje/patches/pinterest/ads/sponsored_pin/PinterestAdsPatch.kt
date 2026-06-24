package app.template.patches.pinterest

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val pinterestAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes sponsored (promoted) pins from the home feed and from search/related/board feeds.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // At the end of a feed-model constructor, hand `this` to the extension, which strips every
        // promoted pin from the model's stored item list. Two entry points cover the whole app:
        //   o12.e   -> the legacy home-feed page
        //   vr1.i0  -> the generic paged response behind every multi-section grid (search, etc.)
        for (method in listOf(PinterestAdsFingerprint.method, PagedResponseConstructorFingerprint.method)) {
            val instructionsList = method.implementation!!.instructions
            val returnIndex = instructionsList.indexOfFirst { it.opcode == Opcode.RETURN_VOID }
            val insertIndex = if (returnIndex != -1) returnIndex else instructionsList.size - 1

            val registerCount = method.implementation!!.registerCount
            val parameterRegisterCount = method.parameters.size + 1 // +1 for `this`
            val p0RegisterIndex = registerCount - parameterRegisterCount

            val compiled = InlineSmaliCompiler.compile(
                "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                    "$EXTENSION_CLASS->filterSponsoredPinsFromFeed(Ljava/lang/Object;)V",
                "",
                registerCount,
                true,
            )
            method.addInstructions(insertIndex, compiled)
        }
    }
}
