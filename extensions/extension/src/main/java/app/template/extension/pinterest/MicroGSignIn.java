package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;

/** Forward the user-initiated credential picker launch with Android's sender opt-in. */
public final class MicroGSignIn {
    private MicroGSignIn() {}

    public static void launch(Activity activity, IntentSender sender, int requestCode, Intent fillIn,
                              int flagsMask, int flagsValues, int extraFlags, Bundle options)
            throws IntentSender.SendIntentException {
        if (Build.VERSION.SDK_INT >= 34 && options == null && !activity.isFinishing()) {
            ActivityOptions launch = ActivityOptions.makeBasic();
            launch.setPendingIntentBackgroundActivityStartMode(Build.VERSION.SDK_INT >= 36
                    ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            options = launch.toBundle();
        }
        activity.startIntentSenderForResult(sender, requestCode, fillIn, flagsMask, flagsValues, extraFlags, options);
    }
}
