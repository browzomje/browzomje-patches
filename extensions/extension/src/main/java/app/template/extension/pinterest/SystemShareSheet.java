package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;

/**
 * Apre il foglio di condivisione <b>di sistema</b> al posto di quello interno di Pinterest
 * (issue #38).
 *
 * <p><b>Dove si aggancia.</b> Al costruttore di
 * {@code com.pinterest.feature.sharesheet.view.SharesheetModalAppListView}, la striscia di icone
 * di app dentro il foglio interno. Non è una scelta di comodo: quella classe è inflatata dagli
 * XML di layout, quindi il suo nome sta in una risorsa e R8 non può accorciarlo — è la stessa
 * categoria di ancora di {@code ContextMenuView}. Il resto del foglio ({@code im1.p0},
 * {@code im1.m1} su 14.34.0) ha nomi che cambiano a ogni versione.
 *
 * <p><b>Il prezzo dell'aggancio</b> è lo stesso del browser esterno: si arriva quando il foglio
 * interno è già a schermo, quindi si vede un lampo prima che compaia quello di sistema. Il foglio
 * interno viene poi chiuso con "indietro".
 *
 * <p><b>Che cosa si condivide.</b> Serve un URL, e va trovato senza nominare niente di offuscato.
 * Si cerca il pin nel grafo di oggetti che sta dietro al foglio (lo stesso meccanismo di "Copia
 * link diretto") e da lì si scrive il link canonico. Se il pin non c'è — condivisione di una
 * bacheca, di un profilo, di una conversazione — <b>non si intercetta</b> e il foglio interno
 * resta quello di Pinterest: meglio la schermata originale che un foglio di sistema con dentro il
 * link sbagliato.
 */
public final class SystemShareSheet {

    /** Quanti genitori si risalgono dalla striscia di icone per arrivare al foglio intero. */
    private static final int MAX_PARENTS = 8;

    /**
     * Ultimo pin già dirottato e quando: come per il browser esterno, evita di riaprire il foglio
     * di sistema a ripetizione se la chiusura di quello interno non è riuscita.
     */
    private static volatile String lastSharedUrl;
    private static volatile long lastShareAtMs;

    private static final long SHARE_DEBOUNCE_MS = 3000L;

    /** Ultimo SendableObject intercettato (id, tipo, timestamp). */
    private static volatile String lastSendableId;
    private static volatile int lastSendableType = -1;
    private static volatile long lastSendableAtMs;
    private static final long SENDABLE_VALIDITY_MS = 6000L;

    private SystemShareSheet() {}

