package app.browzomje.extension.pinterest;

import android.app.DownloadManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import android.os.Build;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persist intent before network work. System downloads survive the Pinterest process. */
final class PinDownloadQueue {
    static final int JOB_ID=0x4d50494e;
    private static final Map<String,Runnable> CALLBACKS=new ConcurrentHashMap<>();
    interface Active { boolean get(); }
    private PinDownloadQueue() {}
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("morphe_image_queue",0); }
    static void add(Context context,String id,String source,int width,int height,String folder,boolean hq,Runnable completion) throws Exception {
        String key="task_"+java.util.UUID.randomUUID();
        JSONObject task=new JSONObject().put("id",id!=null && id.matches("[0-9]+")?id:"pin")
                .put("source",source).put("width",width).put("height",height).put("folder",safe(folder))
                .put("hq",hq).put("attempt",0).put("manager",0);
        if(!prefs(context).edit().putString(key,task.toString()).commit()) throw new java.io.IOException("cannot persist download");
        if(completion!=null) CALLBACKS.put(key,completion);
        schedule(context, "Pins".equals(folder));
        MorpheLog.i(MorpheLog.BOARD,"durable image request queued");
    }
    static void addBoard(Context context,java.util.List<String[]> images,String folder,boolean hq) throws Exception {
        SharedPreferences.Editor edit=prefs(context).edit();
        for(String[] image:images) {
            JSONObject task=new JSONObject().put("id",image[0]).put("source",image[1])
                    .put("width",Integer.parseInt(image[2])).put("height",Integer.parseInt(image[3]))
                    .put("folder",safe(folder)).put("hq",hq).put("attempt",0).put("manager",0);
            edit.putString("task_"+java.util.UUID.randomUUID(),task.toString());
        }
        if(!edit.commit()) throw new java.io.IOException("cannot persist board downloads");
        if(!images.isEmpty()) schedule(context);
    }
    static void resume(Context context) {
        if (!hasPendingDownloads(context)) return;
        try {
            schedule(context, true);
        } catch (RuntimeException failure) {
            MorpheLog.w(MorpheLog.BOARD, "could not resume image downloads", failure);
        }
    }
    private static void schedule(Context context) {
        schedule(context, false);
    }

    private static synchronized void schedule(Context context, boolean immediate) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) throw new IllegalStateException("JobScheduler unavailable");
        // Keep an existing job and its retry state while it owns the queue.
        for (JobInfo pending : scheduler.getAllPendingJobs()) {
            if (pending.getId() == JOB_ID) return;
        }
        JobInfo.Builder job = new JobInfo.Builder(JOB_ID, new ComponentName(context, PinDownloadJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (immediate && Build.VERSION.SDK_INT >= 31) {
            if (scheduler.schedule(job.setExpedited(true).build()) == JobScheduler.RESULT_SUCCESS) return;
            job.setExpedited(false);
        }
        if (scheduler.schedule(job.build()) != JobScheduler.RESULT_SUCCESS) {
            throw new IllegalStateException("cannot schedule downloads");
        }
    }
    static boolean drain(Context context,Active active) {
        boolean retry=false;
        for(Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) {
            if(!active.get()) return true;
            if(!entry.getKey().startsWith("task_") || !(entry.getValue() instanceof String)) continue;
            JSONObject task=null;
            try {
                task=new JSONObject((String)entry.getValue());
                if(task.optLong("manager",0)!=0) { check(context,entry.getKey(),task);continue; }
                boolean quality=task.getBoolean("hq") && task.getInt("attempt")==0;
                PinImageResolver.Result selected=PinImageResolver.resolve(context,task.getString("source"),task.getInt("width"),task.getInt("height"),quality);
                if(!active.get()) return true;
                if(task.getInt("attempt")>0) PinImageResolver.fallbackChosen(context,task.getString("source"),task.getInt("width"),task.getInt("height"),task.getBoolean("hq"),selected);
                DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
                if(manager==null) throw new java.io.IOException("DownloadManager unavailable");
                String filename=task.getString("id")+"."+selected.extension;
                java.io.File directory=new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Pinterest/"+task.getString("folder"));
                java.io.File existing=new java.io.File(directory,filename);
                if(!"Pins".equals(task.getString("folder")) && existing.isFile()) {
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeFile(existing.getAbsolutePath(),bounds);
                    if(bounds.outWidth>=selected.width && bounds.outHeight>=selected.height) {
                        prefs(context).edit().remove(entry.getKey()).commit();continue;
                    }
                }
                int suffix=1;
                while(new java.io.File(directory,filename).exists() || reserved(context,task.getString("folder"),filename))
                    filename=task.getString("id")+" ("+(suffix++)+")."+selected.extension;
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(selected.url));
                request.setTitle(filename).setDescription("Pinterest — "+task.getString("folder"));
                request.setMimeType(selected.mime).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        "Pinterest/"+task.getString("folder")+"/"+filename);
                long managerId=manager.enqueue(request);
                task.put("manager",managerId).put("filename",filename).put("selected",selected.json());
                prefs(context).edit().putString(entry.getKey(),task.toString()).commit();
                MorpheLog.i(MorpheLog.BOARD,"system image download "+managerId+" "+selected.describe()+" original="+selected.original);
                if(!selected.original && task.getBoolean("hq") && "Pins".equals(task.getString("folder"))) toast(context,MorpheStrings.get(MorpheStrings.IMAGE_DOWNLOAD_FALLBACK));
            } catch(Exception error) {
                int failures=task==null?3:task.optInt("failures",0)+1;
                if(task!=null && failures<3) {
                    try { task.put("failures",failures);prefs(context).edit().putString(entry.getKey(),task.toString()).commit(); }
                    catch(Exception ignored) {}
                    retry=true;
                } else {
                    finish(context, entry.getKey(), task, false);
                    CALLBACKS.remove(entry.getKey());
                    toast(context,PinterestUtils.getString("download_failed"));
                }
                MorpheLog.w(MorpheLog.BOARD,"image queue resolution failed (attempt "+failures+")",error);
            }
        }
        return retry || hasPendingDownloads(context);
    }

    // Reconcile with DownloadManager again if its completion broadcast is delayed or missed.
    private static boolean hasPendingDownloads(Context context) {
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            if (entry.getKey().startsWith("task_") && entry.getValue() instanceof String) return true;
        }
        return false;
    }

    static void completed(Context context,long id) {
        for(Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) try {
            if (!entry.getKey().startsWith("task_")) continue;
            JSONObject task=new JSONObject((String)entry.getValue());
            if(task.optLong("manager",0)==id) { check(context,entry.getKey(),task);break; }
        } catch(Exception failure) { MorpheLog.w(MorpheLog.BOARD,"download completion reconciliation failed",failure); }
    }
    private static synchronized void check(Context context,String key,JSONObject task) throws Exception {
        String current = prefs(context).getString(key, null);
        if (current == null || new JSONObject(current).optLong("manager") != task.getLong("manager")) return;
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        if(manager==null) return;
        long id=task.getLong("manager");
        int status;
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if(cursor==null || !cursor.moveToFirst()) { finish(context, key, task, false);CALLBACKS.remove(key);return; }
            status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }
        if(status!=DownloadManager.STATUS_FAILED && status!=DownloadManager.STATUS_SUCCESSFUL) return;
        boolean valid=status==DownloadManager.STATUS_SUCCESSFUL;
        JSONObject selected=task.getJSONObject("selected");
        if(valid) try {
            byte[] magic=new byte[12];
            try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id))) {
                if(input.read(magic)<12 || !selected.getString("ext").equals(PinImageResolver.format(magic))) valid=false;
            }
            // Reopen because the stream above owns/closes its file descriptor.
            if(valid) try(ParcelFileDescriptor boundsFile=manager.openDownloadedFile(id)) {
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFileDescriptor(boundsFile.getFileDescriptor(),null,bounds);
                valid=bounds.outWidth==selected.getInt("width") && bounds.outHeight==selected.getInt("height");
            }
        } catch(Exception invalid) { valid=false; }
        if(valid) {
            finish(context, key, task, true);
            MorpheLog.i(MorpheLog.BOARD,"system image saved "+selected.getInt("width")+"x"+selected.getInt("height")+" "+selected.getString("ext"));
            Runnable callback=CALLBACKS.remove(key);
            if(callback!=null) new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
            return;
        }
        manager.remove(id); // Never retain an HTML response or failed image as a successful download.
        PinImageResolver.invalidate(context,task.getString("source"),task.getInt("width"),task.getInt("height"),task.getBoolean("hq"));
        if(task.getInt("attempt")==0 && selected.getBoolean("original")) {
            task.put("attempt",1).put("manager",0);
            prefs(context).edit().putString(key,task.toString()).commit();
            toast(context,MorpheStrings.get(MorpheStrings.IMAGE_DOWNLOAD_FALLBACK));
            schedule(context);
        } else {
            finish(context, key, task, false);
            CALLBACKS.remove(key);
            toast(context,PinterestUtils.getString("download_failed"));
        }
    }
    static final class HistoryEntry {
        final String key;
        final JSONObject task;
        HistoryEntry(String key, JSONObject task) {
            this.key = key;
            this.task = task;
        }
        boolean pending() { return key.startsWith("task_"); }
        boolean saved() { return task.optBoolean("saved"); }
        long managerId() { return task.optLong("manager"); }
        long sortTime() { return pending() ? Long.MAX_VALUE : task.optLong("finished"); }
        String title() { return task.optString("filename", task.optString("id")); }
    }

    static java.util.List<HistoryEntry> history(Context context) {
        return history(context, true);
    }

    private static java.util.List<HistoryEntry> history(Context context, boolean includePending) {
        java.util.ArrayList<HistoryEntry> entries = new java.util.ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("history_") && !(includePending && key.startsWith("task_"))) continue;
            try {
                entries.add(new HistoryEntry(key, new JSONObject((String) entry.getValue())));
            } catch (Exception ignored) {
                // Ignore malformed records from previous runs.
            }
        }
        java.util.Collections.sort(entries, (first, second) ->
                Long.compare(second.sortTime(), first.sortTime()));
        return entries;
    }

    static synchronized boolean clearHistory(Context context) {
        SharedPreferences store = prefs(context);
        SharedPreferences.Editor edit = store.edit();
        for (String key : store.getAll().keySet()) {
            if (key.startsWith("history_")) edit.remove(key);
        }
        return edit.commit();
    }

    static synchronized void retry(Context context, HistoryEntry entry) throws Exception {
        SharedPreferences store = prefs(context);
        String raw = store.getString(entry.key, null);
        if (entry.pending() || entry.saved() || raw == null) return;
        JSONObject task = new JSONObject(raw);
        task.remove("finished");
        task.remove("saved");
        task.remove("selected");
        task.remove("filename");
        task.put("attempt", 0).put("failures", 0).put("manager", 0);
        String key = "task_" + entry.key.substring("history_".length());
        if (!store.edit().remove(entry.key).putString(key, task.toString()).commit()) {
            throw new java.io.IOException("cannot persist download retry");
        }
        schedule(context, "Pins".equals(task.getString("folder")));
    }

    private static synchronized void finish(Context context, String key, JSONObject task, boolean saved) {
        SharedPreferences.Editor edit = prefs(context).edit().remove(key);
        if (task != null) {
            try {
                int retained = 0;
                for (HistoryEntry entry : history(context, false)) {
                    if (++retained >= 100) edit.remove(entry.key);
                }
                task.put("finished", System.currentTimeMillis()).put("saved", saved);
                task.remove("selected");
                if (saved) task.remove("source");
                edit.putString("history_" + key.substring("task_".length()), task.toString());
            } catch (org.json.JSONException invalid) {
                MorpheLog.w(MorpheLog.BOARD, "could not record download status", invalid);
            }
        }
        edit.commit();
    }

    private static boolean reserved(Context context,String folder,String filename) {
        for(Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) try {
            if (!entry.getKey().startsWith("task_")) continue;
            JSONObject task=new JSONObject((String)entry.getValue());
            if(folder.equals(task.optString("folder")) && filename.equals(task.optString("filename"))) return true;
        } catch(Exception ignored) {}
        return false;
    }
    private static String safe(String value) { return value==null?"Pins":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replace("..","_"); }
    private static void toast(Context context,String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_SHORT).show());
    }
}
