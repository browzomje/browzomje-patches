package app.browzomje.extension.pinterest;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Menu actions bound to the Pin contained in this menu, never a global last-viewed image. */
final class PinImageMenu {
    private PinImageMenu() {}

    static void upgradeDownload(Object menu) {
        if (!(menu instanceof ViewGroup)) return;
        ViewGroup container = (ViewGroup) menu;
        final Object pin = CurrentPin.findPinIn(menu);
        if (bestImage(pin) == null) return; // Preserve the native action when no model is available.
        Set<String> labels = new HashSet<>();
        for (String action : new String[] { "download_image", "save_to_device" }) {
            String label = PinMenuFilter.label(container.getContext(), action);
            if (label != null) labels.add(label);
        }
        upgradeRows(container, pin, labels);
    }

    private static void upgradeRows(ViewGroup container, Object pin, Set<String> labels) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            if (PinMenuFilter.isCustom(row)) continue;
            TextView text = PinterestUtils.findTextView(row);
            if (row.isClickable() && text != null && labels.contains(text.getText().toString())) {
                row.setOnClickListener(v -> {
                    PinterestUtils.dismissMenu(v.getContext());
                    BoardDownloadHandler.downloadSinglePin(v.getContext(), pin);
                });
                MorpheLog.i(MorpheLog.BOARD, "native image download checks original quality with app-variant fallback");
            } else if (row instanceof ViewGroup) upgradeRows((ViewGroup) row, pin, labels);
        }
    }

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
