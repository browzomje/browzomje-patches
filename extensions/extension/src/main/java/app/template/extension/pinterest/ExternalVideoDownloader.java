package app.browzomje.extension.pinterest;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

/** Explicit SEND avoids the chooser when a supported yt-dlp frontend is installed. */
final class ExternalVideoDownloader {
    private ExternalVideoDownloader() {}
    private static final String[] PACKAGES = {"com.deniscerri.ytdl", "com.junkfood.seal"};

    static void open(Context context, String url) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (String packageName : PACKAGES) {
            try {
                context.startActivity(new Intent(send).setPackage(packageName));
                return;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // Package absent or its share receiver unavailable: try the next frontend.
            }
        }
        context.startActivity(Intent.createChooser(send,
                PinterestUtils.getString("download_video_external_label"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
