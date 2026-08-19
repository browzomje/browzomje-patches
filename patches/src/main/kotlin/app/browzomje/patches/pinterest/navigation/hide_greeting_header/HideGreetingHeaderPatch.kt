package app.browzomje.patches.pinterest.navigation.hide_greeting_header

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.inputRegisterCount

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

private const val PATCH_NAME = "Hide greeting header buttons"

/**
 * Aggancia l'intestazione del feed per nascondere i tasti "+" (Crea) e Posta/Notifiche, che da
 * 14.28.0 stanno lì e non più nella barra in basso (issue #24).
 *
 * L'hook è sempre iniettato (`default = true`), come per la barra di navigazione: sono gli
 * stessi due toggle della schermata Morphe ("Nascondi tasto Crea (+)" e "Nascondi tasto
 * Notifiche") a decidere a runtime se nascondere davvero, dentro
 * `PinterestUtils.hideGreetingHeaderButtons`.
 */
@Suppress("unused")
val hideGreetingHeaderButtonsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Hides the create and inbox buttons in the home feed header, when the matching Morphe settings are enabled.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        HomeFeedFragmentOnViewCreatedFingerprint.method.apply {
            val registerCount = implementation!!.registerCount
            val inputRegisters = inputRegisterCount()

            // I due tasti li aggiunge questo stesso metodo, quindi in fondo esisterebbero già —
            // ma l'iniezione va comunque **in testa**, non prima del `return`.
            //
            // Il motivo è il verificatore. In uscita il registro del parametro `view` può essere
            // stato riciclato da R8 per un locale di tipo diverso (`view` viene letto l'ultima
            // volta molto prima della fine): un `move-object` da un registro che su quel
            // percorso contiene un int fa fallire il caricamento della classe intera con
            // VerifyError, cioè crash all'apertura del feed. All'ingresso del metodo, invece, i
            // registri dei parametri contengono per definizione i parametri, col tipo dichiarato.
            //
            // È l'extension a rimandare il lavoro a dopo (`View.post`), quando i tasti ci sono.
            check(registerCount > inputRegisters) {
                "$definingClass->$name has no local register: v0 would be a parameter"
            }
            val p1 = registerCount - inputRegisters + 1

            // v0 è un locale non ancora inizializzato: all'ingresso del metodo nessuno può
            // leggerlo prima che il codice originale ci scriva. `move-object/from16` perché il
            // frame è ampio e `invoke-static` non arriva oltre v15.
            addInstructions(
                0,
                """
                move-object/from16 v0, v$p1
                invoke-static/range { v0 .. v0 }, $EXTENSION_CLASS->hideGreetingHeaderButtons(Landroid/view/View;)V
                """.trimIndent(),
            )
            PatchLog.hooked(PATCH_NAME, this, "entry, view in v$p1 of $registerCount")
        }
    }
}
