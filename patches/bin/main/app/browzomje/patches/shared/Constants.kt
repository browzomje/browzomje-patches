package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_PINTEREST = Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xE60023, 
        targets = listOf(
            AppTarget(
                version = "14.23.0" 
            )
        )
    )

    val COMPATIBILITY_ALIGHT_MOTION = Compatibility(
        name = "Alight Motion",
        packageName = "com.alightcreative.motion",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x00D6C2,
        targets = listOf(
            AppTarget(
                version = "5.0.273.1028425"
            )
        )
    )
}
