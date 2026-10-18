package app.browzomje.patches.pinterest.navigation.startup_tab

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn

private const val EXTENSION = "Lapp/browzomje/extension/pinterest/StartupTab;"

@Suppress("unused")
val startupTabPatch = bytecodePatch(
    name = "Choose startup tab",
    description = "Choose Home, Search or Saved for fresh launcher starts. Keeps deep links, notifications and restored sessions unchanged. Configure in Morphe settings.",
    default = true,
) {
    compatibleWith(Compatibility(name = "Pinterest", packageName = "com.pinterest",
        apkFileType = ApkFileType.APK, targets = listOf(AppTarget(version = "14.39.0"))))
    extendWith("extensions/extension.mpe")
    execute {
        // Require the native router before exposing the setting. No obfuscated method names.
        PendingTabRouterFingerprint.method
        val method = MainCreateFingerprint.method
        method.addInstructions(0,
            "invoke-static/range {p0 .. p1}, $EXTENSION->onMainCreate(Ljava/lang/Object;Ljava/lang/Object;)V")
        LauncherCreateFingerprint.method.addInstructions(0,
            "invoke-static/range {p0 .. p1}, $EXTENSION->onLauncherCreate(Ljava/lang/Object;Ljava/lang/Object;)V")
        LauncherNewIntentFingerprint.method.addInstructions(0,
            "invoke-static/range {p0 .. p0}, $EXTENSION->cancelLauncher(Ljava/lang/Object;)V")
        AuthenticatedMainTransitionFingerprint.method.addInstructions(0,
            "invoke-static/range {p1 .. p1}, $EXTENSION->prepareAuthenticated(Ljava/lang/Object;)V")
        mutableClassDefBy(EXTENSION).methods.single { it.name == "isAvailable" }.forceReturn(
            "const/4 v0, 0x1\nreturn v0")
        PatchLog.hooked("Choose startup tab", method, "launcher-only native pending-tab request")
    }
}
