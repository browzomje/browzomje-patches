package app.browzomje.patches.pinterest.navigation.keep_home_feed

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.forceReturn
import app.browzomje.patches.shared.PatchLog
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val PATCH_NAME = "Keep Home feed"
private const val EXTENSION = "Lapp/browzomje/extension/pinterest/HomeFeedRefresh;"

private fun MutableMethod.calls(): List<Pair<Int, MethodReference>> =
    implementation!!.instructions.mapIndexedNotNull { index, instruction ->
        (instruction as? ReferenceInstruction)?.reference.let { reference ->
            (reference as? MethodReference)?.let { index to it }
        }
    }

@Suppress("unused")
val keepHomeFeedPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Keeps the Home feed when returning to Home instead of refreshing automatically. " +
        "Pull-to-refresh remains available. Off by default and in the recommended preset; enable it in Morphe settings.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = COMPATIBILITY_PINTEREST.apkFileType,
        targets = COMPATIBILITY_PINTEREST.targets!!.filter { it.version == "14.39.0" },
    ))
    extendWith("extensions/extension.mpe")
    execute {
        val navigation = HomeTabSelectionFingerprint.methodOrNull
        if (navigation == null) {
            PatchLog.warn(PATCH_NAME, "Home auto-refresh emitter not found; original behavior retained")
            return@execute
        }
        // Derive the shared timed-refresh helper from its call signature, not j60.m/t0.
        val helperRef = navigation.calls().map { it.second }.singleOrNull {
            it.returnType == "Z" && it.parameterTypes.size == 3 &&
                it.parameterTypes[0].toString().startsWith("L") &&
                it.parameterTypes[1] == "Z" && it.parameterTypes[2] == "Ljava/lang/String;"
        }
        checkNotNull(helperRef) { "Cannot uniquely resolve the timed Home refresh helper" }
        val helperClass = mutableClassDefBy(helperRef.definingClass)
        val helper = helperClass.methods.single {
            it.name == helperRef.name && it.parameterTypes == helperRef.parameterTypes && it.returnType == "Z"
        }
        // The event bus is the helper's first argument. Only these source methods are changed.
        val busType = helperRef.parameterTypes[0].toString()
        val busCalls = navigation.calls().filter { (_, ref) ->
            ref.definingClass == busType && ref.returnType == "V" &&
                ref.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;")
        }
        check(busCalls.size == 2 && busCalls.map { it.second.toString() }.distinct().size == 1) {
            "Unexpected Home event emission layout"
        }
        val post = busCalls.first().second
        val helperCalls = helper.calls().filter { it.second.toString() == post.toString() }
        check(helperCalls.size == 1) { "Unexpected timed Home event emission layout" }
        // A typed bridge preserves every caller register and the original bus invocation.
        // Do not gate the shared event bus: search, deeplinks and manual refresh use it too.
        for ((method, calls) in listOf(navigation to busCalls, helper to helperCalls)) {
            val owner = mutableClassDefBy(method.definingClass)
            val bridgeName = "morphePostAutomaticHomeRefresh"
            check(owner.methods.none { it.name == bridgeName })
            val bridge = MutableMethod(
                ImmutableMethod(
                    owner.type, bridgeName,
                    listOf(
                        ImmutableMethodParameter(busType, emptySet(), null),
                        ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null),
                    ),
                    "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    emptySet(), emptySet(), MutableMethodImplementation(3),
                ),
            )
            bridge.addInstructions(
                0,
                """
                    invoke-static {}, $EXTENSION->shouldKeepFeed()Z
                    move-result v0
                    if-nez v0, :done
                    invoke-virtual {p0, p1}, $post
                    :done
                    return-void
                """.trimIndent(),
            )
            owner.methods.add(bridge)
            calls.forEach { (index, _) ->
                val instruction = method.implementation!!.instructions[index]
                check(instruction.opcode == Opcode.INVOKE_VIRTUAL)
                val registers = instruction as FiveRegisterInstruction
                check(registers.registerCount == 2)
                method.replaceInstruction(
                    index,
                    "invoke-static {v${registers.registerC}, v${registers.registerD}}, " +
                        "${owner.type}->$bridgeName($busType" + "Ljava/lang/Object;)V",
                )
            }
            PatchLog.hooked(PATCH_NAME, method, "${calls.size} automatic refresh emission(s), typed bridge")
        }
        // Real-device testing showed that reselecting an already active Home tab
        // refreshes through a separate presenter callback, without posting an event.
        val reselect = HomeReselectionFingerprint.method
        val owner = mutableClassDefBy(reselect.definingClass)
        val refreshCall = reselect.calls().single { (_, ref) ->
            ref.returnType == "V" && ref.parameterTypes.isEmpty() &&
                ref.definingClass.startsWith("Lcom/pinterest/feature/home/mainfeed/") &&
                ref.definingClass != owner.type
        }
        val presenterRefresh = refreshCall.second
        val nativeStateCall = reselect.calls().single { (_, ref) ->
            ref.definingClass == owner.type && ref.returnType == "V" && ref.parameterTypes.isEmpty()
        }
        val presenterRegister = (reselect.implementation!!.instructions[refreshCall.first]
            as FiveRegisterInstruction).registerC
        val fragmentRegister = (reselect.implementation!!.instructions[nativeStateCall.first]
            as FiveRegisterInstruction).registerC
        val recyclerGetter = owner.methods.filter { it.implementation != null }
            .flatMap { it.calls().map { call -> call.second } }
            .filter { it.returnType == "Landroidx/recyclerview/widget/RecyclerView;" && it.parameterTypes.isEmpty() }
            .distinctBy { it.toString() }.single()
        val scrollToPosition = RecyclerScrollToPositionFingerprint.method
        val reselectBridge = MutableMethod(
            ImmutableMethod(
                owner.type, "morpheHomeReselection",
                listOf(
                    ImmutableMethodParameter(owner.type, emptySet(), null),
                    ImmutableMethodParameter(presenterRefresh.definingClass, emptySet(), null),
                ),
                "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet(), emptySet(), MutableMethodImplementation(3),
            ),
        )
        reselectBridge.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION->shouldKeepFeed()Z
                move-result v0
                if-nez v0, :keep
                invoke-virtual {p1}, $presenterRefresh
                return-void
                :keep
                invoke-virtual {p0}, $recyclerGetter
                move-result-object v0
                if-eqz v0, :done
                const/4 v1, 0x0
                invoke-virtual {v0, v1}, $scrollToPosition
                :done
                return-void
            """.trimIndent(),
        )
        owner.methods.add(reselectBridge)
        reselect.replaceInstruction(
            refreshCall.first,
            "invoke-static {v$fragmentRegister, v$presenterRegister}, " +
                "${owner.type}->morpheHomeReselection(${owner.type}${presenterRefresh.definingClass})V",
        )
        PatchLog.hooked(PATCH_NAME, reselect, "reselection refresh replaced with scroll-to-position(0); native state retained")
        mutableClassDefBy(EXTENSION).methods.single { it.name == "isAvailable" }.forceReturn(
            "const/4 v0, 0x1\nreturn v0",
        )
    }
}
