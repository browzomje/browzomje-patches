package app.browzomje.extension.pinterest;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Il tasto "scarica" nel menu circolare che compare tenendo premuto un pin.
 *
 * <p><b>Come si integra.</b> Non disegniamo niente e non calcoliamo nessun angolo: si gonfia lo
 * stesso layout che Pinterest usa per i propri tasti ({@code contextmenu_item}) e lo si accoda alla
 * lista che il menu riceve prima di disporli. Da lì in poi posizione sull'arco, animazione
 * d'ingresso, evidenziazione mentre ci si passa sopra e selezione al rilascio sono gestite dal menu
 * esattamente come per gli altri tasti. Anche l'azione: il menu esegue i propri tasti con un normale
 * {@link View.OnClickListener}, quindi trascinare e rilasciare sopra il nostro fa scattare il
 * download.
 *
 * <p><b>Cosa si nomina e cosa no.</b> Nessuna classe offuscata. Il layout e l'icona si cercano per
 * <em>nome di risorsa</em>, che è molto più stabile di un nome di classe (è il nome che i file XML
 * usano fra loro, e R8 non lo tocca). L'unica cosa che si tocca per struttura è l'etichetta: il
 * testo del tasto sta in un campo privato che è l'unico {@code String} della classe, e si scrive
 * quello invece di cercare il metodo che lo imposta — quel metodo vuole un id di risorsa, e i nostri
 * testi sono tradotti da noi.
 */
final class ContextMenuDownload {

    private ContextMenuDownload() {}

    /** Il layout di un tasto del menu circolare, lo stesso che Pinterest gonfia per i suoi. */
    private static final String ITEM_LAYOUT = "contextmenu_item";

    /** L'icona di download del set Gestalt, la stessa usata nel menu "…" del pin. */
    private static final String ICON = "ic_vr_download_gestalt";

    /**
     * Accoda il tasto alla lista dei tasti del menu.
     *
     * <p>Chiamata in testa al metodo che riceve la lista, quindi prima che il menu ne calcoli le
     * posizioni. Non solleva mai: se qualcosa non si trova, il menu resta quello di prima.
     *
     * @param menuView il menu circolare, da cui si ricava il Context e il pin corrente
     * @param items la lista dei tasti, modificata sul posto
     */
    @SuppressWarnings("unchecked")
    static void addItem(Object menuView, List<Object> items) {
        if (!(menuView instanceof View) || items == null) {
            return;
        }
        if (!MorpheSettingsStore.isLongPressDownloadEnabled()) {
            MorpheLog.d(MorpheLog.BOARD, "long-press download disabled in the Morphe settings");
            return;
        }

        final Context context = ((View) menuView).getContext();
        try {
            // Il pin a cui appartiene il menu: serve sia per scaricarlo sia per sapere se c'è.
            final Object pin = CurrentPin.findPinIn(menuView);
            if (pin == null) {
                MorpheLog.d(MorpheLog.BOARD, "long-press menu: no pin found, button not added");
                return;
            }

            int layoutId = context.getResources()
                    .getIdentifier(ITEM_LAYOUT, "layout", context.getPackageName());
            if (layoutId == 0) {
                MorpheLog.w(MorpheLog.BOARD, "layout " + ITEM_LAYOUT + " not found");
                return;
            }

            View item = LayoutInflater.from(context).inflate(layoutId, null);
            if (item == null) {
                return;
            }

            setIcon(context, item);
            setLabel(item, PinterestUtils.getString("download_image_label"));

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BoardDownloadHandler.downloadSinglePin(v.getContext(), pin);
                }
            });

            // In coda: il menu dispone i tasti nell'ordine della lista, quindi il nostro finisce
            // all'estremità dell'arco invece di spostare quelli a cui l'utente è abituato.
            items.add(item);
            MorpheLog.ok(MorpheLog.BOARD, "download button added to the long-press menu");
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.BOARD, "could not add the long-press download button", t);
        }
    }

    /** L'icona sta nell'unica ImageView del layout del tasto. */
    private static void setIcon(Context context, View item) {
        ImageView icon = PinterestUtils.findImageView(item);
        if (icon == null) {
            return;
        }
        int iconId = context.getResources()
                .getIdentifier(ICON, "drawable", context.getPackageName());
        if (iconId != 0) {
            icon.setImageResource(iconId);
        }
    }

    /**
     * Scrive l'etichetta mostrata mentre si tiene il dito sul tasto.
     *
     * <p>Il testo non è una view ma un campo del tasto, che il menu legge per scriverlo al centro
     * del cerchio. Si individua per tipo — è l'unico {@code String} dichiarato dalla classe — invece
     * che per nome, che è offuscato.
     */
    private static void setLabel(View item, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        try {
            for (Field field : item.getClass().getDeclaredFields()) {
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(item, label);
                return;
            }
            MorpheLog.d(MorpheLog.BOARD, "no label field on " + item.getClass().getName());
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.BOARD, "could not set the button label: " + t);
        }
    }
}
