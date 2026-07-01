package app.browzomje.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.Fingerprint

object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Luz0/z;" && method.name == "<init>" && method.parameters.size == 28) ||
        (classDef.type == "Lnz0/b0;" && method.name == "<init>" && method.parameters.size == 27)
    }
)

object PinCloseupBitmapFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/ui/imageview/GenericWebImageView;" &&
            (method.name == "e0" || method.name == "G0") &&
            method.parameters.size == 2 &&
            method.parameters[0].type == "Landroid/graphics/Bitmap;" &&
            (method.parameters[1].type == "Ld12/a0;" || method.parameters[1].type == "Lu02/a0;")
    }
)
