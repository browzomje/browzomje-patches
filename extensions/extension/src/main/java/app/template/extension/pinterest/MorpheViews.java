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

    /** Chiave del tag usata per non registrare due volte il listener sulla stessa View. */
    private static final int TAG_KEY = "morphe_hidden".hashCode() & 0x00ffffff;

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

        MorpheLog.ok(channel, what + " hidden (" + view.getClass().getSimpleName() + ")");
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
