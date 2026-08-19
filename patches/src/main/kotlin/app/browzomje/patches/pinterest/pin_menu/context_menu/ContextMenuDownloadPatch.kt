package app.browzomje.patches.pinterest.pin_menu.context_menu

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.AccessFlags

private const val PATCH_NAME = "Download pin from long press"
private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

/**
 * Il metodo con cui il menu circolare — quello che compare tenendo premuto un pin — riceve i propri
 * tasti, prima di disporli sull'arco.
 *
 * <p>**Un fingerprint insolitamente solido**, per gli standard di Pinterest: la classe che lo
 * definisce, `ContextMenuView`, ha un nome pulito. Non è offuscata perché viene inflatata per nome
 * dai layout XML, e R8 non può rinominare una classe citata in un file di risorse. Il nome del
 * *metodo* invece è accorciato (`n0` su 14.32.0), quindi non lo si nomina: è l'unico metodo `void`
 * della classe che prende una `List`, e tanto basta.
 *
 * <p>Agganciarsi qui, e non alla disposizione dei tasti, è ciò che rende il tasto aggiunto
 * indistinguibile dagli altri: la lista viene consegnata *prima* che il menu calcoli le posizioni,
 * quindi angolo, dimensione, animazione d'ingresso e selezione al rilascio se ne occupa lui.
 */
internal object ContextMenuItemsFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/ui/menu/ContextMenuView;",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

/**
 * Aggiunge un tasto "scarica" al menu circolare che compare tenendo premuto un pin, così l'immagine
 * si salva senza doverlo aprire.
 *
 * <p>Il tasto si comporta come gli altri — si trascina e si rilascia sopra — e non perché lo
 * riproduciamo noi: si accoda alla stessa lista che il menu riceve, e da lì in poi è il menu a
 * disporlo, animarlo e selezionarlo. Anche l'azione passa per la strada normale, perché il menu
 * esegue i propri tasti con un banale `OnClickListener`.
 *
 * <p>L'iniezione è in testa al metodo: la lista va arricchita **prima** che il menu la legga per
 * calcolare le posizioni sull'arco.
 */
@Suppress("unused")
val contextMenuDownloadPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Adds a download button to the circular menu you get by long-pressing a pin, " +
        "so the image can be saved without opening it.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val method = ContextMenuItemsFingerprint.methodOrNull
        if (method == null) {
            PatchLog.warn(
                PATCH_NAME,
                "ContextMenuView's item list method not found: no download button is added.",
            )
            return@execute
        }

        // p0 è il menu, p1 la lista dei tasti. Si scrivono per nome: in testa al metodo sono
        // certamente ancora i parametri, e calcolarne l'indice è il modo di sbagliare in silenzio.
        method.addInstructions(
            0,
            "invoke-static/range { p0 .. p1 }, " +
                "$EXTENSION_CLASS->addContextMenuDownloadItem(Ljava/lang/Object;Ljava/util/List;)V",
        )
        PatchLog.hooked(PATCH_NAME, method, "long-press menu items")
    }
}
