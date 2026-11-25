"""Check the association between the native Pin menu and the player's MP4 keys."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TRACKS = ROOT / "extensions/extension/src/main/java/app/template/extension/pinterest/PinVideoTracks.java"
TEST = """
package app.browzomje.extension.pinterest;
import java.util.List;
import java.util.Map;

public class VideoTrackSelectionTest {
    public static void main(String[] args) {
        Map<String, String> tracks = Map.of("123-0", "standard", "456", "known",
            "456-0", "page-zero", "1234-0", "another-pin", "321-1", "another-page");
        PinVideoTracks.VideoPage video = "video"::equals;
        check(Integer.valueOf(0).equals(PinVideoTracks.soleVideoPage(List.of("video"), null, video)),
            "single story page");
        check(Integer.valueOf(1).equals(PinVideoTracks.soleVideoPage(List.of("image", "video"), null, video)),
            "single video among images");
        check(PinVideoTracks.soleVideoPage(List.of("video", "video"), List.of("video"), video) == null,
            "full story overrides preview and does not pick one of multiple videos");
        check("standard".equals(PinVideoTracks.selectedMp4(tracks, "123", 0)), "single video uses page zero");
        check("known".equals(PinVideoTracks.selectedMp4(tracks, "456", 0)), "exact Pin match wins");
        check(PinVideoTracks.selectedMp4(tracks, "123", null) == null, "story has no unique page");
        check(PinVideoTracks.selectedMp4(tracks, "32", 0) == null, "do not match prefixes");
        check(PinVideoTracks.selectedMp4(tracks, "321", 0) == null, "do not choose another page");
        check("another-page".equals(PinVideoTracks.selectedMp4(tracks, "321", 1)), "match known page");
        check(PinVideoTracks.selectedMp4(tracks, null, 0) == null, "missing selection");
        System.out.println("PASS exact Pin and single-video player key selection");
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
"""
with tempfile.TemporaryDirectory() as directory:
    test = Path(directory) / "VideoTrackSelectionTest.java"
    test.write_text(TEST)
    subprocess.run(["javac", "-d", directory, TRACKS, test], check=True)
    subprocess.run(["java", "-cp", directory, "app.browzomje.extension.pinterest.VideoTrackSelectionTest"], check=True)
