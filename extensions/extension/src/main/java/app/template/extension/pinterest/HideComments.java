package app.browzomje.extension.pinterest;

import android.view.View;

/**
 * Nasconde la sezione commenti del pin e la sua anteprima, "Visualizza tutti" compreso
 * (issue #36).
 *
 * <p><b>Non si nasconde nessuna view.</b> Si risponde "no" al metodo con cui il modulo decide da
 * sé se comparire — {@code PinCloseupBaseModule.shouldShowForPin()} — che è la stessa strada che
 * l'app percorre quando un pin non ha commenti. Costa una riga di bytecode per modulo, non
 * combatte con il layout del RecyclerView e non lascia lo spazio vuoto che lascerebbe un
 * {@code setVisibility(GONE)} su una itemView (vedi la nota su {@code MorpheViews} in
 * {@code PATCHING_MEMORY.md}).
 *
 * <p><b>I due moduli.</b> Il closeup ha due sezioni commenti diverse che convivono, scelte da un
 * experiment — è la struttura ricorrente dell'app, "quasi ogni feature ha due percorsi":
 *
 * <ul>
 *   <li>la sezione completa ({@code pin_closeup_unified_comments_module}: intestazione reazioni,
 *       anteprima dei commenti, "altri commenti", composer);</li>
 *   <li>l'anteprima nuova ({@code pin_closeup_new_comments_module}: la riga con l'anteprima e
 *       "visualizza tutti" della schermata nella issue).</li>
 * </ul>
 *
 * Vengono agganciate entrambe, perché agganciarne una sola darebbe il classico "a me funziona".
 *
 * <p><b>Il tasto nella barra azioni</b> ({@code action_module_comments_wrapper}, il fumetto
 * accanto a "Salva") è una view a sé, fuori dai moduli: quello sì va nascosto, altrimenti la
 * sezione resterebbe raggiungibile con un tocco — e la issue chiede esplicitamente che non lo sia.
 */
public final class HideComments {

    /** Il contenitore radice di ciascuno dei due moduli commenti, per nome di risorsa. */
    private static final String[] MODULE_CONTAINER_IDS = {
        "unified_comments_module_container",
        "new_comments_module_container",
    };

    /** Il fumetto "commenti" nella barra delle azioni del pin. */
    private static final String ACTION_BAR_COMMENTS_ID = "action_module_comments_wrapper";

    private HideComments() {}

    /**
     * Chiamata dal bytecode in testa a {@code shouldShowForPin()} dei due moduli commenti.
     *
     * <p>Il parametro è {@code Object} e non {@code View} perché il registro sorgente ha il tipo
     * statico offuscato del modulo: dichiarare qui un tipo più stretto farebbe fallire il
     * verificatore Dalvik al caricamento della classe.
     *
     * @param module il modulo del closeup ({@code this} del metodo agganciato).
     * @return true se il modulo non deve comparire.
     */
    public static boolean shouldHideCommentsModule(Object module) {
        MorpheLog.hookFired(MorpheLog.COMMENTS, "shouldShowForPin() on "
                + (module == null ? "null" : module.getClass().getName()));

        if (!MorpheSettingsStore.isCommentsHidden()) {
            MorpheLog.d(MorpheLog.COMMENTS, "switch is off: the comments section stays");
            return false;
        }
        if (module instanceof View) {
            checkIsCommentsModuleOnce((View) module);
        }
        MorpheLog.ok(MorpheLog.COMMENTS, "comments module hidden ("
                + (module == null ? "null" : module.getClass().getSimpleName()) + ")");
        return true;
    }

    /** Classi già verificate, per non rifare la ricerca a ogni chiamata di `shouldShowForPin`. */
    private static final java.util.Map<String, Boolean> CHECKED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Verifica <b>una volta per classe</b> che il modulo agganciato sia davvero un modulo
     * commenti, e lo dice in logcat se non lo è.
     *
     * <p>I due moduli sono individuati in fase di patch, quindi in teoria l'hook non può finire
     * altrove. Ma il modo peggiore in cui questa patch può rompersi è proprio agganciare il
     * modulo sbagliato in una versione futura: la build riuscirebbe e l'utente vedrebbe sparire
     * una sezione a caso del closeup, senza nessun errore da nessuna parte. Questo controllo
     * trasforma quel guasto muto in una riga di log.
     */
    private static void checkIsCommentsModuleOnce(View module) {
        String className = module.getClass().getName();
        if (CHECKED.containsKey(className)) {
            return;
        }
        String container = commentsContainerNameOf(module);
        CHECKED.put(className, Boolean.TRUE);
        if (container != null) {
            MorpheLog.d(MorpheLog.COMMENTS, className + " holds @id/" + container
                    + ": this is the comments module, as expected");
        } else {
            MorpheLog.w(MorpheLog.COMMENTS, className + " holds neither "
                    + MODULE_CONTAINER_IDS[0] + " nor " + MODULE_CONTAINER_IDS[1]
                    + ": the fingerprint may have landed on the wrong closeup module");
        }
    }

    /**
     * Chiamata dal bytecode dopo il costruttore di
     * {@code com.pinterest.feature.pin.closeup.view.UnifiedPinActionBarView}, per togliere il
     * fumetto che apre i commenti.
     *
     * <p>Il lavoro è rimandato con {@link View#post}: qui la barra è appena stata costruita e i
     * suoi figli potrebbero non essere ancora tutti in piedi. Su una view non ancora attaccata
     * l'azione viene accodata ed eseguita all'attach, cioè quando la barra è completa.
     *
     * @param actionBar la barra azioni del pin ({@code this} del costruttore agganciato).
     */
    public static void hideCommentsActionButton(final Object actionBar) {
        MorpheLog.hookFired(MorpheLog.COMMENTS, "UnifiedPinActionBarView built, container = "
                + (actionBar == null ? "null" : actionBar.getClass().getName()));

        if (!(actionBar instanceof View)) {
            MorpheLog.w(MorpheLog.COMMENTS, "hook received "
                    + (actionBar == null ? "null" : actionBar.getClass().getName())
                    + " instead of a View: the comment button stays");
            return;
        }

        final View bar = (View) actionBar;
        bar.post(new Runnable() {
            @Override
            public void run() {
                if (!MorpheSettingsStore.isCommentsHidden()) {
                    MorpheLog.d(MorpheLog.COMMENTS, "switch is off: the comment button stays");
                    return;
                }
                View button = MorpheViews.findDescendantByIdName(
                        bar, ACTION_BAR_COMMENTS_ID, MorpheLog.COMMENTS);
                if (button == null) {
                    MorpheLog.w(MorpheLog.COMMENTS, "@id/" + ACTION_BAR_COMMENTS_ID
                            + " is not in this action bar: nothing to hide here");
                    return;
                }
                MorpheViews.hidePersistently(button, MorpheLog.COMMENTS, "comment button");
            }
        });
    }

    /**
     * @return il nome del contenitore commenti trovato dentro {@code module}, o null se questa
     *     view non è un modulo commenti.
     */
    private static String commentsContainerNameOf(View module) {
        for (String idName : MODULE_CONTAINER_IDS) {
            if (MorpheViews.findDescendantByIdName(module, idName, MorpheLog.COMMENTS) != null) {
                return idName;
            }
        }
        return null;
    }
}
