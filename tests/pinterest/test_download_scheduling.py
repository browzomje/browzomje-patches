#!/usr/bin/env python3
"""Exercise the production scheduling methods with a synthetic JobScheduler."""
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2]
          / "extensions/extension/src/main/java/app/template/extension/pinterest/PinDownloadQueue.java")
text = source.read_text()
methods = text[text.index("    static void resume("):text.index("    static boolean drain(")]

methods += text[text.index("    private static boolean hasPendingDownloads("):
                text.index("    static void completed(")]

fixture = r"""
import java.util.List;
import java.util.Map;

class ScheduleTest {
    static final int JOB_ID = 123;
    static class Build {
        static class VERSION { static int SDK_INT = 34; }
    }
    static class PinDownloadJob {}
    record ComponentName(Context context, Class<?> service) {}
    static class MorpheLog {
        static final String BOARD = "board";
        static void w(String tag, String message, Exception failure) {}
    }
    record Preferences(Map<String, ?> getAll) {}
    static Preferences prefs(Context context) { return new Preferences(context.records); }
    static class Context {
        Map<String, ?> records = Map.of();
        static final String JOB_SCHEDULER_SERVICE = "jobscheduler";
        JobScheduler scheduler = new JobScheduler();
        Object getSystemService(String service) { return scheduler; }
    }
    record JobInfo(int id, boolean expedited, boolean persisted, boolean network) {
        static final int NETWORK_TYPE_ANY = 1, BACKOFF_POLICY_EXPONENTIAL = 1;
        int getId() { return id; }
        static class Builder {
            final int id;
            boolean expedited, persisted, network;
            Builder(int id, ComponentName service) { this.id = id; }
            Builder setRequiredNetworkType(int type) { network = type == NETWORK_TYPE_ANY; return this; }
            Builder setPersisted(boolean value) { persisted = value; return this; }
            Builder setBackoffCriteria(int delay, int policy) { return this; }
            Builder setExpedited(boolean value) {
                check(Build.VERSION.SDK_INT >= 31, "expedited API called on old Android");
                expedited = value;
                return this;
            }
            JobInfo build() { return new JobInfo(id, expedited, persisted, network); }
        }
    }
    static class JobScheduler {
        static final int RESULT_SUCCESS = 1;
        List<JobInfo> pending = List.of();
        final java.util.ArrayList<JobInfo> scheduled = new java.util.ArrayList<>();
        boolean outOfQuota, fail;
        List<JobInfo> getAllPendingJobs() { return pending; }
        int schedule(JobInfo info) {
            scheduled.add(info);
            return fail || (outOfQuota && info.expedited()) ? 0 : RESULT_SUCCESS;
        }
    }
    static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    public static void main(String[] args) {
        Context fast = new Context();
        schedule(fast, true);
        check(fast.scheduler.scheduled.equals(List.of(new JobInfo(JOB_ID, true, true, true))),
            "interactive work requests expedited, persisted network job");

        Context quota = new Context();
        quota.scheduler.outOfQuota = true;
        schedule(quota, true);
        check(quota.scheduler.scheduled.equals(List.of(
            new JobInfo(JOB_ID, true, true, true), new JobInfo(JOB_ID, false, true, true))),
            "quota rejection falls back to a regular persisted job");

        Context pending = new Context();
        pending.scheduler.pending = List.of(new JobInfo(JOB_ID, false, true, true));
        schedule(pending, true);
        check(pending.scheduler.scheduled.isEmpty(), "never replace the queue's existing job");

        Context board = new Context();
        schedule(board);
        check(board.scheduler.scheduled.equals(List.of(new JobInfo(JOB_ID, false, true, true))),
            "board work uses a regular job");

        Context recovered = new Context();
        recovered.records = Map.of("history_saved", "{}", "task_invalid", 1);
        resume(recovered);
        check(recovered.scheduler.scheduled.isEmpty(), "history and invalid records do not keep a job alive");
        recovered.records = Map.of("task_previous", "{\"manager\":123}");
        check(hasPendingDownloads(recovered), "handed-off downloads still need reconciliation");
        resume(recovered);
        check(recovered.scheduler.scheduled.equals(List.of(new JobInfo(JOB_ID, true, true, true))),
            "opening history can restart work for an existing download");

        Build.VERSION.SDK_INT = 30;
        Context older = new Context();
        schedule(older, true);
        check(older.scheduler.scheduled.equals(List.of(new JobInfo(JOB_ID, false, true, true))),
            "older Android uses a regular job");

        Context failure = new Context();
        failure.scheduler.fail = true;
        try {
            schedule(failure, true);
            throw new AssertionError("scheduler rejection must be reported");
        } catch (IllegalStateException expected) {}
        System.out.println("PASS download scheduling: immediate, quota fallback, existing job, board, legacy and rejection");
    }
"""
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "ScheduleTest.java"
    java.write_text(fixture + methods + "}\n")
    subprocess.run(["java", str(java)], check=True, timeout=20)
