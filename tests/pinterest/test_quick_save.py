#!/usr/bin/env python3
"""Exercise production quick-save completion with delayed and failed replies."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "extensions/extension/src/main/java/app/template/extension/pinterest/QuickSave.java").read_text()
membership = source[source.index("    private static final class Membership"):source.index("    private static final class Binding")]
pending = source[source.index("    private static final class PendingWrite"):source.index("    private QuickSave()")]
finish = source[source.index("    private static void finishWrite"):source.index("    private static boolean isFresh")]

fixture = """
import java.util.HashMap;
import java.util.Map;

class QuickSaveTest {
    static class View {}
    static class android {
        static class os {
            static class SystemClock {
                static long elapsedRealtime() { return 5; }
            }
        }
    }

    static final Map<String, PendingWrite> WRITES = new HashMap<>();
    static final Map<String, Membership> MEMBERSHIPS = new HashMap<>();
    static Membership visible;
    static int errors;
    static boolean active = true;

    static boolean sameAccount(String key, String pin) { return active; }
    static Object call(Object data, String method) throws ReflectiveOperationException {
        return method.equals("h") ? "success" : data;
    }
    static void write(View view, String pin, String key, Membership known, boolean desired) {
        WRITES.put(key, new PendingWrite(known, desired));
        visible = WRITES.get(key).visible();
    }
    static void remember(String key, Membership known) { MEMBERSHIPS.put(key, known); visible = known; }
    static void renderBound(String key, Membership known) { visible = known; }
    static void failure(View view) { errors++; }
    static void check(boolean value) { if (!value) throw new AssertionError(); }

    public static void main(String[] args) {
        View view = new View();
        Membership unsaved = new Membership(false, true, 1);
        PendingWrite save = new PendingWrite(unsaved, true);
        save.desired = false;
        finishWrite(view, "pin", "key", save, true);
        PendingWrite remove = WRITES.get("key");
        check(!visible.profile && visible.boards && remove.confirmed.profile);
        finishWrite(view, "pin", "key", remove, false);
        check(WRITES.isEmpty() && !MEMBERSHIPS.get("key").profile && errors == 0);

        finishWrite(view, "pin", "key", save, null);
        check(!WRITES.get("key").sent && !visible.profile);

        save.desired = true;
        finishWrite(view, "pin", "key", save, true);
        check(WRITES.isEmpty() && MEMBERSHIPS.get("key").profile);
        finishWrite(view, "pin", "key", save, null);
        check(!MEMBERSHIPS.containsKey("key") && !visible.profile && errors == 1);

        active = false;
        finishWrite(view, "pin", "key", save, true);
        check(WRITES.isEmpty() && MEMBERSHIPS.isEmpty());
        System.out.println("PASS quick-save reversal, coalescing, rollback and account isolation");
    }
"""
with tempfile.TemporaryDirectory() as folder:
    java = Path(folder) / "QuickSaveTest.java"
    java.write_text(fixture + membership + pending + finish + "}\n")
    subprocess.run(["java", str(java)], check=True, timeout=20)
