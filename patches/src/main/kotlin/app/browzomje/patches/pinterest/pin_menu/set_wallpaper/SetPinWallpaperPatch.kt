package app.browzomje.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn
import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

private val addWallpaperPermissionPatch = resourcePatch(
    description = "Adds android.permission.SET_WALLPAPER to the manifest."
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("AndroidManifest.xml").use { document ->
            val existing = document.getElementsByTagName("uses-permission")
            for (i in 0 until existing.length) {
                val name = existing.item(i).attributes
                    ?.getNamedItem("android:name")?.nodeValue
                if (name == "android.permission.SET_WALLPAPER") return@use
            }

            val manifest = document.getElementsByTagName("manifest").item(0)
            val permission = document.createElement("uses-permission")
            permission.setAttribute("android:name", "android.permission.SET_WALLPAPER")
            manifest.appendChild(permission)
        }
    }
}

@Suppress("unused")
val setPinWallpaperPatch = bytecodePatch(
    name = "Set pin as wallpaper",
    description = "It adds the ‘Set as wallpaper’ option to the pin menu, which downloads uses the image and sets it as the device’s wallpaper.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(addWallpaperPermissionPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val method = OverflowMenuBuilderFingerprint.method
        val registerCount = method.implementation!!.registerCount
        val p0RegisterIndex = registerCount - (method.parameters.size + 1)

        val exits = method.addInstructionsBeforeEveryReturn(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                "$EXTENSION_CLASS->addWallpaperOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Set pin as wallpaper", method, "pin menu option, $exits exits")

        PinCloseupBitmapFingerprint.methodOrNull?.let { pinMethod ->
            val pinRegisterCount = pinMethod.implementation!!.registerCount
            val pinParameterRegisterCount = pinMethod.parameters.size + 1
            val p0RegisterIndex = pinRegisterCount - pinParameterRegisterCount
            val p1RegisterIndex = p0RegisterIndex + 1

            val pinInstructions = InlineSmaliCompiler.compile(
                "invoke-static/range { v$p0RegisterIndex .. v$p1RegisterIndex }, $EXTENSION_CLASS->setCurrentPinView(Ljava/lang/Object;Landroid/graphics/Bitmap;)V",
                "",
                pinRegisterCount,
                true
            )
            pinMethod.addInstructions(0, pinInstructions)
            PatchLog.hooked("Set pin as wallpaper", pinMethod, "pin bitmap capture")
        } ?: PatchLog.warn(
            "Set pin as wallpaper",
            "bitmap capture point not found: wallpaper will be re-downloaded from network " +
                "instead of reusing in-memory image.",
        )
    }
}
