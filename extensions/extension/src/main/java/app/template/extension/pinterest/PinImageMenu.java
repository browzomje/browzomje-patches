package app.browzomje.extension.pinterest;

import java.util.Locale;
import java.util.Map;

/** Helpers for image variants on a Pin model. */
final class PinImageMenu {
    private PinImageMenu() {}

    static Object bestImage(Object pin) {
        if (pin == null) return null;
        Object images = AdDetector.fieldValueBySerializedName(pin, "images");
        if (!(images instanceof Map)) return null;
        Object best = null;
        double width = -1;
        for (Object image : ((Map<?, ?>) images).values()) {
            Object url = AdDetector.fieldValueBySerializedName(image, "url");
            Object candidateWidth = AdDetector.fieldValueBySerializedName(image, "width");
            if (!(url instanceof String) || !((String) url).startsWith("https://")) continue;
            double value = candidateWidth instanceof Number ? ((Number) candidateWidth).doubleValue() : 0;
            if (value > width) { best = image; width = value; }
        }
        return best;
    }

    static String describe(Object image) {
        Object width = AdDetector.fieldValueBySerializedName(image, "width");
        Object height = AdDetector.fieldValueBySerializedName(image, "height");
        Object type = AdDetector.fieldValueBySerializedName(image, "type");
        String dimensions = width instanceof Number && height instanceof Number
                ? ((Number) width).intValue() + " × " + ((Number) height).intValue() + " px" : "";
        String format = type instanceof String ? ((String) type).toUpperCase(Locale.ROOT) : "";
        if (format.isEmpty()) {
            Object url = AdDetector.fieldValueBySerializedName(image, "url");
            String path = url instanceof String ? android.net.Uri.parse((String) url).getPath() : null;
            if (path != null && path.lastIndexOf('.') >= 0) {
                String extension = path.substring(path.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
                if ("JPG".equals(extension) || "JPEG".equals(extension)) format = "JPEG";
                else if ("PNG".equals(extension) || "WEBP".equals(extension) || "GIF".equals(extension))
                    format = extension;
            }
        }
        if (format.startsWith("IMAGE/")) format = format.substring(6);
        if ("JPG".equals(format)) format = "JPEG";
        return dimensions + (dimensions.isEmpty() || format.isEmpty() ? "" : " · ") + format;
    }
}
