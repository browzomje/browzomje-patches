package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accesso alle API interne di Pinterest senza dipendere dai nomi offuscati.
 *
 * <p>Regola generale: <b>cercare per forma, non per nome</b>. Nomi come {@code nz0.b0} o
 * {@code y()} cambiano a ogni release; la <em>firma</em> di un metodo no. Dove è possibile
 * ricavare qualcosa dalla struttura (il "view creator" del menu, l'enum delle icone, l'istanza
 * dell'EventManager) lo facciamo così; per le poche cose che non hanno una forma riconoscibile
 * (le classi-evento, che sono solo contenitori di dati) resta una lista di nomi candidati, uno
 * per versione supportata, con un log esplicito quando nessuno risolve.
 *
 * <p>Vedi {@code pinterest/OBFUSCATION_MAP.md} per la tabella completa dei nomi per versione.
 */
final class PinterestReflection {

    /**
     * Classi-evento "chiudi il menu contestuale": {@code new <classe>()} passato all'EventManager.
     * 14.28.0 → ii0.o, 14.23.0 → ai0.u.
     */
    private static final String[] DISMISS_EVENT_CANDIDATES = {
            "ii0.o", "ai0.u"
    };

    /** Holder statico dell'EventManager, usato solo se non si riesce a ricavarlo dagli oggetti. */
    private static final String[] EVENT_MANAGER_HOLDER_CANDIDATES = {
            "ob0.p", "fb0.q", "fb0.t"
    };

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /** Istanza dell'EventManager, catturata dal primo oggetto Pinterest che ce la mostra. */
    private static volatile Object cachedEventManager;

    private PinterestReflection() {}

    // ------------------------------------------------------------------ menu contestuale

    /**
     * Trova il "view creator" del menu overflow: l'oggetto che sa costruire una riga di menu con
     * lo stile nativo di Pinterest.
     *
     * <p>Ricerca strutturale: fra i metodi senza argomenti del container, quello il cui tipo di
     * ritorno dichiara {@code RelativeLayout a(CharSequence, String, <enum>, boolean)}. Su
     * 14.23.0 quel metodo si chiamava {@code D()}, su 14.28.0
     * {@code y0()}: cercandolo per forma non ci interessa più.
     */
    static Object findMenuRowViewCreator(Object container) {
        for (Method method : container.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive() || returnType == void.class
                    || returnType.getName().startsWith("java.")
                    || returnType.getName().startsWith("android.")) {
                continue;
            }
            if (findRowFactoryMethod(returnType) == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object creator = method.invoke(container);
                if (creator != null) {
                    MorpheLog.d(MorpheLog.REFLECTION, "view creator found via "
                            + container.getClass().getName() + "." + method.getName()
                            + "() -> " + creator.getClass().getName());
                    rememberEventManagerFrom(creator);
                    return creator;
                }
            } catch (Throwable t) {
                MorpheLog.d(MorpheLog.REFLECTION, "call to " + method.getName()
                        + "() failed: " + t);
            }
        }
        return null;
    }

    /**
     * Il metodo con cui il "view creator" del menu costruisce una riga.
     *
     * <p>Su 14.28.0 era {@code RelativeLayout a(CharSequence, String, <enum icone>, boolean)}. Su
     * 14.32.0 ha preso un parametro in più — {@code a(CharSequence, String, <enum>, boolean,
     * boolean)} — e siccome il vecchio riconoscimento pretendeva <em>esattamente</em> quattro
     * parametri non lo trovava più: le nostre due voci finivano nella riga di ripiego, con font,
     * icona e allineamento diversi da tutte le altre.
     *
     * <p>È lo stesso errore descritto nel PATCHING_MEMORY a proposito di
     * {@code ModelListWithBookmark}: l'ancora serve a trovare il membro, ma ogni vincolo in più
     * è una cosa in più che si può rompere. Qui quindi si pretende solo ciò che rende il metodo
     * riconoscibile e utilizzabile:
     *
     * <ul>
     *   <li>restituisce una {@link View} — è la riga;
     *   <li>il primo parametro è la {@link CharSequence} del testo;
     *   <li>c'è un parametro enum, che è l'icona.
     * </ul>
     *
     * Tutto il resto (quanti parametri, in che ordine, di che tipo) lo riempie
     * {@link #buildFactoryArguments}. Un parametro aggiunto in una versione futura non romperà più
     * nulla.
     */
    static Method findRowFactoryMethod(Class<?> viewCreatorClass) {
        for (Method method : viewCreatorClass.getMethods()) {
            if (!View.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length < 4 || parameters[0] != CharSequence.class) {
                continue;
            }
            if (indexOfEnumParameter(parameters) < 0) {
                continue;
            }
            return method;
        }
        return null;
    }

    /** @return la posizione del primo parametro enum (l'icona), o -1 se non ce n'è. */
    private static int indexOfEnumParameter(Class<?>[] parameters) {
        for (int i = 1; i < parameters.length; i++) {
            if (parameters[i].isEnum()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Riempie gli argomenti della fabbrica: il testo, l'icona, e per tutto il resto il valore
     * neutro del tipo.
     *
     * <p>I parametri che non conosciamo sono opzioni di presentazione (riga compatta, badge, …):
     * passando il valore neutro si ottiene la riga standard, cioè esattamente quella che vogliamo.
     */
    private static Object[] buildFactoryArguments(Class<?>[] parameters, CharSequence label,
                                                  Object icon, int iconIndex) {
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            if (i == 0) {
                arguments[i] = label;
            } else if (i == iconIndex) {
                arguments[i] = icon;
            } else if (parameters[i] == boolean.class) {
                arguments[i] = Boolean.FALSE;
            } else if (parameters[i] == int.class) {
                arguments[i] = Integer.valueOf(0);
            } else if (parameters[i] == long.class) {
                arguments[i] = Long.valueOf(0L);
            } else if (parameters[i].isPrimitive()) {
                arguments[i] = Integer.valueOf(0);
            } else {
                arguments[i] = null;
            }
        }
        return arguments;
    }

    /**
     * Costruisce una riga di menu identica a quelle native di Pinterest.
     *
     * @param iconName nome della costante nell'enum delle icone Gestalt (es. {@code "LINK"},
     *     {@code "IMAGE"}, {@code "ARROW_DOWN"}). Se non esiste in questa versione la riga viene
     *     comunque creata, senza icona.
     * @return la riga, oppure null se la costruzione nativa non è possibile.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static View buildNativeMenuRow(ViewGroup container, String label, String iconName,
                                   View.OnClickListener onClick) {
        Object creator = findMenuRowViewCreator(container);
        if (creator == null) {
            MorpheLog.w(MorpheLog.REFLECTION, "no view creator on container "
                    + container.getClass().getName() + ": the fallback row will be used");
            return null;
        }

        Method factory = findRowFactoryMethod(creator.getClass());
        if (factory == null) {
            MorpheLog.w(MorpheLog.REFLECTION, "view creator has no row-building method");
            return null;
        }

        Class<?>[] parameters = factory.getParameterTypes();
        int iconIndex = indexOfEnumParameter(parameters);
        Class<?> iconEnum = parameters[iconIndex];
        Object icon = null;
        try {
            icon = Enum.valueOf((Class<Enum>) iconEnum, iconName);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "icon " + iconName + " missing from "
                    + iconEnum.getName() + ": row without an icon");
        }

        try {
            factory.setAccessible(true);
            Object built = factory.invoke(creator,
                    buildFactoryArguments(parameters, label, icon, iconIndex));
            if (!(built instanceof View)) {
                return null;
            }
            View row = (View) built;
            row.setOnClickListener(onClick);
            return row;
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "native row construction failed", t);
            return null;
        }
    }

    // ------------------------------------------------------------------ EventManager

    /**
     * Registra l'istanza dell'EventManager se l'oggetto passato ne contiene una.
     *
     * <p>Il view creator del menu tiene un riferimento all'EventManager fra i suoi campi: ce lo
     * prendiamo da lì, così non serve conoscere il nome della classe holder.
     */
    private static void rememberEventManagerFrom(Object host) {
        if (cachedEventManager != null || host == null) {
            return;
        }
        try {
            for (Field field : host.getClass().getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type.isPrimitive() || type.getName().startsWith("java.")
                        || type.getName().startsWith("android.")) {
                    continue;
                }
                if (!hasPostMethod(type)) {
                    continue;
                }
                field.setAccessible(true);
                Object candidate = field.get(host);
                if (candidate != null) {
                    cachedEventManager = candidate;
                    MorpheLog.i(MorpheLog.REFLECTION, "EventManager found: "
                            + candidate.getClass().getName());
                    return;
                }
            }
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.REFLECTION, "could not look for EventManager in the fields: " + t);
        }
    }

    private static boolean hasPostMethod(Class<?> type) {
        try {
            type.getMethod("d", Object.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** @return l'EventManager, cercandolo prima fra quelli già visti e poi per nome. */
    static Object eventManager() {
        Object cached = cachedEventManager;
        if (cached != null) {
            return cached;
        }
        for (String className : EVENT_MANAGER_HOLDER_CANDIDATES) {
            Class<?> holder = findClass(className);
            if (holder == null) {
                continue;
            }
            for (Field field : holder.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!hasPostMethod(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object candidate = field.get(null);
                    if (candidate != null) {
                        cachedEventManager = candidate;
                        MorpheLog.i(MorpheLog.REFLECTION, "EventManager resolved from " + className
                                + "." + field.getName());
                        return candidate;
                    }
                } catch (Throwable ignored) {
                    // proviamo il candidato successivo
                }
            }
        }
        MorpheLog.w(MorpheLog.REFLECTION, "EventManager not resolved: tried "
                + java.util.Arrays.toString(EVENT_MANAGER_HOLDER_CANDIDATES)
                + ". Native toasts and menu dismissal will use the fallback.");
        return null;
    }

    /** Pubblica un evento sull'EventManager di Pinterest. @return true se riuscito. */
    static boolean postEvent(Object event) {
        Object manager = eventManager();
        if (manager == null || event == null) {
            return false;
        }
        try {
            Method post = manager.getClass().getMethod("d", Object.class);
            post.setAccessible(true);
            post.invoke(manager, event);
            return true;
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "could not send event "
                    + event.getClass().getName(), t);
            return false;
        }
    }

    // ------------------------------------------------------------------ eventi concreti

    /** Chiude il menu contestuale del pin. @return true se l'evento è stato inviato. */
    static boolean dismissContextualMenu() {
        Object event = newInstanceOfAny(DISMISS_EVENT_CANDIDATES);
        if (event == null) {
            MorpheLog.w(MorpheLog.REFLECTION, "\"dismiss menu\" event class not found "
                    + "(tried " + java.util.Arrays.toString(DISMISS_EVENT_CANDIDATES)
                    + "): the menu will stay open. PinterestReflection needs updating.");
            return false;
        }
        return postEvent(event);
    }

    /**
     * Mostra un toast con lo stile Gestalt di Pinterest.
     *
     * @return true se il toast nativo è stato inviato; false se il chiamante deve ripiegare su
     *     {@link android.widget.Toast}.
     */
    /** Il contenitore dei toast: nome pulito, sta nella libreria di componenti Gestalt. */
    private static final String TOAST_CONTAINER_CLASS =
            "com.pinterest.gestalt.toast.PinterestToastContainer";

    static boolean showGestaltToast(Context context, String message, int durationMs) {
        String modelClassName = MorpheRuntimeNames.textToastClass;
        if (modelClassName == null || modelClassName.isEmpty()) {
            MorpheLog.w(MorpheLog.REFLECTION, "TOAST: the patch did not deliver a toast class "
                    + "(is \"Morphe runtime names\" enabled?) — drawing our own");
            return false;
        }

        try {
            // 1) Il contenitore: si cerca nell'albero delle view dell'Activity per nome di classe.
            //    È lui che sa animare, impilare e far sparire i toast.
            Activity activity = PinterestUtils.activityOf(context);
            if (activity == null) {
                MorpheLog.w(MorpheLog.REFLECTION, "TOAST: no Activity — drawing our own");
                return false;
            }
            View container = findByClassName(activity.getWindow().getDecorView(),
                    TOAST_CONTAINER_CLASS);
            if (container == null) {
                MorpheLog.w(MorpheLog.REFLECTION, "TOAST: " + TOAST_CONTAINER_CLASS
                        + " not in the view tree of " + activity.getClass().getName()
                        + " — drawing our own");
                return false;
            }

            // 2) Il modello, costruito con la classe che la patch ha risolto leggendo il dex.
            Object model = Class.forName(modelClassName)
                    .getConstructor(String.class, int.class)
                    .newInstance(message, durationMs);

            // 3) Il metodo con cui si consegna il modello al contenitore. Il suo nome è offuscato,
            //    ma la firma no: è l'unico metodo pubblico che restituisce void e prende un solo
            //    parametro del tipo base dei modelli — che ricaviamo dal modello stesso, senza
            //    nominarlo.
            Class<?> modelBase = model.getClass();
            while (modelBase.getSuperclass() != null
                    && modelBase.getSuperclass() != Object.class) {
                modelBase = modelBase.getSuperclass();
            }
            Method show = findShowMethod(container.getClass(), modelBase);
            if (show == null) {
                MorpheLog.w(MorpheLog.REFLECTION, "TOAST: no method on "
                        + container.getClass().getName() + " takes a " + modelBase.getName()
                        + " — drawing our own");
                return false;
            }

            show.setAccessible(true);
            show.invoke(container, model);
            MorpheLog.i(MorpheLog.REFLECTION, "TOAST: shown with Pinterest's own toast ("
                    + modelClassName + " -> " + container.getClass().getSimpleName() + "."
                    + show.getName() + ")");
            return true;
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "TOAST: native toast failed — drawing our own", t);
            return false;
        }
    }

    /** Il metodo `void <qualcosa>(<modello di toast>)` del contenitore. */
    private static Method findShowMethod(Class<?> containerClass, Class<?> modelBase) {
        for (Method method : containerClass.getMethods()) {
            if (method.getReturnType() != void.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == modelBase) {
                return method;
            }
        }
        return null;
    }

    /** Cerca in profondità la prima view di questa classe. */
    private static View findByClassName(View root, String className) {
        if (root == null) {
            return null;
        }
        if (className.equals(root.getClass().getName())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByClassName(group.getChildAt(i), className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ modello Pin

    /**
     * Estrae dal Pin la mappa dei formati video (`{"V_HLSV4": …, "V_720P": …}`).
     *
     * <p>La versione precedente lo faceva con {@code invokeNoArg(pin, "v7")} seguito da
     * {@code invokeNoArg(videos, "g")}: nomi offuscati, validi solo su 14.23.0. Su 14.28.0 il
     * getter è {@code x7()} e su 14.28.0 {@code D7()}, quindi la catena falliva e il menu
     * ripiegava sempre su "Scarica con app esterna (yt-dlp)" — è l'issue #14.
     *
     * <p>Adesso il campo si prende per nome JSON ({@code "videos"}, che non cambia mai) e la
     * mappa si trova per forma: l'unico metodo senza argomenti che restituisce una Map.
     *
     * @return la mappa dei formati, oppure null se il pin non ha video.
     */
    static Map<?, ?> findVideoTracks(Object pin) {
        if (pin == null) {
            return null;
        }

        // Nessun log qui: questo metodo viene chiamato per OGNI pin di OGNI pagina del feed
        // (la cattura per il download bacheca), e una riga per pin rendeva logcat illeggibile.
        // Chi ha bisogno di sapere perché un singolo pin non è scaricabile lo logga dal proprio
        // punto di chiamata, dove il contesto c'è.
        Object videos = AdDetector.fieldValueBySerializedName(pin, "videos");
        if (videos == null) {
            return null;
        }
        if (videos instanceof Map) {
            return (Map<?, ?>) videos;
        }

        return findMapAccessor(videos);
    }

    /** @return la Map restituita dal primo metodo senza argomenti che ne restituisce una. */
    static Map<?, ?> findMapAccessor(Object holder) {
        if (holder == null) {
            return null;
        }
        for (Method method : holder.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0
                    || !Map.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(holder);
                if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
                    return (Map<?, ?>) value;
                }
            } catch (Throwable ignored) {
                // proviamo il metodo successivo
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ utilità

    static Class<?> findClass(String name) {
        Class<?> cached = CLASS_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> loaded = Class.forName(name);
            CLASS_CACHE.put(name, loaded);
            return loaded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Class<?> findFirstClass(String[] candidates) {
        for (String candidate : candidates) {
            Class<?> loaded = findClass(candidate);
            if (loaded != null) {
                return loaded;
            }
        }
        return null;
    }

    private static Object newInstanceOfAny(String[] candidates) {
        for (String candidate : candidates) {
            Class<?> clazz = findClass(candidate);
            if (clazz == null) {
                continue;
            }
            try {
                Constructor<?> ctor = clazz.getConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (Throwable ignored) {
                // proviamo il candidato successivo
            }
        }
        return null;
    }
}
