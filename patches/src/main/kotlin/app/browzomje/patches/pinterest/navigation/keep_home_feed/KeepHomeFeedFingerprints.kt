package app.browzomje.patches.pinterest.navigation.keep_home_feed

import app.morphe.patcher.Fingerprint

// Public XML view name plus experiment string; never use the obfuscated method name.
internal object HomeTabSelectionFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/navigation/view/FloatingBottomNavBar;",
    returnType = "V",
    strings = listOf("android_hf_auto_refresh"),
    custom = { method, _ ->
        method.parameterTypes.size == 2 && method.parameterTypes[0] == "I" &&
            method.parameterTypes[1].toString().startsWith("Lcom/pinterest/framework/screens/")
    },
)

// Root Home reselection callback. The same presenter refresh method is also used by
// manual refresh, so intercept only its call from this Bundle callback.
internal object HomeReselectionFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/feature/home/mainfeed/HomeFeedFragment;",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    strings = listOf("com.pinterest.BOTTOM_NAV_GOING_TO_HOME", "com.pinterest.EXTRA_PIN_INSERTION_DATA"),
)

internal object RecyclerScrollToPositionFingerprint : Fingerprint(
    definingClass = "Landroidx/recyclerview/widget/RecyclerView;",
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("Cannot scroll to position a LayoutManager set. Call setLayoutManager with a non-null argument."),
)