    /**
     * Intercetta la creazione di un SendableObject (eseguita all'inizio di ogni flusso di
     * condivisione nell'app: pin, bacheche, profili).
     *
     * <p>Estrae l'id e il tipo dell'oggetto prima che il modale interno venga costruito.
     */
    public static void recordSendableObject(Object sendableObject) {
        if (sendableObject == null) {
            return;
        }
        try {
            Class<?> clazz = sendableObject.getClass();
            String id = null;
            int type = 0; // 0 = PIN per default

            // I campi di SendableObject contengono l'id come primo campo String e il tipo come intero
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (id == null && field.getType() == String.class) {
                    Object val = field.get(sendableObject);
                    if (val instanceof String) {
                        String s = (String) val;
                        if (!s.isEmpty()) {
                            id = s;
                        }
                    }
                } else if (field.getType() == int.class) {
                    int val = field.getInt(sendableObject);
                    if (val >= 0 && val <= 10) {
                        type = val;
                    }
                }
            }

            if (id != null) {
                lastSendableId = id;
                lastSendableType = type;
                lastSendableAtMs = System.currentTimeMillis();
                if (type == 0) {
                    CurrentPin.recordPinId(id);
                }
                MorpheLog.d(MorpheLog.SHARE_SHEET, "captured SendableObject: id=" + id + ", type=" + type);
            }
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SHARE_SHEET, "failed to inspect SendableObject", t);
        }
    }

    /**
     * Chiamata dal bytecode subito dopo il costruttore della superclasse di
     * {@code SharesheetModalAppListView}.
     *
     * <p>Il parametro è {@code Object} per non far fallire il verificatore Dalvik, e il lavoro è
     * rimandato con {@link View#post}: agganciandosi subito dopo {@code super()} si riceve
     * l'oggetto <b>appena nato</b> — campi non valorizzati, genitori non ancora collegati — e
     * risalire l'albero delle view in quel momento non troverebbe niente. Con {@code post}
     * l'azione viene eseguita quando il foglio è montato.
     *
     * @param appListView la striscia di icone di app ({@code this} del costruttore agganciato).
     */
    public static void replaceWithSystemSheet(final Object appListView) {
        MorpheLog.hookFired(MorpheLog.SHARE_SHEET, "SharesheetModalAppListView built, view = "
                + (appListView == null ? "null" : appListView.getClass().getName()));

        if (!(appListView instanceof View)) {
            MorpheLog.w(MorpheLog.SHARE_SHEET, "hook received "
                    + (appListView == null ? "null" : appListView.getClass().getName())
                    + " instead of a View: keeping Pinterest's own share sheet");
            return;
        }

        final View list = (View) appListView;
        list.post(new Runnable() {
            @Override
            public void run() {
                try {
                    open(list);
                } catch (Throwable t) {
                    // Qualunque cosa vada storta, il foglio interno è ancora lì e funziona.
                    MorpheLog.w(MorpheLog.SHARE_SHEET,
                            "could not swap in the system share sheet", t);
                }
            }
        });
    }

    private static void open(View list) {
        if (!MorpheSettingsStore.isSystemShareSheetEnabled()) {
            MorpheLog.d(MorpheLog.SHARE_SHEET, "switch is off: keeping Pinterest's own share sheet");
            return;
        }

        Context context = list.getContext();
        PinterestUtils.rememberActivityFrom(context);

        String url = findShareUrl(list);
        if (url == null) {
            MorpheLog.w(MorpheLog.SHARE_SHEET, "no pin behind this share sheet (a board, a "
                    + "profile or a conversation?): keeping Pinterest's own share sheet");
            return;
        }

        if (url.equals(lastSharedUrl)
                && System.currentTimeMillis() - lastShareAtMs < SHARE_DEBOUNCE_MS) {
            MorpheLog.w(MorpheLog.SHARE_SHEET, "already opened the system sheet for " + url
                    + " a moment ago: not doing it again (did the in-app sheet fail to close?)");
            return;
        }

        String text = url;
        if (MorpheSettingsStore.isLinkSanitizerEnabled()) {
            String stripped = UrlSanitizer.stripTrackingParams(url);
            if (stripped != null) {
                text = stripped;
            }
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);

        // Titolo null: da Android 10 il selettore di sistema ignora comunque il titolo passato,
        // e su quelli precedenti "null" fa comparire l'intestazione predefinita del sistema,
        // che è già nella lingua giusta senza che si debba tradurre niente.
        Intent chooser = Intent.createChooser(send, null);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Il foglio interno va chiuso *prima* di far partire il chooser: dopo, l'Activity di
        // Pinterest è già in pausa dietro al foglio di sistema e l'"indietro" non le arriva più —
        // tornando indietro ci si ritrovava quello interno ancora aperto.
        dismissInAppShareSheet(list);

        try {
            context.startActivity(chooser);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SHARE_SHEET, "the system share sheet did not open: keeping "
                    + "Pinterest's own", t);
            return;
        }

        lastSharedUrl = url;
        lastShareAtMs = System.currentTimeMillis();
        MorpheLog.ok(MorpheLog.SHARE_SHEET, "system share sheet opened for " + text);
    }

    private static void dismissInAppShareSheet(View list) {
        if (list == null) {
            return;
        }

        // 1. Nascondi il foglio, e nient'altro che il foglio.
        hideTheSheetOnly(list);

        // 2. Prova a trovare il Fragment tramite FragmentManager.findFragment(list)
        Object fragment = null;
        try {
            Class<?> fmClass = Class.forName("androidx.fragment.app.FragmentManager");
            Method findFragmentMethod = fmClass.getMethod("findFragment", View.class);
            fragment = findFragmentMethod.invoke(null, list);
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.SHARE_SHEET, "findFragment reflection failed: " + t);
        }

        if (fragment != null) {
            // Chiama B6() del fragment (interfaccia n62.g di Pinterest per chiudere lo schermo)
            try {
                Method b6 = fragment.getClass().getMethod("B6");
                b6.invoke(fragment);
                MorpheLog.ok(MorpheLog.SHARE_SHEET, "in-app share sheet fragment closed via B6()");
                return;
            } catch (Throwable t) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "fragment.B6() failed: " + t);
            }

            // Prova dismissAllowingStateLoss() se è un DialogFragment
            try {
                Class<?> dfClass = Class.forName("androidx.fragment.app.DialogFragment");
                if (dfClass.isInstance(fragment)) {
                    Method dismissMethod = dfClass.getMethod("dismissAllowingStateLoss");
                    dismissMethod.invoke(fragment);
                    MorpheLog.ok(MorpheLog.SHARE_SHEET, "in-app share sheet dismissed (DialogFragment)");
                    return;
                }
            } catch (Throwable t) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "DialogFragment.dismiss failed: " + t);
            }

            // Prova remove(fragment) tramite parentFragmentManager
            try {
                Class<?> fragClass = Class.forName("androidx.fragment.app.Fragment");
                Method getPfm = fragClass.getMethod("getParentFragmentManager");
                Object pfm = getPfm.invoke(fragment);
                if (pfm != null) {
                    Method beginTx = pfm.getClass().getMethod("beginTransaction");
                    Object tx = beginTx.invoke(pfm);
                    Method removeMethod = tx.getClass().getMethod("remove", fragClass);
                    removeMethod.invoke(tx, fragment);
                    Method commitMethod = tx.getClass().getMethod("commitAllowingStateLoss");
                    commitMethod.invoke(tx);
                    MorpheLog.ok(MorpheLog.SHARE_SHEET, "in-app share sheet fragment removed from FragmentManager");
                    return;
                }
            } catch (Throwable t) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "fragment remove failed: " + t);
            }
        }

        // 3. Fallback: OnBackPressedDispatcher di ComponentActivity
        Context context = list.getContext();
        Activity activity = PinterestUtils.activityOf(context);
        if (activity != null) {
            try {
                Class<?> compActClass = Class.forName("androidx.activity.ComponentActivity");
                if (compActClass.isInstance(activity)) {
                    Method getDispatcher = compActClass.getMethod("getOnBackPressedDispatcher");
                    Object dispatcher = getDispatcher.invoke(activity);
                    if (dispatcher != null) {
                        Method onBp = dispatcher.getClass().getMethod("onBackPressed");
                        onBp.invoke(dispatcher);
                        MorpheLog.ok(MorpheLog.SHARE_SHEET, "in-app share sheet closed via OnBackPressedDispatcher");
                        return;
                    }
                }
            } catch (Throwable t) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "OnBackPressedDispatcher failed: " + t);
            }
        }

        // 4. Ultimo ripiego: "indietro", ma consegnato subito — vedi pressBackNow.
        PinterestUtils.pressBackNow(context, MorpheLog.SHARE_SHEET, "in-app share sheet");
    }

    /** Prefisso dei nomi di risorsa delle viste che compongono il foglio scorrevole. */
    private static final String SHEET_ID_PREFIX = "draggable_sheet";

    /**
     * Quanti genitori al massimo si risalgono cercando la radice del foglio.
     *
     * <p>Non è la condizione di arresto — quella è il nome della risorsa — ma solo una briglia
     * contro le risalite a vuoto. Sta largo apposta: fra la striscia di icone e
     * {@code draggable_sheet} ci sono dieci livelli su 14.34.0, e con gli otto di
     * {@link #MAX_PARENTS} la ricerca finiva prima di arrivarci.
     */
    private static final int MAX_PARENTS_TO_THE_SHEET = 20;

    /**
     * Rende invisibile il foglio interno, fermandosi ai confini del foglio stesso.
     *
     * <p><b>Perché non si risale e basta.</b> È esattamente quello che si faceva prima, ed è la
     * ragione per cui lo schermo restava nero: il foglio non è una finestra a sé ma un modale
     * montato dentro i contenitori permanenti dell'Activity, quindi la risalita arrivava a
     * {@code root_activity_layout} e {@code base_activity_layout} e li lasciava GONE per sempre.
     * Chiuso il foglio, dietro non c'era più niente da disegnare e l'unica via d'uscita era
     * chiudere Pinterest.
     *
     * <p><b>Dove si ferma.</b> Al foglio: le viste che lo compongono si chiamano
     * {@code draggable_sheet}, {@code draggable_sheet_scrim}, {@code draggable_sheet_coordinator}.
     * Sono nomi di <em>risorsa</em>, la stessa categoria di ancora che questa patch usa già per
     * agganciarsi — R8 non li tocca. Tutto ciò che sta sopra al foglio non viene mai sfiorato, così
     * questo passaggio non può più spegnere l'app nemmeno se il foglio cambia forma.
     */
    private static void hideTheSheetOnly(View list) {
        try {
            list.setVisibility(View.GONE);

            ViewParent parent = list.getParent();
            for (int level = 0; level < MAX_PARENTS_TO_THE_SHEET && parent instanceof View; level++) {
                View pv = (View) parent;
                if (isPartOfTheSheet(pv)) {
                    pv.setVisibility(View.GONE);
                    hideSheetSiblings(pv);
                    MorpheLog.d(MorpheLog.SHARE_SHEET, "in-app share sheet hidden at "
                            + nameOf(pv));
                    return;
                }
                parent = pv.getParent();
            }
            MorpheLog.d(MorpheLog.SHARE_SHEET, "no " + SHEET_ID_PREFIX + "* view above the app "
                    + "list: only the app icon strip was hidden");
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.SHARE_SHEET, "could not hide the in-app share sheet: " + t);
        }
    }

    /**
     * Nasconde anche i fratelli del foglio che ne fanno parte: la velatura scura e la sua variante
     * sfocata, che stanno accanto al foglio e non dentro, e da sole basterebbero a lasciare lo
     * schermo velato.
     */
    private static void hideSheetSiblings(View sheet) {
        ViewParent parent = sheet.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup host = (android.view.ViewGroup) parent;
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child != sheet && isPartOfTheSheet(child)) {
                child.setVisibility(View.GONE);
            }
        }
    }

    /** @return true se la vista è una di quelle che compongono il foglio scorrevole. */
    private static boolean isPartOfTheSheet(View view) {
        String name = nameOf(view);
        return name != null && name.startsWith(SHEET_ID_PREFIX);
    }

    /** @return il nome di risorsa dell'id della vista, o null se non ne ha uno. */
    private static String nameOf(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return null;
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable notFound) {
            return null;
        }
    }

    /**
     * Risale dalla striscia di icone verso il foglio intero e cerca il pin, un genitore alla
     * volta, fermandosi al primo che lo contiene.
     *
     * <p>Si risale invece di partire dalla radice della finestra per una ragione di precisione: la
     * radice contiene anche il closeup che sta sotto al foglio, e sotto potrebbe esserci un pin
     * diverso da quello che si sta condividendo. Il primo genitore che porta un pin è il foglio,
     * cioè il contesto giusto.
     */
    private static String findShareUrl(View list) {
        // 1. Prova dall'ultimo SendableObject intercettato (< 6 secondi)
        if (lastSendableId != null && System.currentTimeMillis() - lastSendableAtMs < SENDABLE_VALIDITY_MS) {
            String url = urlForSendable(lastSendableId, lastSendableType);
            if (url != null) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "share URL from recent SendableObject: " + url);
                return url;
            }
        }

        // 2. Prova dal CurrentPin catturato di recente (es. menu ...)
        String canonical = CurrentPin.canonicalUrl();
        if (canonical != null) {
            MorpheLog.d(MorpheLog.SHARE_SHEET, "share URL from CurrentPin.canonicalUrl(): " + canonical);
            return canonical;
        }

        // 3. Fallback: risalita nei parent
        ViewParent parent = list.getParent();
        for (int level = 0; level < MAX_PARENTS && parent instanceof View; level++) {
            String pinId = CurrentPin.pinIdIn(parent);
            if (pinId != null) {
                MorpheLog.d(MorpheLog.SHARE_SHEET, "pin " + pinId + " found "
                        + (level + 1) + " level(s) above the app list, in "
                        + parent.getClass().getName());
                return "https://pinterest.com/pin/" + pinId + "/";
            }
            parent = ((View) parent).getParent();
        }
        MorpheLog.d(MorpheLog.SHARE_SHEET, "no pin in the " + MAX_PARENTS
                + " views above the app list");
        return null;
    }

    private static String urlForSendable(String id, int type) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        switch (type) {
            case 0: // PIN
                return "https://pinterest.com/pin/" + id + "/";
            case 1: // BOARD
                return "https://pinterest.com/board/" + id + "/";
            case 2: // USER
                return "https://pinterest.com/" + id + "/";
            default:
                if (id.matches("[0-9]{15,20}")) {
                    return "https://pinterest.com/pin/" + id + "/";
                }
                return "https://pinterest.com/" + id + "/";
        }
    }
}
