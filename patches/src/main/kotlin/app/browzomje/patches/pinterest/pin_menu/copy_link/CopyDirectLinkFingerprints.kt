package app.browzomje.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.Fingerprint

/**
 * Il costruttore della view del menu overflow del pin (quello con "Scarica immagine",
 * "Nascondi", "Segnala"…), a cui accodiamo le nostre voci.
 *
 * È un LinearLayout con un costruttore lunghissimo (27-28 parametri): è quello il tratto
 * distintivo, il nome della classe cambia a ogni versione.
 * 14.23.0: uz0.z (28 par.) — 14.28.0: uz0.b0 (27).
 *
 * Come ritrovarlo: `rg -n 'RelativeLayout a\(CharSequence'` dà il "view creator" delle righe di
 * menu; la classe che lo espone tramite un getter è questa.
 */
object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        method.name == "<init>" && when (classDef.type) {
            "Luz0/z;" -> method.parameters.size == 28
            "Luz0/b0;" -> method.parameters.size == 27
            else -> false
        }
    }
)
