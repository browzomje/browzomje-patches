package app.browzomje.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.Fingerprint

object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Luz0/z;" &&
            method.name == "<init>" &&
            method.parameters.size == 28
    }
)

object PinCloseupBitmapFingerprint : Fingerprint(
    parameters = listOf("Landroid/graphics/Bitmap;", "Ld12/a0;"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/ui/imageview/GenericWebImageView;" &&
            method.name == "e0"
    }
)
