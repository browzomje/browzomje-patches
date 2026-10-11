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
import java.io.FileInputStream;
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
        schedule(context);
        MorpheLog.i(MorpheLog.BOARD,"durable image request queued for Pin "+task.getString("id"));
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
        if(!prefs(context).getAll().isEmpty()) schedule(context);
    }
    static void schedule(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null) throw new IllegalStateException("JobScheduler unavailable");
        // Avoid replacing a running job when a board queues more images.
        for(JobInfo job:scheduler.getAllPendingJobs()) if(job.getId()==JOB_ID) return;
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(context,PinDownloadJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1)
                .setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        if(scheduler.schedule(job)!=JobScheduler.RESULT_SUCCESS) throw new IllegalStateException("cannot schedule downloads");
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
                    prefs(context).edit().remove(entry.getKey()).commit();CALLBACKS.remove(entry.getKey());
                    toast(context,PinterestUtils.getString("failed"));
                }
                MorpheLog.w(MorpheLog.BOARD,"image queue resolution failed (attempt "+failures+")",error);
            }
        }
        // New entries may have been added while a snapshot was being drained.
        for(Object raw:prefs(context).getAll().values()) try {
            if(new JSONObject((String)raw).optLong("manager",0)==0) retry=true;
        } catch(Exception ignored) {}
        return retry;
    }
    static void completed(Context context,long id) {
        for(Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) try {
            JSONObject task=new JSONObject((String)entry.getValue());
            if(task.optLong("manager",0)==id) { check(context,entry.getKey(),task);break; }
        } catch(Exception failure) { MorpheLog.w(MorpheLog.BOARD,"download completion reconciliation failed",failure); }
    }
    private static synchronized void check(Context context,String key,JSONObject task) throws Exception {
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        if(manager==null) return;
        long id=task.getLong("manager");
        int status;
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if(cursor==null || !cursor.moveToFirst()) { prefs(context).edit().remove(key).commit();CALLBACKS.remove(key);return; }
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
            prefs(context).edit().remove(key).commit();
            MorpheLog.i(MorpheLog.BOARD,"system image saved "+task.getString("id")+" "+selected.getInt("width")+"x"+selected.getInt("height")+" "+selected.getString("ext"));
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
            prefs(context).edit().remove(key).commit();CALLBACKS.remove(key);
            toast(context,PinterestUtils.getString("failed"));
        }
    }
    private static boolean reserved(Context context,String folder,String filename) {
        for(Object raw:prefs(context).getAll().values()) try {
            JSONObject task=new JSONObject((String)raw);
            if(folder.equals(task.optString("folder")) && filename.equals(task.optString("filename"))) return true;
        } catch(Exception ignored) {}
        return false;
    }
    private static String safe(String value) { return value==null?"Pins":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replace("..","_"); }
    private static void toast(Context context,String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_SHORT).show());
    }
}
