package app.browzomje.extension.pinterest;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riconosce i moduli "a bacheche" della schermata di ricerca che l'issue #30 chiede di togliere:
 * il carosello sponsorizzato in cima e la sezione delle bacheche in evidenza.
 *
 * <h2>Su cosa si decide</h2>
 * La search landing page è un feed di "story" — modelli con un campo JSON {@code story_type} che
 * dice all'app quale modulo costruire. Si guarda quello, letto per <em>nome JSON</em> come fa
 * {@link AdDetector}: il nome del campo è il protocollo di rete e non viene offuscato, mentre il
 * nome della classe e quello del getter cambiano a ogni versione.
 *
 * <h2>Perché due regole e non una</h2>
 * Il carosello sponsorizzato ha uno {@code story_type} noto e stabile
 * ({@code shopping_spotlight}). Le bacheche in evidenza no: il titolo che si vede a schermo
 * ("Explore featured boards") arriva dal server, non dalle risorse dell'app, e non c'è un tipo
 * documentato. Si riconoscono quindi dai <em>contenuti</em>: una story le cui bacheche portano i
 * marcatori "featured". È volutamente conservativa — se una story contiene anche una sola cosa
 * che non è una bacheca in evidenza, non viene toccata — così "Ideas for you" e i moduli simili,
 * che sono fatti di pin, restano dove sono.
 *
 * <h2>Diagnostica</h2>
 * Ogni {@code story_type} incontrato viene loggato <b>una volta sola</b>: senza deduplica lo
 * scroll riempirebbe logcat e diventerebbe illeggibile. È il log che serve per pinnare il tipo
 * esatto delle bacheche in evidenza e sostituire la regola euristica con una esatta.
 */
final class SearchModuleDetector {

    /** Nome JSON del campo che dice di che modulo si tratta. */
    private static final String STORY_TYPE = "story_type";

    /**
     * Tipi di story da togliere sempre.
     *
     * <p>{@code shopping_spotlight} è il carosello a tutta larghezza con i pallini in cima alla
     * ricerca: nel codice dell'app è smistato su una view che dipende dal gestore delle
     * impression sponsorizzate e dall'utility dei deep link pubblicitari, cioè è un modulo
     * pubblicitario a tutti gli effetti.
     */
    private static final Set<String> BLOCKED_STORY_TYPES = new HashSet<>(Arrays.asList(
            "shopping_spotlight"
    ));

    /**
     * Tipi da non toccare mai, nemmeno se una regola euristica dicesse il contrario.
     *
     * <p>Rete di sicurezza esplicita: sono i moduli che l'utente vuole tenere, e averli scritti
     * qui rende il comportamento leggibile senza dover ricostruire la logica.
     */
    private static final Set<String> KEEP_STORY_TYPES = new HashSet<>(Arrays.asList(
            "slp_recent_searches",
            "slp_search_recommendation"
    ));

    // NOTA: "slp_immersive_header" stava qui e non doveva. Il log del primo collaudo lo ha
    // smentito: `story_type="slp_immersive_header" items=7 of=s1 featured=true`, cioè sette
    // bacheche in evidenza — ed è esattamente il carosello con sette pallini dell'issue #30.
    // Proteggendolo si otteneva l'unica cosa che l'utente voleva togliere. Ora non è più
    // protetto e ci pensa la regola sulle bacheche in evidenza, che lo rimuove solo se *tutti*
    // i suoi elementi sono tali: se un domani Pinterest ci mettesse dentro altro, resta.

    /**
     * Marcatori di "bacheca in evidenza" sul modello della bacheca.
     *
     * <p>Tre nomi diversi perché l'app stessa usa tre strade per la stessa cosa: i metadati del
     * programma, il tipo della bacheca, e la lista dei contesti in cui è messa in evidenza.
     */
    private static final String FEATURED_METADATA = "featured_board_metadata";
    private static final String BOARD_TYPE = "board_type";
    private static final String FEATURED_IN = "featured_in";

    /** Valore di {@link #BOARD_TYPE} e di {@link #FEATURED_IN} che marca la bacheca. */
    private static final String FEATURED_BOARD = "FEATURED_BOARD";
    private static final String FEATURED_BOARDS = "FEATURED_BOARDS";

    /**
     * story_type già finiti in logcat, per non ripeterli a ogni scroll.
     *
     * <p>Dichiarato con il tipo concreto e non come {@code Map}: {@code Map.putIfAbsent} è un
     * metodo di default arrivato con API 24, mentre {@code ConcurrentHashMap.putIfAbsent} c'è
     * da sempre — e il minSdk dell'extension è 23.
     */
    private static final ConcurrentHashMap<String, Boolean> ALREADY_LOGGED =
            new ConcurrentHashMap<>();

    /** Solo per diagnostica: quanti moduli sono stati tolti in tutta la sessione. */
    private static volatile int totalRemoved;

    private SearchModuleDetector() {}

