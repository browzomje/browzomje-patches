package app.browzomje.patches.pinterest.video

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val downloadVideoPatch = bytecodePatch(
    name = "Download video",
    description = "Adds a 'Download video' option to the pin menu for video pins, saving the clip to the Downloads folder.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val captureMethod = VideoTracksBuilderFingerprint.method
        val captureImpl = captureMethod.implementation
            ?: throw Exception("VideoTracksBuilder (d.w) has no implementation")
        val captureRegisterCount = captureImpl.registerCount
        val captureParamRegisterCount = captureMethod.parameters.size
        val pinRegister = captureRegisterCount - captureParamRegisterCount

        captureMethod.addInstructions(
            0,
            "invoke-static/range { v$pinRegister .. v$pinRegister }, " +
                "$EXTENSION_CLASS->setCurrentVideoPin(Ljava/lang/Object;)V",
        )

        val captureMethod2 = VideoTracksBuilderFingerprint2.method
        val captureImpl2 = captureMethod2.implementation
            ?: throw Exception("VideoTracksBuilder (d.p) has no implementation")
        val captureRegisterCount2 = captureImpl2.registerCount
        val captureParamRegisterCount2 = captureMethod2.parameters.size
        val uidRegister = captureRegisterCount2 - captureParamRegisterCount2
        val listRegister = uidRegister + 1

        captureMethod2.addInstructions(
            0,
            "invoke-static/range { v$uidRegister .. v$listRegister }, " +
                "$EXTENSION_CLASS->setCurrentVideoTracks(Ljava/lang/String;Ljava/util/Map;)V",
        )

        val menuMethod = VideoOverflowMenuBuilderFingerprint.method
        val returnVoidIndex = menuMethod.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_VOID
        }
        val insertIndex = if (returnVoidIndex != -1) {
            returnVoidIndex
        } else {
            menuMethod.implementation!!.instructions.size - 1
        }

        val menuRegisterCount = menuMethod.implementation!!.registerCount
        val menuParameterRegisterCount = menuMethod.parameters.size + 1
        val p0RegisterIndex = menuRegisterCount - menuParameterRegisterCount

        val menuInstructions = InlineSmaliCompiler.compile(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                "$EXTENSION_CLASS->addDownloadVideoOption(Ljava/lang/Object;)V",
            "",
            menuRegisterCount,
            true,
        )
        menuMethod.addInstructions(insertIndex, menuInstructions)
    }
}
