package app.browzomje.patches.pinterest.pin_menu.context_menu

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

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

private object ContextMenuShowFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/ui/menu/ContextMenuView;",
    returnType = "V",
    custom = { method, _ ->
        method.name == "n" && method.parameterTypes.map(CharSequence::toString) in setOf(
            listOf("Lq80/d;", "Lde/b;"),
            listOf("Lb90/d;", "Lgp1/c;"),
        )
    },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

private object ComposePinMenu : Fingerprint(
    definingClass = "Lja3/u0;",
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;", "Lrd2/z;", "Ll22/e;"),
)

private object ComposePinAction : Fingerprint(
    definingClass = "Lja3/g;",
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
)

private const val COMPOSE_DOWNLOAD = "Lapp/browzomje/extension/pinterest/ComposePinDownload;"

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
    description = "Adds a download action to the long-press pin menu, including the list layout on Pinterest 14.45.0.",
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
        val bindsSelection = packageMetadata.versionName in setOf("14.44.0", "14.45.0")
        val addItem = if (bindsSelection) "addBoundContextMenuDownloadItem" else "addContextMenuDownloadItem"
        if (bindsSelection) {
            val show = ContextMenuShowFingerprint.method
            show.addInstructions(
                0,
                "invoke-static/range { p0 .. p1 }, " +
                    "$EXTENSION_CLASS->bindContextMenuDownloadSelection(Ljava/lang/Object;Ljava/lang/Object;)V",
            )
            PatchLog.hooked(PATCH_NAME, show, "bind the native long-press selection")
        }
        method.addInstructions(
            0,
            "invoke-static/range { p0 .. p1 }, " +
                "$EXTENSION_CLASS->$addItem(Ljava/lang/Object;Ljava/util/List;)V",
        )
        PatchLog.hooked(PATCH_NAME, method, "long-press menu items")

        if (packageMetadata.versionName == "14.45.0") {
            val compose = ComposePinMenu.method
            val code = compose.implementation!!.instructions
            val nativeOptions = code.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL && (it as? ReferenceInstruction)?.reference.toString() ==
                    "Lh82/a;->q(Lia3/s0;)Ljava/util/ArrayList;"
            }
            check(nativeOptions >= 0 && code[nativeOptions + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
            val listRegister = (code[nativeOptions + 1] as OneRegisterInstruction).registerA
            compose.addInstructions(nativeOptions + 2, """
                invoke-static/range {v$listRegister .. v$listRegister}, $COMPOSE_DOWNLOAD->append(Ljava/util/ArrayList;)V
            """.trimIndent())
            PatchLog.hooked(PATCH_NAME, compose, "Compose long-press download item")

            val action = ComposePinAction.method
            val actionCode = action.implementation!!.instructions
            val repin = actionCode.indexOfFirst {
                it.opcode == Opcode.SGET_OBJECT && (it as? ReferenceInstruction)?.reference.toString() ==
                    "Lia3/r0;->REPIN:Lia3/r0;"
            }
            check(repin >= 0 && actionCode[repin + 2].opcode == Opcode.IF_NE)
            val pin = actionCode[repin - 4] as TwoRegisterInstruction
            check(pin.opcode == Opcode.IGET_OBJECT && (pin as ReferenceInstruction).reference.toString() ==
                "Lia3/a;->a:Lcom/pinterest/api/model/uu;")
            val option = actionCode[repin + 2] as TwoRegisterInstruction
            val scratch = (actionCode[repin] as OneRegisterInstruction).registerA
            check(option.registerB == scratch)
            val unit = actionCode.filter {
                it.opcode == Opcode.SGET_OBJECT && (it as? ReferenceInstruction)?.reference.toString() ==
                    "Lkotlin/Unit;->a:Lkotlin/Unit;"
            }.first() as ReferenceInstruction
            action.addInstructions(repin, """
                invoke-static {v${option.registerA}, v${pin.registerA}}, $COMPOSE_DOWNLOAD->download(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v$scratch
                if-eqz v$scratch, :morphe_native_option
                sget-object v$scratch, ${unit.reference}
                return-object v$scratch
                :morphe_native_option
                nop
            """.trimIndent())
            PatchLog.hooked(PATCH_NAME, action, "Compose long-press selected pin")
        }
    }
}
