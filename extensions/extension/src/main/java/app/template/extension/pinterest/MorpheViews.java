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

    private static void apply(View view) {
        view.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != 0) {
            params.height = 0;
            view.setLayoutParams(params);
        }
    }
}
