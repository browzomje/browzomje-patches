package app.browzomje.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.Fingerprint

object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Luz0/z;" && method.name == "<init>" && method.parameters.size == 28) ||
        (classDef.type == "Lnz0/b0;" && method.name == "<init>" && method.parameters.size == 27)
    }
)
