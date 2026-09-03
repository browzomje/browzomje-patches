package app.browzomje.patches.pinterest.comments.hide_comments

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.browzomje.patches.shared.returnFalseWhen
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/HideComments;"

private const val PATCH_NAME = "Hide comments"

/**
 * Toglie dal pin la sezione commenti e la sua anteprima, "Visualizza tutti" e fumetto della barra
 * azioni compresi (issue #36).
 *
 * L'hook è **sempre** iniettato (`default = true`), ma l'interruttore della schermata Morphe è
 * **spento** per default: questa patch toglie una funzione dell'app invece di cambiare la strada
 * con cui ci si arriva, e la issue la chiede come interruttore, non come comportamento. Chi non la
 * accende non si accorge di niente.
 *
 * ### Perché non si nasconde nessuna view
 *
 * Ogni modulo del closeup implementa `PinCloseupBaseModule.shouldShowForPin()` per decidere da sé
 * se comparire. Farlo rispondere "no" è la strada che l'app percorre già quando un pin non ha
 * commenti: costa una riga di bytecode per modulo, non lascia lo spazio vuoto che lascerebbe un
 * `setVisibility(GONE)` su una itemView di RecyclerView, e non c'è niente da riapplicare quando il
 * layout viene rifatto. Il fumetto della barra azioni è invece una view a sé, fuori dai moduli, e
 * lì si nasconde davvero — con `MorpheViews`, che è fatto apposta per resistere ai ripristini.
 *
 * ### Se qualcosa non combacia
 *
 * Nessuna delle due parti fa fallire la build da sola: se una versione futura cambiasse la forma
 * dei moduli, l'altra metà della patch continuerebbe a funzionare. Il log di build dice quante
 * cose sono state agganciate, e a runtime `HideComments` avvisa se il modulo agganciato non porta
 * nessuno dei due contenitori dei commenti — cioè se il fingerprint è atterrato sul modulo
 * sbagliato, che è l'unico modo in cui questa patch può rompersi in silenzio.
 */
@Suppress("unused")
val hideCommentsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Hides the pin comments section, the comment preview and the comments button, " +
        "when the matching Morphe setting is enabled.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // ------------------------------------------------ i due moduli commenti del closeup
        //
        // L'iniezione è in testa al metodo, dove `v0` è un locale non ancora inizializzato:
        // nessuno può leggerlo prima che il codice originale ci scriva. `returnFalseWhen`
        // verifica che il frame abbia davvero un registro oltre ai parametri e, se non ce l'ha,
        // non inietta niente invece di sovrascrivere `this` — un metodo che R8 ha compilato in
        // `return p0` non ha locali liberi.
        val moduleMatches = CommentsModuleShouldShowForPinFingerprint.matchAllOrNull()
        if (moduleMatches.isNullOrEmpty()) {
            PatchLog.warn(
                PATCH_NAME,
                "no closeup module mentioning the comment anchors was found: " +
                    "the comments section is NOT hidden.",
            )
        } else {
            var hookedModules = 0
            for (match in moduleMatches) {
                val method = match.method
                val injected = method.returnFalseWhen(
                    "invoke-static/range { p0 .. p0 }, " +
                        "$EXTENSION_CLASS->shouldHideCommentsModule(Ljava/lang/Object;)Z",
                )
                if (injected) {
                    hookedModules++
                    PatchLog.hooked(PATCH_NAME, method, "comments module")
                } else {
                    PatchLog.warn(
                        PATCH_NAME,
                        "${method.definingClass}->${method.name} has no free local register: skipped.",
                    )
                }
            }

            // Due moduli convivono e un experiment sceglie quale mostrare: agganciarne uno solo è
            // il modo in cui questa patch "funziona a me" e non all'utente.
            if (hookedModules < 2) {
                PatchLog.warn(
                    PATCH_NAME,
                    "only $hookedModules comments module(s) hooked, 2 expected: " +
                        "one of the two closeup layouts may show comments anyway.",
                )
            } else {
                PatchLog.info(PATCH_NAME, "hooked $hookedModules comments modules")
            }
        }

        // ------------------------------------------------ il fumetto nella barra azioni
        //
        // Ci si aggancia **subito dopo la chiamata al costruttore della superclasse**, e non prima
        // del `return`, per due ragioni che si sommano: prima di `super()` l'oggetto non è
        // inizializzato e chiamarci sopra un metodo di `View` è un errore, mentre in coda al
        // costruttore `p0` può non essere più `this` — R8 riusa i registri dei parametri appena il
        // parametro è morto. Subito dopo `super()` è l'unico punto in cui l'oggetto è insieme
        // inizializzato e ancora in `p0`.
        //
        // Di conseguenza l'extension riceve la barra **appena nata**, con le view figlie non
        // ancora aggiunte: il lavoro è rimandato con `View.post`, come per l'intestazione del feed.
        val actionBarMatches = PinActionBarConstructorFingerprint.matchAllOrNull()
        if (actionBarMatches.isNullOrEmpty()) {
            PatchLog.warn(
                PATCH_NAME,
                "UnifiedPinActionBarView not found: the comments button stays visible.",
            )
        } else {
            var hookedBars = 0
            for (match in actionBarMatches) {
                val method = match.method
                try {
                    method.addInstructionsAfterSuperConstructor(
                        "invoke-static/range { p0 .. p0 }, " +
                            "$EXTENSION_CLASS->hideCommentsActionButton(Ljava/lang/Object;)V",
                    )
                    hookedBars++
                    PatchLog.hooked(PATCH_NAME, method, "pin action bar, after super()")
                } catch (e: IllegalStateException) {
                    // Un costruttore che delega a un altro dello stesso oggetto non chiama nessun
                    // `super()`: non c'è niente da agganciare lì, e l'aggancio sull'altro basta.
                    PatchLog.info(
                        PATCH_NAME,
                        "${method.definingClass}-><init> has no super() call: skipped",
                    )
                }
            }
            PatchLog.info(PATCH_NAME, "hooked $hookedBars pin action bar constructor(s)")
        }
    }
}
