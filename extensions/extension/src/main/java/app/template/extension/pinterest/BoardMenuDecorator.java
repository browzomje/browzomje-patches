package app.browzomje.extension.pinterest;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggiunge la voce di download al menu "…" della bacheca.
 *
 * <p>Quel menu è immutabile: intestazione, voci e callback vengono passati al costruttore e non
 * esistono setter. Si intercetta quindi il valore di ritorno del builder (vedi
 * `DownloadBoardPatch`) e se ne restituisce una copia con una voce in più.
 *
 * <p>Niente nomi di classi o metodi, che cambiano a ogni release: l'elenco delle voci è l'unico
 * getter senza argomenti che restituisce una {@link List}, la callback l'unico che restituisce
 * una {@code kotlin.jvm.functions.Function1} (stdlib Kotlin, non offuscata), e così via.
 *
 * <p>La riga è un oggetto di Pinterest, clonato da una voce già presente nel menu: eredita
 * stile e spaziature e resta serializzabile come le altre, cosa che un {@link Proxy} dinamico
 * non sarebbe.
 */
final class BoardMenuDecorator {

    /**
     * Nome della string resource con l'etichetta della voce, aggiunta all'APK da
     * `MorpheSettingsResourcePatch` in 14 lingue più l'inglese come ripiego.
     *
     * <p>Perché una risorsa e non un testo passato qui a runtime: la view che disegna queste
     * righe mostra l'icona <b>solo</b> dentro un {@code instanceof} sulla classe concreta della
     * voce. Un oggetto nostro avrebbe il testo libero ma nessuna icona; una voce autentica di
     * Pinterest ha l'icona ma prende il titolo solo da un id di risorsa. Con una risorsa vera si
     * ottengono entrambe, e le traduzioni le sceglie Android come per qualunque altra stringa.
     *
     * <p>L'id non è scritto da nessuna parte: si risolve per nome, quindi non importa quale
     * valore gli assegni aapt2 a ogni build.
     */
    private static final String LABEL_STRING_RESOURCE = "morphe_download_board";

    private BoardMenuDecorator() {}

    /**
     * @param menu il modello del menu appena costruito da Pinterest.
     * @return una copia con in fondo la voce di download, oppure {@code menu} invariato se
     *     qualcosa non torna. Non restituisce mai null: il valore finisce dritto in un
     *     {@code return} dell'app.
     */
    static Object decorate(Object menu) {
        if (menu == null) {
            return null;
        }
        try {
            Class<?> menuClass = menu.getClass();
            ClassLoader loader = menuClass.getClassLoader();
            Class<?> function1Class = Class.forName("kotlin.jvm.functions.Function1", false, loader);

            Method itemsGetter = noArgGetterReturning(menuClass, List.class);
            Method handlerGetter = noArgGetterReturning(menuClass, function1Class);
            if (itemsGetter == null || handlerGetter == null) {
                MorpheLog.e(MorpheLog.BOARD_MENU, "il menu non espone elenco voci e callback: "
                        + menuClass.getName());
                return menu;
            }

            // Il costruttore da riusare per la copia: (intestazione, voci, callback).
            Constructor<?> constructor = findConstructor(menuClass, function1Class);
            if (constructor == null) {
                MorpheLog.e(MorpheLog.BOARD_MENU,
                        "nessun costruttore (intestazione, List, Function1) su " + menuClass.getName());
                return menu;
            }

            List<?> items = (List<?>) itemsGetter.invoke(menu);
            if (items == null || items.isEmpty()) {
                MorpheLog.d(MorpheLog.BOARD_MENU, "menu senza voci: niente da decorare");
                return menu;
            }

            Class<?> headerType = constructor.getParameterTypes()[0];
            Method headerGetter = noArgGetterReturning(menuClass, headerType);
            Object header = headerGetter == null ? null : headerGetter.invoke(menu);

            int labelResource = labelResource();
            if (labelResource == 0) {
                MorpheLog.e(MorpheLog.BOARD_MENU,
                        "string resource \"" + LABEL_STRING_RESOURCE + "\" non trovata nell'APK");
                return menu;
            }

            int downloadIndex = items.size();
            Object row = BoardMenuRowFactory.cloneWithTitle(items.get(0), labelResource, downloadIndex);
            if (row == null) {
                return menu;
            }

            List<Object> decorated = new ArrayList<>(items);
            decorated.add(row);

            Object handler = handlerGetter.invoke(menu);
            Object wrappedHandler = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[] { function1Class },
                    new DownloadAwareHandler(handler, downloadIndex));

            Object result = constructor.newInstance(header, decorated, wrappedHandler);
            MorpheLog.ok(MorpheLog.BOARD_MENU, "voce di download aggiunta al menu della bacheca "
                    + "(posizione " + downloadIndex + ")");
            return result;
        } catch (Throwable t) {
            // Qualunque cosa vada storta, l'app deve continuare a mostrare il suo menu.
            MorpheLog.e(MorpheLog.BOARD_MENU, "impossibile decorare il menu della bacheca", t);
            return menu;
        }
    }

    /**
     * Callback di click del menu. Pinterest la invoca con l'indice della riga toccata: se è la
     * nostra facciamo partire il download, altrimenti si passa la palla all'originale.
     *
     * <p>Non è serializzabile, ma non serve che lo sia: nel modello del menu la callback è un
     * campo {@code transient}.
     */
    private static final class DownloadAwareHandler implements InvocationHandler {

        private final Object delegate;
        private final int downloadIndex;

        DownloadAwareHandler(Object delegate, int downloadIndex) {
            this.delegate = delegate;
            this.downloadIndex = downloadIndex;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            boolean isOurRow = args != null
                    && args.length == 1
                    && args[0] instanceof Integer
                    && (Integer) args[0] == downloadIndex;

            if (isOurRow) {
                MorpheLog.i(MorpheLog.BOARD_MENU, "voce di download toccata");
                Context context = MorpheSettingsStore.appContext();
                if (context != null) {
                    PinterestUtils.dismissMenu();
                    BoardDownloadHandler.downloadCurrentBoard(context);
                }
                return kotlinUnit();
            }

            if (delegate == null) {
                return kotlinUnit();
            }
            return method.invoke(delegate, args);
        }
    }

    /** {@code kotlin.Unit.INSTANCE}, che è ciò che una {@code Function1<Integer, Unit>} deve restituire. */
    private static Object kotlinUnit() {
        try {
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int labelResource() {
        Context context = MorpheSettingsStore.appContext();
        if (context == null) {
            return 0;
        }
        return context.getResources()
                .getIdentifier(LABEL_STRING_RESOURCE, "string", context.getPackageName());
    }

    /**
     * @return il costruttore {@code (intestazione, List, Function1)} del modello del menu, o null.
     */
    private static Constructor<?> findConstructor(Class<?> menuClass, Class<?> function1Class) {
        for (Constructor<?> candidate : menuClass.getDeclaredConstructors()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 3
                    && parameters[1].isAssignableFrom(List.class)
                    && parameters[2].isAssignableFrom(function1Class)) {
                candidate.setAccessible(true);
                return candidate;
            }
        }
        return null;
    }

    /**
     * @return il primo metodo pubblico senza argomenti che restituisce esattamente {@code type},
     *     o null. Serve a leggere i pezzi del menu senza conoscerne i nomi offuscati.
     */
    private static Method noArgGetterReturning(Class<?> owner, Class<?> type) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == type) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
