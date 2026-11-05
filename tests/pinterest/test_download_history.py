#!/usr/bin/env python3
"""Exercise the production history/retry transaction with an in-memory preference store."""
import os
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] /
          "extensions/extension/src/main/java/app/template/extension/pinterest/PinDownloadQueue.java").read_text()
history = source[source.index("    static final class HistoryEntry"):source.index("    private static boolean reserved")]
fixture = """
import java.util.*;
import org.json.JSONObject;

class DownloadHistoryTest {
    static class Context {
        final SharedPreferences store = new SharedPreferences();
        boolean scheduled;
    }
    static class SharedPreferences {
        final Map<String, String> values = new HashMap<>();
        boolean fail;
        Map<String, String> getAll() { return new HashMap<>(values); }
        String getString(String key, String fallback) { return values.getOrDefault(key, fallback); }
        Editor edit() { return new Editor(); }
        class Editor {
            final Map<String, String> changes = new HashMap<>();
            Editor remove(String key) { changes.put(key, null); return this; }
            Editor putString(String key, String value) { changes.put(key, value); return this; }
            boolean commit() {
                if (fail) return false;
                changes.forEach((key, value) -> {
                    if (value == null) values.remove(key); else values.put(key, value);
                });
                return true;
            }
        }
    }
    static class MorpheLog {
        static final String BOARD = "board";
        static void w(String channel, String message, Exception error) { throw new AssertionError(error); }
    }
    static SharedPreferences prefs(Context context) { return context.store; }
    static void schedule(Context context, boolean immediate) { context.scheduled = immediate; }
    static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    static JSONObject task() {
        return new JSONObject().put("id", "1").put("source", "https://example.invalid/pin.jpg")
                .put("folder", "Pins").put("manager", 4).put("failures", 3).put("attempt", 1);
    }
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        context.store.values.put("task_one", task().toString());
        check(history(context).get(0).pending(), "legacy task remains visible");
        finish(context, "task_one", task(), false);
        HistoryEntry failed = history(context).get(0);
        check(!failed.pending() && !failed.saved(), "terminal failure");
        context.store.fail = true;
        try {
            retry(context, failed);
            throw new AssertionError("failed commit must be reported");
        } catch (java.io.IOException expected) {}
        check(context.store.values.containsKey("history_one") && !context.scheduled, "retry commit is atomic");
        context.store.fail = false;
        retry(context, failed);
        retry(context, failed);
        HistoryEntry pending = history(context).get(0);
        check(context.scheduled && pending.pending() && pending.managerId() == 0 && history(context).size() == 1,
                "retry schedules one fresh task");
        check(pending.task.getInt("attempt") == 0 && pending.task.getInt("failures") == 0
                && pending.task.has("source"), "retry retains source and resets counters");
        finish(context, "task_one", task(), true);
        HistoryEntry saved = history(context).get(0);
        check(saved.saved() && saved.managerId() == 4 && !saved.task.has("source"),
                "completion keeps the file handle without retaining the URL");
        for (int i = 0; i < 110; i++) finish(context, "task_" + i, task(), true);
        check(history(context).size() == 100, "bounded history");
        System.out.println("PASS download history, bounded retention, and atomic retry");
    }
""" + history + "\n}"
with tempfile.TemporaryDirectory(prefix="pinterest-history-test-") as work:
    path = Path(work) / "DownloadHistoryTest.java"
    path.write_text(fixture)
    jar = os.environ.get("PINTEREST_JSON_JAR")
    if not jar:
        jar = str(next((Path.home() / ".gradle/caches").rglob("json-*.jar")))
    subprocess.run(["java", "-cp", jar, str(path)], check=True)
