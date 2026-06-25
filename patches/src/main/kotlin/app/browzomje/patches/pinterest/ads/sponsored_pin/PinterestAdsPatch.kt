package app.browzomje.patches.pinterest

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val pinterestAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes sponsored (promoted) pins from the home feed and from search/related/board feeds.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        for (method in listOf(
            PinterestAdsFingerprint.method,
            PagedResponseConstructorFingerprint.method,
            ModelListWithBookmarkConstructorFingerprint.method
        )) {
            val instructionsList = method.implementation!!.instructions
            val returnIndex = instructionsList.indexOfFirst { it.opcode == Opcode.RETURN_VOID }
            val insertIndex = if (returnIndex != -1) returnIndex else instructionsList.size - 1

            val registerCount = method.implementation!!.registerCount
            val parameterRegisterCount = method.parameters.size + 1
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
