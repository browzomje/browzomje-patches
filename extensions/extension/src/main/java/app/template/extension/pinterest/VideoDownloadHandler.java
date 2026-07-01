package app.browzomje.extension.pinterest;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
class VideoDownloadHandler {
    private static final String TAG = "MorpheVideoDownload";

    /** Pin UID → direct MP4 URL */
    private static final Map<String, String> VIDEO_URL_BY_PIN = new ConcurrentHashMap<String, String>();

    /** Pin UID → HLS/DASH streaming URL (fallback for yt-dlp) */
    private static final Map<String, String> VIDEO_STREAMING_URL_BY_PIN = new ConcurrentHashMap<String, String>();

    /**
     * Pin UID → URL pagina del pin (https://www.pinterest.com/pin/<uid>/).
     * Popolata per OGNI pin osservato, anche quando non riusciamo a estrarre un
     * URL media. È la rete di sicurezza per yt-dlp quando l'estrazione diretta
     * fallisce (offuscamento Pinterest cambiato): l'abbinamento avviene SEMPRE
     * per UID del menu selezionato — mai a livello globale — così non passiamo
     * mai a yt-dlp il pin sbagliato.
     */
    private static final Map<String, String> PIN_PAGE_URL_BY_PIN = new ConcurrentHashMap<String, String>();

    /**
     * Hash cover immagine (i.pinimg.com/.../<hash>.<ext>) → URL pagina del pin.
     * La cover e il video hanno hash diversi, ma la cover identifica il pin in
     * modo univoco. currentPinImageUrl (impostato per il pin APERTO/selezionato,
     * come per "copia link") è il segnale più affidabile del pin scelto: quando
     * il match per UID sui campi del menu fallisce o è ambiguo, risaliamo al pin
     * corretto tramite l'hash della sua cover.
     */
    private static final Map<String, String> IMAGE_HASH_TO_PIN_URL = new ConcurrentHashMap<String, String>();

    // -------------------------------------------------------------------------
    // Tracking hooks (called by the patch bytecode)
    // -------------------------------------------------------------------------

