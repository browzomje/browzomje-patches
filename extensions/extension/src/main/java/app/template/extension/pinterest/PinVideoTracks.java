package app.browzomje.extension.pinterest;

import java.util.List;
import java.util.Map;

final class PinVideoTracks {
    interface VideoPage {
        boolean hasVideo(Object page) throws ReflectiveOperationException;
    }

    private PinVideoTracks() {}

    static Integer soleVideoPage(Object pages, Object preview, VideoPage video) {
        Object displayed = pages == null ? preview : pages;
        if (!(displayed instanceof List<?>)) return null;
        Integer match = null;
        int index = 0;
        try {
            for (Object page : (List<?>) displayed) {
                if (video.hasVideo(page)) {
                    if (match != null) return null;
                    match = index;
                }
                index++;
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
        return match;
    }

    static String selectedMp4(Map<String, String> tracks, String pinId, Integer page) {
        if (pinId == null || !pinId.matches("[0-9]+")) return null;
        String exact = tracks.get(pinId);
        if (exact != null || page == null) return exact;
        return tracks.get(pinId + "-" + page);
    }
}
