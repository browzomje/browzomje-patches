package app.browzomje.patches.pinterest.pin_menu.image_tools

import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.Fingerprint
import app.browzomje.patches.shared.returnVoidWhen
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

private const val EXTENSION = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val pinImageInfoPatch = bytecodePatch(
    name = "Show pin image information",
    description = "Shows optional image dimensions and format below the image or instead of like/comment actions.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute {
        val method = PinModuleUpdateFingerprint.method
        method.addInstructions(0,
            "invoke-static/range { p0 .. p1 }, " +
                "Lapp/browzomje/extension/pinterest/PinImageDetails;->bind(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Show pin image information", method, "bind inline metadata to module Pin")
    }
}

@Suppress("unused")
val highestQualityImagePatch = bytecodePatch(
    name = "Download images in highest available quality",
    description = "Checks for an original image before downloading, with automatic fallback to the largest available app variant.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(pinImageInfoPatch)
    extendWith("extensions/extension.mpe")
    execute {
        val method = OverflowMenuBuilderFingerprint.method
        method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION->upgradePinImageDownload(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Download images in highest available quality", method, "native image action")
        val native = NativeImageDownloadFingerprint.method
        NativeImageDownloadConstructorFingerprint.method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p2 }, " +
                "Lapp/browzomje/extension/pinterest/NativeImageDownload;->capture(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V",
        )
        check(native.returnVoidWhen(
            "invoke-static/range { p0 .. p3 }, " +
                "Lapp/browzomje/extension/pinterest/NativeImageDownload;->intercept(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
        )) { "Native image download has no free local register" }
        PatchLog.hooked("Download images in highest available quality", native, "shared native image pipeline")
    }
}

// Public non-obfuscated lifecycle contract. Every closeup module calls this base implementation.
private object PinModuleUpdateFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/activity/pin/view/modules/PinCloseupBaseModule;",
    name = "updatePin",
    returnType = "V",
    custom = { method, _ -> method.parameters.size == 1 },
)

private object NativeImageDownloadFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function0;"),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.name == "PIN_DOWNLOAD_BUTTON"
        } == true
    },
)

private object NativeImageDownloadConstructorFingerprint : Fingerprint(
    name = "<init>",
    custom = { method, classDef ->
        method.parameters.take(2).map { it.type } == listOf("Ljava/lang/String;", "Ljava/lang/String;") &&
            classDef.methods.any { candidate ->
                candidate.returnType == "V" && candidate.parameters.map { it.type } == listOf(
                    "Landroid/content/Context;", "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function0;",
                ) && candidate.implementation?.instructions?.any { instruction ->
                    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    field?.name == "PIN_DOWNLOAD_BUTTON"
                } == true
            }
    },
)