    public static void setCurrentVideoTracks(String uid, Map<?, ?> videoList) {
        if (uid != null && !uid.isEmpty()) {
            PIN_PAGE_URL_BY_PIN.put(uid, "https://www.pinterest.com/pin/" + uid + "/");
        }
        if (uid == null || uid.isEmpty() || videoList == null || videoList.isEmpty()) {
            return;
        }
        try {
            String mp4 = pickBestMp4Url(videoList);
            if (mp4 != null) {
                VIDEO_URL_BY_PIN.put(uid, mp4);
                Log.d(TAG, "MP4 associato al Pin (via tracks) " + uid);
            } else {
                // No direct MP4 — capture a streaming URL as fallback for yt-dlp.
                String streaming = pickStreamingUrl(videoList);
                if (streaming != null) {
                    VIDEO_STREAMING_URL_BY_PIN.put(uid, streaming);
                    Log.d(TAG, "Streaming URL (HLS/DASH) associato al Pin " + uid);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Estrazione URL dalle tracce fallita", t);
        }
    }

    public static void setCurrentVideoPin(Object pin) {
        if (pin == null) {
            return;
        }
        try {
            String uid = (String) PinterestUtils.invokeNoArg(pin, "d");
            if (uid == null || uid.isEmpty()) {
                uid = (String) PinterestUtils.invokeNoArg(pin, "getSnapshotUid");
            }
            if (uid == null || uid.isEmpty()) {
                uid = (String) PinterestUtils.invokeNoArg(pin, "getProfileSnapshotUid");
            }
            if (uid == null || uid.isEmpty()) {
                return;
            }
            final String pinPageUrl = "https://www.pinterest.com/pin/" + uid + "/";
            PIN_PAGE_URL_BY_PIN.put(uid, pinPageUrl);

            // Correla l'hash della cover al pin, così possiamo ritrovare il pin
            // selezionato da currentPinImageUrl quando il match per UID fallisce.
            String coverHash = findPinImageHash(pin, 0,
                    Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
            if (coverHash != null) {
                IMAGE_HASH_TO_PIN_URL.put(coverHash, pinPageUrl);
            }

            Object videos = PinterestUtils.invokeNoArg(pin, "v7");
            Object listObj = videos != null ? PinterestUtils.invokeNoArg(videos, "g") : null;
            if (listObj instanceof Map) {
                setCurrentVideoTracks(uid, (Map<?, ?>) listObj);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Estrazione URL dal Pin fallita", t);
        }
    }

    // -------------------------------------------------------------------------
    // Menu option injection
    // -------------------------------------------------------------------------

    public static void addDownloadVideoOption(final Object menuContainer) {
        if (!(menuContainer instanceof ViewGroup)) {
            Log.w(TAG, "menuContainer non è un ViewGroup: " + menuContainer);
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            final String labelText = PinterestUtils.getString("download_video_label");
            final View.OnClickListener dummyListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {}
            };

            final View[] rowHolder = new View[1];
            try {
                rowHolder[0] = PinterestUtils.buildRowReflective(container, labelText, "ARROW_DOWN", dummyListener);
            } catch (Throwable t) {
                rowHolder[0] = PinterestUtils.buildRowFallback(context, labelText, container, android.R.drawable.ic_menu_save, dummyListener);
            }

            if (rowHolder[0] == null) {
                return;
            }

            final View row = rowHolder[0];
            row.setEnabled(false);
            row.setAlpha(0.5f);
            container.addView(row);

            final ImageView icon = PinterestUtils.findImageView(row);
            final ViewGroup parent = icon != null ? (ViewGroup) icon.getParent() : null;
            final int iconIndex = parent != null ? parent.indexOfChild(icon) : -1;
            final android.widget.ProgressBar progressBar;
            final boolean isSafeToSwap = parent != null && !parent.getClass().getName().contains("gestalt");

            if (isSafeToSwap && iconIndex != -1) {
                progressBar = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
                ViewGroup.LayoutParams lp = icon.getLayoutParams();
                if (lp != null) {
                    progressBar.setLayoutParams(lp);
                }
                parent.removeView(icon);
                parent.addView(progressBar, iconIndex);
            } else {
                progressBar = null;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final VideoResolutionResult resResult = resolveVideoUrlWithDiagnostics(menuContainer);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            // Restore icon from spinner (shared by both success paths).
                            if (isSafeToSwap && iconIndex != -1 && progressBar != null) {
                                parent.removeView(progressBar);
                                if (icon.getParent() == null) {
                                    parent.addView(icon, iconIndex);
                                }
                            }

                            // Il tasto resta SEMPRE visibile e azionabile.
                            row.setEnabled(true);
                            row.setAlpha(1.0f);

                            if (resResult.videoUrl != null && !resResult.videoUrl.isEmpty()) {
                                // ── MP4 diretto: download via DownloadManager ─────
                                row.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        PinterestUtils.dismissMenu();
                                        downloadVideo(v.getContext(), resResult.videoUrl);
                                    }
                                });

                            } else if (resResult.streamingUrl != null
                                    && !resResult.streamingUrl.isEmpty()) {
                                // ── Tutto il resto (HLS/DASH o estrazione fallita):
                                //    delega all'app esterna yt-dlp ──────────────────
                                TextView tvLabel = PinterestUtils.findTextView(row);
                                if (tvLabel != null) {
                                    tvLabel.setText(PinterestUtils.getString("download_video_external_label"));
                                }
                                row.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        PinterestUtils.dismissMenu();
                                        shareLinkToDownloader(v.getContext(), resResult.streamingUrl);
                                    }
                                });

                            } else {
                                // ── Nessun URL e nessun UID: tasto comunque presente.
                                //    Al click mostra la diagnostica, senza sparire. ──
                                TextView tvLabel = PinterestUtils.findTextView(row);
                                if (tvLabel != null) {
                                    tvLabel.setText(PinterestUtils.getString("download_video_external_label"));
                                }
                                final String diag = resResult.errorMsg;
                                row.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        PinterestUtils.dismissMenu();
                                        if (diag != null && !diag.isEmpty()) {
                                            PinterestUtils.showNativeToast(v.getContext(), diag);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }).start();

        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce scarica video", t);
        }
    }

    // -------------------------------------------------------------------------
    // Download helpers
    // -------------------------------------------------------------------------

    private static void downloadVideo(Context context, String url) {
        if (url == null || url.isEmpty()) {
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("no_video"));
            return;
        }
        try {
            android.app.DownloadManager dm =
                (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
                return;
            }
            String fileName = "pinterest_" + System.currentTimeMillis() + ".mp4";
            android.app.DownloadManager.Request request =
                new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("Pinterest");
            request.setMimeType("video/mp4");
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36");
            dm.enqueue(request);
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("video_download_started"));
        } catch (Throwable t) {
            Log.e(TAG, "Download video fallito", t);
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
        }
    }

    /**
     * Fires an Android share-sheet with the streaming URL so the user can
     * choose Seal / PowerTube / any yt-dlp-backed app to handle the download.
     */
    private static void shareLinkToDownloader(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            context.startActivity(Intent.createChooser(intent,
                PinterestUtils.getString("download_video_external_label")));
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aprire il selettore downloader", t);
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
        }
    }

    // -------------------------------------------------------------------------
    // Resolution logic
    // -------------------------------------------------------------------------

    private static String resolveVideoUrlForMenu(Object menu) {
        if (menu == null || VIDEO_URL_BY_PIN.isEmpty()) {
            return null;
        }
        try {
            Class<?> clazz = menu.getClass();
            while (clazz != null) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        f.setAccessible(true);
                        Object value = f.get(menu);
                        if (value instanceof String) {
                            String mp4 = VIDEO_URL_BY_PIN.get((String) value);
                            if (mp4 != null) {
                                return mp4;
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Risoluzione URL video per il menu fallita", t);
        }
        return null;
    }

    /** Like resolveVideoUrlForMenu but looks in the streaming map. */
    private static String resolveStreamingUrlForMenu(Object menu) {
        if (menu == null || VIDEO_STREAMING_URL_BY_PIN.isEmpty()) {
            return null;
        }
        try {
            Class<?> clazz = menu.getClass();
            while (clazz != null) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        f.setAccessible(true);
                        Object value = f.get(menu);
                        if (value instanceof String) {
                            String url = VIDEO_STREAMING_URL_BY_PIN.get((String) value);
                            if (url != null) {
                                return url;
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Risoluzione streaming URL per il menu fallita", t);
        }
        return null;
    }

    /**
     * Come resolveVideoUrlForMenu, ma cerca nella mappa degli URL-pagina del pin.
     * Abbina l'UID confrontando i campi String del menu con le chiavi tracciate:
     * restituisce SOLO l'URL del pin a cui il menu appartiene davvero. yt-dlp
     * supporta nativamente pinterest.com/pin/<id>/ e risolve da sé MP4/HLS/DASH,
     * quindi è la rete di sicurezza quando la reflection sui model offuscati di
     * Pinterest non riesce a estrarre l'URL media. Se nessun UID del menu è noto,
     * restituisce null: meglio nessun download che il download di un pin sbagliato.
     */
    private static String resolvePinPageUrlForMenu(Object menu) {
        if (menu == null || PIN_PAGE_URL_BY_PIN.isEmpty()) {
            return null;
        }
        try {
            Class<?> clazz = menu.getClass();
            while (clazz != null) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        f.setAccessible(true);
                        Object value = f.get(menu);
                        if (value instanceof String) {
                            String url = PIN_PAGE_URL_BY_PIN.get((String) value);
                            if (url != null) {
                                return url;
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Risoluzione URL pagina pin per il menu fallita", t);
        }
        return null;
    }

    /**
     * Estrae l'hash della cover da un URL i.pinimg.com, indipendente dalla
     * dimensione (webp85/564x, 736x, originals…): è il nome-file di 32 cifre
     * esadecimali senza estensione. Ritorna null se non è un'immagine Pinterest.
     * Es: https://i.pinimg.com/736x/96/37/10/963710e4c3882176805c97acb6b8555b.jpg
     *  →  963710e4c3882176805c97acb6b8555b
     */
    private static String extractPinterestImageHash(String url) {
        if (url == null || !url.contains("pinimg.com")) {
            return null;
        }
        String path = url;
        int q = path.indexOf('?');
        if (q != -1) path = path.substring(0, q);
        int slash = path.lastIndexOf('/');
        if (slash == -1 || slash == path.length() - 1) return null;
        String file = path.substring(slash + 1);
        int dot = file.indexOf('.');
        String name = dot != -1 ? file.substring(0, dot) : file;
        // Hash Pinterest = 32 esadecimali. Evita di mappare segmenti spuri.
        if (name.length() == 32 && name.matches("[0-9a-fA-F]{32}")) {
            return name.toLowerCase();
        }
        return null;
    }

    /** Scansione superficiale del Pin (campi String e Map) per la cover. */
    private static String findPinImageHash(Object obj, int depth, Set<Object> seen) {
        if (obj == null || depth > 3 || !seen.add(obj) || obj instanceof android.view.View) {
            return null;
        }
        Class<?> cls = obj.getClass();
        String cn = cls.getName();
        if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("kotlin.")
                || cn.startsWith("androidx.")) {
            return null;
        }
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof String) {
                        String h = extractPinterestImageHash((String) val);
                        if (h != null) return h;
                    } else if (val instanceof Map) {
                        for (Object v : ((Map<?, ?>) val).values()) {
                            if (v instanceof String) {
                                String h = extractPinterestImageHash((String) v);
                                if (h != null) return h;
                            }
                        }
                    } else {
                        String fn = val.getClass().getName();
                        if (fn.startsWith("java.") || fn.startsWith("android.")
                                || fn.startsWith("kotlin.") || fn.startsWith("androidx.")) {
                            continue;
                        }
                        String h = findPinImageHash(val, depth + 1, seen);
                        if (h != null) return h;
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Risale al pin SELEZIONATO tramite l'hash della cover corrente. È il
     * percorso più affidabile perché currentPinImageUrl è impostato apposta per
     * il pin aperto, mentre il match per UID sui campi del menu può abbinare un
     * pin riciclato/correlato diverso.
     */
    private static String resolvePinPageUrlByImage() {
        String img = PinterestUtils.currentPinImageUrl;
        String hash = extractPinterestImageHash(img);
        if (hash == null) return null;
        return IMAGE_HASH_TO_PIN_URL.get(hash);
    }

    private static VideoResolutionResult resolveVideoUrlWithDiagnostics(final Object menuContainer) {
        VideoResolutionResult result = new VideoResolutionResult();
        DiagnosticScanner scanner = new DiagnosticScanner();

        // 1. Deep scan of the menu object graph.
        String rawUrl = scanner.scan(menuContainer, 0,
                Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));

        // 2. Fall back to pre-tracked MP4 map.
        if (rawUrl == null || rawUrl.isEmpty()) {
            rawUrl = resolveVideoUrlForMenu(menuContainer);
        }

        // 3. MP4 diretto trovato → download diretto (percorso preferito).
        if (rawUrl != null && !rawUrl.isEmpty() && !isStreamingUrl(rawUrl)) {
            result.videoUrl = rawUrl;
            return result;
        }

        // 4. Qualsiasi altro caso → yt-dlp. Scegli il miglior URL disponibile, in
        //    ordine DI AFFIDABILITÀ DEL PIN (non solo di "diretto"), così il tasto
        //    è sempre azionabile e non scarichiamo mai il pin sbagliato:
        //      a) URL streaming preso dal grafo del menu stesso (è del pin giusto);
        //      b) URL pagina del pin via hash cover di currentPinImageUrl
        //         (pin APERTO/selezionato → segnale più affidabile);
        //      c) match per UID sui campi del menu (streaming, poi pagina pin) —
        //         meno affidabile: il menu può contenere pin riciclati/correlati.
        //    yt-dlp risolve da sé MP4/HLS/DASH dall'URL pagina del pin: copre il
        //    caso "duplo-hls-video" in cui il manifest non è nei metadati.
        String ytDlpUrl = null;
        if (rawUrl != null && !rawUrl.isEmpty() && isStreamingUrl(rawUrl)) {
            ytDlpUrl = rawUrl;                       // (a) stream dal grafo menu
        }
        if (ytDlpUrl == null) {
            ytDlpUrl = scanner.streamingUrl;          // (a) stream dal grafo menu
        }
        if (ytDlpUrl == null || ytDlpUrl.isEmpty()) {
            ytDlpUrl = resolvePinPageUrlByImage();    // (b) pin selezionato via cover
        }
        if (ytDlpUrl == null || ytDlpUrl.isEmpty()) {
            ytDlpUrl = resolveStreamingUrlForMenu(menuContainer);   // (c) UID match
        }
        if (ytDlpUrl == null || ytDlpUrl.isEmpty()) {
            ytDlpUrl = resolvePinPageUrlForMenu(menuContainer);     // (c) UID match
        }

        if (ytDlpUrl != null && !ytDlpUrl.isEmpty()) {
            result.isStreaming = true;
            result.streamingUrl = ytDlpUrl;
            return result;
        }

        // 5. Davvero nessun URL e nessun UID: il tasto resta comunque visibile
        //    (vedi addDownloadVideoOption) e al click mostra una diagnostica.
        if (scanner.hasHlsOrDash) {
            result.errorMsg = PinterestUtils.getLocalizedError("video_err_hls");
        } else if (scanner.hasVideoField) {
            result.errorMsg = PinterestUtils.getLocalizedError("video_err_no_mp4");
        } else if (!VIDEO_URL_BY_PIN.isEmpty() || !VIDEO_STREAMING_URL_BY_PIN.isEmpty()) {
            result.errorMsg = PinterestUtils.getLocalizedError("video_err_no_tracking");
        } else {
            result.errorMsg = PinterestUtils.getLocalizedError("video_err_image_post");
        }
        return result;
    }

    /**
     * Returns true only for manifest-based streaming formats.
     * We intentionally do NOT match "/hls/" or "/dash/" path segments because
     * Pinterest CDNs often serve direct MP4 files under those path prefixes
     * (e.g. .../hls/720p/video.mp4), which must go through DownloadManager.
     */
    private static boolean isStreamingUrl(String url) {
        // Strip query string before checking the extension.
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return path.endsWith(".m3u8") || path.endsWith(".mpd");
    }

    // -------------------------------------------------------------------------
    // Scanning utilities
    // -------------------------------------------------------------------------

    private static String scanForMp4(Object obj, int depth, Set<Object> seen) {
        if (obj == null || depth > 6 || !seen.add(obj) || obj instanceof android.view.View) {
            return null;
        }
        Class<?> cls = obj.getClass();
        String cn = cls.getName();
        if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("kotlin.")
                || cn.startsWith("androidx.")) {
            return null;
        }
        if (obj instanceof Map) {
            String mp4 = pickBestMp4Url((Map<?, ?>) obj);
            if (mp4 != null) return mp4;
        }
        Object viaG = PinterestUtils.invokeNoArg(obj, "g");
        if (viaG instanceof Map) {
            String mp4 = pickBestMp4Url((Map<?, ?>) viaG);
            if (mp4 != null) return mp4;
        }
        Object videos = PinterestUtils.invokeNoArg(obj, "v7");
        if (videos != null) {
            Object listObj = PinterestUtils.invokeNoArg(videos, "g");
            if (listObj instanceof Map) {
                String mp4 = pickBestMp4Url((Map<?, ?>) listObj);
                if (mp4 != null) return mp4;
            }
        }
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof String) {
                        String s = (String) val;
                        if (s.startsWith("http") && s.contains(".mp4")
                                && !s.contains(".mpd") && !s.contains(".m3u8")) {
                            return s;
                        }
                    } else if (val instanceof Map) {
                        String mp4 = pickBestMp4Url((Map<?, ?>) val);
                        if (mp4 != null) return mp4;
                    } else {
                        Class<?> ft = val.getClass();
                        String fn = ft.getName();
                        if (fn.startsWith("java.") || fn.startsWith("android.")
                                || fn.startsWith("kotlin.") || fn.startsWith("androidx.")) {
                            continue;
                        }
                        String mp4 = scanForMp4(val, depth + 1, seen);
                        if (mp4 != null) return mp4;
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static String pickBestMp4Url(Map<?, ?> videoList) {
        String bestUrl = null;
        int maxRes = -1;
        for (Map.Entry<?, ?> entry : videoList.entrySet()) {
            Object spec = entry.getValue();
            if (spec == null) continue;
            String key = String.valueOf(entry.getKey()).toUpperCase();
            if (key.contains("HLS") || key.contains("DASH")) continue;

            String url = null;
            if (spec.getClass().getName().equals("com.pinterest.api.model.jq")) {
                url = (String) PinterestUtils.invokeNoArg(spec, "s");
            }
            if (url == null || url.isEmpty()) {
                Class<?> clazz = spec.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            try {
                                f.setAccessible(true);
                                Object v = f.get(spec);
                                if (v instanceof String) {
                                    String s = (String) v;
                                    if (s.startsWith("http") && s.contains(".mp4")
                                            && !s.contains(".mpd") && !s.contains(".m3u8")) {
                                        url = s;
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (url != null) break;
                    clazz = clazz.getSuperclass();
                }
            }

            if (url != null && !url.isEmpty()
                    && url.startsWith("http") && url.contains(".mp4")
                    && !url.contains(".mpd") && !url.contains(".m3u8")) {
                int res = 0;
                if (key.contains("1080")) res = 1080;
                else if (key.contains("720")) res = 720;
                else if (key.contains("480")) res = 480;
                else if (key.contains("360")) res = 360;
                else if (key.contains("240")) res = 240;
                if (res > maxRes) {
                    maxRes = res;
                    bestUrl = url;
                }
            }
        }
        return bestUrl;
    }

    /** Returns the first HLS/DASH URL found in the map when no MP4 is available. */
    private static String pickStreamingUrl(Map<?, ?> videoList) {
        for (Map.Entry<?, ?> entry : videoList.entrySet()) {
            Object spec = entry.getValue();
            if (spec == null) continue;
            // Try well-known getter first.
            Object urlObj = PinterestUtils.invokeNoArg(spec, "s");
            if (urlObj instanceof String && isStreamingUrl((String) urlObj)) {
                return (String) urlObj;
            }
            // Field scan.
            Class<?> clazz = spec.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(spec);
                            if (v instanceof String && isStreamingUrl((String) v)) {
                                return (String) v;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Result / scanner data classes
    // -------------------------------------------------------------------------

    private static class VideoResolutionResult {
        String videoUrl = null;      // direct MP4 URL  → DownloadManager
        boolean isStreaming = false; // true when only HLS/DASH available
        String streamingUrl = null;  // the HLS/DASH URL → yt-dlp via share sheet
        String errorMsg = null;      // set only when the post is definitely non-video
    }

    private static class DiagnosticScanner {
        boolean hasHlsOrDash = false;
        boolean hasVideoField = false;
        String streamingUrl = null; // first HLS/DASH URL encountered during scan

        String scan(Object obj, int depth, Set<Object> seen) {
            if (obj == null || depth > 6 || !seen.add(obj) || obj instanceof android.view.View) {
                return null;
            }
            Class<?> cls = obj.getClass();
            String cn = cls.getName();
            if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("kotlin.")
                    || cn.startsWith("androidx.")) {
                return null;
            }

            if (obj instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) obj;
                inspectMapKeys(m);
                String mp4 = pickBestMp4Url(m);
                if (mp4 != null) return mp4;
                captureStreamingUrl(m);
            }

            Object viaG = PinterestUtils.invokeNoArg(obj, "g");
            if (viaG instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) viaG;
                inspectMapKeys(m);
                String mp4 = pickBestMp4Url(m);
                if (mp4 != null) return mp4;
                captureStreamingUrl(m);
            }

            Object videos = PinterestUtils.invokeNoArg(obj, "v7");
            if (videos != null) {
                hasVideoField = true;
                Object listObj = PinterestUtils.invokeNoArg(videos, "g");
                if (listObj instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) listObj;
                    inspectMapKeys(m);
                    String mp4 = pickBestMp4Url(m);
                    if (mp4 != null) return mp4;
                    captureStreamingUrl(m);
                }
            }

            Class<?> c = cls;
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val == null) continue;
                        if (val instanceof String) {
                            String s = (String) val;
                            if (s.startsWith("http") && s.contains(".mp4")
                                    && !s.contains(".mpd") && !s.contains(".m3u8")) {
                                return s;
                            }
                            if (isStreamingUrl(s)) {
                                hasHlsOrDash = true;
                                if (streamingUrl == null) streamingUrl = s;
                            }
                        } else if (val instanceof Map) {
                            Map<?, ?> m = (Map<?, ?>) val;
                            inspectMapKeys(m);
                            String mp4 = pickBestMp4Url(m);
                            if (mp4 != null) return mp4;
                            captureStreamingUrl(m);
                        } else {
                            Class<?> ft = val.getClass();
                            String fn = ft.getName();
                            if (fn.startsWith("java.") || fn.startsWith("android.")
                                    || fn.startsWith("kotlin.") || fn.startsWith("androidx.")) {
                                continue;
                            }
                            String mp4 = scan(val, depth + 1, seen);
                            if (mp4 != null) return mp4;
                        }
                    } catch (Throwable ignored) {}
                }
                c = c.getSuperclass();
            }
            return null;
        }

        /**
         * Delegates to the static pickStreamingUrl so that streaming URLs nested
         * inside spec-objects (e.g. Pinterest's jq model) are captured even when
         * inspectMapKeys sees only non-String values.
         */
        private void captureStreamingUrl(Map<?, ?> map) {
            if (streamingUrl != null) return; // already have one
            String found = pickStreamingUrl(map);
            if (found != null) {
                streamingUrl = found;
                hasHlsOrDash = true;
            }
        }

        private void inspectMapKeys(Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String ks = String.valueOf(entry.getKey()).toUpperCase();
                if (ks.contains("HLS") || ks.contains("DASH")) {
                    hasHlsOrDash = true;
                }
                if (ks.contains("VIDEO") || ks.contains("TRACKS") || ks.contains("V7")) {
                    hasVideoField = true;
                }
                // Capture streaming URLs from map values too.
                Object v = entry.getValue();
                if (v instanceof String && isStreamingUrl((String) v) && streamingUrl == null) {
                    streamingUrl = (String) v;
                }
            }
        }
    }
}
