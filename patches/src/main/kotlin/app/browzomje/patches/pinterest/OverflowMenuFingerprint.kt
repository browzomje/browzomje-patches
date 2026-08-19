package app.browzomje.patches.pinterest

import app.morphe.patcher.Fingerprint

/**
 * La classe che costruisce il menu "…" del pin (quello con "Scarica immagine", "Nascondi",
 * "Segnala"…), a cui tre patch accodano le proprie voci: "Copy direct link", "Set pin as
 * wallpaper" e "Download video".
 *
 * Era il punto più fragile dell'intero progetto: le tre patch elencavano a mano gli stessi nomi
 * offuscati (`uz0.z` a 28 parametri su 14.23, `uz0.b0` a 27 su 14.28), e su 14.32.0 quei nomi
 * sono spariti del tutto — la build si fermava proprio qui.
 *
 * L'ancora è il letterale `"overflow_menu_options"`, la chiave con cui la classe registra
 * l'evento di analytics quando il menu viene mostrato. Sta dentro il corpo di un metodo (è un
 * `const-string`, non una costante di campo), quindi i fingerprint lo vedono.
 *
 * Su 14.28.0 compare in una sola classe; su 14.32.0 in due (`l01.g0` e `l01.v`), e a quel punto
 * il letterale da solo non basta più. Si aggiunge quindi un tratto di forma che la classe
 * sbagliata non ha: il costruttore lunghissimo. La soglia è volutamente bassa (25) perché il
 * numero di parametri cresce di versione in versione — 28, poi 27, poi ancora — mentre nessuna
 * altra classe candidata ne ha lontanamente tanti.
 *
 * Verificato: 14.28.0 → `uz0.b0`, 14.32.0 → `l01.g0`.
 */
private object OverflowMenuClassFingerprint : Fingerprint(
    strings = listOf("overflow_menu_options"),
    custom = { _, classDef ->
        classDef.methods.any { it.name == "<init>" && it.parameters.size >= 25 }
    },
)

/** Il costruttore della view del menu, cioè il punto in cui accodare le voci. */
object OverflowMenuBuilderFingerprint : Fingerprint(
    classFingerprint = OverflowMenuClassFingerprint,
    name = "<init>",
    custom = { method, _ -> method.parameters.size >= 25 },
)
