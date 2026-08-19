package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
public final class PinterestUtils {

    static final String TAG = "MorpheWallpaper";

    private PinterestUtils() {}

    public static volatile String currentPinImageUrl = null;

    public static volatile Bitmap currentPinBitmap = null;

    public static void setCurrentPinImageUrl(String url) {
        if (url != null && !url.isEmpty()) {
            currentPinImageUrl = url;
        }
    }

    public static void setCurrentPinView(Object view, Bitmap bitmap) {
        if (bitmap != null) {
            currentPinBitmap = bitmap;
        }
        if (view instanceof View) {
            // Occasione per sapere qual è l'Activity in primo piano: vedi rememberActivityFrom.
            rememberActivityFrom(((View) view).getContext());
        }
        if (view != null) {
            try {
                Class<?> clazz = view.getClass();
                while (clazz != null) {
                    for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            f.setAccessible(true);
                            String val = (String) f.get(view);
                            if (val != null && (val.startsWith("http://") || val.startsWith("https://"))) {
                                currentPinImageUrl = val;
                                Log.d(TAG, "captured image URL by scanning the fields: " + currentPinImageUrl);
                                return;
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            } catch (Throwable t) {
                Log.e(TAG, "could not get the URL from the view", t);
            }
        }
    }

    // Delegates for video download
    public static void setCurrentVideoTracks(String uid, java.util.Map<?, ?> videoList) {
        MorpheLog.hookFired(MorpheLog.VIDEO, "video tracks for uid=" + uid
                + " (" + (videoList == null ? 0 : videoList.size()) + " formats)");
        try {
            VideoDownloadHandler.setCurrentVideoTracks(uid, videoList);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.VIDEO, "could not store the video tracks", t);
        }
    }

    public static void setCurrentVideoPin(Object pin) {
        try {
            VideoDownloadHandler.setCurrentVideoPin(pin);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.VIDEO, "could not store the video pin", t);
        }
    }

    /**
     * Rimanda {@code action} a quando la view del menu è finita di costruirsi.
     *
     * <p>Le tre voci che aggiungiamo al menu "…" del pin si agganciano subito dopo la chiamata al
     * costruttore della superclasse: è l'unico punto del costruttore in cui la view è raggiungibile
     * con certezza (vedi {@code addInstructionsAfterSuperConstructor} lato patch). Lì però la view è
     * appena nata — i campi non sono valorizzati e le righe del menu non sono ancora state aggiunte —
     * quindi lavorarci subito vorrebbe dire mettere le nostre voci in cima e cercare il pin dove
     * ancora non c'è.
     *
     * <p>{@link View#post(Runnable)} risolve entrambe le cose: su una view non ancora attaccata
     * accoda l'azione e la esegue al momento dell'attach, cioè quando il menu è completo e visibile.
     * Se l'oggetto non fosse una view, si esegue subito: meglio provarci che non fare niente.
     */
    private static void whenMenuIsReady(final Object menuContainer, final Runnable action) {
        if (menuContainer instanceof View) {
            rememberActivityFrom(((View) menuContainer).getContext());
            ((View) menuContainer).post(action);
        } else {
            action.run();
        }
    }

    public static void addDownloadVideoOption(final Object menuContainer) {
        MorpheLog.hookFired(MorpheLog.VIDEO,
                "menu " + (menuContainer == null ? "null" : menuContainer.getClass().getName()));
        whenMenuIsReady(menuContainer, new Runnable() {
            @Override
            public void run() {
                try {
                    VideoDownloadHandler.addDownloadVideoOption(menuContainer);
                } catch (Throwable t) {
                    MorpheLog.e(MorpheLog.VIDEO, "could not add the \"download video\" entry", t);
                }
            }
        });
    }

    // Delegate for wallpaper
    public static void addWallpaperOption(final Object menuContainer) {
        MorpheLog.hookFired(MorpheLog.WALLPAPER,
                "menu " + (menuContainer == null ? "null" : menuContainer.getClass().getName()));
        whenMenuIsReady(menuContainer, new Runnable() {
            @Override
            public void run() {
                try {
                    WallpaperHandler.addWallpaperOption(menuContainer);
                } catch (Throwable t) {
                    MorpheLog.e(MorpheLog.WALLPAPER, "could not add the \"set wallpaper\" entry", t);
                }
            }
        });
    }

    /**
     * Aggiunge la voce di download al menu "…" **della bacheca** — quello in alto a destra, che
     * apre il foglio "Opzioni".
     *
     * <p>Prima questa voce stava nel menu "…" di un singolo pin, che è il posto in cui nessuno
     * la cerca; per di più compariva solo se quel menu dichiarava di provenire da una bacheca,
     * e aprendo un pin spesso dichiara altro (`RELATED_PINS`), quindi in pratica non compariva
     * quasi mai. Nel menu della bacheca il contesto è invece certo: se quel menu è aperto, sei
     * dentro una bacheca.
     *
     * @param menu il modello del menu appena costruito dall'app.
     * @return il menu da usare al posto dell'originale. <b>Mai null</b>: questo valore finisce
     *     dritto nel {@code return} del metodo agganciato.
     */
    public static Object decorateBoardOptionsMenu(Object menu) {
        MorpheLog.hookFired(MorpheLog.BOARD_MENU,
                "menu " + (menu == null ? "null" : menu.getClass().getName()));

        if (!MorpheSettingsStore.isBoardDownloadEnabled()) {
            MorpheLog.d(MorpheLog.BOARD_MENU, "option disabled in the Morphe settings");
            return menu;
        }
        return BoardMenuDecorator.decorate(menu);
    }

    // Copy Link Logic
    public static void addCopyLinkOption(final Object menuContainer) {
        MorpheLog.hookFired(MorpheLog.COPY_LINK,
                menuContainer == null ? "null" : menuContainer.getClass().getName());
        whenMenuIsReady(menuContainer, new Runnable() {
            @Override
            public void run() {
                addCopyLinkOptionNow(menuContainer);
            }
        });
    }

