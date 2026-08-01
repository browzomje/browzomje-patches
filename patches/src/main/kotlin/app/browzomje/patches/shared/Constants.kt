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
                version = "14.28.0"
            )
        )
    )

    /**
     * Easy Sudoku (Meevii) — `easy.sudoku.puzzle.solver.free`.
     *
     * A differenza di Pinterest, qui **i nomi delle classi dell'app non sono offuscati**
     * (`com.meevii.common.utils.AdUtil`, `com.meevii.iap.hepler.d`, …): a cambiare sono solo i
     * nomi dei metodi (`w()`, `G()`, `V()`, …) e i package "corti" delle classi di supporto
     * (`xa`, `vc`, `sk`, …). I fingerprint si ancorano quindi al **nome di classe** + firma +
     * stringhe, e quasi mai al nome del metodo: vedi i RECAP delle singole patch.
     */
    val COMPATIBILITY_EASY_SUDOKU = Compatibility(
        name = "Easy Sudoku",
        packageName = "easy.sudoku.puzzle.solver.free",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x1E88E5,
        targets = listOf(
            AppTarget(
                version = "5.70.0"
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
