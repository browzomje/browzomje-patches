package app.browzomje.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.Fingerprint

/** Vedi `CopyDirectLinkFingerprints.OverflowMenuBuilderFingerprint`: stesso target. */
object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        method.name == "<init>" && when (classDef.type) {
            "Luz0/z;" -> method.parameters.size == 28
            "Luz0/b0;" -> method.parameters.size == 27
            else -> false
        }
    }
)

/**
 * Il punto in cui `GenericWebImageView` riceve la Bitmap già decodificata: da lì catturiamo
 * l'immagine del pin aperto, per poterla impostare come sfondo senza riscaricarla.
 *
 * La classe non è offuscata (custom View inflatata per nome dall'XML). Il metodo sì, ma è
 * l'unico `void <nome>(Bitmap, X)` della classe, quindi lo si cerca per firma anziché per nome.
 * 14.23.0: G0(Bitmap, u02.a0) — 14.28.0: d0(Bitmap, l12.z).
 */
object PinCloseupBitmapFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/ui/imageview/GenericWebImageView;" &&
            method.parameters.size == 2 &&
            method.parameters[0].type == "Landroid/graphics/Bitmap;" &&
            // il secondo parametro è un enum offuscato ("da dove arriva l'immagine"):
            // non è un tipo di libreria, e questo basta a distinguere il metodo.
            !method.parameters[1].type.startsWith("Landroid/") &&
            !method.parameters[1].type.startsWith("Ljava/")
    }
)
