package app.browzomje.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
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
    description = "Adds a \"Set as wallpaper\" option to the pin menu, which downloads the image and sets it as the device wallpaper.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(addWallpaperPermissionPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val method = OverflowMenuBuilderFingerprint.method

        val at = method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->addWallpaperOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Set pin as wallpaper", method, "pin menu option, after super at $at")

        PinCloseupBitmapFingerprint.methodOrNull?.let { pinMethod ->
            // Iniezione in testa al metodo: qui `p0`/`p1` sono `this` e il primo parametro, e
            // scriverli per nome evita lo stesso conto a mano che aveva rotto il menu del pin.
            pinMethod.addInstructions(
                0,
                "invoke-static/range { p0 .. p1 }, $EXTENSION_CLASS->setCurrentPinView(Ljava/lang/Object;Landroid/graphics/Bitmap;)V",
            )
            PatchLog.hooked("Set pin as wallpaper", pinMethod, "pin bitmap capture")
        } ?: PatchLog.warn(
            "Set pin as wallpaper",
            "bitmap capture point not found: wallpaper will be re-downloaded from network " +
                "instead of reusing in-memory image.",
        )
    }
}
