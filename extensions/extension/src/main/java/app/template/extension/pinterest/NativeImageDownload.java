package app.browzomje.extension.pinterest;

import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Bridge native overflow/share downloads to the same downloader, keyed by the exact Pin ID. */
public final class NativeImageDownload {
    private static final Map<String, WeakReference<Object>> PINS = new LinkedHashMap<>();
    private static final Map<Object, String[]> REQUESTS = new WeakHashMap<>();
    private NativeImageDownload() {}

    static synchronized void remember(Object pin) {
        if (pin == null) return;
        Object id = AdDetector.fieldValueBySerializedName(pin, "id");
        if (!(id instanceof String) || !((String) id).matches("[0-9]+")) return;
        PINS.put((String) id, new WeakReference<>(pin));
        while (PINS.size() > 16) PINS.remove(PINS.keySet().iterator().next());
    }

    public static synchronized void capture(Object handler, String id, String url) {
        if (id != null && id.matches("[0-9]+")) REQUESTS.put(handler, new String[] { id, url });
    }

    public static boolean intercept(Object handler, Object context, Object progress, Object completion) {
        if (android.os.Build.VERSION.SDK_INT < 29 || !(context instanceof Context)) return false;
        String[] request;
        Object pin;
        synchronized (NativeImageDownload.class) {
            request = REQUESTS.get(handler);
            if (request == null) return false;
            WeakReference<Object> reference = PINS.get(request[0]);
            pin = reference == null ? null : reference.get();
        }
        if (Boolean.TRUE.equals(AdDetector.fieldValueBySerializedName(pin, "is_video"))) return false;

        Object image = PinImageMenu.bestImage(pin);
        String primary = (String) AdDetector.fieldValueBySerializedName(image, "url");
        String url = request[1];
        int width = 0;
        int height = 0;
        // Some overflow paths omit the page URL or pass the cover image. Preserve a
        // distinct explicit URL, for example from visual search on a different slide.
        if (url == null || url.isEmpty() || sameImage(url, primary)) {
            String selected = VisibleCarousel.selectedImage((Context) context, request[0]);
            if (selected != null) {
                url = selected;
                MorpheLog.d(MorpheLog.BOARD, "download uses the visible carousel page");
            } else if (url == null || url.isEmpty()) {
                url = primary;
                Object imageWidth = AdDetector.fieldValueBySerializedName(image, "width");
                Object imageHeight = AdDetector.fieldValueBySerializedName(image, "height");
                if (imageWidth instanceof Number) width = ((Number) imageWidth).intValue();
                if (imageHeight instanceof Number) height = ((Number) imageHeight).intValue();
            }
        }
        if (url == null || !url.startsWith("https://i.pinimg.com/")) return false;
        PinImageDownloader.download((Context) context, request[0], url, width, height, () -> {
                    if (completion == null) return;
                    try {
                        java.lang.reflect.Method callback = completion.getClass().getMethod("invoke");
                        callback.setAccessible(true);
                        callback.invoke(completion);
                    } catch (Exception failure) {
                        MorpheLog.d(MorpheLog.BOARD, "native download completion callback unavailable");
                    }
                });
        MorpheLog.i(MorpheLog.BOARD, "native image request handled");
        return true;
    }

    private static boolean sameImage(String first, String second) {
        if (second == null || !first.startsWith("https://i.pinimg.com/")
                || !second.startsWith("https://i.pinimg.com/")) return false;
        String firstPath = android.net.Uri.parse(first).getLastPathSegment();
        String secondPath = android.net.Uri.parse(second).getLastPathSegment();
        return firstPath != null && firstPath.equals(secondPath);
    }

}
