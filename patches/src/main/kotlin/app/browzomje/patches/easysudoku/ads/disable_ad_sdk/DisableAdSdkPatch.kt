package app.browzomje.patches.easysudoku.ads.disable_ad_sdk

import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_EASY_SUDOKU
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn

private const val PATCH_NAME = "Disable ad SDK"

@Suppress("unused")
val disableAdSdkPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Stops the AppLovin MAX mediation stack — and with it Pangle, Amazon APS, " +
        "Chartboost, InMobi, Mintegral, Fyber, Bigo and the rest — from ever initialising. " +
        "The game starts faster and makes no ad-network requests at all. Requires \"Remove ads\": " +
        "on its own it would only make the app fail to find ads, not stop asking for them.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EASY_SUDOKU)

    execute {
        val method = AdModuleInitFingerprint.method

        method.forceReturn("return-void", registersUsed = 0)

        // Cosa succede alle chiamate successive: senza inizializzazione la tabella dei
        // placement resta vuota, quindi ogni interrogazione al SDK (`isReady`, `show`, `load`)
        // trova `null`, logga "not find config for placementId" e torna indietro. È lo stesso
        // percorso che l'app segue quando la configurazione remota non arriva, cioè un caso
        // che gestisce già di suo: per questo spegnere il SDK non la fa crashare.
        //
        // Effetto collaterale voluto: la callback "ad init success" non scatta mai, quindi non
        // parte nemmeno il precaricamento degli annunci all'avvio.
        PatchLog.hooked(PATCH_NAME, method, "ad mediation stack never initialises")
    }
}
