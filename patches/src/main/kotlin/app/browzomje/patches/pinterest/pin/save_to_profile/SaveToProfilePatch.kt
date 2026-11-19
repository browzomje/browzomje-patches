package app.browzomje.patches.pinterest.pin.save_to_profile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturnUsingParameters

private const val PATCH_NAME = "Quick save to profile"
private const val PIN = "Lcom/pinterest/api/model/pe;"
private const val EXTENSION = "Lapp/browzomje/extension/pinterest/QuickSave;"

private object CloseupSaveButton : Fingerprint(
    definingClass = "Lmd1/a1;",
    returnType = "V",
    parameters = listOf("I", PIN),
)

private object CloseupModule : Fingerprint(
    definingClass = "Lcom/pinterest/activity/pin/view/modules/PinCloseupBaseModule;",
    name = "updatePin",
    returnType = "V",
    parameters = listOf(PIN),
)

private object RadialShow : Fingerprint(
    definingClass = "Lcom/pinterest/ui/menu/ContextMenuView;",
    returnType = "V",
    parameters = listOf("Lq80/d;", "Lde/b;"),
)

@Suppress("unused")
val saveToProfilePatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Tap Save to add or remove a Pin from your profile. Press and hold Save on an open Pin to choose a board.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "14.44.0")),
    ))
    extendWith("extensions/extension.mpe")

    execute {
        val closeup = CloseupSaveButton.method
        check(closeup.addInstructionsBeforeEveryReturnUsingParameters(
            "invoke-static/range { p0 .. p2 }, $EXTENSION->bindButton(Ljava/lang/Object;ILjava/lang/Object;)V",
        ) > 0) { "Close-up Save button binding not found" }
        PatchLog.hooked(PATCH_NAME, closeup, "bind profile toggle and board picker")

        val module = CloseupModule.method
        module.addInstructions(
            0,
            "invoke-static/range { p0 .. p1 }, $EXTENSION->bindModule(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, module, "bind profile toggle to legacy close-up modules")

        val radial = RadialShow.method
        radial.addInstructions(
            0,
            "invoke-static/range { p0 .. p1 }, $EXTENSION->bindRadial(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
        PatchLog.hooked(PATCH_NAME, radial, "bind profile toggle to radial pin")
    }
}
