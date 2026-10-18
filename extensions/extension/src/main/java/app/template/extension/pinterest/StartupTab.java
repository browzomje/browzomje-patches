package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Hands a fresh launcher choice to Pinterest's own authenticated deferred tab router. */
public final class StartupTab {
    private StartupTab() {}
    private static final String PENDING = "com.pinterest.EXTRA_PENDING_TAB";
    private static final String OWN_REQUEST = "app.browzomje.STARTUP_TAB_REQUEST";
    private static final WeakHashMap<Activity, Boolean> LAUNCHES = new WeakHashMap<>();
    private static WeakReference<Activity> main = new WeakReference<>(null);
    public static boolean isAvailable() { return false; }

    public static void onLauncherCreate(Object owner, Object savedState) {
        if (!(owner instanceof Activity)) return;
        Activity activity = (Activity) owner;
        LAUNCHES.remove(activity);
        if (savedState != null || live(main.get())) return;
        Intent intent = activity.getIntent();
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction()) ||
                !intent.hasCategory(Intent.CATEGORY_LAUNCHER) || intent.getData() != null ||
                (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return;
        Bundle extras = intent.getExtras();
        if (extras != null && !extras.isEmpty()) return;
        LAUNCHES.put(activity, true);
    }

    public static void cancelLauncher(Object owner) {
        if (owner instanceof Activity) LAUNCHES.remove((Activity) owner);
    }

    /** Called only from the authenticated launcher-to-main transition; extras are copied natively. */
    public static void prepareAuthenticated(Object owner) {
        if (!(owner instanceof Activity)) return;
        Activity activity = (Activity) owner;
        if (LAUNCHES.remove(activity) == null || !live(activity) || live(main.get())) return;
        Intent intent = activity.getIntent();
        if (intent == null || (intent.getExtras() != null && !intent.getExtras().isEmpty())) return;
        String tab = MorpheSettingsStore.startupTab();
        if ("SEARCH".equals(tab) && MorpheSettingsStore.isSearchButtonHidden()) tab = "HOME";
        if ("HOME".equals(tab)) return;
        intent.putExtra(PENDING, tab);
        intent.putExtra(OWN_REQUEST, true);
        MorpheLog.i("startup-tab", "native startup destination: " + tab);
    }

    public static void onMainCreate(Object owner, Object savedState) {
        if (!(owner instanceof Activity)) return;
        Activity activity = (Activity) owner;
        main = new WeakReference<>(activity);
        Intent intent = activity.getIntent();
        if (intent != null && intent.getBooleanExtra(OWN_REQUEST, false)) {
            intent.removeExtra(OWN_REQUEST);
            // Android/native restored state takes precedence over our request, never over an external one.
            if (savedState != null) intent.removeExtra(PENDING);
        }
    }

    private static boolean live(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
