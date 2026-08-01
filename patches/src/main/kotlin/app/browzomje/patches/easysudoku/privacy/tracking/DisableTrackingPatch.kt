package app.browzomje.patches.easysudoku.privacy.tracking

import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_EASY_SUDOKU
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn

private const val PATCH_NAME = "Disable tracking"

@Suppress("unused")
val disableTrackingPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Hands out an empty Google Advertising ID with \"limit ad tracking\" turned on, " +
        "and stops AppsFlyer from sending install attribution and events. The game keeps working " +
        "normally: nothing in it waits for attribution data.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EASY_SUDOKU)

    execute {
        // GAID vuoto + "limit ad tracking" attivo. Restituire una stringa vuota invece di null
        // è deliberato: parecchie SDK fanno `id.equals(...)` senza controllo, e un null le farebbe
        // crashare — è lo stesso compromesso della patch omonima su Pinterest.
        AdvertisingIdGetIdFingerprint.method.let { method ->
            method.forceReturn(
                """
                const-string v0, ""
                return-object v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, method, "advertising ID emptied")
        }

        AdvertisingIdLimitTrackingFingerprint.method.let { method ->
            method.forceReturn(
                """
                const/4 v0, 0x1
                return v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, method, "limit ad tracking forced on")
        }

        // AppsFlyer: si spegne `start()`, non `init()`. `init()` si limita a configurare
        // l'oggetto, mentre la classe base dell'app accoda gli eventi finché il modulo non si
        // dichiara pronto: bloccare l'inizializzazione lascerebbe quella coda a crescere senza
        // fine. Fermando `start()` la SDK resta viva e muta, che è ciò che serve.
        AppsFlyerStartFingerprint.methodOrNull?.let { method ->
            method.forceReturn("return-void", registersUsed = 0)
            PatchLog.hooked(PATCH_NAME, method, "AppsFlyer never starts sending")
        } ?: PatchLog.warn(
            PATCH_NAME,
            "AppsFlyer start() not found: attribution and event tracking stay active. " +
                "The advertising ID is still neutralized.",
        )
    }
}
