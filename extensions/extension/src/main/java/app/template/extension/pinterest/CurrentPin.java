package app.browzomje.extension.pinterest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * L'id del pin su cui l'utente ha appena aperto il menu "…".
 *
 * <p><b>A cosa serve.</b> Quando si copia o si condivide un pin, Pinterest mette negli appunti uno
 * short link {@code pin.it/<slug>}. Lo slug non contiene l'id del pin: per sapere a quale pin
 * corrisponde bisogna seguire il redirect, cioè fare una richiesta di rete e far aspettare l'utente
 * un secondo e mezzo (vedi {@link UrlSanitizer}). Ma l'app quell'id ce l'ha già in memoria: se lo
 * catturiamo quando il menu viene costruito, il link canonico si scrive in locale, all'istante, e
 * per giunta senza dire a Pinterest che stiamo copiando quel link.
 *
 * <p><b>Come si trova l'id.</b> Non per posizione né per nome di campo — sono offuscati e cambiano a
 * ogni versione — ma riconoscendo il <em>modello</em>: si percorre il grafo degli oggetti a partire
 * dalla view del menu e ci si ferma sul primo oggetto che {@link AdDetector#isCandidateModel} sa
 * essere un modello di Pinterest, cioè che porta i marcatori Gson del Pin. Da lì l'id si legge per
 * nome JSON (`id`), che è protocollo di rete e non cambia. È la stessa tecnica già usata per il
 * filtro pubblicità e per il download delle bacheche.
 *
 * <p><b>Il limite, dichiarato.</b> Questa è una cattura "dell'ultimo pin visto", non un legame
 * dimostrato fra <em>quello</em> short link e <em>quel</em> pin. Vale quindi solo entro
 * {@link #FRESHNESS_MS} dall'apertura del menu — il tempo di aprire il menu e premere copia — e per
 * qualunque altro caso si torna alla risoluzione via rete, che è lenta ma è sempre corretta. Meglio
 * aspettare un secondo che consegnare all'utente il link di un pin sbagliato.
 */
final class CurrentPin {

    private CurrentPin() {}

    /**
     * Quanto resta valida la cattura. Coprire il gesto "apro il menu → condividi → copia link"
     * basta e avanza; tenerla valida più a lungo aumenterebbe solo la probabilità di attribuire uno
     * short link al pin sbagliato.
     */
    private static final long FRESHNESS_MS = 20_000L;

    /** Quanti oggetti al massimo si visitano prima di rinunciare, per non pesare sull'apertura del menu. */
    private static final int MAX_VISITED = 1500;

    /** Quanto in profondità si scende nel grafo degli oggetti. */
    private static final int MAX_DEPTH = 7;

    /** Un id di pin è un numero lungo: serve a scartare stringhe che id non sono. */
    private static final Pattern PIN_ID = Pattern.compile("[0-9]{15,20}");

    private static volatile String pinId;
    private static volatile long capturedAt;

    /**
     * Cerca l'id del pin dentro {@code menuView} e lo memorizza.
     *
     * <p>Chiamata alla costruzione del menu "…" del pin. Non solleva mai: se non trova niente,
     * semplicemente non aggiorna nulla e il sanificatore continuerà a risolvere gli short link via
     * rete.
     */
    static void captureFrom(Object menuView) {
        if (menuView == null) {
            return;
        }
        try {
            String found = search(menuView);
            if (found == null) {
                MorpheLog.d(MorpheLog.COPY_LINK, "id del pin non trovato nel menu "
                        + menuView.getClass().getName());
                return;
            }
            pinId = found;
            capturedAt = System.currentTimeMillis();
            MorpheLog.d(MorpheLog.COPY_LINK, "pin corrente: " + found);
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.COPY_LINK, "cattura dell'id del pin non riuscita", t);
        }
    }

    /**
     * Registra direttamente l'id del pin se valido (es. intercettato da SendableObject).
     */
    static void recordPinId(String id) {
        if (id != null && PIN_ID.matcher(id).matches()) {
            pinId = id;
            capturedAt = System.currentTimeMillis();
            MorpheLog.d(MorpheLog.COPY_LINK, "pin corrente (da SendableObject): " + id);
        }
    }

    /**
     * @return il link canonico del pin corrente, o null se non ce n'è uno abbastanza recente. Chi
     *     riceve null deve ricadere sulla risoluzione via rete.
     */
    static String canonicalUrl() {
        String id = pinId;
        if (id == null) {
            return null;
        }
        if (System.currentTimeMillis() - capturedAt > FRESHNESS_MS) {
            return null;
        }
        return "https://pinterest.com/pin/" + id + "/";
    }

    /**
     * Visita in ampiezza il grafo a partire da {@code root} e restituisce l'id del primo modello di
     * Pinterest incontrato.
     *
     * <p>In ampiezza e non in profondità di proposito: il modello del pin sta vicino alla radice —
     * di solito è un campo della view del menu o del suo oggetto di configurazione — mentre in
     * profondità si finisce nella gerarchia delle view di Android prima di averlo trovato.
     */
    /**
     * Il modello del Pin dentro {@code root}, o null.
     *
     * <p>Stessa visita di {@link #search}, ma restituisce l'oggetto invece del solo id: serve a chi
     * dal pin deve ricavare altro, per esempio l'URL dell'immagine da scaricare.
     */
    static Object findPinIn(Object root) {
        if (root == null) {
            return null;
        }
        try {
            return walk(root, true);
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.COPY_LINK, "pin non trovato: " + t);
            return null;
        }
    }

    /**
     * Cerca l'id del pin dentro un oggetto qualsiasi, senza toccare il "pin corrente".
     *
     * <p>La differenza con {@link #canonicalUrl()} è che lì il pin è quello visto per ultimo — un
     * ricordo, valido solo per pochi secondi — mentre qui è quello contenuto nell'oggetto che si
     * passa, cioè un legame dimostrato. Chi ha in mano l'oggetto giusto (per esempio il foglio di
     * condivisione, che il pin da condividere ce l'ha dentro) deve usare questo.
     *
     * @return l'id del pin, o null se nel grafo non ce n'è nessuno.
     */
    static String pinIdIn(Object root) {
        if (root == null) {
            return null;
        }
        try {
            return search(root);
        } catch (Throwable t) {
            MorpheLog.d(MorpheLog.COPY_LINK, "no pin id in " + root.getClass().getName() + ": " + t);
            return null;
        }
    }

    private static String search(Object root) throws IllegalAccessException {
        Object pin = walk(root, false);
        return pin instanceof String ? (String) pin : null;
    }

    /**
     * @param wantModel true per farsi restituire il modello del Pin, false per il solo id.
     */
    private static Object walk(Object root, boolean wantModel) throws IllegalAccessException {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[] {root, 0});
        seen.add(root);

        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_VISITED) {
            Object[] entry = queue.poll();
            Object node = entry[0];
            int depth = (Integer) entry[1];
            visited++;

            if (AdDetector.isCandidateModel(node.getClass())) {
                Object id = AdDetector.fieldValueBySerializedName(node, "id");
                if (id instanceof String && PIN_ID.matcher((String) id).matches()) {
                    return wantModel ? node : id;
                }
            }

            if (depth >= MAX_DEPTH) {
                continue;
            }

            Class<?> clazz = node.getClass();
            while (clazz != null && !skip(clazz)) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()
                            || field.getType().isArray()) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(node);
                    if (value == null || seen.contains(value)) {
                        continue;
                    }
                    // Si decide sulla classe **effettiva** del valore, non sul tipo dichiarato del
                    // campo. È la differenza che faceva fallire il menu circolare: il campo che
                    // porta al pin è dichiarato `View`, quindi guardando il tipo dichiarato lo si
                    // scartava come "roba di Android" — mentre l'oggetto dentro è la view di
                    // Pinterest che il pin ce l'ha davvero.
                    if (skip(value.getClass())) {
                        continue;
                    }
                    seen.add(value);
                    queue.add(new Object[] {value, depth + 1});
                }
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /** Classi in cui non ha senso scendere: framework Android, libreria standard, Kotlin. */
    private static boolean skip(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("android.")
                || name.startsWith("androidx.")
                || name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("kotlin.");
    }
}
