package app.browzomje.extension.pinterest;

/** System restarts this persisted job if Pinterest dies while resolving a queued image. */
public final class PinDownloadJob extends android.app.job.JobService {
    private volatile Object run;
    @Override public boolean onStartJob(android.app.job.JobParameters parameters) {
        Object token=new Object();run=token;
        new Thread(() -> {
            boolean retry=PinDownloadQueue.drain(getApplicationContext(),() -> run==token);
            if(run==token) { run=null;jobFinished(parameters,retry); }
        },"morphe-image-queue").start();
        return true;
    }
    @Override public boolean onStopJob(android.app.job.JobParameters parameters) { run=null;return true; }
}
