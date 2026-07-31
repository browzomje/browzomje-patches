package app.browzomje.extension.pinterest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riconosce se un modello dell'API Pinterest (Pin, "story", carosello…) è un contenuto
 * sponsorizzato.
 *
 * <h2>Perché non si usano i getter</h2>
 * La versione precedente chiamava {@code getMethod("I5")} sul Pin. È un nome offuscato: cambia
 * a ogni release. Su 14.24.0 {@code re.I5()} non legge affatto {@code is_promoted} (legge un
 * altro Boolean), quindi la rilevazione restituiva sempre {@code false} e la patch "Disable
 * ads" era di fatto un no-op — è la causa dell'issue #15.
 *
 * <h2>Cosa si usa al posto loro</h2>
 * I modelli sono deserializzati con Gson e ogni campo porta l'annotazione {@code @SerializedName}
 * con il nome del campo JSON: {@code @dp.b("is_promoted")} su 14.24, {@code @fp.b("is_promoted")}
 * su 14.28. La <em>classe</em> dell'annotazione è offuscata, ma il suo <em>valore</em> no: è il
 * protocollo di rete, che Pinterest non può rinominare. Cerchiamo quindi i campi per valore
 * dell'annotazione. Funziona su tutte le versioni, senza aggiornare nulla.
 *
 * <p>La mappa classe → campi-marcatore è calcolata una volta sola e messa in cache: a regime il
 * costo per pin è qualche {@code Field.get}.
 */
final class AdDetector {

    /**
     * Campi la cui presenza identifica con certezza un annuncio.
     *
     * <p>Volutamente <em>esclusi</em> perché generano falsi positivi su contenuti organici:
     * {@code has_been_promoted} (pin dell'utente promosso in passato), {@code is_quick_promotable*}
     * ed {@code is_eligible_for_*} (semplice idoneità), {@code sponsorship} e {@code promoter}
     * (partnership dichiarate dai creator: contenuto organico, non un annuncio comprato).
     */
    private static final Set<String> BOOLEAN_MARKERS = new HashSet<>(Arrays.asList(
            "is_promoted",
            "is_active_ad",
            "is_cpc_ad",
            "is_third_party_ad",
            "is_downstream_promotion",
            "promoted_is_lead_ad"
    ));

    /** Marcatori di tipo String: contano come annuncio se valorizzati e non vuoti. */
    private static final Set<String> STRING_MARKERS = new HashSet<>(Arrays.asList(
            "ad_destination_url",
            "pin_promotion_id",
            "pin_promotion_id_reformatted",
            "promoted_android_deep_link"
    ));

    /** Marcatori di tipo oggetto: contano come annuncio se non nulli. */
    private static final Set<String> OBJECT_MARKERS = new HashSet<>(Collections.singletonList(
            "ad_data"
    ));

    /**
     * Campi che identificano un pin di catalogo di un negozio: il nome e il dominio del
     * commerciante, e le sue "shopping flags".
     *
     * <p>Sono una cosa <em>diversa</em> dai marcatori qui sopra, e la distinzione è il motivo per
     * cui esistono due opzioni separate. Un pin di prodotto non è un annuncio: Pinterest non lo
     * marca come promosso e non gli mette l'etichetta "Promoted" che invece mette agli annunci
     * veri. È contenuto organico, che però nel feed occupa il posto di un annuncio e come tale
     * viene percepito (issue #15) — da cui l'opzione, spenta per default.
     */
    private static final Set<String> SHOPPING_MARKERS = new HashSet<>(Arrays.asList(
            "canonical_merchant_name",
            "canonical_merchant_domain",
            "shopping_flags"
    ));

    /** Cache: classe del modello -> campi marcatore già resi accessibili. */
    private static final Map<Class<?>, List<Field>> MARKER_CACHE = new ConcurrentHashMap<>();

    /** Come {@link #MARKER_CACHE}, per i campi di {@link #SHOPPING_MARKERS}. */
    private static final Map<Class<?>, List<Field>> SHOPPING_MARKER_CACHE = new ConcurrentHashMap<>();

    /** Cache: classe dell'annotazione -> metodo {@code value()}. */
    private static final Map<Class<?>, Method> ANNOTATION_VALUE_CACHE = new ConcurrentHashMap<>();

