package app.browzomje.patches.pinterest.education.hide_save_tooltip

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.returnVoidWhen
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val hideSaveTooltipPatch = bytecodePatch(
    name = "Hide save for later tooltip",
    description = "Always hides the save-for-later education popup without disabling the Save button.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "14.39.0")),
    ))
    extendWith("extensions/extension.mpe")
    execute {
        val placement = SaveTooltipPlacementFingerprint.method
        check(placement.accessFlags and AccessFlags.STATIC.value == 0) { "Tooltip placement must be an instance method" }
        check(placement.returnVoidWhen(
            "invoke-static/range {p0 .. p3}, " +
                "Lapp/browzomje/extension/pinterest/SavePinEducation;->suppressSaveAnchor(Ljava/lang/Object;Ljava/lang/Object;ZLjava/lang/Object;)Z"
        )) { "Tooltip placement needs a free local" }
        PatchLog.hooked("Hide save for later tooltip", placement, "unconditional save-anchor gate")
    }
}
