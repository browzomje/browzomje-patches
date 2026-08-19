package app.browzomje.patches.pinterest.ads.ad_views

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val PATCH_NAME = "Hide ad views"
private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

/**
 * Le view che in Pinterest esistono solo per mostrare pubblicità.
 *
 * <p>Sono classi *di Pinterest*, non modelli serializzati con Gson: R8 ne conserva il nome completo,
 * ed è il motivo per cui qui — a differenza di quasi tutto il resto delle patch — ci si può ancorare
 * al nome della classe. Verificate presenti su 14.32.0.
 *
 * <p>Se una versione futura ne rinomina o ne toglie una, il fingerprint corrispondente semplicemente
 * non trova nulla e quella riga viene saltata: le altre continuano a funzionare.
 */
private val AD_VIEW_CLASSES = listOf(
    // Le schede "Shop now" / "Compra ora" del feed.
    "Lcom/pinterest/featurelibrary/textads/TextAdView;",
    // Barra dei tasti d'azione sul closeup di un pin promosso, versione vecchia…
    "Lcom/pinterest/activity/pin/view/modules/LegacyPromotedCloseupActionButtonModule;",
    // …e versione nuova, flottante.
    "Lcom/pinterest/feature/pin/closeup/view/PromotedPinCloseupFloatingActionBarModule;",
    // Intestazione "bacheca sponsorizzata da…".
    "Lcom/pinterest/feature/board/detail/header/view/lego/BoardSponsoredCuratorView;",
)

/**
 * Il costruttore più largo, `<init>(Context, AttributeSet, int)`.
 *
 * <p>In Kotlin queste view sono dichiarate con `@JvmOverloads`, che genera tre costruttori
 * (`(Context)`, `(Context, AttributeSet)`, `(Context, AttributeSet, int)`) di cui i primi due
 * delegano al terzo. Agganciare solo il terzo li copre quindi tutti.
 */
private fun adViewConstructorFingerprint(className: String) = Fingerprint(
    definingClass = className,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Landroid/util/AttributeSet;",
        "I",
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
)

/**
 * Rete di sicurezza dietro a "Disable ads": collassa a zero le view che servono solo a mostrare
 * pubblicità, se mai l'app dovesse costruirne una.
 *
 * <p>**Perché serve, visto che gli annunci li togliamo già.** "Disable ads" lavora a monte: filtra i
 * contenuti sponsorizzati dalle risposte di rete, prima che l'adapter del feed li veda. È l'approccio
 * giusto — niente buchi nel feed, niente scatti di layout — ma copre quello che *passa dalle
 * risposte che agganciamo*. Se Pinterest costruisce una di queste view per un'altra strada (il primo
 * caricamento dopo un'installazione pulita è il caso tipico), a monte non c'è niente da filtrare.
 * Questa patch chiude quella finestra.
 *
 * <p>**Come.** In coda al costruttore — dopo che tutto il resto è girato: iniezioni Dagger, inflate,
 * listener, così chi tiene un riferimento alla view non trova campi null — si chiama
 * `PinterestUtils.hideAdView(this)`, che mette la view a `GONE`. `GONE` salta sia la misura sia il
 * disegno: la view non appare e non occupa spazio.
 *
 * <p>**L'interruttore resta vero.** Il controllo su "Disabilita pubblicità" lo fa l'extension a
 * runtime, non il patch: spegnendo l'opzione dalla schermata Morphe queste view tornano visibili,
 * come ci si aspetta. Fosse deciso in fase di patch, l'interruttore non le riporterebbe più
 * indietro.
 */
@Suppress("unused")
val hideAdViewsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Collapses Pinterest's ad-only views (text ad cards, promoted closeup action bars, " +
        "sponsored board headers) so no ad chrome is drawn even if the app builds one. " +
        "Follows the \"Disable ads\" switch in the Morphe settings screen.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // Una sola istruzione, senza registri locali: `p0` è `this`, e la chiamata non ha bisogno
        // di nient'altro. È il motivo per cui non serve verificare che il costruttore abbia un
        // registro libero — cosa che con R8 non è affatto scontata.
        val call = "invoke-static { p0 }, $EXTENSION_CLASS->hideAdView(Landroid/view/View;)V"

        var hooked = 0
        for (className in AD_VIEW_CLASSES) {
            val method = adViewConstructorFingerprint(className).methodOrNull
            if (method == null) {
                PatchLog.warn(PATCH_NAME, "$className not found: skipped.")
                continue
            }

            val returnIndex = method.implementation
                ?.instructions
                ?.indexOfLast { it.opcode == Opcode.RETURN_VOID }
                ?: -1
            if (returnIndex < 0) {
                PatchLog.warn(PATCH_NAME, "$className: constructor has no return-void, skipped.")
                continue
            }

            method.addInstructions(returnIndex, call)
            PatchLog.hooked(PATCH_NAME, method, "ad view collapsed on construction")
            hooked++
        }

        if (hooked == 0) {
            PatchLog.warn(
                PATCH_NAME,
                "no ad view matched: the ad rendering surface has changed and this patch needs " +
                    "an update. \"Disable ads\" keeps working on its own.",
            )
        }
    }
}