    /** Classi già analizzate e risultate prive di marcatori (evita di riscansionarle). */
    private static final List<Field> NO_MARKERS = Collections.emptyList();

    private AdDetector() {}

    /** @return true se il modello è un contenuto sponsorizzato. */
    static boolean isAd(Object model) {
        return matches(model, markersFor(model), "ad");
    }

    /**
     * @return true se il modello è un pin di catalogo di un negozio. Vedi {@link #SHOPPING_MARKERS}
     *     per perché è tenuto distinto da {@link #isAd}.
     */
    static boolean isShoppingPin(Object model) {
        return matches(model, shoppingMarkersFor(model), "product pin");
    }

    /**
     * @param markers i campi da esaminare; il primo valorizzato decide.
     * @param what come chiamare la cosa nel log, per distinguere a colpo d'occhio quale delle due
     *     opzioni ha tolto un pin.
     */
    private static boolean matches(Object model, List<Field> markers, String what) {
        for (Field field : markers) {
            try {
                if (isSet(field.get(model))) {
                    MorpheLog.d(MorpheLog.ADS, what + ": " + model.getClass().getSimpleName()
                            + " marked by " + serializedNameOf(field));
                    return true;
                }
            } catch (Throwable t) {
                MorpheLog.w(MorpheLog.ADS, "could not read field " + field.getName(), t);
            }
        }
        return false;
    }

