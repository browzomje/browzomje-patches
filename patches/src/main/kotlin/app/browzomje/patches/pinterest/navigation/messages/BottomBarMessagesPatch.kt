package app.browzomje.patches.pinterest.navigation.messages

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.pinterest.navigation.hide_greeting_header.HomeFeedFragmentOnViewCreatedFingerprint
import app.browzomje.patches.pinterest.navigation.hide_greeting_header.hideGreetingHeaderButtonsPatch
import app.browzomje.patches.shared.forceReturn
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object NavigationTabsFingerprint : Fingerprint(
    returnType = "Ljava/util/ArrayList;",
    parameters = emptyList(),
    strings = listOf("hfp_global_nav_greeting_header_android", "android_search_fos2"),
    custom = { _, classDef -> classDef.type == "Ldg0/j;" },
)

private val previewCompatibility = Compatibility(
    name = "Pinterest",
    packageName = "com.pinterest",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = "14.44.0")),
)

private fun List<Instruction>.createFactoryIndex() =
    indices.single {
        val instruction = this[it]
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            (instruction as ReferenceInstruction).reference.toString() == "Ldg0/j;->b()Ldg0/t;"
    }

@Suppress("unused")
val bottomBarMessagesPatch = bytecodePatch(
    name = "Show Messages in the bottom bar",
    description = "Moves Pinterest's Inbox from the Home header to the bottom navigation.",
    default = false,
) {
    compatibleWith(previewCompatibility)
    dependsOn(hideGreetingHeaderButtonsPatch)

    execute {
        val method = NavigationTabsFingerprint.method
        val implementation = checkNotNull(method.implementation)
        val instructions = implementation.instructions
        val inbox = instructions.indices.single {
            val instruction = instructions[it]
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                (instruction as ReferenceInstruction).reference.toString() == "Ldg0/j;->e()Ldg0/t;"
        }
        val create = instructions.createFactoryIndex()
        // Keep the native Create decision. Both paths reach the Inbox/Notifications decision next.
        val inboxDecision = create + 3
        check(instructions[create + 2].opcode == Opcode.INVOKE_VIRTUAL)
        check(instructions[inboxDecision].opcode == Opcode.IF_EQZ)
        implementation.replaceInstruction(
            inboxDecision,
            BuilderInstruction20t(Opcode.GOTO_16, implementation.newLabelForIndex(inbox)),
        )
        mutableClassDefBy("Lapp/browzomje/extension/pinterest/PinterestUtils;")
            .methods.single { it.name == "isInboxInBottomBar" }
            .forceReturn("const/4 v0, 0x1\nreturn v0")
    }
}

@Suppress("unused")
val bottomBarCreatePatch = bytecodePatch(
    name = "Show Create in the bottom bar",
    description = "Moves Pinterest's Create button from the Home header to the bottom navigation.",
    default = false,
) {
    compatibleWith(previewCompatibility)

    execute {
        val tabs = checkNotNull(NavigationTabsFingerprint.method.implementation)
        val createDecision = tabs.instructions.createFactoryIndex() - 1
        check(tabs.instructions[createDecision].opcode == Opcode.IF_EQZ)

        val header = checkNotNull(HomeFeedFragmentOnViewCreatedFingerprint.method.implementation)
        val instructions = header.instructions
        val inboxId = instructions.indices.single {
            val reference = (instructions[it] as? ReferenceInstruction)?.reference as? FieldReference
            reference?.name == "home_feed_greeting_header_newshub_icon"
        }
        // Pinterest attaches Create and the Inbox frame in that order to the same container.
        val addView = "Landroid/view/ViewGroup;->addView(Landroid/view/View;)V"
        val attachCreate = (inboxId + 1 until instructions.lastIndex).single {
            val create = instructions[it]
            val inbox = instructions[it + 1]
            (create as? ReferenceInstruction)?.reference?.toString() == addView &&
                (inbox as? ReferenceInstruction)?.reference?.toString() == addView &&
                (create as FiveRegisterInstruction).registerC == (inbox as FiveRegisterInstruction).registerC
        }

        tabs.replaceInstruction(createDecision, BuilderInstruction10x(Opcode.NOP))
        header.replaceInstruction(attachCreate, BuilderInstruction10x(Opcode.NOP))
    }
}
