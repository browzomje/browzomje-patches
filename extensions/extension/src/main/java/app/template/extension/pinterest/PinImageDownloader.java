package app.browzomje.extension.pinterest;

import android.content.Context;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One entry point for durable image downloads, including board images. */
public final class PinImageDownloader {
    private static final Pattern SIZED = Pattern.compile(
            "^https://i\\.pinimg\\.com/(?:[0-9]+x|originals)/([0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32})\\.(jpg|jpeg|png|webp|gif)$",
            Pattern.CASE_INSENSITIVE);
    private PinImageDownloader() {}

    static Set<String> originals(String source) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher match = SIZED.matcher(source);
        if (!match.matches()) return urls; // Signed URLs, video thumbnails and other hosts stay untouched.
        String prefix = "https://i.pinimg.com/originals/" + match.group(1) + ".";
        String extension = match.group(2).toLowerCase(java.util.Locale.ROOT);
        urls.add(prefix + extension);
        // Animated GIFs must not silently become a static original of another format.
        if (!extension.equals("gif")) {
            urls.add(prefix + "png");
            urls.add(prefix + "webp");
            urls.add(prefix + "jpg");
        }
        return urls;
    }

    static void download(Context context,String id,String source,int width,int height) {
        download(context,id,source,width,height,null);
    }
    static void download(Context context,String id,String source,int width,int height,Runnable completion) {
        queue(context,id,source,width,height,"Pins",completion);
    }
    static void queue(Context context,String id,String source,int width,int height,String folder,Runnable completion) {
        if(context==null || source==null || !source.startsWith("https://")) return;
        try {
            PinDownloadQueue.add(context.getApplicationContext(),id,source,width,height,folder,
                    MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIGH_QUALITY_IMAGES),completion);
            if("Pins".equals(folder)) PinterestUtils.showNativeToast(context,
                    PinterestUtils.getString("download_image_started"));
        } catch(Exception failure) {
            MorpheLog.w(MorpheLog.BOARD,"could not queue image download",failure);
            PinterestUtils.showNativeToast(context,PinterestUtils.getString("failed"));
        }
    }
}
