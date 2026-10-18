package app.browzomje.patches.pinterest.navigation.startup_tab

import app.morphe.patcher.Fingerprint

internal object MainCreateFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/activityLibrary/activity/task/activity/MainActivity;",
    name = "onCreate", returnType = "V", parameters = listOf("Landroid/os/Bundle;"),
)

// Pinterest's own deferred tab router, called after navbar inflation completes.
internal object PendingTabRouterFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/activityLibrary/activity/task/activity/MainActivity;",
    returnType = "V", parameters = listOf("Landroid/content/Intent;"),
    strings = listOf("com.pinterest.EXTRA_PENDING_TAB", "com.pinterest.EXTRA_PENDING_TAB_EXTRAS", "com.pinterest.EXTRA_DO_NOT_RESET_PENDING_TAB"),
)

internal object LauncherCreateFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/activity/PinterestActivity;",
    name = "onCreate", returnType = "V", parameters = listOf("Landroid/os/Bundle;"),
)
internal object LauncherNewIntentFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/activity/PinterestActivity;",
    name = "onNewIntent", returnType = "V", parameters = listOf("Landroid/content/Intent;"),
)
private object PendingTabFactoryClass : Fingerprint(
    returnType = "Landroid/content/Intent;",
    strings = listOf("com.pinterest.EXTRA_PENDING_TAB", "com.pinterest.EXTRA_PENDING_TAB_EXTRAS", "com.pinterest.EXTRA_IS_DEEPLINK"),
)
internal object AuthenticatedMainTransitionFingerprint : Fingerprint(
    classFingerprint = PendingTabFactoryClass,
    returnType = "V", parameters = listOf("Landroid/app/Activity;", "Z"),
)
