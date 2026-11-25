package app.browzomje.patches.pinterest.pin_menu.saved_pin

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.Opcode

private object PinMenuConfigClass : Fingerprint(
    name = "toString",
    strings = listOf("PinContextMenuOptionsConfig(isRepinnable=", ", isMeOwnerOrCollaborator="),
)

private object PinMenuConfigConstructor : Fingerprint(
    classFingerprint = PinMenuConfigClass,
    name = "<init>",
    parameters = List(15) { "Z" } + "Ll82/a;" + List(5) { "Z" } + "Lkotlin/jvm/functions/Function0;",
)

@Suppress("unused")
val hideRepinOnSavedLongPressPatch = bytecodePatch(
    name = "Hide repeat Save in saved pin menus",
    description = "Hides Save on long press for pins already on a board you own or collaborate on when viewing saved pins. Open the pin to save it to another board.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Pinterest", packageName = "com.pinterest", apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "14.45.0")),
    ))
    extendWith("extensions/extension.mpe")
    execute {
        val constructor = PinMenuConfigConstructor.method
        val instructions = constructor.implementation!!.instructions
        val last = instructions.lastIndex
        check(constructor.definingClass == "Lia3/s0;" && constructor.implementation!!.registerCount == 23 &&
            instructions[last].opcode == Opcode.RETURN_VOID)
        // Both radial and Compose build their options from this configuration. Only the
        // repin capability is changed. p9 is hasBoard, p16 the surface, p21 owner/collaborator.
        constructor.addInstructions(last, """
            move-object/from16 v1, p16
            move/from16 v2, p21
            invoke-static {v1, v2, p9}, Lapp/browzomje/extension/pinterest/SavedPinMenu;->hideSave(Ljava/lang/Enum;ZZ)Z
            move-result v1
            if-eqz v1, :morphe_keep_repin
            const/4 v1, 0x0
            iput-boolean v1, p0, Lia3/s0;->a:Z
            :morphe_keep_repin
            nop
        """.trimIndent())
        PatchLog.hooked("Hide repeat Save in saved pin menus", constructor, "shared saved pin long-press options")
    }
}
