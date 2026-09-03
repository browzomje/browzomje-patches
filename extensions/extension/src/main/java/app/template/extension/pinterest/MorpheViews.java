package app.browzomje.extension.pinterest;

import android.view.View;
import android.view.ViewGroup;

/**
 * Utilità per nascondere in modo <em>stabile</em> una View appartenente a Pinterest.
 *
 * <p>Un semplice {@code setVisibility(GONE)} non basta: il codice originale rifà il layout
 * (ricostruisce la barra di navigazione, ri-binda la riga del RecyclerView…) e rimette la
 * visibilità a VISIBLE. Inoltre, dentro un RecyclerView, una itemView GONE può continuare a
 * occupare spazio perché il LayoutManager la misura comunque.
 *
 * <p>Qui si fa quindi: GONE + altezza forzata a 0 + un {@link View.OnLayoutChangeListener} che
 * riapplica entrambi quando qualcuno prova a ripristinarli. Il listener è registrato una volta
 * sola per View (marcata con un tag) per non accumularne uno per ogni ri-bind.
 */
final class MorpheViews {

    /**
     * Chiave del tag usata per non registrare due volte il listener sulla stessa View.
     *
     * <p>Il byte alto <b>deve</b> valere almeno 2: {@link View#setTag(int, Object)} rifiuta le
     * chiavi con {@code key >>> 24 < 2} — sono lo spazio degli id di sistema — con
     * {@code IllegalArgumentException: The key must be an application-specific resource id}.
     * La versione precedente mascherava l'hash con {@code & 0x00ffffff}, che azzera proprio quel
     * byte: <em>ogni</em> chiamata a {@link #hidePersistently} finiva quindi in eccezione subito
     * dopo aver nascosto la view, saltando tutto quello che veniva dopo (il log "hidden", e
     * soprattutto le eventuali altre view da nascondere nello stesso giro).
     *
     * <p>Si usa il byte 0x02 e non 0x7f: quest'ultimo è lo spazio delle risorse dell'app, dove
     * la chiave potrebbe coincidere con un id vero usato da Pinterest sulla stessa view.
     */
    private static final int TAG_KEY = 0x02000000 | ("morphe_hidden".hashCode() & 0x00ffffff);

    private MorpheViews() {}

    /**
     * Nasconde la View e continua a tenerla nascosta.
     *
     * @param channel canale di log della feature che sta nascondendo (per i messaggi).
     * @param what descrizione leggibile dell'elemento, per il log.
     */
    static void hidePersistently(final View view, final String channel, final String what) {
        if (view == null) {
            MorpheLog.w(channel, "could not hide " + what + ": null view");
            return;
        }

        apply(view);
        MorpheLog.ok(channel, what + " hidden (" + view.getClass().getSimpleName() + ")");

        // Il presidio contro i ripristini è un extra: se fallisce, la view è comunque già
        // nascosta e chi ha chiamato deve poter proseguire (magari ha altre view da nascondere).
        try {
            if (Boolean.TRUE.equals(view.getTag(TAG_KEY))) {
                return; // listener già installato
            }
            view.setTag(TAG_KEY, Boolean.TRUE);

            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (v.getVisibility() != View.GONE || v.getHeight() != 0) {
                        MorpheLog.d(channel, what + " was restored by the app: hiding it again");
                        apply(v);
                    }
                }
            });
        } catch (Throwable t) {
            MorpheLog.w(channel, what + " hidden, but it could not be kept hidden: the app can "
                    + "bring it back", t);
        }
    }

    /** Rende di nuovo visibile una View precedentemente nascosta (toggle spento a runtime). */
    static void show(View view, int originalHeight) {
        if (view == null) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height == 0) {
            params.height = originalHeight;
            view.setLayoutParams(params);
        }
    }

    /**
     * Cerca dentro {@code root} (compresa) la View il cui id di risorsa si chiama {@code idName}.
     *
     * <p>L'id si risolve <b>per nome</b> con {@code getIdentifier} e mai per valore numerico: i
     * valori sono ricalcolati a ogni build dell'app, i nomi no — sono il modo in cui gli XML di
     * layout si citano fra loro, quindi R8 non può toccarli. È la stessa regola che segue
     * l'intestazione del feed.
     *
     * @param root radice della sottostruttura da esplorare; se null si restituisce null.
     * @param idName nome della risorsa, es. {@code "unified_comments_module_container"}.
     * @param channel canale di log, per dire quale ricerca è fallita e perché.
     * @return la View trovata, o null se l'id non esiste in questa versione dell'app oppure se
     *     nessuna View della sottostruttura lo porta.
     */
    static View findDescendantByIdName(View root, String idName, String channel) {
        if (root == null) {
            return null;
        }
        int id;
        try {
            id = root.getContext().getResources()
                    .getIdentifier(idName, "id", root.getContext().getPackageName());
        } catch (Throwable t) {
            MorpheLog.w(channel, "could not resolve @id/" + idName, t);
            return null;
        }
        if (id == 0) {
            MorpheLog.d(channel, "@id/" + idName + " does not exist in this version of the app");
            return null;
        }
        if (root.getId() == id) {
            return root;
        }
        return root.findViewById(id);
    }

    private static void apply(View view) {
        view.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != 0) {
            params.height = 0;
            view.setLayoutParams(params);
        }
    }
}
