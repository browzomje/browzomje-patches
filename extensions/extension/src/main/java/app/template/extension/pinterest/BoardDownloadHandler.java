package app.browzomje.extension.pinterest;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Scarica tutta la bacheca": salva in blocco le immagini e i video di una bacheca.
 *
 * <h2>Da dove arrivano i pin</h2>
 * Non c'è modo di interrogare l'API di Pinterest per conto nostro (servirebbero i token di
 * sessione). Si sfrutta invece il fatto che le patch pubblicità intercettano già <em>tutte</em>
 * le risposte del feed; da lì i pin vengono raggruppati per bacheca di appartenenza (ogni pin
 * dichiara la propria) e messi da parte.
 *
 * <p>La conseguenza — detta esplicitamente all'utente nella schermata impostazioni e nel
 * messaggio finale — è che si scarica <b>quello che è già stato caricato</b>: i pin che l'app
 * ha scaricato mentre si scorreva. Per prendere una bacheca lunga per intero bisogna prima
 * scorrerla fino in fondo.
 *
 * <h2>Video</h2>
 * I video con un MP4 diretto vengono scaricati come le immagini. Quelli disponibili solo in
 * streaming (HLS/DASH) non sono scaricabili con DownloadManager: vengono contati e segnalati
 * nella snackbar finale, senza interrompere il resto.
 */
final class BoardDownloadHandler {

    /** `boards/<id>/pins/`, con o senza prefisso di versione dell'API. */
    private static final Pattern BOARD_FEED_URL = Pattern.compile("boards/([^/]+)/pins");

    /**
     * Quante bacheche tenere in memoria contemporaneamente.
     *
     * <p>Va tenuto alto perché una singola risposta del feed principale porta pin di decine di
     * bacheche diverse, e ognuna entra in questa mappa. Con un limite basso bastava una
     * schermata di feed per sfrattare la bacheca aperta mentre {@link #currentBoardId}
     * continuava a puntarci, e il download non trovava più nulla da scaricare. L'accorgimento
     * decisivo è però che la bacheca corrente non viene mai sfrattata, vedi {@link #store}.
     */
    private static final int MAX_BOARDS = 64;

    /** boardId -> (pinId -> pin catturato), in ordine di comparsa. */
    private static final LinkedHashMap<String, LinkedHashMap<String, CapturedPin>> BOARDS =
            new LinkedHashMap<>();

    /** Ultima bacheca di cui è arrivata una risposta: è quella che l'utente sta guardando. */
    private static volatile String currentBoardId;

    private BoardDownloadHandler() {}

    /** Un pin ridotto a ciò che serve per scaricarlo. */
    private static final class CapturedPin {
        String id;
        String boardId;
        String boardName;
        String imageUrl;
        String videoUrl;      // MP4 diretto, scaricabile
        boolean videoOnlyStreaming; // video presente ma solo HLS/DASH
    }

    // ------------------------------------------------------------------ cattura

