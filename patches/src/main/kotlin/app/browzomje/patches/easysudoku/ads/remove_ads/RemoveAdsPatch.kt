package app.browzomje.patches.easysudoku.ads.remove_ads

import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_EASY_SUDOKU
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn

internal const val PATCH_NAME = "Remove ads"

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Turns off every ad Easy Sudoku shows: the home banner, the interstitials " +
        "between puzzles, the app-open ad and all the background preloading. It works by telling " +
        "the game its own \"ads removed\" purchase is already active, which also unlocks the Fun, " +
        "Ice and Killer Sudoku modes that are otherwise gated behind watching ads. " +
        "Keep \"Instant rewards\" enabled too, or the \"watch an ad for a hint\" buttons stop giving anything.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EASY_SUDOKU)

    execute {
        // Tutte le iniezioni sono stub in testa al metodo: il corpo originale resta nel dex ma
        // non viene mai raggiunto. Nessuna firma cambia, quindi i ~45 chiamanti continuano a
        // funzionare senza sapere nulla — è il motivo per cui questa patch non può rompere la
        // navigazione dell'app.

        // 1) L'interruttore principale: "ads già rimossi". Da qui passano banner, interstitial,
        //    annuncio di apertura, precaricamenti e gli sblocchi delle modalità extra.
        AdFreeStateFingerprint.method.let { method ->
            method.forceReturn(
                """
                const/4 v0, 0x1
                return v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, method, "IapHelper: ad-free state forced to true")
        }

        // 2) Lo stesso stato letto tramite service locator. Serve perché durante l'avvio
        //    l'IapHelper può non essere ancora registrato: lì (1) non verrebbe nemmeno chiamato.
        AdUtilIsAdFreeFingerprint.method.let { method ->
            method.forceReturn(
                """
                const/4 v0, 0x1
                return v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, method, "AdUtil: ad-free state forced to true")
        }

        // 3) Rete di sicurezza sugli interstitial: "no" incondizionato, senza passare dallo
        //    stato IAP. I chiamanti trattano già `false` come "nessun annuncio da mostrare" e
        //    proseguono col gioco: è lo stesso ramo che l'app percorre quando la rete è assente.
        AdUtilShouldShowInterstitialFingerprint.methodOrNull?.let { method ->
            method.forceReturn(
                """
                const/4 v0, 0x0
                return v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, method, "interstitial gate forced to false")
        } ?: PatchLog.warn(
            PATCH_NAME,
            "interstitial gate not found: interstitials are still blocked by the ad-free " +
                "state, but the extra safety net is missing.",
        )

        // 4) Il banner della schermata di gioco. Anche questo è già coperto dallo stato IAP;
        //    lo stub evita che il contenitore venga anche solo toccato.
        //    Lo spazio che resta libero lo recupera la patch "Reclaim ad banner space".
        AdUtilShowBannerFingerprint.methodOrNull?.let { method ->
            method.forceReturn("return-void", registersUsed = 0)
            PatchLog.hooked(PATCH_NAME, method, "banner loader stubbed out")
        } ?: PatchLog.warn(
            PATCH_NAME,
            "banner loader not found: the banner is still blocked by the ad-free state.",
        )
    }
}
