package app.browzomje.patches.easysudoku.ads.banner_space

import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_EASY_SUDOKU
import app.browzomje.patches.shared.PatchLog

private const val PATCH_NAME = "Reclaim ad banner space"

/**
 * La dimensione che riserva l'altezza del riquadro pubblicitario.
 *
 * Nei layout non c'è nessun `View.GONE` da attivare: il contenitore del banner è un `FrameLayout`
 * con `android:layout_height="@dimen/ad_banner_height"` fisso, quindi togliendo la pubblicità
 * resterebbe una fascia vuota in fondo alla schermata di gioco. Azzerando la dimensione, il
 * `FrameLayout` genitore (che è `wrap_content`) collassa e lo spazio torna alla griglia.
 *
 * ```bash
 * grep -rn 'ad_banner_height' resources/res/            # 4 layout + 2 dimens.xml
 * grep -n -B6 'adBanner' resources/res/layout/activity_main.xml
 * ```
 *
 * Agire sulla risorsa invece che sulle singole view copre in un colpo solo tutte e quattro le
 * schermate che la usano (partita, tutorial, guida iniziale, lezione "cross hatching") e ogni
 * variante di dimensione dello schermo, senza codice a runtime.
 */
private const val DIMEN_NAME = "ad_banner_height"

/**
 * Cartelle `res/values*` in cui cercare la dimensione.
 *
 * Nell'APK 5.70.0 è definita solo in due (`values` = 60dp e `values-sw560dp-h900dp` = 90dp), ma
 * l'elenco comprende tutte le cartelle che contengono un `dimens.xml`: una cartella che non
 * dichiara la dimensione viene semplicemente saltata, quindi tenerlo largo non fa danno e mette
 * al riparo da una release che aggiunga una variante per un altro formato di schermo.
 */
private val DIMENS_DIRS = listOf(
    "values",
    "values-h320dp-port", "values-h360dp-land", "values-h480dp", "values-h480dp-land",
    "values-h550dp-port", "values-h720dp", "values-hdpi", "values-land", "values-large",
    "values-ldpi", "values-mdpi",
    "values-sw320dp", "values-sw360dp", "values-sw384dp", "values-sw392dp", "values-sw400dp",
    "values-sw410dp", "values-sw411dp", "values-sw432dp", "values-sw480dp", "values-sw480dp-h900dp",
    "values-sw533dp", "values-sw560dp", "values-sw560dp-h900dp", "values-sw592dp", "values-sw600dp",
    "values-sw880dp", "values-sw880dp-h1020dp",
    "values-v28",
    "values-w320dp", "values-w320dp-land", "values-w360dp-port", "values-w400dp-port",
    "values-w600dp-land",
    "values-xhdpi", "values-xlarge", "values-xxhdpi", "values-xxxhdpi",
)

@Suppress("unused")
val reclaimBannerSpacePatch = resourcePatch(
    name = PATCH_NAME,
    description = "Collapses the empty strip the banner used to occupy at the bottom of the " +
        "board, so the puzzle gets the space back instead of staring at a blank rectangle. " +
        "Only useful together with \"Remove ads\".",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EASY_SUDOKU)

    execute {
        var zeroed = 0

        for (dir in DIMENS_DIRS) {
            try {
                document("res/$dir/dimens.xml").use { doc ->
                    val dimens = doc.getElementsByTagName("dimen")
                    for (index in 0 until dimens.length) {
                        val node = dimens.item(index)
                        if (node.attributes?.getNamedItem("name")?.nodeValue == DIMEN_NAME) {
                            node.textContent = "0dp"
                            zeroed++
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // dimens.xml assente in questa cartella: normale, si prosegue.
            }
        }

        check(zeroed > 0) {
            "Dimension $DIMEN_NAME was not found in any res/values*/dimens.xml: " +
                "the empty banner strip would stay on screen."
        }
        PatchLog.info(PATCH_NAME, "banner height zeroed in $zeroed resource folders")
    }
}
