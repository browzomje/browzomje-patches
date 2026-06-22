package app.template.patches.pinterest.privacy.telemetry

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST

/**
 * Neutralizza l'identificatore pubblicitario (GAID) letto dagli SDK di telemetria.
 *
 * Perché questo target e non "spegnere Firebase / Pinalytics":
 *  - È un singolo punto di strozzatura: tutti i tracker leggono l'ID da queste due
 *    funzioni, quindi una sola patch copre Firebase Analytics, GMS measurement, gli
 *    ad-SDK, ecc.
 *  - È a rischio quasi nullo: restituire un ID vuoto è uno stato già previsto dall'app
 *    (es. utente che ha resettato l'ID o negato il consenso), quindi non rompe nulla.
 *  - Non tocca codice di rete o di navigazione, dove rimuovere telemetria a caso
 *    farebbe crashare l'app.
 *
 * Risultato: ogni componente che chiede il GAID riceve stringa vuota e
 * "limit ad tracking = true", azzerando il tracciamento cross-app basato sull'ID.
 *
 * Per estendere la rimozione di telemetria (Pinalytics, Firebase, Bugsnag) vedi il
 * RECAP.md di questa cartella: contiene i comandi rg/jadx già pronti e i target
 * candidati, da abilitare dopo verifica su device.
 */
@Suppress("unused")
val privacyTelemetryPatch = bytecodePatch(
    name = "Neutralize advertising ID",
    description = "Returns an empty Google Advertising ID and forces ‘limit ad tracking’, disabling ad tracking without causing the app to crash.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        // getId() -> "" (nessun advertising ID da spedire)
        AdvertisingIdGetIdFingerprint.method.addInstructions(
            0,
            """
                const-string v0, ""
                return-object v0
            """
        )

        // isLimitAdTrackingEnabled() -> true (segnala opt-out dal tracciamento)
        AdvertisingIdLimitTrackingFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