    /**
     * @return true se il modello è un modulo della ricerca da togliere.
     *
     * <p>Chiamata per ogni elemento di ogni lista di feed, quindi tutto ciò che costa (la
     * scansione dei campi) sta dietro le cache di {@link AdDetector}.
     */
    static boolean isUnwantedSearchModule(Object model) {
        if (model == null) {
            return false;
        }

        Object rawType = AdDetector.fieldValueBySerializedName(model, STORY_TYPE);
        if (!(rawType instanceof String)) {
            return false; // non è una story: non ci riguarda
        }
        String storyType = ((String) rawType).toLowerCase(java.util.Locale.ROOT);

        describeOnce(storyType, model);

        if (KEEP_STORY_TYPES.contains(storyType)) {
            return false;
        }
        if (BLOCKED_STORY_TYPES.contains(storyType)) {
            record(storyType, "sponsored carousel");
            return true;
        }
        if (isFeaturedBoardStory(model)) {
            record(storyType, "featured boards");
            return true;
        }
        return false;
    }

    /**
     * @return true se la story è fatta <em>soltanto</em> di bacheche in evidenza.
     *
     * <p>Il "soltanto" è la garanzia di non toccare i moduli buoni: una sezione mista, o fatta di
     * pin, non viene rimossa nemmeno se contiene per caso una bacheca in evidenza.
     */
    private static boolean isFeaturedBoardStory(Object story) {
        List<?> objects = storyObjects(story);
        if (objects == null || objects.isEmpty()) {
            return false;
        }
        for (Object item : objects) {
            if (!isFeaturedBoard(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gli oggetti trasportati dalla story. Il campo non ha annotazione Gson — l'app lo riempie
     * con un deserializzatore scritto a mano — quindi non si può cercare per nome JSON: si prende
     * la prima lista non vuota fra i campi dichiarati.
     */
    private static List<?> storyObjects(Object story) {
        try {
            for (java.lang.reflect.Field field : story.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(story);
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    return (List<?>) value;
                }
            }
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SEARCH_MODULES, "could not read the story contents", t);
        }
        return null;
    }

    /** @return true se il modello è una bacheca marcata come "in evidenza". */
    private static boolean isFeaturedBoard(Object model) {
        if (model == null) {
            return false;
        }
        if (AdDetector.fieldValueBySerializedName(model, FEATURED_METADATA) != null) {
            return true;
        }

        Object boardType = AdDetector.fieldValueBySerializedName(model, BOARD_TYPE);
        if (boardType instanceof String && FEATURED_BOARD.equalsIgnoreCase((String) boardType)) {
            return true;
        }

        Object featuredIn = AdDetector.fieldValueBySerializedName(model, FEATURED_IN);
        if (featuredIn instanceof Collection) {
            for (Object entry : (Collection<?>) featuredIn) {
                if (entry instanceof String && FEATURED_BOARDS.equalsIgnoreCase((String) entry)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Logga la prima volta che si incontra un dato {@code story_type}, con quello che serve per
     * decidere se andrebbe filtrato: il titolo, quanti oggetti porta, di che classe sono e se
     * risultano bacheche in evidenza.
     */
    private static void describeOnce(String storyType, Object story) {
        if (ALREADY_LOGGED.putIfAbsent(storyType, Boolean.TRUE) != null) {
            return;
        }
        try {
            List<?> objects = storyObjects(story);
            int total = objects == null ? 0 : objects.size();
            StringBuilder sb = new StringBuilder("story_type=\"").append(storyType).append('"');
            sb.append(" items=").append(total);
            if (total > 0) {
                // Si contano TUTTI gli elementi, non solo il primo: la regola di rimozione
                // pretende che lo siano tutti, quindi un log sul solo primo elemento non
                // permetteva di prevedere se la story sarebbe stata tolta o no.
                int featured = 0;
                for (Object item : objects) {
                    if (isFeaturedBoard(item)) {
                        featured++;
                    }
                }
                sb.append(" of=").append(objects.get(0).getClass().getSimpleName());
                sb.append(" featured=").append(featured).append('/').append(total);
                sb.append(" -> ").append(featured == total ? "REMOVE" : "keep");
            }
            Object promoted = AdDetector.fieldValueBySerializedName(story, "is_promoted");
            if (Boolean.TRUE.equals(promoted)) {
                sb.append(" is_promoted=true");
            }
            MorpheLog.i(MorpheLog.SEARCH_MODULES, "seen " + sb);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SEARCH_MODULES, "could not describe \"" + storyType + '"', t);
        }
    }

    private static void record(String storyType, String why) {
        totalRemoved++;
        MorpheLog.i(MorpheLog.SEARCH_MODULES, "removed \"" + storyType + "\" (" + why
                + ") — session total: " + totalRemoved);
        MorpheLog.setStatus(MorpheLog.SEARCH_MODULES, "ok — " + totalRemoved + " modules removed");
    }
}
