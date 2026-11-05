package app.browzomje.extension.pinterest;

import java.util.ArrayList;
import java.util.List;

/** Copy incoming lists before native feed/grid operations capture indices or calculate diffs. */
public final class VideoFeedFilter {
    private VideoFeedFilter() {}

    public static List<?> filter(List<?> items) {
        boolean hideVideos = MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS);
        boolean hideAi = MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_AI_PINS);
        MutedPinWords words = MutedPinWords.forInput(MorpheSettingsStore.mutedPinWords());
        if (items == null || (!hideVideos && !hideAi && words.isEmpty())) return items;
        ArrayList<Object> copy = null;
        int removed = 0;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if ((hideVideos && AdDetector.isVideoPin(item)) || (hideAi && AdDetector.isAiLabeledPin(item)) || words.matches(item)) {
                if (copy == null) {
                    copy = new ArrayList<>(items.size());
                    copy.addAll(items.subList(0, i));
                }
                removed++;
            } else if (copy != null) copy.add(item);
        }
        if (copy == null) return items;
        MorpheLog.i("feed-filter", "grid input: removed " + removed + " matching Pins out of " + items.size());
        return copy;
    }
}
