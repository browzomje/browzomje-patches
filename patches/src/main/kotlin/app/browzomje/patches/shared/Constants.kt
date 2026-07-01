package app.browzomje.patches.shared

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
            ),
            AppTarget(
                version = "14.24.0",
                isExperimental = true
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

    val COMPATIBILITY_BETTER_LAUNCHER = Compatibility(
        name = "BetterLauncher",
        packageName = "TODO_PACKAGE_NAME", 
        apkFileType = ApkFileType.APK,
        appIconColor = 0x00BCD4, 
        targets = listOf(
            AppTarget(
                version = "TODO_VERSION" 
            )
        )
    )
}
