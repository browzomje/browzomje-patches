"""Run board row construction and the saved-pin long-press policy against synthetic models."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "extensions/extension/src/main/java/app/template/extension/pinterest"
PACKAGE = "app.browzomje.extension.pinterest"

LOG = '''
package app.browzomje.extension.pinterest;
final class MorpheLog {
    static final String BOARD_MENU = "board-menu";
    static void d(String tag, String message) {}
    static void e(String tag, String message) {}
}
'''

TEST = '''
package app.browzomje.extension.pinterest;

public class MenuTest {
    enum Icon { PENCIL, DOWNLOAD, BOX }
    enum Typography { UI_300 }
    enum Surface { HOMEFEED, SEARCH, BOARD, BOARD_SECTION, USER_PINS, BOARD_IDEAS }
    enum Option { Edit, Archive, Other }

    public record Row(int titleRes, int getIndex, String title, String subtitle, Icon icon, Typography style) {
        @Override public String toString() { return "OptionItem(titleRes=" + titleRes + ")"; }
    }

    static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var original = new Row(123, 2, null, null, Icon.PENCIL, Typography.UI_300);
        var download = (Row) BoardMenuRowFactory.cloneWithTitle(original, 456, 3);
        check(download.titleRes() == 456 && download.getIndex() == 3, "new title and click index");
        check(download.title() == null && download.subtitle() == null, "native string ID must supply the label");
        check(download.icon() == Icon.DOWNLOAD && download.style() == Typography.UI_300, "download styling");
        check(BoardMenuRowFactory.boardOptionIcon(Option.Edit, Icon.class) == Icon.PENCIL, "edit icon");
        check(BoardMenuRowFactory.boardOptionIcon(Option.Archive, Icon.class) == Icon.BOX, "archive icon");
        check(BoardMenuRowFactory.boardOptionIcon(Option.Other, Icon.class) == null, "unknown option fallback");

        for (Surface surface : new Surface[]{Surface.BOARD, Surface.BOARD_SECTION, Surface.USER_PINS}) {
            check(SavedPinMenu.hideSave(surface, true, true), "saved pin on " + surface);
            check(!SavedPinMenu.hideSave(surface, false, true), "board belongs to someone else");
            check(!SavedPinMenu.hideSave(surface, true, false), "pin has no board");
        }
        for (Surface surface : new Surface[]{Surface.HOMEFEED, Surface.SEARCH, Surface.BOARD_IDEAS}) {
            check(!SavedPinMenu.hideSave(surface, true, true), "discovery surface " + surface);
        }
        check(!SavedPinMenu.hideSave(null, true, true), "unknown surface fallback");
        System.out.println("PASS board option labels, native icons, and saved-pin menu scope");
    }
}
'''

with tempfile.TemporaryDirectory() as tmp:
    directory = Path(tmp)
    files = [directory / "MorpheLog.java", directory / "MenuTest.java"]
    for file, text in zip(files, [LOG, TEST]):
        file.write_text(text)
    subprocess.run([
        "javac", "-d", tmp, *files, SOURCE / "BoardMenuRowFactory.java", SOURCE / "SavedPinMenu.java",
    ], check=True)
    subprocess.run(["java", "-cp", tmp, PACKAGE + ".MenuTest"], check=True)
