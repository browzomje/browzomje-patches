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

        // Pinterest passes the displayed image here, including the selected carousel page.
        // Only use the Pin's primary image when the caller did not specify an image.
        String url = request[1];
        int width = 0;
        int height = 0;
        if (url == null || url.isEmpty()) {
            Object image = PinImageMenu.bestImage(pin);
            if (image == null) return false;
            url = (String) AdDetector.fieldValueBySerializedName(image, "url");
            Object imageWidth = AdDetector.fieldValueBySerializedName(image, "width");
            Object imageHeight = AdDetector.fieldValueBySerializedName(image, "height");
            if (imageWidth instanceof Number) width = ((Number) imageWidth).intValue();
            if (imageHeight instanceof Number) height = ((Number) imageHeight).intValue();
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
}
