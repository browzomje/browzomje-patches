package app.browzomje.patches.easysudoku.privacy.tracking

import app.morphe.patcher.Fingerprint

private const val ADVERTISING_ID_INFO =
    "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient\$Info;"

/**
 * `AdvertisingIdClient.Info.getId()` → il GAID, l'identificativo con cui i circuiti
 * pubblicitari ricollegano fra loro le sessioni su app diverse.
 *
 * Le classi dei Play Services **non** sono offuscate (sono API pubbliche), quindi qui il nome
 * del metodo è un'ancora perfettamente stabile.
 */
object AdvertisingIdGetIdFingerprint : Fingerprint(
    definingClass = ADVERTISING_ID_INFO,
    name = "getId",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

/**
 * `AdvertisingIdClient.Info.isLimitAdTrackingEnabled()` → il flag "l'utente ha chiesto di
 * limitare il tracciamento". Forzarlo a `true` è la metà che conta: un id vuoto senza il flag
 * viene spesso trattato come "id non ancora disponibile, riprova".
 */
object AdvertisingIdLimitTrackingFingerprint : Fingerprint(
    definingClass = ADVERTISING_ID_INFO,
    name = "isLimitAdTrackingEnabled",
    returnType = "Z",
    parameters = emptyList(),
)

/**
 * `AppsFlyerLib.start(Context, String devKey, AppsFlyerRequestListener)` — l'implementazione
 * reale, in `com.appsflyer.internal.AFb1rSDK`.
 *
 * È la chiamata che fa partire l'invio dei dati di attribuzione e degli eventi. Le altre due
 * sovrapposizioni (`start(Context)` e `start(Context, String)`) delegano a questa, quindi
 * neutralizzarla le copre tutte e tre.
 *
 * Il nome della classe di implementazione cambia a ogni versione della SDK: l'ancora è il
 * messaggio d'errore che il metodo contiene, invariato da anni.
 *
 * ```bash
 * rg -n 'AppsFlyerLib\.getInstance\(\)\.(start|init)' -g '*.java' sources/   # -> p9/c.java
 * rg -ln 'extends AppsFlyerLib' -g '*.java' sources/                        # -> com/appsflyer/internal/AFb1rSDK.java
 * rg -n 'void start' sources/com/appsflyer/internal/AFb1rSDK.java           # -> le tre sovrapposizioni
 * ```
 */
object AppsFlyerStartFingerprint : Fingerprint(
    name = "start",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "L"),
    strings = listOf("ERROR: AppsFlyer SDK is not initialized!"),
)
