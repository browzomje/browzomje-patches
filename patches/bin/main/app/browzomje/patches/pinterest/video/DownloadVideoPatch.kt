package app.template.patches.pinterest.video

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/WallpaperUtils;"

@Suppress("unused")
val downloadVideoPatch = bytecodePatch(
    name = "Download video",
    description = "Adds a 'Download video' option to the pin menu for video pins, saving the clip to the Downloads folder.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) Capture the Pin's downloadable video URL.
        //    com.bumptech.glide.d.w(me pin, Integer, cu2.n) is STATIC, so `pin` is parameter p0.
        //    We hand the Pin to the extension, which reads its video_list and stores a real .mp4
        //    URL keyed by the pin uid.
        val captureMethod = VideoTracksBuilderFingerprint.method
        val captureImpl = captureMethod.implementation
            ?: throw Exception("VideoTracksBuilder (d.w) has no implementation")
        val captureRegisterCount = captureImpl.registerCount
        val captureParamRegisterCount = captureMethod.parameters.size // static: no `this`
        // p0 (the Pin) = first parameter register.
        val pinRegister = captureRegisterCount - captureParamRegisterCount

        captureMethod.addInstructions(
            0,
            "invoke-static/range { v$pinRegister .. v$pinRegister }, " +
                "$EXTENSION_CLASS->setCurrentVideoPin(Ljava/lang/Object;)V",
        )

        // 2) Inject the menu row. Same anchor as Copy direct link: the uz0.z constructor.
        //    addDownloadVideoOption(this) — the extension only adds the row when a video URL is set,
        //    so the option appears exclusively on video pins.
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
