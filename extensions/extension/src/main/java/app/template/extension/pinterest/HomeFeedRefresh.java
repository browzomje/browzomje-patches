package app.browzomje.extension.pinterest;

/** Gate used only at the automatic Home-refresh emitters, never at the shared event bus. */
public final class HomeFeedRefresh {
    private HomeFeedRefresh() {}

    /** Replaced only after both source emitters were successfully hooked. */
    public static boolean isAvailable() { return false; }

    public static boolean shouldKeepFeed() {
        boolean keep = MorpheSettingsStore.effective(MorpheSettingsStore.KEY_KEEP_HOME_FEED);
        MorpheLog.hookFired("home-refresh", "automatic refresh emitter");
        MorpheLog.ok("home-refresh", keep ? "automatic refresh suppressed" : "original refresh allowed");
        return keep;
    }
}
