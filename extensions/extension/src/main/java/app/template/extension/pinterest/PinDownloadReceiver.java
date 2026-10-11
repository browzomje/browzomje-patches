package app.browzomje.extension.pinterest;

public final class PinDownloadReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context,android.content.Intent intent) {
        if(!android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id=intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID,-1);
        if(id<0) return;
        PendingResult pending=goAsync();
        new Thread(() -> {
            try { PinDownloadQueue.completed(context.getApplicationContext(),id); }
            finally { pending.finish(); }
        },"morphe-image-completed").start();
    }
}