    private static void addCopyLinkOptionNow(Object menuContainer) {
        // Si fa comunque, anche se poi la voce non venisse aggiunta: serve al sanificatore dei
        // link, che così può scrivere il link canonico senza chiedere niente alla rete.
        CurrentPin.captureFrom(menuContainer);

        if (!(menuContainer instanceof ViewGroup)) {
            MorpheLog.e(MorpheLog.COPY_LINK, "expected a ViewGroup, got "
                    + (menuContainer == null ? "null" : menuContainer.getClass().getName()));
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            View row;
            String labelText = getString("copy_link_label");
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMenu(v.getContext());
                    copyLinkToClipboard(v.getContext());
                }
            };
            try {
                row = buildRowReflective(container, labelText, "LINK", onClickListener);
                MorpheLog.ok(MorpheLog.COPY_LINK, "row added with the native style");
            } catch (Throwable t) {
                MorpheLog.w(MorpheLog.COPY_LINK,
                        "native style not available, using the fallback row", t);
                row = buildRowFallback(context, labelText, container,
                        android.R.drawable.ic_menu_share, onClickListener);
                MorpheLog.setStatus(MorpheLog.COPY_LINK, "ok — fallback row (different style)");
            }
            if (row != null) {
                matchRowTextAppearance(container, row);
                container.addView(row);
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.COPY_LINK, "could not add the copy-link entry", t);
        }
    }

    private static void copyLinkToClipboard(Context context) {
        String url = currentPinImageUrl;
        if (url == null || url.isEmpty()) {
            showNativeToast(context, getString("no_link"));
            return;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Pinterest Direct Link", url);
            clipboard.setPrimaryClip(clip);
            showNativeToast(context, getString("direct_link_copied"));
        } catch (Throwable t) {
            Log.e(TAG, "could not copy to the clipboard", t);
            showNativeToast(context, getString("failed"));
        }
    }

    // ---------------------------------------------------------------- filtro pubblicità
    //
    // Issue #15 ("Hide ads fail"): la versione precedente rilevava i pin promossi chiamando
    // getMethod("I5") sul modello. È un nome offuscato e su 14.24.0 quel getter NON legge
    // is_promoted, quindi la rilevazione restituiva sempre false: la patch non rimuoveva nulla.
    // Ora il riconoscimento passa da AdDetector, che legge le annotazioni Gson dei campi — il
    // nome JSON ("is_promoted", "ad_destination_url", …) è protocollo di rete e non cambia mai.
    //
    // Secondo motivo per cui gli annunci "ricomparivano scorrendo": il filtro girava solo nel
    // costruttore della risposta di rete. Le pagine successive vengono accodate alla lista già
    // esistente del feed, quindi non passavano più da lì. Per questo la patch aggancia anche
    // l'accessor della lista (filterSponsoredPinsFromList), che rifiltra a ogni lettura.

    /** Profondità massima di discesa nei campi annidati di una risposta del feed. */
    private static final int MAX_FILTER_DEPTH = 4;

    /** Contatore globale, solo per diagnostica: quanti annunci sono stati rimossi in totale. */
    private static volatile int totalAdsRemoved;

    /** Come {@link #totalAdsRemoved}, per i pin di prodotto: sono due opzioni distinte. */
    private static volatile int totalShoppingPinsRemoved;

    /** @return true se almeno una delle opzioni di filtro del feed è accesa. */
    private static boolean isFilteringEnabled() {
        return MorpheSettingsStore.isAdsDisabled()
                || MorpheSettingsStore.isShoppingPinsHidden()
                || MorpheSettingsStore.isSearchBoardModulesHidden();
    }

    /**
     * Hook sul menu circolare del pin (pressione prolungata): accoda il tasto "scarica" alla lista
     * dei tasti prima che il menu li disponga sull'arco. Vedi {@link ContextMenuDownload}.
     */
    @SuppressWarnings("unchecked")
    public static void addContextMenuDownloadItem(Object menuView, java.util.List<?> items) {
        MorpheLog.hookFired(MorpheLog.BOARD,
                "long-press menu " + (menuView == null ? "null" : menuView.getClass().getName()));
        try {
            ContextMenuDownload.addItem(menuView, (java.util.List<Object>) items);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.BOARD, "could not add the long-press download button", t);
        }
    }

    /**
     * Collassa a zero una view che serve solo a mostrare pubblicità, se il blocco è acceso.
     *
     * <p>Hook sui costruttori delle view pubblicitarie di Pinterest (vedi {@code HideAdViewsPatch}).
     * È una rete di sicurezza dietro a {@link #filterSponsoredPinsFromFeed(Object)}, non un
     * doppione: il filtro toglie i contenuti sponsorizzati dalle risposte di rete, ma l'app può
     * comunque decidere di costruire una di queste view — per esempio al primissimo caricamento del
     * feed dopo un'installazione pulita, prima che il filtro abbia visto passare qualcosa. Qui la
     * view viene costruita lo stesso (iniezioni Dagger, inflate del layout, listener: tutto gira,
     * così chi ne tiene un riferimento non trova null) ma non disegna e non occupa spazio, perché
     * {@code GONE} salta sia la misura sia il disegno.
     *
     * <p>Il controllo dell'interruttore sta qui e non nel bytecode di proposito: se stesse nel
     * patch, spegnere "Disabilita pubblicità" dalla schermata Morphe non rimetterebbe indietro
     * queste view, e l'interruttore mentirebbe.
     */
    public static void hideAdView(View view) {
        if (view == null || !MorpheSettingsStore.isAdsDisabled()) {
            return;
        }
        try {
            view.setVisibility(View.GONE);
            MorpheLog.hookFired(MorpheLog.ADS, "ad view collassata: " + view.getClass().getName());
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.ADS, "non sono riuscito a collassare la ad view", t);
        }
    }

    /**
     * Hook sui costruttori delle risposte del feed (Feed, PagedResponse, ModelListWithBookmark):
     * rimuove i contenuti sponsorizzati dalle liste che l'oggetto appena costruito contiene.
     */
    public static void filterSponsoredPinsFromFeed(Object feedPage) {
        if (feedPage == null) {
            return;
        }
        MorpheLog.hookFired(MorpheLog.ADS, "response " + feedPage.getClass().getName());

        if (isFilteringEnabled()) {
            filterRecursive(feedPage, 0, new IdentityHashMap<Object, Boolean>());
        } else {
            MorpheLog.d(MorpheLog.ADS, "filter disabled in the Morphe settings");
        }

        // Si riusa lo stesso hook per memorizzare i pin delle bacheche: sono le uniche
        // risposte del feed che portano l'URL boards/<id>/pins/. La cattura avviene DOPO il
        // filtro, così i pin sponsorizzati non finiscono nel download in blocco.
        if (MorpheSettingsStore.isBoardDownloadEnabled()) {
            BoardDownloadHandler.captureFeed(feedPage);
        }
    }

    /**
     * Liste del feed già filtrate. Vedi {@link #filterSponsoredPinsFromList}.
     *
     * <p>Si tengono <b>riferimenti deboli</b> confrontati per identità, non identity hash con un
     * tetto. Con un tetto, la lista del feed principale — che è la prima a essere inserita ed è
     * quella che vive più a lungo — sarebbe la prima a essere dimenticata: alla lettura
     * successiva verrebbe rifiltrata mentre un RecyclerView la sta già mostrando, cioè
     * esattamente il crash che questa struttura esiste per evitare. Con i riferimenti deboli una
     * voce sparisce quando sparisce la lista, quindi mai mentre è ancora a schermo, e la
     * struttura si mantiene piccola da sola.
     *
     * <p>Non si può usare un {@code HashSet} di liste: {@code ArrayList.hashCode()} dipende dal
     * contenuto e cambierebbe a ogni pagina caricata.
     */
    private static final java.util.List<java.lang.ref.WeakReference<Object>> ALREADY_FILTERED_LISTS =
            new java.util.ArrayList<>();

    /**
     * Registra una lista come già filtrata, ripulendo intanto le voci morte.
     *
     * @return true se non l'avevamo ancora vista, cioè se va filtrata adesso.
     */
    private static boolean markAsFiltered(Object list) {
        synchronized (ALREADY_FILTERED_LISTS) {
            boolean known = false;
            java.util.Iterator<java.lang.ref.WeakReference<Object>> iterator =
                    ALREADY_FILTERED_LISTS.iterator();
            while (iterator.hasNext()) {
                Object seen = iterator.next().get();
                if (seen == null) {
                    iterator.remove(); // lista raccolta dal GC: la voce non serve più
                } else if (seen == list) {
                    known = true;
                }
            }
            if (known) {
                return false;
            }
            ALREADY_FILTERED_LISTS.add(new java.lang.ref.WeakReference<>(list));
            return true;
        }
    }

    /**
     * Hook sull'accessor della lista del feed: rete di sicurezza per i percorsi che non passano
     * dai costruttori agganciati sopra.
     *
     * <p><b>Filtra solo la prima volta che vede una data lista.</b> Il motivo è la sicurezza:
     * rimuovere elementi da una lista che un RecyclerView sta già mostrando cambia il numero di
     * righe senza notificarlo all'adapter, e Android reagisce con un crash
     * ("Inconsistency detected"). La prima lettura invece avviene quando il repository consegna
     * il feed al presenter, prima che qualunque adapter lo osservi: lì la modifica è sicura,
     * ed è lo stesso istante in cui interviene l'hook sul costruttore.
     */
    public static void filterSponsoredPinsFromList(Object list) {
        if (!(list instanceof List)) {
            return;
        }
        MorpheLog.hookFired(MorpheLog.ADS, "reading the feed list");
        if (!isFilteringEnabled()) {
            return;
        }
        if (!markAsFiltered(list)) {
            return; // già filtrata: non la tocchiamo più
        }

        removeAdsFrom((List<?>) list, 0);
    }

    private static void filterRecursive(Object obj, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (obj == null || depth > MAX_FILTER_DEPTH || seen.put(obj, Boolean.TRUE) != null) {
            return;
        }

        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                String name = clazz.getName();
                if (name.startsWith("java.") || name.startsWith("android.")
                        || name.startsWith("kotlin.") || name.startsWith("androidx.")) {
                    break;
                }

                for (Field field : clazz.getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    if (value instanceof List) {
                        removeAdsFrom((List<?>) value, depth);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.ADS, "filter failed at depth " + depth, t);
        }
    }

    /**
     * Rimuove da una lista di modelli quello che le opzioni attive dicono di togliere: gli
     * annunci, i pin di prodotto, o entrambi.
     *
     * <p>Le due preferenze si leggono una volta per lista e non per elemento: sono su
     * SharedPreferences, e una pagina di feed ha decine di pin.
     *
     * <p>La rimozione avviene sotto {@code synchronized} sulla lista: l'accessor del feed può
     * essere letto da thread diversi e senza questo si rischia una ConcurrentModificationException
     * durante il rendering.
     *
     * <p>Se la pagina è composta <em>solo</em> da roba da togliere non viene svuotata: una lista
     * vuota fa credere all'app che il feed sia finito e blocca lo scroll infinito.
     */
    private static void removeAdsFrom(List<?> items, int depth) {
        if (items == null || items.isEmpty()) {
            return;
        }
        boolean removeAds = MorpheSettingsStore.isAdsDisabled();
        boolean removeShopping = MorpheSettingsStore.isShoppingPinsHidden();
        boolean removeSearchModules = MorpheSettingsStore.isSearchBoardModulesHidden();
        try {
            synchronized (items) {
                int total = items.size();
                int matching = 0;
                for (Object item : items) {
                    if (isAd(item, removeAds) || isShoppingPin(item, removeShopping)
                            || isSearchModule(item, removeSearchModules)) {
                        matching++;
                    }
                }
                if (matching == 0) {
                    // Niente a questo livello: forse è annidato (caroselli, "story").
                    if (depth < MAX_FILTER_DEPTH) {
                        for (Object item : items) {
                            if (item != null && !AdDetector.isCandidateModel(item.getClass())) {
                                filterRecursive(item, depth + 1, new IdentityHashMap<Object, Boolean>());
                            }
                        }
                    }
                    return;
                }
                if (matching >= total) {
                    MorpheLog.w(MorpheLog.ADS, "page made only of items to remove (" + total
                            + "): leaving it alone, or the app would think the feed is over");
                    return;
                }

                int ads = 0;
                int shopping = 0;
                int modules = 0;
                java.util.Iterator<?> iterator = items.iterator();
                while (iterator.hasNext()) {
                    Object item = iterator.next();
                    boolean ad = isAd(item, removeAds);
                    boolean module = !ad && isSearchModule(item, removeSearchModules);
                    if (!ad && !module && !isShoppingPin(item, removeShopping)) {
                        continue;
                    }
                    try {
                        iterator.remove();
                    } catch (UnsupportedOperationException e) {
                        MorpheLog.w(MorpheLog.ADS, "immutable list ("
                                + items.getClass().getName() + "): cannot remove anything");
                        return;
                    }
                    if (ad) {
                        ads++;
                    } else if (module) {
                        modules++;
                    } else {
                        shopping++;
                    }
                }
                if (ads + shopping > 0) {
                    totalAdsRemoved += ads;
                    totalShoppingPinsRemoved += shopping;
                    MorpheLog.i(MorpheLog.ADS, "removed " + ads + " ads and " + shopping
                            + " product pins out of " + total + " (session total: "
                            + totalAdsRemoved + " ads, " + totalShoppingPinsRemoved
                            + " product pins)");
                    MorpheLog.setStatus(MorpheLog.ADS, "ok — " + totalAdsRemoved + " ads and "
                            + totalShoppingPinsRemoved + " product pins removed so far");
                }
                if (modules > 0) {
                    // Canale a sé: sono moduli della ricerca, non annunci, e tenerli distinti
                    // rende leggibile in logcat quale delle due opzioni ha tolto cosa.
                    MorpheLog.i(MorpheLog.SEARCH_MODULES, "removed " + modules
                            + " search board modules out of " + total);
                }
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.ADS, "could not remove from the list", t);
        }
    }

    /** @param enabled se false non si guarda nemmeno il modello: l'opzione è spenta. */
    private static boolean isAd(Object item, boolean enabled) {
        return enabled && AdDetector.isAd(item);
    }

    /** @param enabled se false non si guarda nemmeno il modello: l'opzione è spenta. */
    private static boolean isShoppingPin(Object item, boolean enabled) {
        return enabled && AdDetector.isShoppingPin(item);
    }

    /** @param enabled se false non si guarda nemmeno il modello: l'opzione è spenta. */
    private static boolean isSearchModule(Object item, boolean enabled) {
        return enabled && SearchModuleDetector.isUnwantedSearchModule(item);
    }

    // ------------------------------------------------------------- screenshot (issue #32)

    /**
     * Percorso legacy: chiamata all'ingresso del metodo che, dopo uno screenshot, registra
     * l'evento e apre il pannello di condivisione.
     *
     * @param fragment il fragment del closeup, usato solo per il log.
     * @return true se il metodo deve uscire subito senza fare nulla.
     */
    public static boolean blockLegacyScreenshotFlow(Object fragment) {
        MorpheLog.hookFired(MorpheLog.SCREENSHOT, "legacy funnel on "
                + (fragment == null ? "null" : fragment.getClass().getName()));

        if (!MorpheSettingsStore.isScreenshotShareDisabled()) {
            MorpheLog.d(MorpheLog.SCREENSHOT, "option off: letting the panel through");
            return false;
        }
        MorpheLog.ok(MorpheLog.SCREENSHOT, "screenshot panel blocked (legacy path)");
        return true;
    }

    /**
     * Percorso SBA: chiamata all'ingresso del processore degli effetti screenshot.
     *
     * <p>Blocca l'intero processore, quindi anche gli effetti che <em>avviano</em> la
     * sorveglianza degli screenshot: con l'opzione attiva la rilevazione non parte affatto.
     *
     * @param effect l'effetto in arrivo. Il suo {@code toString()} è leggibile
     *     ({@code StartScreenshotObservation}, {@code ShowScreenshotUpsell}, …), quindi finisce
     *     nel log: è il modo più diretto per vedere cosa stava per succedere.
     * @return true se il processore deve uscire subito.
     */
    /**
     * Rilevatore screenshot generico, usato fuori dal closeup (feed, bacheche).
     *
     * <p>Chiamata all'ingresso del metodo che <em>avvia</em> l'osservazione: se si esce subito,
     * né il {@code FileObserver} né la callback di Android 14 vengono registrati, quindi non c'è
     * proprio niente che possa poi aprire il pannello.
     *
     * @return true se l'osservazione non deve partire.
     */
    public static boolean blockScreenshotObserver() {
        MorpheLog.hookFired(MorpheLog.SCREENSHOT, "generic observer starting");

        if (!MorpheSettingsStore.isScreenshotShareDisabled()) {
            MorpheLog.d(MorpheLog.SCREENSHOT, "option off: letting the observer start");
            return false;
        }
        MorpheLog.ok(MorpheLog.SCREENSHOT, "screenshot observation not started");
        return true;
    }

    public static boolean blockScreenshotEffect(Object effect) {
        String what = effect == null ? "null" : String.valueOf(effect);
        MorpheLog.hookFired(MorpheLog.SCREENSHOT, "SBA effect " + what);

        if (!MorpheSettingsStore.isScreenshotShareDisabled()) {
            MorpheLog.d(MorpheLog.SCREENSHOT, "option off: running effect " + what);
            return false;
        }
        MorpheLog.ok(MorpheLog.SCREENSHOT, "blocked effect " + what);
        return true;
    }

    // ---------------------------------------------------------------- barra di navigazione

    /**
     * Nasconde un tasto della barra di navigazione, se attivato nelle impostazioni Morphe.
     *
     * <p>Chiamata al termine del metodo che costruisce la barra, con {@code this} e l'ordinale
     * del tab. Il tasto viene cercato per <em>forma</em>: fra i campi lista della barra si
     * prende quella i cui elementi espongono un metodo che restituisce l'enum dei tab e uno che
     * restituisce la View. Nomi come {@code d42.p0.Q()} / {@code t42.l0.P()} cambiano a ogni
     * versione, la forma no.
     *
     * <p>Si nasconde la View invece di rimuovere l'elemento dalla lista: gli indici restano
     * validi e il resto del codice della barra continua a funzionare.
     */
    public static void hideNavBarTab(Object navBar, int tabOrdinal) {
        String tabName = navTabName(tabOrdinal);
        MorpheLog.hookFired(MorpheLog.NAVBAR, "tab " + tabName);

        if (!MorpheSettingsStore.isNavTabHidden(tabOrdinal)) {
            MorpheLog.d(MorpheLog.NAVBAR, "tab " + tabName + " is not one to hide");
            return;
        }
        if (!(navBar instanceof ViewGroup)) {
            MorpheLog.e(MorpheLog.NAVBAR, "the bar is not a ViewGroup but "
                    + (navBar == null ? "null" : navBar.getClass().getName()));
            return;
        }

        try {
            Object tab = findNavTab(navBar, tabName);
            if (tab == null) {
                if (lastSeenNavTabs.isEmpty()) {
                    // Nessun tab letto: la barra non è più fatta come ci aspettiamo.
                    MorpheLog.e(MorpheLog.NAVBAR, "no readable tab in the bar "
                            + navBar.getClass().getName() + " — the fingerprint points at the "
                            + "right method but its internals have changed");
                } else {
                    // I tab si leggono, semplicemente questo non c'è: succede, la barra non è
                    // uguale per tutti gli account. Non è un errore della patch.
                    MorpheLog.w(MorpheLog.NAVBAR, "tab " + tabName + " is not in this bar "
                            + "(it has: " + lastSeenNavTabs + "): nothing to hide");
                }
                return;
            }
            View tabView = findViewAccessor(tab);
            if (tabView == null) {
                MorpheLog.e(MorpheLog.NAVBAR, "no View accessor on tab item "
                        + tab.getClass().getName());
                return;
            }
            MorpheViews.hidePersistently(tabView, MorpheLog.NAVBAR, "tasto " + tabName);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.NAVBAR, "could not hide tab " + tabName, t);
        }
    }

    private static String navTabName(int ordinal) {
        switch (ordinal) {
            case 0: return "HOME";
            case 1: return "CREATE";
            case 2: return "NOTIFICATIONS";
            case 3: return "PROFILE";
            case 4: return "SEARCH";
            default: return "TAB_" + ordinal;
        }
    }

    /**
     * Nomi dei tab visti dall'ultima {@link #findNavTab} — serve solo a distinguere, quando un
     * tab non si trova, fra "la barra non contiene quel tab" e "non siamo riusciti a leggere la
     * barra". Il primo caso è normale (non tutti gli account hanno tutti i tab), il secondo è un
     * problema della patch.
     */
    private static final java.util.List<String> lastSeenNavTabs = new java.util.ArrayList<>();

    /** Cerca fra i campi lista della barra l'elemento il cui enum di tab si chiama {@code tabName}. */
    private static Object findNavTab(Object navBar, String tabName) throws IllegalAccessException {
        lastSeenNavTabs.clear();
        Class<?> clazz = navBar.getClass();
        while (clazz != null && !clazz.getName().startsWith("android.")) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Collection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(navBar);
                if (!(value instanceof Collection)) {
                    continue;
                }
                for (Object item : (Collection<?>) value) {
                    if (item == null) {
                        continue;
                    }
                    String name = enumNameOf(item);
                    if (name == null) {
                        break; // questa lista non contiene tab: passa al campo successivo
                    }
                    lastSeenNavTabs.add(name);
                    MorpheLog.d(MorpheLog.NAVBAR, "tab present: " + name);
                    if (tabName.equals(name)) {
                        return item;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * @return il nome della costante enum restituita dall'unico metodo senza argomenti che
     *     restituisce un enum contenente le costanti dei tab; null se l'oggetto non è un tab.
     */
    private static String enumNameOf(Object tab) {
        for (Method method : tab.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0 || !method.getReturnType().isEnum()) {
                continue;
            }
            Object[] constants = method.getReturnType().getEnumConstants();
            if (constants == null || constants.length < 5) {
                continue;
            }
            // L'enum giusto è quello dei tab: contiene HOME e SEARCH.
            boolean hasHome = false;
            boolean hasSearch = false;
            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                hasHome |= "HOME".equals(name);
                hasSearch |= "SEARCH".equals(name);
            }
            if (!hasHome || !hasSearch) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(tab);
                if (value instanceof Enum) {
                    return ((Enum<?>) value).name();
                }
            } catch (Throwable ignored) {
                // proviamo il metodo successivo
            }
        }
        return null;
    }

    /** @return la View restituita dall'unico metodo senza argomenti che ne restituisce una. */
    private static View findViewAccessor(Object tab) {
        for (Method method : tab.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0
                    || !View.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(tab);
                if (value instanceof View) {
                    return (View) value;
                }
            } catch (Throwable ignored) {
                // proviamo il metodo successivo
            }
        }
        return null;
    }

    // ------------------------------------------------- intestazione del feed (tasti in alto)

    /** Contenitore dei tasti in alto a destra, dichiarato nell'XML dell'intestazione. */
    private static final String HEADER_CONTAINER_ID = "home_feed_greeting_header_icon_container";

    /** Il "+" (Crea). L'id glielo assegna il fragment mentre lo costruisce. */
    private static final String HEADER_CREATE_ID = "home_feed_greeting_header_create_icon";

    /**
     * Posta/Notifiche: è un {@code FrameLayout} che contiene il tasto e il pallino dei non
     * letti, quindi nascondendolo sparisce anche il badge.
     */
    private static final String HEADER_NEWSHUB_ID = "home_feed_greeting_header_newshub_icon";

    /**
     * Nasconde i tasti "+" (Crea) e Posta/Notifiche dell'intestazione del feed (issue #24).
     *
     * <p>Da 14.28.0 quei due tasti non sono più nella barra in basso — che per gli account con
     * la nuova interfaccia contiene solo Home, Cerca e Profilo — ma in alto a destra, aggiunti
     * a mano dal fragment del feed dentro {@code onViewCreated}. {@link #hideNavBarTab} non li
     * trovava più e i due toggle della schermata Morphe non avevano quindi alcun effetto
     * visibile. I toggle restano gli stessi: questo è solo il secondo posto in cui cercarli.
     *
     * <p>Chiamata <b>all'ingresso</b> di {@code onViewCreated} — l'unico punto in cui il
     * registro del parametro contiene di sicuro la view, vedi il commento nella patch — quindi
     * i tasti non sono ancora stati aggiunti al contenitore: il lavoro vero si rimanda con
     * {@link View#post}, che viene eseguito dopo il ritorno del metodo. Gli id si risolvono per
     * <em>nome</em> con {@code getIdentifier}: i valori numerici cambiano a ogni build di
     * Pinterest, i nomi no.
     *
     * <p>Non lascia uscire nessuna eccezione: gira dentro un metodo del ciclo di vita di un
     * fragment, dove qualunque throw sarebbe un crash dell'app.
     *
     * @param root la view radice del fragment del feed.
     */
    public static void hideGreetingHeaderButtons(final View root) {
        try {
            boolean hideCreate = MorpheSettingsStore.isCreateButtonHidden();
            boolean hideNewsHub = MorpheSettingsStore.isNotificationsButtonHidden();
            MorpheLog.hookFired(MorpheLog.HEADER,
                    "create=" + hideCreate + ", inbox/notifications=" + hideNewsHub);

            if (!hideCreate && !hideNewsHub) {
                MorpheLog.d(MorpheLog.HEADER, "no button to hide: both options are off");
                return;
            }
            if (root == null) {
                MorpheLog.e(MorpheLog.HEADER, "null root view: nothing to search");
                return;
            }

            root.post(new Runnable() {
                @Override
                public void run() {
                    hideGreetingHeaderButtonsNow(root);
                }
            });
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.HEADER, "could not schedule hiding the header buttons", t);
        }
    }

    /** Il lavoro vero, a tasti ormai aggiunti. Vedi {@link #hideGreetingHeaderButtons}. */
    private static void hideGreetingHeaderButtonsNow(View root) {
        try {
            View container = findByName(root, HEADER_CONTAINER_ID);
            if (container == null) {
                // Succede se l'account non ha ancora la nuova intestazione: i tasti sono
                // ancora nella barra in basso e li nasconde hideNavBarTab. Non è un errore.
                MorpheLog.w(MorpheLog.HEADER, "this account has no header with the buttons at "
                        + "the top: it is the navigation bar that hides them");
                return;
            }

            // Le preferenze si rileggono qui e non si portano dietro dall'hook: fra i due
            // momenti passa un giro di message loop, e così un toggle spento nel frattempo non
            // nasconde comunque il tasto.
            if (MorpheSettingsStore.isCreateButtonHidden()) {
                hideHeaderButton(root, HEADER_CREATE_ID, "tasto Crea (+)");
            }
            if (MorpheSettingsStore.isNotificationsButtonHidden()) {
                hideHeaderButton(root, HEADER_NEWSHUB_ID, "tasto Posta/Notifiche");
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.HEADER, "could not hide the header buttons", t);
        }
    }

    /** Guardato a sé: un tasto che non si riesce a nascondere non deve saltare l'altro. */
    private static void hideHeaderButton(View root, String idName, String what) {
        try {
            View button = findByName(root, idName);
            if (button == null) {
                MorpheLog.e(MorpheLog.HEADER, "no " + what + " (@id/" + idName + ") in the "
                        + "header: the hook fired at the right place but the header has changed");
                return;
            }
            MorpheViews.hidePersistently(button, MorpheLog.HEADER, what);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.HEADER, "could not hide " + what, t);
        }
    }

    /**
     * @return la view con quell'id, cercata a partire dalla radice dell'albero — il chiamante
     *     può passare una view qualsiasi del fragment. Null se l'id non esiste in questa
     *     versione dell'app o se la view non c'è.
     */
    private static View findByName(View view, String idName) {
        Context context = view.getContext();
        int id = context.getResources().getIdentifier(idName, "id", context.getPackageName());
        if (id == 0) {
            MorpheLog.d(MorpheLog.HEADER, "@id/" + idName + " does not exist in this version");
            return null;
        }
        return view.getRootView().findViewById(id);
    }

    // ---------------------------------------------------------------- cronologia ricerche

    /**
     * Nasconde una sezione "Ricerche recenti" (issue #11).
     *
     * <p>Agganciata sia al costruttore della lista nella schermata di ricerca sia a quello del
     * carosello sotto la barra di ricerca. Pinterest non offre un modo di disattivare il
     * salvataggio lato server: qui si impedisce che vengano mostrate nell'app.
     */
    /**
     * Toglie gli elementi di cronologia dalla lista dei suggerimenti di ricerca, prima che
     * l'adapter li trasformi in righe.
     *
     * <p>È l'aggancio principale di "nascondi cronologia ricerche". Nasconderle come view non
     * bastava: su 14.28 la sezione non è un'unica view ma un'intestazione più N celle di testo
     * della stessa classe usata anche da "di tendenza", quindi non isolabile. Togliendo gli
     * elementi, l'intestazione e le righe non vengono proprio costruite.
     *
     * <p>Si filtra per <b>tipo di elemento</b>, non per posizione: ogni suggerimento porta un
     * enum che dice cos'è, e le costanti della cronologia si chiamano {@code RECENT_HISTORY_*}.
     * I nomi delle costanti di enum sopravvivono all'offuscamento. Tutto il resto della lista —
     * ricerche di tendenza, completamento automatico, suggerimenti — passa intatto.
     *
     * @return la lista da usare al posto dell'originale. <b>Mai null.</b>
     */
    public static List<?> filterRecentSearches(List<?> items) {
        MorpheLog.hookFired(MorpheLog.SEARCH_HISTORY,
                "suggestion items: " + (items == null ? "null" : String.valueOf(items.size())));

        if (items == null || items.isEmpty()) {
            return items;
        }
        if (!MorpheSettingsStore.isSearchHistoryHidden()) {
            MorpheLog.d(MorpheLog.SEARCH_HISTORY, "option disabled in the Morphe settings");
            return items;
        }

        try {
            java.util.ArrayList<Object> kept = new java.util.ArrayList<>(items.size());
            int removed = 0;
            for (Object item : items) {
                if (isRecentHistoryItem(item)) {
                    removed++;
                } else {
                    kept.add(item);
                }
            }
            if (removed == 0) {
                MorpheLog.d(MorpheLog.SEARCH_HISTORY, "no history item in this list");
                return items;
            }
            MorpheLog.ok(MorpheLog.SEARCH_HISTORY, "removed " + removed + " history items out of "
                    + items.size());
            return kept;
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.SEARCH_HISTORY, "the history filter failed", t);
            return items;
        }
    }

    /**
     * @return true se l'elemento dichiara, tramite un proprio campo enum, di essere una voce di
     *     cronologia ({@code RECENT_HISTORY_PIN}, {@code RECENT_HISTORY_PIN_HEADER}, …).
     */
    private static boolean isRecentHistoryItem(Object item) throws IllegalAccessException {
        if (item == null) {
            return false;
        }
        for (Field field : item.getClass().getDeclaredFields()) {
            if (!field.getType().isEnum()) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(item);
            if (value instanceof Enum && ((Enum<?>) value).name().startsWith("RECENT_HISTORY")) {
                return true;
            }
        }
        return false;
    }

    public static void hideRecentSearches(Object view) {
        MorpheLog.hookFired(MorpheLog.SEARCH_HISTORY,
                view == null ? "null" : view.getClass().getName());

        if (!MorpheSettingsStore.isSearchHistoryHidden()) {
            MorpheLog.d(MorpheLog.SEARCH_HISTORY, "option disabled in the Morphe settings");
            return;
        }
        if (!(view instanceof View)) {
            MorpheLog.e(MorpheLog.SEARCH_HISTORY, "expected a View, got "
                    + (view == null ? "null" : view.getClass().getName()));
            return;
        }
        MorpheViews.hidePersistently((View) view, MorpheLog.SEARCH_HISTORY, "recent searches");
    }

    /**
     * The search bar of the typeahead screen. Not obfuscated — Android custom views are inflated
     * by their full name from XML, so R8 cannot rename it.
     */
    private static final String TYPEAHEAD_SEARCH_BAR =
            "com.pinterest.feature.search.typeahead.view.TypeaheadSearchBarContainer";

    /**
     * Decides whether the full-screen spinner of a {@code PinterestLoadingLayout} may be shown.
     *
     * <p>Hooked at the head of {@code PinterestLoadingLayout.a(boolean)}, whose argument this
     * replaces. With "hide search history" on, the typeahead screen keeps spinning: Pinterest
     * builds that screen out of the very suggestions we take away, so its list stays in a state
     * the app reads as "still loading" (issue #11). The suggestions are gone by design — the
     * spinner promising they are about to arrive is not.
     *
     * <p>Narrow on purpose: the spinner is denied only on that one screen, only while the option
     * is on, and only while the search box is <b>empty</b> — that is, exactly the state we broke.
     * Once the user types something the screen is loading real results, and a spinner is an
     * honest thing to show, so it is left alone.
     *
     * @param loadingLayout the {@code PinterestLoadingLayout} the call was made on.
     * @param loading what Pinterest asked for.
     * @return what Pinterest gets: {@code false} suppresses the spinner and leaves the children
     *     of the layout visible.
     */
    public static boolean filterLoadingSpinner(Object loadingLayout, boolean loading) {
        if (!loading || !(loadingLayout instanceof View)) {
            return loading;
        }
        if (!MorpheSettingsStore.isSearchHistoryHidden()) {
            return true;
        }
        try {
            View searchBar = typeaheadSearchBarNextTo((View) loadingLayout);
            if (searchBar == null) {
                return true; // another screen: none of our business
            }
            if (!isQueryEmpty(searchBar)) {
                return true; // a real search is loading
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.SEARCH_HISTORY, "could not tell which screen the spinner "
                    + "belongs to", t);
            return true;
        }
        MorpheLog.hookFired(MorpheLog.SEARCH_HISTORY, "search spinner suppressed (empty query)");
        return false;
    }

    /**
     * @return the {@link #TYPEAHEAD_SEARCH_BAR} sitting next to this loading layout, or null if
     *     there is none — in which case this is not the search typeahead screen. Both typeahead
     *     layouts — {@code fragment_search_typeahead} and its bottom-bar variant — put the two
     *     under the same parent, and no other screen pairs them.
     */
    private static View typeaheadSearchBarNextTo(View loadingLayout) {
        ViewParent parent = loadingLayout.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup siblings = (ViewGroup) parent;
        for (int i = 0; i < siblings.getChildCount(); i++) {
            View sibling = siblings.getChildAt(i);
            if (sibling == loadingLayout) {
                continue;
            }
            View searchBar = findTypeaheadSearchBar(sibling, 3);
            if (searchBar != null) {
                return searchBar;
            }
        }
        return null;
    }

    /**
     * @param depth how many levels down to look. The search bar is either a direct sibling
     *     (bottom-bar layout) or one level down inside an {@code AppBarLayout}; the bound keeps
     *     this from ever walking into the suggestion list.
     */
    private static View findTypeaheadSearchBar(View view, int depth) {
        if (view == null) {
            return null;
        }
        if (TYPEAHEAD_SEARCH_BAR.equals(view.getClass().getName())) {
            return view;
        }
        if (depth <= 0 || !(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTypeaheadSearchBar(group.getChildAt(i), depth - 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * @return true if the user has typed nothing in the search bar.
     *
     * <p>Read off the {@link EditText} inside the bar rather than from Pinterest's own state:
     * the bar holds one whichever way it is built — a {@code GestaltSearchField} wraps a
     * {@code SearchView.SearchAutoComplete}, a {@code GestaltTextComposer} a
     * {@code TextInputEditText}, and both are {@code EditText} subclasses. Their own classes and
     * the fields holding them are obfuscated; {@code EditText} is framework, so it is not.
     *
     * <p>If no text field turns up, this returns false — spinner shown. Not being able to check
     * the precondition is not the same as it holding, and the log line says as much, so a future
     * Pinterest release that changes the bar shows up in the diagnostics panel instead of
     * silently switching the spinner off everywhere on the screen.
     */
    private static boolean isQueryEmpty(View searchBar) {
        EditText field = findEditText(searchBar, 8);
        if (field == null) {
            MorpheLog.e(MorpheLog.SEARCH_HISTORY, "no text field inside "
                    + searchBar.getClass().getSimpleName() + ": cannot tell whether the search "
                    + "box is empty, leaving the spinner alone");
            return false;
        }
        CharSequence text = field.getText();
        return text == null || text.length() == 0;
    }

    private static EditText findEditText(View view, int depth) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view == null || depth <= 0 || !(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findEditText(group.getChildAt(i), depth - 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- voce Impostazioni

    /**
     * Accoda la voce "Morphe" alla lista delle Impostazioni account.
     *
     * <p>La classe della riga da costruire ha un nome offuscato ({@code i1} su 14.24,
     * {@code j1} su 14.28): non è hardcodata qui, la risolve la patch leggendo il dex e la
     * deposita in {@link MorpheRuntimeNames}.
     *
     * @param list la {@code java.util.List} mutabile delle voci, presa dal bytecode.
     */
    public static void appendMorpheSettingsEntry(Object list) {
        String rowClassName = MorpheRuntimeNames.settingsRowClass;
        if (rowClassName == null || rowClassName.isEmpty()) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "the patch did not resolve the settings row class "
                    + "(" + MorpheRuntimeNames.describe() + "): Morphe entry not added. "
                    + "Open the settings with: adb shell am start -a android.intent.action.VIEW "
                    + "-d \"morphe://settings\"");
            return;
        }
        appendMorpheSettingsEntry(list, rowClassName, MorpheRuntimeNames.SETTINGS_URI);
    }

    static void appendMorpheSettingsEntry(Object list, String rowClassName, String uri) {
        MorpheLog.hookFired(MorpheLog.SETTINGS_ENTRY, "row=" + rowClassName + " uri=" + uri);

        if (!(list instanceof List)) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "expected a List, got "
                    + (list == null ? "null" : list.getClass().getName()));
            return;
        }
        try {
            Class<?> rowClass = Class.forName(rowClassName);
            Constructor<?> ctor = rowClass.getConstructor(String.class);
            ctor.setAccessible(true);
            Object row = ctor.newInstance(uri);

            @SuppressWarnings("unchecked")
            List<Object> entries = (List<Object>) list;
            entries.add(row);
            MorpheLog.ok(MorpheLog.SETTINGS_ENTRY, "Morphe entry added to the Settings");
        } catch (ClassNotFoundException e) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "settings row class " + rowClassName
                    + " missing: the patch resolved it on a dex other than the installed one", e);
        } catch (UnsupportedOperationException e) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "the Settings list is immutable", e);
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "could not add the Morphe entry", t);
        }
    }

    // ---------------------------------------------------------------- dialog conferma email

    /**
     * Chiude il modale "conferma la tua email" (e i suoi fratelli: collega Google, ecc.).
     *
     * <p>Agganciata a {@code onViewCreated} del Fragment che ospita il flusso. Non si chiama
     * più il metodo di chiusura interno di Pinterest (nome offuscato, cambiava a ogni versione):
     * si usano solo API AndroidX, che non vengono mai rinominate.
     *
     * @param fragment il Fragment, cioè {@code this} del metodo agganciato.
     * @param view la View appena creata, nascosta subito così il modale non lampeggia mentre la
     *     transazione di rimozione viene eseguita.
     */
    public static void suppressRecoveryFlow(Object fragment, Object view) {
        MorpheLog.hookFired(MorpheLog.EMAIL_DIALOG,
                fragment == null ? "null" : fragment.getClass().getName());

        if (!MorpheSettingsStore.isEmailConfirmDialogDisabled()) {
            MorpheLog.d(MorpheLog.EMAIL_DIALOG, "option disabled in the Morphe settings");
            return;
        }

        if (view instanceof View) {
            ((View) view).setVisibility(View.GONE);
        }
        if (fragment == null) {
            return;
        }

        try {
            // 1) DialogFragment: dismissAllowingStateLoss() è la via pulita.
            Class<?> dialogFragment = Class.forName("androidx.fragment.app.DialogFragment");
            if (dialogFragment.isInstance(fragment)) {
                Method dismiss = dialogFragment.getMethod("dismissAllowingStateLoss");
                dismiss.invoke(fragment);
                MorpheLog.ok(MorpheLog.EMAIL_DIALOG, "dialog dismissed (DialogFragment)");
                return;
            }
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.EMAIL_DIALOG, "not a DialogFragment: " + t);
        }

        try {
            // 2) Fragment normale: lo si rimuove dal suo FragmentManager.
            Class<?> fragmentClass = Class.forName("androidx.fragment.app.Fragment");
            Method getManager = fragmentClass.getMethod("getParentFragmentManager");
            Object manager = getManager.invoke(fragment);

            Class<?> managerClass = Class.forName("androidx.fragment.app.FragmentManager");
            Object transaction = managerClass.getMethod("beginTransaction").invoke(manager);

            Class<?> transactionClass = Class.forName("androidx.fragment.app.FragmentTransaction");
            transactionClass.getMethod("remove", fragmentClass).invoke(transaction, fragment);
            transactionClass.getMethod("commitAllowingStateLoss").invoke(transaction);

            MorpheLog.ok(MorpheLog.EMAIL_DIALOG, "dialog removed (FragmentTransaction)");
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.EMAIL_DIALOG, "could not remove the dialog; it stays invisible anyway "
                    + "because its View was set to GONE", t);
        }
    }



    // Common Package-Private Helpers
    static Object invokeNoArg(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    static void toast(Handler main, final Context context, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /**
     * Mostra un messaggio nello stile di Pinterest: striscia scura arrotondata in alto.
     *
     * <p>Lo disegna {@link MorpheToast}, che non nomina nessuna classe dell'app — il tentativo
     * precedente di riusare il toast nativo per nome offuscato costruiva silenziosamente l'oggetto
     * sbagliato su 14.32.0. Il toast di sistema resta solo come ultima rete, per quando non c'è
     * un'Activity a cui attaccare la striscia.
     */
    static void showNativeToast(final Context context, final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // Prima il toast vero di Pinterest, così i messaggi di Morphe sono
                // indistinguibili da quelli dell'app. La striscia disegnata da noi resta come
                // ripiego per quando il modello nativo non è disponibile.
                if (PinterestReflection.showGestaltToast(context, message, 5000)) {
                    return;
                }
                if (MorpheToast.show(context, message)) {
                    return;
                }
                MorpheLog.d(MorpheLog.REFLECTION,
                        "no Activity for the Morphe toast, using the system one: " + message);
                Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Chiude il menu aperto, esattamente come farebbe l'utente premendo "indietro".
     *
     * <p>Prima si provava a mandare all'EventManager un evento "chiudi il menu contestuale",
     * cercandone la classe fra due nomi offuscati: su 14.32.0 nessuno dei due esiste più e il menu
     * restava aperto sotto al messaggio di conferma.
     *
     * <p>Il menu del pin è un modale, e i modali di Pinterest si chiudono col tasto indietro. Il
     * modo più solido di chiuderlo è quindi chiedere all'Activity di gestire un "indietro" invece
     * di ricostruire l'evento interno: `onBackPressed` è API pubblica di Android, non cambia mai, e
     * fa passare la chiusura per la strada normale dell'app — animazione e stato di navigazione
     * compresi.
     *
     * <p>Si chiama solo mentre un menu è effettivamente aperto (dal gestore di una sua voce), quindi
     * non c'è il rischio che l'"indietro" venga interpretato come una navigazione.
     *
     * @param context il Context della riga premuta: da lì si risale all'Activity.
     */
    @SuppressWarnings("deprecation")
    static void dismissMenu(final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Activity activity = activityOf(context);
                if (activity == null) {
                    MorpheLog.d(MorpheLog.REFLECTION, "no Activity: the menu stays open");
                    return;
                }
                try {
                    activity.onBackPressed();
                    MorpheLog.d(MorpheLog.REFLECTION, "menu closed with back");
                } catch (Throwable t) {
                    MorpheLog.w(MorpheLog.REFLECTION, "could not close the menu", t);
                }
            }
        });
    }

    /**
     * L'Activity a cui appartiene {@code context}, o quella in primo piano se non si arriva a
     * un'Activity per quella strada.
     *
     * <p>Il Context di una View è quasi sempre un wrapper attorno all'Activity, quindi la catena si
     * risale. Ma non tutti i punti da cui serve chiudere un menu o mostrare un messaggio hanno una
     * View sottomano — la voce di download della bacheca, per esempio, ha solo il Context
     * dell'Application, da cui non si risale a niente. Per quei casi c'è
     * {@link #foregroundActivity()}.
     */
    static Activity activityOf(Context context) {
        Activity fromChain = activityFromContextChain(context);
        return fromChain != null ? fromChain : foregroundActivity();
    }

    /** @return l'Activity risalendo i wrapper di {@code context}, o null se non ce n'è una. */
    private static Activity activityFromContextChain(Context context) {
        Context current = context;
        for (int i = 0; i < 10 && current != null; i++) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            if (!(current instanceof android.content.ContextWrapper)) {
                return null;
            }
            current = ((android.content.ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    /**
     * Prende nota dell'Activity a cui appartiene questa View, se non l'abbiamo già.
     *
     * <p>Serve ai punti che un'Activity non ce l'hanno: la voce "scarica bacheca" arriva da una
     * callback del modello del menu, che riceve solo l'indice della riga toccata e da cui si può
     * risalire al massimo al Context dell'Application — dal quale non si arriva a nessuna Activity,
     * quindi né il menu si chiudeva né il messaggio compariva nello stile giusto.
     *
     * <p>Il ciclo di vita da solo non basta a coprire il buco: {@code registerActivityLifecycleCallbacks}
     * notifica solo gli eventi <em>futuri</em>, e in Pinterest l'unica Activity va in primo piano
     * all'avvio, prima che qualunque codice di Morphe giri. Da lì in poi si naviga fra fragment, e
     * un altro {@code onActivityResumed} non arriva più: il riferimento sarebbe rimasto vuoto per
     * tutta la sessione.
     *
     * <p>Le View invece ci passano di continuo per le mani — ogni immagine del feed, ogni menu — e
     * ognuna conosce la propria Activity.
     */
    static void rememberActivityFrom(Context context) {
        Activity activity = activityFromContextChain(context);
        if (activity == null) {
            return;
        }
        Activity known = foreground == null ? null : foreground.get();
        if (known != activity) {
            foreground = new java.lang.ref.WeakReference<>(activity);
        }
    }

    /** L'ultima Activity andata in primo piano, o null se non ne è ancora passata nessuna. */
    private static java.lang.ref.WeakReference<Activity> foreground;

    private static volatile boolean lifecycleWatchInstalled;

    static Activity foregroundActivity() {
        installLifecycleWatch();
        java.lang.ref.WeakReference<Activity> reference = foreground;
        return reference == null ? null : reference.get();
    }

    /**
     * Si mette in ascolto del ciclo di vita delle Activity per sapere qual è quella in primo piano.
     *
     * <p>Riferimento **debole**: tenerne uno forte impedirebbe all'Activity di essere liberata
     * quando l'utente la chiude, che è una perdita di memoria vera e delle peggiori.
     *
     * <p>L'ascolto si installa alla prima richiesta e non all'avvio del processo: se nessuna
     * funzione di Morphe ha bisogno di sapere qual è l'Activity corrente, non paghiamo niente.
     */
    private static void installLifecycleWatch() {
        if (lifecycleWatchInstalled) {
            return;
        }
        Application application = MorpheSettingsStore.appContext();
        if (application == null) {
            return;
        }
        lifecycleWatchInstalled = true;
        try {
            application.registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityResumed(Activity activity) {
                            foreground = new java.lang.ref.WeakReference<>(activity);
                        }

                        @Override
                        public void onActivityCreated(Activity activity, android.os.Bundle b) {}

                        @Override
                        public void onActivityStarted(Activity activity) {}

                        @Override
                        public void onActivityPaused(Activity activity) {}

                        @Override
                        public void onActivityStopped(Activity activity) {}

                        @Override
                        public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}

                        @Override
                        public void onActivityDestroyed(Activity activity) {}
                    });
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "could not watch the activity lifecycle", t);
        }
    }

    /**
     * Costruisce una riga di menu con lo stile nativo di Pinterest.
     *
     * @throws Exception se la costruzione nativa non è possibile: il chiamante ripiega su
     *     {@link #buildRowFallback}.
     */
    static View buildRowReflective(ViewGroup container, String labelText, String iconEnumName,
                                   View.OnClickListener onClickListener) throws Exception {
        View row = PinterestReflection.buildNativeMenuRow(
                container, labelText, iconEnumName, onClickListener);
        if (row == null) {
            throw new IllegalStateException(
                    "costruzione nativa della riga non riuscita su " + container.getClass().getName());
        }
        return row;
    }

    static View buildRowFallback(Context context, String labelText, ViewGroup container, int iconResId, View.OnClickListener onClickListener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(iconResId);
        } catch (Throwable ignored) {}

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(16);

        View refRow = findReferenceRow(container);
        if (refRow != null) {
            try {
                if (refRow.getBackground() != null) {
                    row.setBackground(refRow.getBackground().getConstantState().newDrawable().mutate());
                }
            } catch (Throwable ignored) {}

            row.setPadding(refRow.getPaddingLeft(), refRow.getPaddingTop(), refRow.getPaddingRight(), refRow.getPaddingBottom());

            TextView refText = findTextView(refRow);
            if (refText != null) {
                label.setTextColor(refText.getTextColors());
                label.setTextSize(0, refText.getTextSize());
                label.setTypeface(refText.getTypeface());
            } else {
                label.setTextColor(0xFFFFFFFF);
            }

            ImageView refImage = findImageView(refRow);
            if (refImage != null) {
                if (refImage.getColorFilter() != null) {
                    icon.setColorFilter(refImage.getColorFilter());
                } else {
                    icon.setColorFilter(0xFFFFFFFF);
                }
                ViewGroup.LayoutParams lp = refImage.getLayoutParams();
                if (lp != null) {
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(lp.width, lp.height);
                    iconLp.rightMargin = dp(icon.getContext(), 16);
                    iconLp.gravity = Gravity.CENTER_VERTICAL;
                    icon.setLayoutParams(iconLp);
                } else {
                    int iconSize = dp(context, 24);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                    iconLp.rightMargin = dp(context, 16);
                    icon.setLayoutParams(iconLp);
                }
            } else {
                icon.setColorFilter(0xFFFFFFFF);
                int iconSize = dp(context, 24);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.rightMargin = dp(context, 16);
                icon.setLayoutParams(iconLp);
            }
        } else {
            row.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
            label.setTextColor(0xFFFFFFFF);
            icon.setColorFilter(0xFFFFFFFF);
            int iconSize = dp(context, 24);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.rightMargin = dp(context, 16);
            icon.setLayoutParams(iconLp);
        }

        row.addView(icon);
        row.addView(label);

        row.setOnClickListener(onClickListener);

        return row;
    }

    static View findReferenceRow(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof RelativeLayout) {
                TextView tv = findTextView(child);
                if (tv != null) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Allinea il testo di una riga che abbiamo aggiunto a quello delle righe già presenti nel menu.
     *
     * <p>La fabbrica nativa delle righe prende, oltre a testo e icona, due flag che decidono come
     * il testo viene composto: il menu li valorizza leggendo il proprio stato, noi non possiamo
     * saperli e passiamo il valore neutro. Il risultato è una riga giusta in tutto tranne che nel
     * corpo del testo, che veniva più grande delle altre.
     *
     * <p>Invece di indovinare i flag — che sono due oggi e potrebbero essere tre domani — si copia
     * il risultato: dimensione, carattere e colore si prendono dalla prima riga già nel menu, che
     * per definizione è composta come Pinterest vuole. Si adatta da sé a qualunque variante il menu
     * stia usando, e non nomina niente.
     *
     * @param container il menu, con dentro le righe di Pinterest
     * @param row la riga appena costruita, non ancora aggiunta
     */
    static void matchRowTextAppearance(ViewGroup container, View row) {
        try {
            TextView ours = findTextView(row);
            if (ours == null) {
                return;
            }
            for (int i = 0; i < container.getChildCount(); i++) {
                TextView theirs = findTextView(container.getChildAt(i));
                if (theirs == null || theirs == ours || theirs.getTextSize() <= 0f) {
                    continue;
                }
                ours.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, theirs.getTextSize());
                ours.setTypeface(theirs.getTypeface());
                ours.setTextColor(theirs.getTextColors());
                MorpheLog.d(MorpheLog.REFLECTION, "row text aligned with the menu ("
                        + theirs.getTextSize() + "px)");
                return;
            }
            MorpheLog.d(MorpheLog.REFLECTION, "no existing row to copy the text style from");
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.REFLECTION, "could not align the row text: " + t);
        }
    }

    static TextView findTextView(View v) {
        if (v instanceof TextView) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findTextView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    static ImageView findImageView(View v) {
        if (v instanceof ImageView) {
            return (ImageView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView found = findImageView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    // Localization maps
    static String getString(String key) {
        // La lingua dell'app, non quella di sistema: vedi MorpheStrings.language().
        String lang = MorpheStrings.language();
        boolean isIt = "it".equals(lang);
        boolean isEs = "es".equals(lang);
        boolean isFr = "fr".equals(lang);
        boolean isDe = "de".equals(lang);
        boolean isPt = "pt".equals(lang);
        boolean isRu = "ru".equals(lang);
        boolean isJa = "ja".equals(lang);
        boolean isZh = "zh".equals(lang);
        boolean isKo = "ko".equals(lang);
        boolean isPl = "pl".equals(lang);
        boolean isNl = "nl".equals(lang);
        boolean isTr = "tr".equals(lang);
        boolean isAr = "ar".equals(lang);
        boolean isHi = "hi".equals(lang);
        boolean isIn = "in".equals(lang) || "id".equals(lang);
        boolean isMs = "ms".equals(lang);
        boolean isVi = "vi".equals(lang);
        boolean isUk = "uk".equals(lang);
        boolean isSv = "sv".equals(lang);
        boolean isNb = "nb".equals(lang) || "no".equals(lang);
        boolean isDa = "da".equals(lang);
        boolean isFi = "fi".equals(lang);
        boolean isFil = "fil".equals(lang) || "tl".equals(lang);
        boolean isEl = "el".equals(lang);
        boolean isCs = "cs".equals(lang);
        boolean isSk = "sk".equals(lang);
        boolean isHu = "hu".equals(lang);
        boolean isRo = "ro".equals(lang);
        boolean isIw = "iw".equals(lang) || "he".equals(lang);
        boolean isHr = "hr".equals(lang);

        if ("copy_link_label".equals(key)) {
            if (isIt) return "Copia link diretto";
            if (isEs) return "Copiar enlace directo";
            if (isFr) return "Copier le lien direct";
            if (isDe) return "Direkten Link kopieren";
            if (isPt) return "Copiar link direto";
            if (isRu) return "Копировать прямую ссылку";
            if (isJa) return "直接リンクをコピー";
            if (isZh) return "复制直链";
            if (isKo) return "직접 링크 복사";
            if (isPl) return "Kopiuj bezpośredni link";
            if (isNl) return "Directe link gekopiëerd";
            if (isTr) return "Doğrudan bağlantıyı kopyala";
            if (isAr) return "نسخ الرابط المباشر";
            return "Copy direct link";
        }
        if ("download_image_label".equals(key)) {
            if (isIt) return "Scarica";
            if (isEs) return "Descargar";
            if (isFr) return "Télécharger";
            if (isDe) return "Herunterladen";
            if (isPt) return "Baixar";
            if (isRu) return "Скачать";
            if (isJa) return "ダウンロード";
            if (isZh) return "下载";
            if (isKo) return "다운로드";
            if (isPl) return "Pobierz";
            if (isNl) return "Downloaden";
            if (isTr) return "İndir";
            if (isAr) return "تنزيل";
            return "Download";
        }
        if ("download_image_started".equals(key)) {
            if (isIt) return "Immagine in download…";
            if (isEs) return "Descargando la imagen…";
            if (isFr) return "Téléchargement de l'image…";
            if (isDe) return "Bild wird heruntergeladen…";
            if (isPt) return "A baixar a imagem…";
            if (isRu) return "Изображение загружается…";
            if (isJa) return "画像をダウンロード中…";
            if (isZh) return "正在下载图片…";
            if (isKo) return "이미지 다운로드 중…";
            if (isPl) return "Pobieranie obrazu…";
            if (isNl) return "Afbeelding downloaden…";
            if (isTr) return "Görsel indiriliyor…";
            if (isAr) return "جارٍ تنزيل الصورة…";
            return "Downloading image…";
        }
        if ("download_video_label".equals(key)) {
            if (isIt) return "Scarica video";
            if (isEs) return "Descargar vídeo";
            if (isFr) return "Télécharger la vidéo";
            if (isDe) return "Video herunterladen";
            if (isPt) return "Baixar vídeo";
            if (isRu) return "Скачать видео";
            if (isJa) return "動画をダウンロード";
            if (isZh) return "下载视频";
            if (isKo) return "동영상 다운로드";
            if (isPl) return "Pobierz wideo";
            if (isNl) return "Video downloaden";
            if (isTr) return "Videoyu indir";
            if (isAr) return "تنزيل الفيديو";
            return "Download video";
        }
        if ("download_video_external_label".equals(key)) {
            if (isIt) return "Scarica con app esterna (yt-dlp)";
            if (isEs) return "Descargar con app externa (yt-dlp)";
            if (isFr) return "Télécharger avec app externe (yt-dlp)";
            if (isDe) return "Mit externer App herunterladen (yt-dlp)";
            if (isPt) return "Baixar com app externo (yt-dlp)";
            if (isRu) return "Скачать через внешнее приложение (yt-dlp)";
            if (isJa) return "外部アプリでダウンロード (yt-dlp)";
            if (isZh) return "使用外部应用下载 (yt-dlp)";
            if (isKo) return "외부 앱으로 다운로드 (yt-dlp)";
            if (isPl) return "Pobierz za pomocą zewnętrznej aplikacji (yt-dlp)";
            if (isNl) return "Downloaden met externe app (yt-dlp)";
            if (isTr) return "Harici uygulama ile indir (yt-dlp)";
            if (isAr) return "(yt-dlp) التنزيل باستخدام تطبيق خارجي";
            return "Download with external app (yt-dlp)";
        }
        if ("video_download_started".equals(key)) {
            if (isIt) return "Download del video avviato…";
            if (isEs) return "Descarga del vídeo iniciada…";
            if (isFr) return "Téléchargement de la vidéo lancé…";
            if (isDe) return "Video-Download gestartet…";
            if (isPt) return "Download do vídeo iniciado…";
            if (isRu) return "Загрузка видео начата…";
            if (isJa) return "動画のダウンロードを開始しました…";
            if (isZh) return "已开始下载视频…";
            if (isKo) return "동영상 다운로드를 시작했습니다…";
            if (isNl) return "Video downloaden gestart…";
            if (isTr) return "Video indirme başladı…";
            if (isAr) return "بدأ تنزيل الفيديو…";
            return "Video download started…";
        }
        if ("board_download_label".equals(key)) {
            if (isIt) return "Scarica tutta la bacheca";
            if (isEs) return "Descargar todo el tablero";
            if (isFr) return "Télécharger tout le tableau";
            if (isDe) return "Ganze Pinnwand herunterladen";
            if (isPt) return "Baixar a pasta inteira";
            if (isRu) return "Скачать всю доску";
            if (isJa) return "ボード全体をダウンロード";
            if (isZh) return "下载整个画板";
            if (isKo) return "보드 전체 다운로드";
            if (isPl) return "Pobierz całą tablicę";
            if (isNl) return "Hele bord downloaden";
            if (isTr) return "Tüm panoyu indir";
            if (isAr) return "تنزيل اللوحة بالكامل";
            return "Download whole board";
        }
        if ("board_no_pins".equals(key)) {
            if (isIt) return "Nessun pin in memoria: scorri la bacheca e riprova";
            if (isEs) return "No hay pines en memoria: desplázate por el tablero e inténtalo de nuevo";
            if (isFr) return "Aucun pin en mémoire : faites défiler le tableau et réessayez";
            if (isDe) return "Keine Pins im Speicher: scrolle die Pinnwand und versuche es erneut";
            if (isPt) return "Nenhum pin na memória: role a pasta e tente de novo";
            if (isRu) return "Нет пинов в памяти: прокрутите доску и повторите";
            if (isJa) return "メモリにピンがありません。ボードをスクロールしてからもう一度お試しください";
            if (isZh) return "内存中没有 Pin：请先滚动画板后重试";
            if (isKo) return "메모리에 핀이 없습니다. 보드를 스크롤한 후 다시 시도하세요";
            if (isNl) return "Geen pins in geheugen: scroll door het bord en probeer opnieuw";
            if (isTr) return "Bellekte pin yok: panoyu kaydırıp tekrar deneyin";
            if (isAr) return "لا توجد دبابيس في الذاكرة: مرّر اللوحة وأعد المحاولة";
            return "No pins in memory: scroll the board and try again";
        }
        if ("board_download_started".equals(key)) {
            if (isIt) return "Download di %d pin avviato…";
            if (isEs) return "Descarga de %d pines iniciada…";
            if (isFr) return "Téléchargement de %d pins lancé…";
            if (isDe) return "Download von %d Pins gestartet…";
            if (isPt) return "Download de %d pins iniciado…";
            if (isRu) return "Загрузка %d пинов начата…";
            if (isJa) return "%d 件のピンのダウンロードを開始しました…";
            if (isZh) return "已开始下载 %d 个 Pin…";
            if (isKo) return "%d개 핀 다운로드를 시작했습니다…";
            if (isNl) return "Downloaden van %d pins gestart…";
            if (isTr) return "%d pin indirme başladı…";
            if (isAr) return "بدأ تنزيل %d دبوس…";
            return "Started downloading %d pins…";
        }
        if ("board_download_done".equals(key)) {
            if (isIt) return "Scaricate %1 immagini e %2 video.";
            if (isEs) return "Descargadas %1 imágenes y %2 vídeos.";
            if (isFr) return "%1 images et %2 vidéos téléchargées.";
            if (isDe) return "%1 Bilder und %2 Videos heruntergeladen.";
            if (isPt) return "Baixadas %1 imagens e %2 vídeos.";
            if (isRu) return "Скачано %1 изображений и %2 видео.";
            if (isJa) return "画像 %1 件、動画 %2 件をダウンロードしました。";
            if (isZh) return "已下载 %1 张图片和 %2 个视频。";
            if (isKo) return "이미지 %1개, 동영상 %2개를 다운로드했습니다.";
            if (isNl) return "%1 afbeeldingen en %2 video's gedownload.";
            if (isTr) return "%1 resim ve %2 video indirildi.";
            if (isAr) return "تم تنزيل %1 صورة و%2 فيديو.";
            return "Downloaded %1 images and %2 videos.";
        }
        if ("board_download_already".equals(key)) {
            if (isIt) return "%d già presenti, saltati.";
            if (isEs) return "%d ya presentes, omitidos.";
            if (isFr) return "%d déjà présents, ignorés.";
            if (isDe) return "%d bereits vorhanden, übersprungen.";
            if (isPt) return "%d já presentes, ignorados.";
            if (isRu) return "%d уже есть, пропущено.";
            if (isJa) return "%d 件は既にあるためスキップしました。";
            if (isZh) return "%d 个已存在，已跳过。";
            if (isKo) return "%d개는 이미 있어 건너뛰었습니다.";
            if (isNl) return "%d al aanwezig, overgeslagen.";
            if (isTr) return "%d zaten var, atlandı.";
            if (isAr) return "%d موجودة بالفعل، تم تخطيها.";
            return "%d already there, skipped.";
        }
        if ("board_download_skipped".equals(key)) {
            if (isIt) return "%d video saltati: richiedono un'app esterna (yt-dlp).";
            if (isEs) return "%d vídeos omitidos: requieren una app externa (yt-dlp).";
            if (isFr) return "%d vidéos ignorées : elles nécessitent une app externe (yt-dlp).";
            if (isDe) return "%d Videos übersprungen: benötigen eine externe App (yt-dlp).";
            if (isPt) return "%d vídeos ignorados: exigem um app externo (yt-dlp).";
            if (isRu) return "%d видео пропущено: нужно внешнее приложение (yt-dlp).";
            if (isJa) return "%d 件の動画をスキップしました: 外部アプリ (yt-dlp) が必要です。";
            if (isZh) return "已跳过 %d 个视频：需要外部应用 (yt-dlp)。";
            if (isKo) return "동영상 %d개 건너뜀: 외부 앱(yt-dlp)이 필요합니다.";
            if (isNl) return "%d video's overgeslagen: vereisen een externe app (yt-dlp).";
            if (isTr) return "%d video atlandı: harici uygulama (yt-dlp) gerekiyor.";
            if (isAr) return "تم تخطي %d فيديو: تتطلب تطبيقًا خارجيًا (yt-dlp).";
            return "%d videos skipped: they need an external app (yt-dlp).";
        }
        if ("board_download_failed".equals(key)) {
            if (isIt) return "%d falliti.";
            if (isEs) return "%d fallidos.";
            if (isFr) return "%d échecs.";
            if (isDe) return "%d fehlgeschlagen.";
            if (isPt) return "%d falharam.";
            if (isRu) return "%d не удалось.";
            if (isJa) return "%d 件失敗しました。";
            if (isZh) return "%d 个失败。";
            if (isKo) return "%d개 실패.";
            if (isNl) return "%d mislukt.";
            if (isTr) return "%d başarısız.";
            if (isAr) return "%d فشل.";
            return "%d failed.";
        }
        if ("no_video".equals(key)) {
            if (isIt) return "Nessun video disponibile per questo pin";
            if (isEs) return "No hay vídeo disponible para este pin";
            if (isFr) return "Aucune vidéo disponible pour ce pin";
            if (isDe) return "Kein Video für diesen Pin verfügbar";
            return "No video available for this pin";
        }
        if ("downloading".equals(key)) {
            if (isIt) return "Scaricamento dell'immagine…";
            if (isEs) return "Descargando imagen…";
            if (isFr) return "Téléchargement de l'image…";
            if (isDe) return "Bild wird heruntergeladen…";
            if (isPt) return "Baixando imagem…";
            if (isRu) return "Скачивание изображения…";
            if (isJa) return "画像をダウンロード中…";
            if (isZh) return "正在下载图片…";
            if (isKo) return "이미지 다운로드 중…";
            if (isPl) return "Pobieranie obrazu…";
            if (isNl) return "Afbeelding downloaden…";
            if (isTr) return "Resim indiriliyor…";
            if (isAr) return "جاري تنزيل الصورة…";
            if (isHi) return "छви डाउनलोड हो रही है…";
            if (isIn || isMs) return "Mengunduh gambar…";
            if (isVi) return "Đang tải hình ảnh xuống…";
            if (isUk) return "Завантаження зображення…";
            if (isSv) return "Laddar ner bild…";
            if (isNb) return "Laster ned bilde…";
            if (isDa) return "Downloader billede…";
            if (isFi) return "Ladataan kuvaa…";
            if (isFil) return "Dina-download ang larawan…";
            if (isEl) return "Λήψη εικόνας…";
            if (isCs || isSk) return "Stahování obrázku…";
            if (isHu) return "Kép letöltése…";
            if (isRo) return "Se descarcă imaginea…";
            if (isIw) return "מוריד תמונה…";
            if (isHr) return "Preuzimanje slike…";
            return "Downloading image…";
        }
        if ("success".equals(key)) {
            if (isIt) return "Sfondo impostato.";
            if (isEs) return "Fondo de pantalla establecido.";
            if (isFr) return "Fond d'écran défini.";
            if (isDe) return "Hintergrundbild festgelegt.";
            if (isPt) return "Papel de parede definido.";
            if (isRu) return "Обои установлены.";
            if (isJa) return "壁紙を設定しました。";
            if (isZh) return "壁纸设置成功。";
            if (isKo) return "배경화면 설정 완료.";
            if (isPl) return "Tapeta została ustawiona.";
            if (isNl) return "Achtergrond ingesteld.";
            if (isTr) return "Duvar kağıdı ayarlandı.";
            if (isAr) return "تم تعيين الخلفية.";
            if (isHi) return "वॉलपेपर सेट हो गया.";
            if (isIn || isMs) return "Wallpaper diatur.";
            if (isVi) return "Đã đặt hình nền.";
            if (isUk) return "Шпалери встановлено.";
            if (isSv) return "Bakgrundsbild ändrad.";
            if (isNb) return "Bakgrunnsbilde satt.";
            if (isDa) return "Baggrund indstillet.";
            if (isFi) return "Taustakuva asetettu.";
            if (isFil) return "Naitakda ang wallpaper.";
            if (isEl) return "Η ταπετσαρία ορίστηκε.";
            if (isCs || isSk) return "Tapeta nastavena.";
            if (isHu) return "Háttérkép beállítva.";
            if (isRo) return "Fundal setat.";
            if (isIw) return "הרקע הוגדר.";
            if (isHr) return "Pozadina postavljena.";
            return "Wallpaper set.";
        }
        if ("failed".equals(key)) {
            if (isIt) return "Impossibile impostare lo sfondo";
            if (isEs) return "Error al establecer el fondo de pantalla";
            if (isFr) return "Impossible de définir le fond d'écran";
            if (isDe) return "Hintergrundbild konnte nicht festgelegt werden";
            if (isPt) return "Não foi possibile definir o papel de parede";
            if (isRu) return "Не удалось установить обои";
            if (isJa) return "壁紙の設定に失敗しました";
            if (isZh) return "设置壁纸失败";
            if (isKo) return "배경화면 설정 실패";
            if (isPl) return "Nie udało się ustawić tapety";
            if (isNl) return "Instellen van achtergrond mislukt";
            if (isTr) return "Duvar kağıdı ayarlanamadı";
            if (isAr) return "فشل تعيين الخلفية";
            if (isHi) return "वॉलपेपर सेट करने में विफल";
            if (isIn || isMs) return "Gagal mengatur wallpaper";
            if (isVi) return "Không thể đặt hình nền";
            if (isUk) return "Не вдалося встановити шпалери";
            if (isSv) return "Misslyckades att ändra bakgrundsbild";
            if (isNb) return "Kunne ikke sette bakgrunnsbilde";
            if (isDa) return "Kunne ikke indstille baggrund";
            if (isFi) return "Taustakuvan asettaminen epäonnistui";
            if (isFil) return "Bigo sa pagtatakda ng wallpaper";
            if (isEl) return "Αποτυχία ορισμού ταπετσαρίας";
            if (isCs || isSk) return "Nepodařilo se nastavit tapetu";
            if (isHu) return "Háttérkép beállítása sikertelen";
            if (isRo) return "Eroare la setarea fundalului";
            if (isIw) return "הגדרת הרקע נכשله";
            if (isHr) return "Postavljanje pozadine nije uspjelo";
            return "Failed to set wallpaper";
        }
        if ("dialog_title".equals(key)) {
            if (isIt) return "Imposta sfondo";
            if (isEs) return "Establecer fondo de pantalla";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Hintergrundbild festlegen";
            if (isPt) return "Definir papel de parede";
            if (isRu) return "Установить обои";
            if (isJa) return "壁紙を設定";
            if (isZh) return "设置壁纸";
            if (isKo) return "배경화면 설정";
            if (isPl) return "Ustaw tapetę";
            if (isNl) return "Achtergrond instellen";
            if (isTr) return "Duvar kağıdını ayarla";
            if (isAr) return "تعيين الخلفية";
            if (isHi) return "वॉलपेपर सेट करें";
            if (isIn || isMs) return "Atur wallpaper";
            if (isVi) return "Đặt hình nền";
            if (isUk) return "Встановити шпалери";
            if (isSv) return "Ange bakgrundsbild";
            if (isNb) return "Sett bakgrunnsbilde";
            if (isDa) return "Indstil baggrund";
            if (isFi) return "Aseta taustakuva";
            if (isFil) return "Itakda ang wallpaper";
            if (isEl) return "Ορισμός ταπετσαρίας";
            if (isCs || isSk) return "Nastavit tapetu";
            if (isHu) return "Háttérkép beállítása";
            if (isRo) return "Setează fundalul";
            if (isIw) return "הגדר רקע";
            if (isHr) return "Postavi pozadinu";
            return "Set wallpaper";
        }
        if ("option_home".equals(key)) {
            if (isIt) return "Schermata Home";
            if (isEs) return "Pantalla de inicio";
            if (isFr) return "Écran d'accueil";
            if (isDe) return "Startbildschirm";
            if (isPt) return "Tela inicial";
            if (isRu) return "Экран \"Домой\"";
            if (isJa) return "ホーム画面";
            if (isZh) return "主屏幕";
            if (isKo) return "홈 화면";
            if (isPl) return "Ekran startowy";
            if (isNl) return "Beginscherm";
            if (isTr) return "Ana ekran";
            if (isAr) return "الشاشة الرئيسية";
            if (isHi) return "होम स्क्रीन";
            if (isIn || isMs) return "Layar Utama";
            if (isVi) return "Màn hình chính";
            if (isUk) return "Домашній екран";
            if (isSv) return "Hemskärm";
            if (isNb) return "Hjem-skjerm";
            if (isDa) return "Startskærm";
            if (isFi) return "Alkunäyttö";
            if (isFil) return "Home screen";
            if (isEl) return "Αρχική οθόνη";
            if (isCs || isSk) return "Domovská obrazovka";
            if (isHu) return "Kezdőképernyő";
            if (isRo) return "Ecran de pornire";
            if (isIw) return "מסך הבית";
            if (isHr) return "Početni zaslon";
            return "Home screen";
        }
        if ("option_lock".equals(key)) {
            if (isIt) return "Schermata di blocco";
            if (isEs) return "Pantalla de bloqueo";
            if (isFr) return "Écran de verrouillage";
            if (isDe) return "Sperrbildschirm";
            if (isPt) return "Tela de bloqueio";
            if (isRu) return "Экран блокировки";
            if (isJa) return "ロック画面";
            if (isZh) return "锁定屏幕";
            if (isKo) return "잠금 화면";
            if (isPl) return "Ekran blokady";
            if (isNl) return "Vergrendelscherm";
            if (isTr) return "Kilit ekranı";
            if (isAr) return "شاشة القفل";
            if (isHi) return "लॉक स्क्रीन";
            if (isIn || isMs) return "Layar Kunci";
            if (isVi) return "Màn hình khóa";
            if (isUk) return "Екран блокування";
            if (isSv) return "Låsskärm";
            if (isNb) return "Låseskjerm";
            if (isDa) return "Låseskærm";
            if (isFi) return "Lukitusnäyttö";
            if (isFil) return "Lock screen";
            if (isEl) return "Οθόνη κλειδώματος";
            if (isCs || isSk) return "Uzamykací obrazovka";
            if (isHu) return "Képernyőzár";
            if (isRo) return "Ecran de blocare";
            if (isIw) return "מסך הנעילה";
            if (isHr) return "Zaslon zaključavanja";
            return "Lock screen";
        }
        if ("option_both".equals(key)) {
            if (isIt) return "Entrambe";
            if (isEs) return "Ambas";
            if (isFr) return "Les deux";
            if (isDe) return "Beide";
            if (isPt) return "Ambos";
            if (isRu) return "Оба экрана";
            if (isJa) return "両方";
            if (isZh) return "两者";
            if (isKo) return "둘 다";
            if (isPl) return "Oba";
            if (isNl) return "Beide";
            if (isTr) return "Her ikisi";
            if (isAr) return "كليهma";
            if (isHi) return "दोनों";
            if (isIn || isMs) return "Keduanya";
            if (isVi) return "Cả hai";
            if (isUk) return "Обидва";
            if (isSv) return "Båda";
            if (isNb) return "Begge";
            if (isDa) return "Begge";
            if (isFi) return "Molemmat";
            if (isFil) return "Pareho";
            if (isEl) return "Και τα δύο";
            if (isCs || isSk) return "Obe";
            if (isHu) return "Mindkettő";
            if (isRo) return "Ambele";
            if (isIw) return "שניהם";
            if (isHr) return "Oba";
            return "Both";
        }
        if ("invalid_image".equals(key)) {
            if (isIt) return "Immagine non valida";
            if (isEs) return "Imagen no válida";
            if (isFr) return "Image non valide";
            if (isDe) return "Ungültiges Bild";
            if (isPt) return "Imagem inválida";
            if (isRu) return "Неверное изображение";
            if (isJa) return "無効な画像";
            if (isZh) return "无效图片";
            if (isKo) return "유효하지 않은 이미지";
            if (isPl) return "Nieprawidłowy obraz";
            if (isNl) return "Ongeldige afbeelding";
            if (isTr) return "Geçersiz resim";
            if (isAr) return "صورة غير صالحة";
            if (isHi) return "अमान्य छवि";
            if (isIn || isMs) return "Gambar tidak valid";
            if (isVi) return "Hình ảnh không hợp lệ";
            if (isUk) return "Неприпустиме зображення";
            if (isSv) return "Ogiltig bild";
            if (isNb) return "Ugyldig bilde";
            if (isDa) return "Ugyldigt billede";
            if (isFi) return "Virheellinen kuva";
            if (isFil) return "Hindi wastong larawan";
            if (isEl) return "Μη έγκυρη εικόνα";
            if (isCs || isSk) return "Neplatný obrázek";
            if (isHu) return "Érvénytelen kép";
            if (isRo) return "Imagine invalidă";
            if (isIw) return "תמונה לא תקינה";
            if (isHr) return "Nevaljana slika";
            return "Invalid image";
        }
        if ("label".equals(key)) {
            if (isIt) return "Imposta come sfondo";
            if (isEs) return "Establecer como fondo";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Als Hintergrundbild festlegen";
            if (isPt) return "Definir como fundo";
            if (isRu) return "Установить как обои";
            if (isJa) return "壁紙として設定";
            if (isZh) return "设为壁纸";
            if (isKo) return "배경화면으로 설정";
            if (isPl) return "Ustaw jako tapetę";
            if (isNl) return "Als achtergrond instellen";
            if (isTr) return "Duvar kağıdı yap";
            if (isAr) return "تعيين كخلفية";
            return "Set as wallpaper";
        }
        if ("no_link".equals(key)) {
            if (isIt) return "Nessun indirizzo immagine trovato";
            if (isEs) return "No se encontró enlace de imagen";
            if (isFr) return "Aucun lien d'image trouvé";
            if (isDe) return "Kein Bildlink gefunden";
            if (isPt) return "Nenhum link de imagem encontrado";
            if (isRu) return "Ссылка на изображение не найдена";
            if (isJa) return "画像リンクが見つかりません";
            if (isZh) return "未找到图片链接";
            if (isKo) return "이미지 링크를 찾을 수 없습니다";
            if (isPl) return "Nie znaleziono linku do obrazu";
            if (isNl) return "Geen afbeeldingslink gevonden";
            if (isTr) return "Resim bağlantısı bulunamadı";
            if (isAr) return "لم يتم العثور على رابط الصورة";
            return "No image link found";
        }
        if ("direct_link_copied".equals(key)) {
            if (isIt) return "Link diretto copiato!";
            if (isEs) return "¡Enlace directo copiado!";
            if (isFr) return "Lien direct copié !";
            if (isDe) return "Direkter Link kopiert!";
            if (isPt) return "Link direto copiado!";
            if (isRu) return "Прямая ссылка скопирована!";
            if (isJa) return "直接リンクをコピーしました！";
            if (isZh) return "直链已复制！";
            if (isKo) return "직접 링크가 복사되었습니다!";
            if (isPl) return "Bezpośredni link skopiowany!";
            if (isNl) return "Directe link gekopieerd!";
            if (isTr) return "Doğrudan bağlantı kopyalandı!";
            if (isAr) return "تم نسخ الرابط المباشr!";
            return "Direct link copied!";
        }
        if ("no_image".equals(key)) {
            if (isIt) return "Nessuna immagine disponibile per questo pin";
            if (isEs) return "No hay imagen disponible para este pin";
            if (isFr) return "Aucune image disponible pour ce pin";
            if (isDe) return "Kein Bild für diesen Pin verfügbar";
            return "No image available for this pin";
        }
        return "";
    }

    static String getLocalizedError(String key) {
        // La lingua dell'app, non quella di sistema: vedi MorpheStrings.language().
        String lang = MorpheStrings.language();
        boolean isIt = "it".equals(lang);
        boolean isEs = "es".equals(lang);
        boolean isFr = "fr".equals(lang);
        boolean isDe = "de".equals(lang);
        boolean isPt = "pt".equals(lang);
        boolean isRu = "ru".equals(lang);
        boolean isJa = "ja".equals(lang);
        boolean isZh = "zh".equals(lang);
        boolean isKo = "ko".equals(lang);
        boolean isPl = "pl".equals(lang);
        boolean isNl = "nl".equals(lang);
        boolean isTr = "tr".equals(lang);
        boolean isAr = "ar".equals(lang);
        boolean isHi = "hi".equals(lang);
        boolean isIn = "in".equals(lang) || "id".equals(lang);
        boolean isMs = "ms".equals(lang);
        boolean isVi = "vi".equals(lang);
        boolean isUk = "uk".equals(lang);
        boolean isSv = "sv".equals(lang);
        boolean isNb = "nb".equals(lang) || "no".equals(lang);
        boolean isDa = "da".equals(lang);
        boolean isFi = "fi".equals(lang);
        boolean isFil = "fil".equals(lang) || "tl".equals(lang);
        boolean isEl = "el".equals(lang);
        boolean isCs = "cs".equals(lang);
        boolean isSk = "sk".equals(lang);
        boolean isHu = "hu".equals(lang);
        boolean isRo = "ro".equals(lang);
        boolean isIw = "iw".equals(lang) || "he".equals(lang);
        boolean isHr = "hr".equals(lang);

        if ("video_err_hls".equals(key)) {
            if (isIt) return "Errore: formato streaming (HLS/DASH) non supportato direttamente";
            if (isEs) return "Error: formato de streaming (HLS/DASH) no admitido directamente";
            if (isFr) return "Erreur : format de streaming (HLS/DASH) non pris en charge directement";
            if (isDe) return "Fehler: Streaming-Format (HLS/DASH) wird nicht direkt unterstützt";
            if (isPt) return "Erro: formato de streaming (HLS/DASH) não suportado direttamente";
            if (isRu) return "Ошибка: потоковый формат (HLS/DASH) не поддерживается напрямую";
            if (isJa) return "エラー: ストリーミング形式 (HLS/DASH) は直接サポートされていません";
            if (isZh) return "错误：暂不支持直接下载流媒体格式 (HLS/DASH)";
            if (isKo) return "오류: 스트리밍 형식(HLS/DASH)은 직접 지원되지 않습니다";
            if (isPl) return "Błąd: format strumieniowy (HLS/DASH) nie jest bezpośrednio obsługiwany";
            if (isNl) return "Fout: streamingformaat (HLS/DASH) wordt niet direct ondersteund";
            if (isTr) return "Hata: akış formatı (HLS/DASH) doğrudan desteklenmiyor";
            if (isAr) return "خطأ: تنسيق البث (HLS/DASH) غير مدعوم مباشرة";
            if (isHi) return "त्रुटि: स्ट्रीमिंग प्रारूप (HLS/DASH) सीधे समर्थित नहीं है";
            if (isIn || isMs) return "Kesalahan: format streaming (HLS/DASH) tidak didukung secara langsung";
            if (isVi) return "Lỗi: định dạng phát trực tuyến (HLS/DASH) không được hỗ trợ trực tiếp";
            if (isUk) return "Помилка: потоковий формат (HLS/DASH) не підтримується напряму";
            if (isSv) return "Fel: strömmande format (HLS/DASH) stöds inte direkt";
            if (isNb) return "Feil: strømmeformat (HLS/DASH) støttes ikke direkte";
            if (isDa) return "Fejl: streamingformat (HLS/DASH) understøttes ikke direkte";
            if (isFi) return "Virhe: suoratoistomuotoa (HLS/DASH) ei tueta suoraan";
            if (isFil) return "Error: hindi direktang suportado ang streaming format (HLS/DASH)";
            if (isEl) return "Σφάλμα: η μορφή ροής (HLS/DASH) δεν υποστηρίζεται απευθείας";
            if (isCs) return "Chyba: formát streamování (HLS/DASH) není přímo podporován";
            if (isHu) return "Hiba: a streaming formátum (HLS/DASH) közvetlenül nem támogatott";
            if (isRo) return "Eroare: formatul de streaming (HLS/DASH) nu este acceptat direct";
            if (isIw) return "שגיאה: פורמט הזרמה (HLS/DASH) אינו נתמך ישירות";
            if (isHr) return "Greška: streaming format (HLS/DASH) nije izravno podržan";
            return "Error: streaming format (HLS/DASH) is not directly supported";
        }
        if ("video_err_no_mp4".equals(key)) {
            if (isIt) return "Errore: impossibile estrarre un link scaricabile (MP4/HLS/DASH) dai metadati";
            if (isEs) return "Error: no se pudo extraer un enlace descargable (MP4/HLS/DASH) de los metadatos";
            if (isFr) return "Erreur : impossible d'extraire un lien téléchargeable (MP4/HLS/DASH) des métadonnées";
            if (isDe) return "Fehler: kein herunterladbarer Link (MP4/HLS/DASH) aus den Metadaten extrahierbar";
            if (isPt) return "Erro: não foi possível extrair um link para download (MP4/HLS/DASH) dos metadados";
            if (isRu) return "Ошибка: не удалось извлечь ссылку для скачивания (MP4/HLS/DASH) из метаданных";
            if (isJa) return "エラー: メタデータからダウンロード可能なリンク (MP4/HLS/DASH) を抽出できませんでした";
            if (isZh) return "错误：无法从元数据中提取可下载链接 (MP4/HLS/DASH)";
            if (isKo) return "오류: 메타데이터에서 다운로드 가능한 링크(MP4/HLS/DASH)를 추출할 수 없습니다";
            if (isPl) return "Błąd: nie udało się wyodrębnić linku do pobrania (MP4/HLS/DASH) z metadanych";
            if (isNl) return "Fout: kan geen downloadbare link (MP4/HLS/DASH) uit metadata halen";
            if (isTr) return "Hata: meta verilerden indirilebilir bağlantı (MP4/HLS/DASH) ayıklanamadı";
            if (isAr) return "خطأ: تعذّر استخراج رابط قابل للتنزيل (MP4/HLS/DASH) من البيانات التعريفية";
            if (isHi) return "त्रुटि: मेटाडेटा से डाउनलोड करने योग्य लिंक (MP4/HLS/DASH) निकालने में विफल";
            if (isIn || isMs) return "Kesalahan: gagal mengekstrak tautan yang dapat diunduh (MP4/HLS/DASH) dari metadata";
            if (isVi) return "Lỗi: không thể trích xuất liên kết tải xuống (MP4/HLS/DASH) từ siêu dữ liệu";
            if (isUk) return "Помилка: не вдалося витягти посилання для завантаження (MP4/HLS/DASH) з метаданих";
            if (isSv) return "Fel: kunde inte extrahera en nedladdningsbar länk (MP4/HLS/DASH) från metadata";
            if (isNb) return "Feil: kunne ikke hente ut en nedlastbar lenke (MP4/HLS/DASH) fra metadata";
            if (isDa) return "Fejl: kunne ikke hente et downloadbart link (MP4/HLS/DASH) fra metadata";
            if (isFi) return "Virhe: ladattavaa linkkiä (MP4/HLS/DASH) ei voitu hakea metatiedoista";
            if (isFil) return "Error: bigong i-extract ang nada-download na link (MP4/HLS/DASH) mula sa metadata";
            if (isEl) return "Σφάλμα: αποτυχία εξαγωγής συνδέσμου λήψης (MP4/HLS/DASH) από τα μεταδεδομένα";
            if (isCs) return "Chyba: z metadat se nepodařilo extrahovat odkaz ke stažení (MP4/HLS/DASH)";
            if (isHu) return "Hiba: nem sikerült letölthető hivatkozást (MP4/HLS/DASH) kinyerni a metaadatokból";
            if (isRo) return "Eroare: nu s-a putut extrage un link descărcabil (MP4/HLS/DASH) din metadate";
            if (isIw) return "שגיאה: כשל בחילוץ קישור להורדה (MP4/HLS/DASH) מהמטא-דאטה";
            if (isHr) return "Greška: nije moguće izdvojiti poveznicu za preuzimanje (MP4/HLS/DASH) iz metapodataka";
            return "Error: failed to extract a downloadable link (MP4/HLS/DASH) from metadata";
        }
        if ("video_err_no_tracking".equals(key)) {
            if (isIt) return "Errore: nessun tracciamento video in memoria";
            if (isEs) return "Error: no hay seguimiento de video en memoria";
            if (isFr) return "Erreur : aucun suivi vidéo en mémoire";
            if (isDe) return "Fehler: keine Videoverfolgung im Speicher";
            if (isPt) return "Erro: sem rastreamento de vídeo na memória";
            if (isRu) return "Ошибка: в памяти нет отслеживаемого видео";
            if (isJa) return "エラー: メモリ内に追跡された動画がありません";
            if (isZh) return "错误：内存中没有缓存视频信息";
            if (isKo) return "오류: 메모리에 추적된 동영상이 없습니다";
            if (isPl) return "Błąd: brak śledzenia wideo w pamięci";
            if (isNl) return "Fout: geen videotracking in geheugen";
            if (isTr) return "Hata: bellekte izlenen video yok";
            if (isAr) return "خطأ: لا يوجد تتبع للفيديو في الذاكرة";
            if (isHi) return "त्रुटि: मेमोरी में कोई ट्रैक किया गया वीडियो नहीं है";
            if (isIn || isMs) return "Kesalahan: tidak ada pelacakan video di memori";
            if (isVi) return "Lỗi: không có theo dõi video trong bộ nhớ";
            if (isUk) return "Помилка: немає відстежуваного відео в пам'яті";
            if (isSv) return "Fel: ingen videospårning i minnet";
            if (isNb) return "Feil: ingen videosporing i minnet";
            if (isDa) return "Fejl: ingen videosporing i hukommelsen";
            if (isFi) return "Virhe: videoseurantaa ei ole muistissa";
            if (isFil) return "Error: walang natunton na video sa memorya";
            if (isEl) return "Σφάλμα: δεν υπάρχει παρακολούθηση βίντεο στη μνήμη";
            if (isCs) return "Chyba: v paměti není sledováno žádné video";
            if (isHu) return "Hiba: nincs videó nyomon követése a memóriában";
            if (isRo) return "Eroare: nu există urmărire video în memorie";
            if (isIw) return "שגיאה: אין מעקב וידאו בזיכרון";
            if (isHr) return "Greška: nema praćenja videozapisa u memoriji";
            return "Error: no video tracking in memory";
        }
        if ("video_err_image_post".equals(key)) {
            if (isIt) return "Errore: questo post è un'immagine statica";
            if (isEs) return "Error: esta publicación es una imagen estática";
            if (isFr) return "Erreur : cette publication est une image statique";
            if (isDe) return "Fehler: Dieser Beitrag ist ein statisches Bild";
            if (isPt) return "Erro: esta publicação é uma imagem estática";
            if (isRu) return "Ошибка: эта публикация является статичным изображением";
            if (isJa) return "エラー: この投稿は静止画です";
            if (isZh) return "错误：此帖子是一张静态图片";
            if (isKo) return "오류: 이 게시물은 정적 이미지입니다";
            if (isPl) return "Błąd: ten post to statyczny obraz";
            if (isNl) return "Fout: dit bericht is een statische afbeelding";
            if (isTr) return "Hata: bu gönderi statik bir resim";
            if (isAr) return "خطأ: هذا المنشور عبارة عن صورة ثابتة";
            if (isHi) return "त्रुटि: यह पोस्ट एक स्थिर छवि है";
            if (isIn || isMs) return "Kesalahan: postingan ini adalah gambar statis";
            if (isVi) return "Lỗi: bài đăng này là một hình ảnh tĩnh";
            if (isUk) return "Помилка: ця публікація є статичним зображенням";
            if (isSv) return "Fel: det här inlägget är en statisk bild";
            if (isNb) return "Feil: dette innlegget er et statisk bilde";
            if (isDa) return "Fejl: dette opslag er et statisk billede";
            if (isFi) return "Virhe: tämä julkaisu on staattinen kuva";
            if (isFil) return "Error: ang post na ito ay isang statikong larawan";
            if (isEl) return "Σφάλμα: αυτή η ανάρτηση είναι μια στατική εικόνα";
            if (isCs) return "Chyba: tento příspěvek je statický obrázek";
            if (isHu) return "Hiba: ez a bejegyzés egy statikus kép";
            if (isRo) return "Eroare: această postare este o imagine statică";
            if (isIw) return "שגיאה: פוست זה הוא תמונה סטטית";
            if (isHr) return "Greška: ova objava je statična slika";
            return "Error: this post is a static image";
        }
        return "";
    }
}
