package app.browzomje.extension.pinterest;

import java.util.ArrayList;
import java.util.List;

/** Copy incoming lists before native feed/grid operations capture indices or calculate diffs. */
public final class VideoFeedFilter {
    private VideoFeedFilter() {}

    public static List<?> filter(List<?> items) {
        if (items == null || !MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS)) return items;
        ArrayList<Object> copy = null;
        int removed = 0;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (AdDetector.isVideoPin(item)) {
                if (copy == null) {
                    copy = new ArrayList<>(items.size());
                    copy.addAll(items.subList(0, i));
                }
                removed++;
            } else if (copy != null) copy.add(item);
        }
        if (copy == null) return items;
        MorpheLog.i("video-filter", "grid input: removed " + removed + " video pins out of " + items.size());
        return copy;
    }
}
