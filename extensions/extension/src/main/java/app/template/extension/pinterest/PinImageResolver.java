package app.browzomje.extension.pinterest;

import android.content.Context;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Shared, bounded metadata cache. Probe only a prefix; never assume dimensions from a URL. */
public final class PinImageResolver {
    private static final long TTL = 10 * 60 * 1000;
    private static final int MAX_PREFIX = 128 * 1024;
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final Map<String, Result> CACHE = new LinkedHashMap<>();
    private static final Map<String, FutureTask<Result>> FLIGHTS = new LinkedHashMap<>();
    public interface Callback { void ready(Result result); }
    public static final class Result {
        final String url, extension, mime;
        final int width, height;
        final boolean original;
        final long checked, fileSize;
        Result(String url, String extension, int width, int height, boolean original) {
            this(url, extension, width, height, original, System.currentTimeMillis(), -1);
        }
        Result(String url, String extension, int width, int height, boolean original, long checked, long fileSize) {
            this.url=url; this.extension=extension; this.width=width; this.height=height;
            this.original=original; this.checked=checked; this.fileSize=fileSize;
            mime="image/" + (extension.equals("jpg") ? "jpeg" : extension);
        }
        String describe() { return width + " × " + height + " px · " + (extension.equals("jpg") ? "JPEG" : extension.toUpperCase(java.util.Locale.ROOT))
                + (fileSize > 0 ? " · " + sizeLabel(fileSize) : ""); }
        private static String sizeLabel(long bytes) {
            if (bytes < 1000) return bytes + " B";
            String[] units = {"kB", "MB", "GB", "TB", "PB", "EB"};
            double value = bytes / 1000.0;
            int unit = 0;
            while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
            return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit]);
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("url",url).put("ext",extension).put("width",width)
                    .put("height",height).put("original",original).put("checked",checked).put("fileSize",fileSize);
        }
    }
    private PinImageResolver() {}
    private static String key(String url, int width, int height, boolean hq) { return url+"|"+width+"|"+height+"|"+hq; }
    static void async(Context context, String source, int width, int height, boolean hq, Callback callback) {
        WORKERS.execute(() -> {
            Result result=null;
            try { result=resolve(context,source,width,height,hq); }
            catch(Exception e) { MorpheLog.d(MorpheLog.BOARD,"download metadata unavailable: "+e.getMessage()); }
            final Result ready=result;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.ready(ready));
        });
    }
    static Result resolve(Context context, String source, int width, int height, boolean hq) throws Exception {
        if(source==null || !source.startsWith("https://")) throw new java.io.IOException("invalid image URL");
        String key=key(source,width,height,hq);
        FutureTask<Result> flight;
        boolean owner=false;
        synchronized(CACHE) {
            Result cached=CACHE.get(key);
            if(cached==null) {
                String stored=context.getSharedPreferences("morphe_image_metadata",0).getString(key,null);
                if(stored!=null) try {
                    JSONObject j=new JSONObject(stored);
                    cached=new Result(j.getString("url"),j.getString("ext"),j.getInt("width"),j.getInt("height"),j.getBoolean("original"),j.getLong("checked"),j.optLong("fileSize",-1));
                } catch(Exception ignored) {}
            }
            if(cached!=null && System.currentTimeMillis()-cached.checked>=0 && System.currentTimeMillis()-cached.checked<TTL) return cached;
            flight=FLIGHTS.get(key);
            if(flight==null) {
                flight=new FutureTask<>(() -> select(source,width,height,hq));
                FLIGHTS.put(key,flight);owner=true;
            }
        }
        if(owner) flight.run();
        try {
            Result result=flight.get();
            synchronized(CACHE) {
                CACHE.put(key,result);
                while(CACHE.size()>64) CACHE.remove(CACHE.keySet().iterator().next());
                android.content.SharedPreferences preferences=context.getSharedPreferences("morphe_image_metadata",0);
                android.content.SharedPreferences.Editor edit=preferences.edit();
                if(preferences.getAll().size()>=64 && !preferences.contains(key)) edit.clear();
                edit.putString(key,result.json().toString()).apply();
            }
            return result;
        } finally { if(owner) synchronized(CACHE) { FLIGHTS.remove(key); } }
    }
    static void invalidate(Context context, String source, int width, int height, boolean hq) {
        synchronized(CACHE) { CACHE.remove(key(source,width,height,hq)); }
        context.getSharedPreferences("morphe_image_metadata",0).edit().remove(key(source,width,height,hq)).apply();
    }
    static void fallbackChosen(Context context,String source,int width,int height,boolean hq,Result result) throws Exception {
        String cacheKey=key(source,width,height,hq);
        synchronized(CACHE) { CACHE.put(cacheKey,result); }
        context.getSharedPreferences("morphe_image_metadata",0).edit().putString(cacheKey,result.json().toString()).apply();
        PinImageDetails.refreshDownloadInfo(source,result);
    }
    private static Result select(String source,int width,int height,boolean hq) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        if(hq) for(String candidate:PinImageDownloader.originals(source)) {
            if(android.os.SystemClock.elapsedRealtime()>=deadline) break;
            try {
                Result r=probe(candidate,true,(int)Math.max(1,Math.min(2500,deadline-android.os.SystemClock.elapsedRealtime())));
                if((width<=0 || r.width>=width) && (height<=0 || r.height>=height)) return r;
            } catch(Exception e) { MorpheLog.d(MorpheLog.BOARD,"original metadata unavailable: "+e.getMessage()); }
        }
        return probe(source,false,5000);
    }
    private static Result probe(String url,boolean original,int timeout) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(timeout);connection.setReadTimeout(timeout);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Range","bytes=0-"+(MAX_PREFIX-1));
        connection.setRequestProperty("Accept","image/*");
        connection.setRequestProperty("Accept-Encoding","identity");
        long deadline=android.os.SystemClock.elapsedRealtime()+timeout*2L;
        try {
            int status=connection.getResponseCode();
            if(status!=200 && status!=206) throw new java.io.IOException("HTTP "+status);
            if(status==206) {
                String range=connection.getHeaderField("Content-Range");
                if(range==null || !range.startsWith("bytes 0-")) throw new java.io.IOException("invalid prefix range");
            }
            long fileSize=responseSize(connection,status);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[1024];
                while(output.size()<MAX_PREFIX && android.os.SystemClock.elapsedRealtime()<deadline) {
                    int count=input.read(buffer,0,Math.min(buffer.length,MAX_PREFIX-output.size()));
                    if(count<0) break;
                    output.write(buffer,0,count);
                    byte[] bytes=output.toByteArray();String format=format(bytes);
                    if(format==null) { if(bytes.length>=12) throw new java.io.IOException("not an image");continue; }
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    if(options.outWidth>0 && options.outHeight>0) {
                        MorpheLog.d(MorpheLog.BOARD,"verified download metadata "+options.outWidth+"x"+options.outHeight+" "+format+", prefix="+bytes.length);
                        return new Result(url,format,options.outWidth,options.outHeight,original,System.currentTimeMillis(),fileSize);
                    }
                }
            }
            throw new java.io.IOException("image metadata not found within prefix limit");
        } finally { connection.disconnect(); }
    }
    // A 206 Content-Length is only the prefix size, never the full image size.
    static long responseSize(HttpURLConnection connection, int status) {
        String encoding = connection.getHeaderField("Content-Encoding");
        if (encoding != null && !encoding.trim().equalsIgnoreCase("identity")) return -1;
        try {
            if (status == 206) {
                String range = connection.getHeaderField("Content-Range");
                java.util.regex.Matcher match = java.util.regex.Pattern
                        .compile("bytes 0-([0-9]+)/([0-9]+|\\*)").matcher(range == null ? "" : range.trim());
                if (!match.matches() || match.group(2).equals("*")) return -1;
                long end = Long.parseLong(match.group(1)), total = Long.parseLong(match.group(2));
                return total > end ? total : -1;
            }
            if (status == 200) {
                String length = connection.getHeaderField("Content-Length");
                if (length == null || !length.trim().matches("[0-9]+")) return -1;
                long total = Long.parseLong(length.trim());
                return total > 0 ? total : -1;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }
    static String format(byte[] bytes) {
        if(bytes.length<12) return null;
        if((bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) return "jpg";
        if((bytes[0]&255)==137 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G') return "png";
        if(bytes[0]=='G' && bytes[1]=='I' && bytes[2]=='F') return "gif";
        if(bytes[0]=='R' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='F' && bytes[8]=='W' && bytes[9]=='E' && bytes[10]=='B' && bytes[11]=='P') return "webp";
        return null;
    }
}
