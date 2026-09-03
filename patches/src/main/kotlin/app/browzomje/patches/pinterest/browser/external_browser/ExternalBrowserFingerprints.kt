package app.browzomje.patches.pinterest.browser.external_browser

import app.morphe.patcher.Fingerprint

// Issue #35. Il browser interno di Pinterest è un fragment:
//
//   fd -t d -i browser sources/com/pinterest        # -> sources/com/pinterest/feature/browser/
//   rg -n "class |onViewCreated" sources/com/pinterest/feature/browser/view/InAppBrowserFragment.java
//
// Da lì si vede come il fragment ricava l'indirizzo da aprire (`pw0.f.aa()` su 14.32.0,
// rinominato su 14.34.0): lo legge dall'oggetto di navigazione, `Navigation.getUrl()`. Quella
// interfaccia — e la sua unica implementazione, `NavigationImpl` — hanno il nome vero:
//
//   rg -n "interface Navigation" sources/com/pinterest/navigation/Navigation.java
//   rg -n "public final String" sources/com/pinterest/navigation/NavigationImpl.java   # un solo campo String

/**
 * `onViewCreated(View, Bundle)` del fragment del browser interno.
 *
 * <p>Tre ancore, tutte nella categoria che R8 non può toccare:
 *
 * - **il nome della classe**, `com.pinterest.feature.browser.view.InAppBrowserFragment`, che non è
 *   offuscato perché è citato per nome dai `ScreenLocation` del framework di navigazione
 *   (`BrowserFeatureLocation.BROWSER` lo tiene come `Class`);
 * - **il nome del metodo**, che è un override del framework Android;
 * - **la firma**, garantita da Google.
 *
 * <p>È il fingerprint più solido di tutta questa cartella: fra 14.32.0 e 14.34.0 la classe che
 * monta la sezione commenti è passata da `pt.v3` a `st.x3` e quella che smista le condivisioni da
 * `as2.g0` a `jt2.c0`, mentre qui non è cambiato niente — nemmeno il package.
 *
 * <p>Non si specificano gli `accessFlags`: il metodo è `public` su entrambe le versioni viste, ma
 * è anche `final` su una e non sull'altra, e chiedere una cosa in più che non serve a identificare
 * il metodo è solo una cosa in più che si può rompere.
 */
internal object InAppBrowserOnViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/feature/browser/view/InAppBrowserFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)

/**
 * Metodo di apertura delle Chrome Custom Tabs (CCT) che Pinterest usa per "Visita il sito" e link esterni.
 *
 * <p>Identificato univocamente dalla stringa di telemetria `"android_non_chrome_cct"`, dal return type `"V"`
 * e dal secondo parametro di tipo `String` (l'indirizzo da aprire).
 */
internal object CustomTabsOpenFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("android_non_chrome_cct"),
    custom = { method, _ ->
        val params = method.parameterTypes.map { it.toString() }
        params.size >= 2 && params[1] == "Ljava/lang/String;"
    },
)

