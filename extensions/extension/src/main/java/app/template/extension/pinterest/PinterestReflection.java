package app.browzomje.extension.pinterest;

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

    /** Toast "a stringa": costruttore {@code (String testo, int durataMs)}. */
    private static final String[] TOAST_CANDIDATES = {
            "xs2.e", "ir2.f"
    };

    /** Evento "mostra questo toast": costruttore che prende la classe base dei toast. */
    private static final String[] TOAST_EVENT_CANDIDATES = {
            "xs2.g", "ir2.h"
    };

    /** Classe base astratta dei toast, parametro del costruttore qui sopra. */
    private static final String[] TOAST_BASE_CANDIDATES = {
            "vw1.p", "kw1.p", "ww1.o"
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

    /** {@code RelativeLayout a(CharSequence, String, <enum icone>, boolean)}. */
    static Method findRowFactoryMethod(Class<?> viewCreatorClass) {
        for (Method method : viewCreatorClass.getMethods()) {
            if (method.getReturnType() != RelativeLayout.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 4) {
                continue;
            }
            if (parameters[0] != CharSequence.class
                    || parameters[1] != String.class
                    || !parameters[2].isEnum()
                    || parameters[3] != boolean.class) {
                continue;
            }
            return method;
        }
        return null;
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

        Class<?> iconEnum = factory.getParameterTypes()[2];
        Object icon = null;
        try {
            icon = Enum.valueOf((Class<Enum>) iconEnum, iconName);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "icon " + iconName + " missing from "
                    + iconEnum.getName() + ": row without an icon");
        }

        try {
            factory.setAccessible(true);
            RelativeLayout row = (RelativeLayout) factory.invoke(creator, label, null, icon, false);
            if (row == null) {
                return null;
            }
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
    static boolean showGestaltToast(String message, int durationMs) {
        Class<?> toastClass = findFirstClass(TOAST_CANDIDATES);
        Class<?> eventClass = findFirstClass(TOAST_EVENT_CANDIDATES);
        Class<?> baseClass = findFirstClass(TOAST_BASE_CANDIDATES);
        if (toastClass == null || eventClass == null || baseClass == null) {
            MorpheLog.w(MorpheLog.REFLECTION, "Gestalt toast classes not resolved (toast="
                    + toastClass + ", event=" + eventClass + ", base=" + baseClass
                    + "): falling back to the system Toast.");
            return false;
        }
        try {
            Constructor<?> toastCtor = toastClass.getConstructor(String.class, int.class);
            Object toast = toastCtor.newInstance(message, durationMs);
            Constructor<?> eventCtor = eventClass.getConstructor(baseClass);
            return postEvent(eventCtor.newInstance(toast));
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "could not create the Gestalt toast", t);
            return false;
        }
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