    /**
     * Chiamata dall'hook sulle risposte del feed: memorizza i pin raggruppandoli per bacheca
     * di appartenenza.
     *
     * <h3>Come si capisce qual è la bacheca aperta</h3>
     * Due segnali, perché nessuno dei due è garantito da solo:
     *
     * <ol>
     *   <li><b>URL della risposta</b> ({@code boards/<id>/pins/}): è la prova certa che questa
     *       risposta arriva da una bacheca. Non tutti i tipi di risposta però portano l'URL.</li>
     *   <li><b>Provenienza dei pin</b>: ogni pin dichiara la bacheca in cui è salvato. Se
     *       praticamente tutti i pin di una risposta appartengono alla stessa bacheca, quella
     *       è la bacheca aperta — sul feed principale i pin arrivano invece da bacheche diverse.</li>
     * </ol>
     *
     * I pin vengono comunque raggruppati per bacheca: anche se nessuno dei due segnali
     * identifica la schermata corrente, i dati sono già lì.
     */
    static void captureFeed(Object feedPage) {
        try {
            List<?> items = firstNonEmptyList(feedPage);
            if (items == null || items.isEmpty()) {
                return;
            }

            String urlBoardId = boardIdOf(feedPage);
            Map<String, Integer> perBoard = new LinkedHashMap<>();
            int captured = 0;

            for (Object item : items) {
                CapturedPin pin = capture(item);
                if (pin == null || pin.boardId == null) {
                    continue;
                }
                captured++;
                Integer previous = perBoard.get(pin.boardId);
                perBoard.put(pin.boardId, previous == null ? 1 : previous + 1);
                store(pin);
            }

            if (captured == 0) {
                return;
            }

            String detected = urlBoardId != null ? urlBoardId : dominantBoard(perBoard, captured);
            if (detected != null) {
                currentBoardId = detected;
                int total;
                synchronized (BOARDS) {
                    LinkedHashMap<String, CapturedPin> board = BOARDS.get(detected);
                    total = board == null ? 0 : board.size();
                }
                MorpheLog.i(MorpheLog.BOARD, "current board " + detected + " (detected from "
                        + (urlBoardId != null ? "URL" : "where the pins come from") + "): "
                        + total + " pins in memory");
            } else {
                MorpheLog.d(MorpheLog.BOARD, "response spans " + perBoard.size()
                        + " different boards: this is not a board screen");
            }
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.BOARD, "could not capture the pins of the board", t);
        }
    }

    /**
     * @return la bacheca a cui appartiene la quasi totalità dei pin della risposta, o null se
     *     provengono da bacheche diverse (tipico del feed principale e della ricerca).
     *
     * <p>Una risposta i cui pin vengono <em>tutti</em> dalla stessa bacheca viene accettata a
     * prescindere da quanti sono: le bacheche piccole esistono, e una soglia minima di pin le
     * escludeva (una bacheca da 3 pin non veniva mai riconosciuta). Il rischio di un falso
     * positivo sul feed principale è ininfluente: la voce di download compare solo quando il
     * menu dichiara di essere stato aperto da una bacheca, non in base a questo valore.
     */
    private static String dominantBoard(Map<String, Integer> perBoard, int total) {
        if (perBoard.size() == 1) {
            return perBoard.keySet().iterator().next();
        }
        for (Map.Entry<String, Integer> entry : perBoard.entrySet()) {
            if (entry.getValue() * 10 >= total * 9) { // >= 90%
                return entry.getKey();
            }
        }
        return null;
    }

    private static void store(CapturedPin pin) {
        LinkedHashMap<String, CapturedPin> board;
        synchronized (BOARDS) {
            board = BOARDS.get(pin.boardId);
            if (board == null) {
                board = new LinkedHashMap<>();
                BOARDS.put(pin.boardId, board);
                evictOldest();
            }
        }
        synchronized (board) {
            board.put(pin.id, pin);
        }
    }

    /** Fa spazio togliendo le bacheche più vecchie, saltando quella corrente. */
    private static void evictOldest() {
        String protectedBoard = currentBoardId;
        java.util.Iterator<String> iterator = BOARDS.keySet().iterator();
        while (BOARDS.size() > MAX_BOARDS && iterator.hasNext()) {
            String candidate = iterator.next();
            if (candidate.equals(protectedBoard)) {
                continue;
            }
            iterator.remove();
        }
    }

    /** @return l'id della bacheca se questa risposta arriva da `boards/<id>/pins/`. */
    private static String boardIdOf(Object feedPage) throws IllegalAccessException {
        Class<?> clazz = feedPage.getClass();
        while (clazz != null && !clazz.getName().startsWith("java.")) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(feedPage);
                if (!(value instanceof String)) {
                    continue;
                }
                Matcher matcher = BOARD_FEED_URL.matcher((String) value);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static List<?> firstNonEmptyList(Object feedPage) throws IllegalAccessException {
        Class<?> clazz = feedPage.getClass();
        while (clazz != null && !clazz.getName().startsWith("java.")) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(feedPage);
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    return (List<?>) value;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /** @return il pin ridotto all'essenziale, o null se l'oggetto non è un pin scaricabile. */
    private static CapturedPin capture(Object item) {
        if (item == null) {
            return null;
        }
        Object id = AdDetector.fieldValueBySerializedName(item, "id");
        if (!(id instanceof String) || ((String) id).isEmpty()) {
            return null;
        }

        CapturedPin pin = new CapturedPin();
        pin.id = (String) id;
        pin.imageUrl = bestImageUrl(item);

        // Ogni pin dichiara la bacheca in cui è salvato: è così che i pin vengono raggruppati,
        // senza dover dipendere dall'URL della richiesta.
        Object board = AdDetector.fieldValueBySerializedName(item, "board");
        if (board != null) {
            Object boardId = AdDetector.fieldValueBySerializedName(board, "id");
            Object boardName = AdDetector.fieldValueBySerializedName(board, "name");
            pin.boardId = boardId instanceof String ? (String) boardId : null;
            pin.boardName = boardName instanceof String ? (String) boardName : null;
        }

        Map<?, ?> tracks = PinterestReflection.findVideoTracks(item);
        if (tracks != null && !tracks.isEmpty()) {
            pin.videoUrl = VideoDownloadHandler.bestMp4Url(tracks);
            pin.videoOnlyStreaming = pin.videoUrl == null;
        }

        if (pin.imageUrl == null && pin.videoUrl == null && !pin.videoOnlyStreaming) {
            return null;
        }
        return pin;
    }

    /**
     * @return l'URL dell'immagine alla risoluzione più alta disponibile.
     *
     * <p>Il campo `images` del Pin è una mappa "dimensione -> oggetto immagine" (`236x`,
     * `474x`, `736x`, `orig`…). Si sceglie per larghezza dichiarata, non per nome della chiave:
     * i nomi cambiano, il campo `width` no.
     */
    private static String bestImageUrl(Object pin) {
        Object images = AdDetector.fieldValueBySerializedName(pin, "images");
        if (!(images instanceof Map)) {
            return null;
        }
        String bestUrl = null;
        double bestWidth = -1;
        for (Object image : ((Map<?, ?>) images).values()) {
            Object url = AdDetector.fieldValueBySerializedName(image, "url");
            if (!(url instanceof String) || ((String) url).isEmpty()) {
                continue;
            }
            Object width = AdDetector.fieldValueBySerializedName(image, "width");
            double w = width instanceof Number ? ((Number) width).doubleValue() : 0;
            if (w > bestWidth) {
                bestWidth = w;
                bestUrl = (String) url;
            }
        }
        return bestUrl;
    }

    // ------------------------------------------------------------------ superficie corrente

    /**
     * @return true se il menu contestuale aperto appartiene a una bacheca.
     *
     * <p>Il menu del pin porta con sé un enum che dice da quale schermata è stato aperto
     * (HOMEFEED, SEARCH, BOARD, BOARD_SECTION…). I nomi delle costanti sopravvivono
     * all'offuscamento, quindi si cercano quelli invece del nome della classe.
     */
    static boolean isBoardSurface(Object menuContainer) {
        if (menuContainer == null) {
            return false;
        }
        try {
            Class<?> clazz = menuContainer.getClass();
            while (clazz != null && !clazz.getName().startsWith("android.")) {
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    if (!field.getType().isEnum()) {
                        continue;
                    }
                    Object[] constants = field.getType().getEnumConstants();
                    if (!looksLikeSurfaceEnum(constants)) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(menuContainer);
                    if (!(value instanceof Enum)) {
                        continue;
                    }
                    String name = ((Enum<?>) value).name();
                    MorpheLog.d(MorpheLog.BOARD, "menu opened from surface " + name);
                    return name.startsWith("BOARD");
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.BOARD, "could not determine which surface the menu belongs to", t);
        }
        return false;
    }

    private static boolean looksLikeSurfaceEnum(Object[] constants) {
        if (constants == null || constants.length < 5) {
            return false;
        }
        boolean hasHomefeed = false;
        boolean hasBoard = false;
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            hasHomefeed |= "HOMEFEED".equals(name);
            hasBoard |= "BOARD".equals(name);
        }
        return hasHomefeed && hasBoard;
    }

    /** @return quanti pin della bacheca corrente sono in memoria (0 se nessuna). */
    static int currentBoardPinCount() {
        String boardId = currentBoardId;
        if (boardId == null) {
            return 0;
        }
        synchronized (BOARDS) {
            LinkedHashMap<String, CapturedPin> board = BOARDS.get(boardId);
            return board == null ? 0 : board.size();
        }
    }

    // ------------------------------------------------------------------ download

    /** Avvia il download di tutti i pin catturati per la bacheca corrente. */
    static void downloadCurrentBoard(final Context context) {
        final String boardId = currentBoardId;
        if (boardId == null) {
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("board_no_pins"));
            MorpheLog.w(MorpheLog.BOARD, "no board in memory");
            return;
        }

        final List<CapturedPin> pins;
        synchronized (BOARDS) {
            Map<String, CapturedPin> board = BOARDS.get(boardId);
            if (board == null || board.isEmpty()) {
                // "Non presente" e "presente ma vuota" hanno cause diverse: la prima è uno
                // sfratto, la seconda una bacheca di cui non è ancora arrivato nessun pin.
                MorpheLog.w(MorpheLog.BOARD, "board " + boardId
                        + (board == null ? " not in memory" : " in memory but empty")
                        + " (boards in memory: " + BOARDS.size() + ")");
                PinterestUtils.showNativeToast(context, PinterestUtils.getString("board_no_pins"));
                return;
            }
            pins = new ArrayList<>(board.values());
        }
        MorpheLog.i(MorpheLog.BOARD, "starting download of " + pins.size()
                + " pins of board " + boardId);

        // Nome leggibile della bacheca per la cartella di destinazione; se manca si ripiega
        // sull'id, che c'è sempre.
        String name = null;
        for (CapturedPin pin : pins) {
            if (pin.boardName != null && !pin.boardName.isEmpty()) {
                name = pin.boardName;
                break;
            }
        }
        final String folder = name != null ? name : boardId;

        MorpheLog.i(MorpheLog.BOARD, "starting download of " + pins.size()
                + " pins from board \"" + folder + "\" (" + boardId + ")");
        PinterestUtils.showNativeToast(context,
                PinterestUtils.getString("board_download_started").replace("%d", String.valueOf(pins.size())));

        // Il download vero e proprio è delegato a DownloadManager (che lavora in background),
        // ma le chiamate di accodamento vengono fatte fuori dal main thread: con bacheche
        // grandi sono centinaia di IPC e bloccherebbero la UI.
        new Thread(new Runnable() {
            @Override
            public void run() {
                int images = 0;
                int videos = 0;
                int streamingSkipped = 0;
                int failed = 0;

                DownloadManager manager =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) {
                    MorpheLog.e(MorpheLog.BOARD, "DownloadManager not available");
                    return;
                }

                for (CapturedPin pin : pins) {
                    try {
                        if (pin.videoUrl != null) {
                            enqueue(manager, pin.videoUrl, folder, pin.id + ".mp4");
                            videos++;
                        } else if (pin.videoOnlyStreaming) {
                            // Video solo in streaming: DownloadManager non sa gestire un
                            // manifest HLS/DASH. Lo si conta e lo si segnala alla fine.
                            streamingSkipped++;
                            MorpheLog.d(MorpheLog.BOARD,
                                    "pin " + pin.id + ": streaming only, skipped");
                        } else if (pin.imageUrl != null) {
                            enqueue(manager, pin.imageUrl, folder, pin.id + extensionOf(pin.imageUrl));
                            images++;
                        }
                    } catch (Throwable t) {
                        failed++;
                        MorpheLog.w(MorpheLog.BOARD, "download of pin " + pin.id + " failed", t);
                    }
                }

                final String summary = buildSummary(images, videos, streamingSkipped, failed);
                MorpheLog.ok(MorpheLog.BOARD, summary);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        PinterestUtils.showNativeToast(context, summary);
                    }
                });
            }
        }, "morphe-board-download").start();
    }

    private static void enqueue(DownloadManager manager, String url, String folder, String name) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(name);
        request.setDescription("Pinterest — " + folder);
        request.setMimeType(name.endsWith(".mp4") ? "video/mp4" : "image/jpeg");

        // VISIBILE SOLO DURANTE il download, non a fine download: scaricando una bacheca
        // intera si parla di centinaia di file, e una notifica di completamento per ciascuno
        // sommergerebbe la tendina.
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        // Una sottocartella per bacheca, così i file non si mescolano fra loro né con il
        // resto dei download.
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, "Pinterest/" + safe(folder) + "/" + safe(name));
        manager.enqueue(request);
    }

    private static String extensionOf(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash && dot < path.length() - 1 && path.length() - dot <= 6) {
            return path.substring(dot);
        }
        return ".jpg";
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String buildSummary(int images, int videos, int streamingSkipped, int failed) {
        StringBuilder sb = new StringBuilder();
        sb.append(PinterestUtils.getString("board_download_done")
                .replace("%1", String.valueOf(images))
                .replace("%2", String.valueOf(videos)));
        if (streamingSkipped > 0) {
            sb.append(' ').append(PinterestUtils.getString("board_download_skipped")
                    .replace("%d", String.valueOf(streamingSkipped)));
        }
        if (failed > 0) {
            sb.append(' ').append(PinterestUtils.getString("board_download_failed")
                    .replace("%d", String.valueOf(failed)));
        }
        return sb.toString();
    }

    /** Solo per diagnostica. */
    static String describe() {
        synchronized (BOARDS) {
            if (BOARDS.isEmpty()) {
                return "no board visited";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, LinkedHashMap<String, CapturedPin>> entry : BOARDS.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue().size()).append(" pins; ");
            }
            return sb.toString().trim();
        }
    }
}
