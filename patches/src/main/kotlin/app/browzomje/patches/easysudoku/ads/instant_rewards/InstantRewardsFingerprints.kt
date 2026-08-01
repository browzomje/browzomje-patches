package app.browzomje.patches.easysudoku.ads.instant_rewards

import app.morphe.patcher.Fingerprint

/**
 * `AdUtil.V(Context context, String placement, String from, String scene, IADListener listener)`
 * → mostra un rewarded ("guarda un video e ottieni un suggerimento").
 *
 * Unico metodo di `AdUtil` con cinque parametri; gli altri due percorsi di visualizzazione
 * (`S` e `T`, gli interstitial) ne hanno sei e sette. L'ultimo parametro è `vc.k`
 * (IADListener), il cui package è offuscato: viene dichiarato come `"L"`, che il patcher
 * interpreta come "un qualsiasi tipo oggetto" (vedi `StringComparisonType`).
 *
 * Trovato con:
 * ```bash
 * rg -rn 'AdUtil\.V\(' -g '*.java' sources/     # -> sk/u3.java (RewardAdHelper)
 * rg -n -B20 'AdUtil\.V\(' sources/sk/u3.java   # -> il listener e la sequenza di callback
 * ```
 */
object RewardedAdShowFingerprint : Fingerprint(
    definingClass = "Lcom/meevii/common/utils/AdUtil;",
    returnType = "Z",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "L",
    ),
)
