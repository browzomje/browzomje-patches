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
        // No last-seen-pin fallback: preserve Pinterest's own path if this exact Pin is unavailable.
        if (pin == null || PinImageMenu.bestImage(pin) == null) return false;
        if (Boolean.TRUE.equals(AdDetector.fieldValueBySerializedName(pin, "is_video"))) return false;
        Object image = PinImageMenu.bestImage(pin);
        Object url = AdDetector.fieldValueBySerializedName(image, "url");
        Object width = AdDetector.fieldValueBySerializedName(image, "width");
        Object height = AdDetector.fieldValueBySerializedName(image, "height");
        if (!(url instanceof String)) return false;
        PinImageDownloader.download((Context) context, request[0], (String) url,
                width instanceof Number ? ((Number) width).intValue() : 0,
                height instanceof Number ? ((Number) height).intValue() : 0, () -> {
                    if (completion == null) return;
                    try {
                        java.lang.reflect.Method callback = completion.getClass().getMethod("invoke");
                        callback.setAccessible(true);
                        callback.invoke(completion);
                    } catch (Exception failure) {
                        MorpheLog.d(MorpheLog.BOARD, "native download completion callback unavailable");
                    }
                });
        MorpheLog.i(MorpheLog.BOARD, "native image pipeline handled for exact Pin " + request[0]);
        return true;
    }
}
