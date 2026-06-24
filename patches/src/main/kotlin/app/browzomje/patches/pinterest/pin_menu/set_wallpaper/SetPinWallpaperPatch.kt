package app.template.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/WallpaperUtils;"

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
        val returnVoidIndex = method.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_VOID
        }
        val insertIndex = if (returnVoidIndex != -1) returnVoidIndex else method.implementation!!.instructions.size - 1

        val registerCount = method.implementation!!.registerCount
        val parameterRegisterCount = method.parameters.size + 1
        val p0RegisterIndex = registerCount - parameterRegisterCount

        val instructions = InlineSmaliCompiler.compile(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, $EXTENSION_CLASS->addWallpaperOption(Ljava/lang/Object;)V",
            "",
            registerCount,
            true
        )
        method.addInstructions(insertIndex, instructions)

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
        }
    }
}
