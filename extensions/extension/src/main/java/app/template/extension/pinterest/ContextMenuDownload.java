package app.browzomje.extension.pinterest;

import android.content.Context;
import android.graphics.drawable.Drawable;
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

            // L'ordine conta: il tasto applica cerchio e icona solo quando ha un'etichetta —
            // senza, esce dal proprio metodo di aggiornamento senza disegnare niente.
            setLabel(item, PinterestUtils.getString("download_image_label"));
            applyAppearance(context, item, items);

            // Il pin si cerca al **click**, non adesso.
            //
            // Quando questo metodo gira il menu sta ancora raccogliendo i propri tasti e non è
            // ancora legato al pin: cercandolo qui non si trovava niente e il tasto non veniva
            // nemmeno aggiunto. Al click invece il menu è montato e collegato, e la stessa visita
            // del grafo lo trova. In più il costo si paga solo quando l'utente scarica davvero,
            // invece che a ogni pressione prolungata.
            final Object menu = menuView;
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object pin = CurrentPin.findPinIn(menu);
                    if (pin == null) {
                        MorpheLog.w(MorpheLog.BOARD, "long-press download: pin not found in "
                                + menu.getClass().getName());
                        PinterestUtils.showNativeToast(v.getContext(),
                                PinterestUtils.getString("no_image"));
                        return;
                    }
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

    /**
     * Dà al tasto l'aspetto degli altri: cerchio scuro, icona centrata, colori di stato.
     *
     * <p><b>Perché non basta scrivere l'icona sulla ImageView.</b> Il tasto non disegna quello che
     * gli si mette nella ImageView: tiene l'icona in un proprio campo e la riapplica ogni volta che
     * cambia stato — quando ci passi sopra il dito, per esempio. Scrivendo solo la ImageView,
     * l'icona spariva al primo cambio di stato e il cerchio scuro non compariva mai.
     *
     * <p><b>Perché si copia da un fratello invece di chiamare i metodi del tasto.</b> I metodi che
     * fanno questo lavoro hanno nomi accorciati e la stessa firma (un {@code int} solo): non c'è
     * modo di distinguere quello dell'icona da quello dell'etichetta senza tirare a indovinare.
     * Un tasto già nella lista, invece, è per definizione configurato come Pinterest vuole: si
     * copiano i suoi campi grafici e si sostituisce la sola icona. Si adatta da sé a tema chiaro,
     * tema scuro e a qualunque variante il menu stia usando.
     *
     * <p>I campi che puntano a delle view non si copiano mai: farlo significherebbe far disegnare
     * il nostro tasto dentro quello del vicino.
     */
    private static void applyAppearance(Context context, View item, List<Object> siblings) {
        ImageView ourIcon = PinterestUtils.findImageView(item);
        Drawable icon = loadIcon(context);
        if (ourIcon != null && icon != null) {
            ourIcon.setImageDrawable(icon);
        }

        View sibling = firstSibling(siblings);
        if (sibling == null) {
            MorpheLog.d(MorpheLog.BOARD, "no sibling button to copy the look from");
            return;
        }
        ImageView siblingIcon = PinterestUtils.findImageView(sibling);
        Drawable siblingDrawable = siblingIcon == null ? null : siblingIcon.getDrawable();

        try {
            for (Field field : item.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Class<?> type = field.getType();

                if (Drawable.class.isAssignableFrom(type)) {
                    Object theirs = field.get(sibling);
                    // Il campo che nel fratello contiene proprio l'icona disegnata è quello
                    // dell'icona: lì ci va la nostra, in tutti gli altri (gli sfondi dei vari
                    // stati) si copia il valore del fratello.
                    boolean isTheIconField = theirs != null && theirs == siblingDrawable;
                    field.set(item, isTheIconField ? icon : theirs);
                } else if (type == boolean.class) {
                    // Interruttori di presentazione: quale colore usare da evidenziato, se
                    // l'icona va rimpicciolita, e simili.
                    field.setBoolean(item, field.getBoolean(sibling));
                } else if (type == int.class) {
                    copyIfColour(context, field, sibling, item);
                }
            }
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.BOARD, "could not copy the button look: " + t);
        }

        if (ourIcon != null && siblingIcon != null) {
            ourIcon.setBackground(siblingIcon.getBackground());
            ourIcon.setScaleType(siblingIcon.getScaleType());
            ourIcon.setPaddingRelative(
                    siblingIcon.getPaddingStart(), siblingIcon.getPaddingTop(),
                    siblingIcon.getPaddingEnd(), siblingIcon.getPaddingBottom());

            // Anche la tinta viene dal fratello, ed è la ragione per cui l'icona usciva del
            // colore sbagliato: appena inflatato, il tasto si applica da sé un filtro colore
            // pensato per lo *sfondo*, e a correggerlo è un metodo che sui tasti di Pinterest
            // viene chiamato durante la costruzione e sul nostro no. Il risultato era un'icona
            // scura su tema scuro e chiara su tema chiaro — esattamente l'opposto delle altre.
            ourIcon.setColorFilter(siblingIcon.getColorFilter());
        }
    }

    /**
     * Copia un campo intero solo se contiene un colore.
     *
     * <p><b>Perché non si copiano tutti.</b> Fra gli interi del tasto ci sono sia i colori dei vari
     * stati sia l'<em>identificativo dell'azione</em>, quello con cui il menu riconosce, per dire,
     * la ricerca visuale. Copiare anche quello significherebbe far passare il nostro tasto per un
     * altro, con conseguenze imprevedibili al rilascio del dito.
     *
     * <p>La distinzione non ha bisogno di nomi: un id di colore si risolve con
     * {@code getColor()}, un id di tutt'altro tipo no e solleva. Si prova, e si copia solo ciò che
     * un colore lo è davvero.
     *
     * <p>Serve perché al passaggio del dito il tasto **ricalcola** la propria tinta da questi
     * campi: senza, l'icona spariva appena la si sfiorava e non tornava più.
     */
    private static void copyIfColour(Context context, Field field, View sibling, View item)
            throws IllegalAccessException {
        int theirs = field.getInt(sibling);
        if (theirs == 0) {
            return;
        }
        try {
            context.getColor(theirs);
        } catch (Throwable notAColour) {
            return;
        }
        field.setInt(item, theirs);
    }

    /** @return il primo tasto già nella lista, da cui copiare l'aspetto. */
    private static View firstSibling(List<Object> siblings) {
        for (Object candidate : siblings) {
            if (candidate instanceof View) {
                return (View) candidate;
            }
        }
        return null;
    }

    private static Drawable loadIcon(Context context) {
        int iconId = context.getResources()
                .getIdentifier(ICON, "drawable", context.getPackageName());
        if (iconId == 0) {
            MorpheLog.w(MorpheLog.BOARD, "icon " + ICON + " not found");
            return null;
        }
        try {
            return context.getDrawable(iconId);
        } catch (Throwable t) {
            return null;
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