    /**
     * @return true se il campo è valorizzato, cioè se la sua presenza conta come marcatore.
     *
     * <p>Un booleano vale se è vero, una stringa se non è vuota, una collezione se ha elementi —
     * {@code shopping_flags} è una {@code List<Integer>} e una lista vuota significa "nessuna
     * shopping flag", non "è un prodotto". Qualsiasi altro oggetto vale per il solo fatto di non
     * essere nullo.
     */
    private static boolean isSet(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0;
        }
        if (value instanceof java.util.Collection) {
            return !((java.util.Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    /**
     * @return true se la classe è un modello che <em>può</em> essere un annuncio, cioè uno dei
     *     tipi che vale la pena esaminare (Pin, story, carosello…). Usato per non ricorrere
     *     dentro oggetti che sono già stati valutati.
     */
    static boolean isCandidateModel(Class<?> clazz) {
        return !markersFor(clazz, MARKER_CACHE, "ad markers").isEmpty();
    }

    private static List<Field> markersFor(Object model) {
        return model == null
                ? NO_MARKERS
                : markersFor(model.getClass(), MARKER_CACHE, "ad markers");
    }

    private static List<Field> shoppingMarkersFor(Object model) {
        return model == null
                ? NO_MARKERS
                : markersFor(model.getClass(), SHOPPING_MARKER_CACHE, "product markers");
    }

    /**
     * @param cache dove memorizzare il risultato: distingue anche <em>quali</em> marcatori
     *     cercare, dato che ogni cache ne ha una sola specie.
     */
    private static List<Field> markersFor(Class<?> clazz, Map<Class<?>, List<Field>> cache,
                                          String what) {
        List<Field> cached = cache.get(clazz);
        if (cached != null) {
            return cached;
        }

        boolean shopping = cache == SHOPPING_MARKER_CACHE;
        List<Field> markers = new ArrayList<>();
        try {
            Class<?> current = clazz;
            while (current != null && !current.getName().startsWith("java.")) {
                for (Field field : current.getDeclaredFields()) {
                    String serializedName = serializedNameOf(field);
                    if (serializedName == null) {
                        continue;
                    }
                    boolean isMarker = shopping
                            ? SHOPPING_MARKERS.contains(serializedName)
                            : BOOLEAN_MARKERS.contains(serializedName)
                                    || STRING_MARKERS.contains(serializedName)
                                    || OBJECT_MARKERS.contains(serializedName);
                    if (isMarker) {
                        field.setAccessible(true);
                        markers.add(field);
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.ADS, "could not scan " + clazz.getName() + " for markers", t);
        }

        List<Field> result = markers.isEmpty() ? NO_MARKERS : Collections.unmodifiableList(markers);
        cache.put(clazz, result);
        if (!result.isEmpty()) {
            MorpheLog.i(MorpheLog.ADS, "model recognised: " + clazz.getName()
                    + " with " + result.size() + " " + what);
        }
        return result;
    }

    /**
     * Legge il valore dell'annotazione {@code @SerializedName} (nome della classe offuscato) di
     * un campo, cioè il nome JSON del campo.
     *
     * @return il nome JSON, oppure null se il campo non ha un'annotazione di quella forma.
     */
    private static String serializedNameOf(Field field) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            Method valueMethod = ANNOTATION_VALUE_CACHE.get(type);
            if (valueMethod == null) {
                try {
                    valueMethod = type.getMethod("value");
                } catch (Throwable ignored) {
                    continue;
                }
                if (valueMethod.getReturnType() != String.class) {
                    continue;
                }
                ANNOTATION_VALUE_CACHE.put(type, valueMethod);
            }
            try {
                Object value = valueMethod.invoke(annotation);
                if (value instanceof String) {
                    return (String) value;
                }
            } catch (Throwable ignored) {
                // annotazione con value() non leggibile: la ignoriamo
            }
        }
        return null;
    }

    /** Cache: classe del modello -> (nome JSON -> campo). Vedi {@link #fieldIndexFor}. */
    private static final Map<Class<?>, Map<String, Field>> FIELD_INDEX_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Legge da un modello il valore del campo il cui nome JSON è {@code jsonName}.
     *
     * <p>Stessa idea di {@link #isAd}, ma per uso generico: permette di accedere ai campi dei
     * modelli senza conoscerne il nome offuscato. Per esempio il campo `videos` del Pin si
     * chiama {@code F3} su 14.28 e il suo getter {@code D7()} — mentre {@code "videos"}, che è
     * il nome sul filo, non cambia mai.
     *
     * @return il valore del campo, oppure null se il modello non ha un campo con quel nome JSON.
     */
    static Object fieldValueBySerializedName(Object model, String jsonName) {
        if (model == null) {
            return null;
        }
        Field field = fieldIndexFor(model.getClass()).get(jsonName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(model);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION,
                    "could not read field \"" + jsonName + "\" from " + model.getClass().getName(), t);
            return null;
        }
    }

    /**
     * Indice "nome JSON -> campo" di una classe, calcolato una volta sola.
     *
     * <p>Senza cache ogni lettura costerebbe una scansione completa dei campi dichiarati, e il
     * modello Pin ne ha diverse centinaia: moltiplicato per i pin di ogni pagina del feed e per
     * i campi che leggiamo (id, images, board, videos) diventerebbe lavoro sufficiente a far
     * scattare la UI.
     */
    private static Map<String, Field> fieldIndexFor(Class<?> clazz) {
        Map<String, Field> cached = FIELD_INDEX_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }

        Map<String, Field> index = new HashMap<>();
        try {
            Class<?> current = clazz;
            while (current != null && !current.getName().startsWith("java.")) {
                for (Field field : current.getDeclaredFields()) {
                    String serializedName = serializedNameOf(field);
                    // Le sottoclassi vincono: la gerarchia si percorre dal basso verso l'alto.
                    if (serializedName != null && !index.containsKey(serializedName)) {
                        field.setAccessible(true);
                        index.put(serializedName, field);
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION,
                    "could not index the fields of " + clazz.getName(), t);
        }

        Map<String, Field> result = Collections.unmodifiableMap(index);
        FIELD_INDEX_CACHE.put(clazz, result);
        MorpheLog.d(MorpheLog.REFLECTION, "indexed " + result.size()
                + " JSON fields of " + clazz.getName());
        return result;
    }

    /** Diagnostica: elenca tutti i nomi JSON "ad-like" di una classe. Usata solo nei log. */
    static String describeMarkers(Class<?> clazz) {
        List<Field> markers = markersFor(clazz, MARKER_CACHE, "ad markers");
        if (markers.isEmpty()) {
            return clazz.getName() + ": no ad marker";
        }
        StringBuilder sb = new StringBuilder(clazz.getName()).append(": ");
        for (int i = 0; i < markers.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(serializedNameOf(markers.get(i)));
        }
        return sb.toString();
    }
}
