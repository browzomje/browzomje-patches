package app.template.patches.pinterest.privacy.telemetry

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/*
 * Target: com.google.android.gms.ads.identifier.AdvertisingIdClient$Info
 *
 * In questa build (com.pinterest 14.23.0) la classe e i due metodi NON sono offuscati,
 * perché fanno parte dell'API pubblica di Google Play Services. Verificato con:
 *
 *   rg -n "getId|isLimitAdTrackingEnabled" \
 *      pinterest_extracted/sources/com/google/android/gms/ads/identifier/AdvertisingIdClient.java
 *
 * Decompilato (estratto):
 *
 *   public static final class Info {
 *       public String  getId()                     { return this.zza; }
 *       public boolean isLimitAdTrackingEnabled()  { return this.zzb; }
 *   }
 *
 * Sono i due punti da cui ogni SDK di tracciamento (Firebase, GMS measurement, ad-libs)
 * legge l'identificatore pubblicitario del dispositivo. Neutralizzandoli alla radice
 * tutti i consumatori ottengono un ID vuoto + "limit ad tracking" attivo, senza dover
 * toccare i singoli SDK e senza rischiare di rompere l'app.
 */

private const val ADVERTISING_ID_INFO_CLASS =
    "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient\$Info;"

/** AdvertisingIdClient.Info.getId() -> String (l'advertising ID vero e proprio). */
object AdvertisingIdGetIdFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "getId" && classDef.type == ADVERTISING_ID_INFO_CLASS
    }
)

/** AdvertisingIdClient.Info.isLimitAdTrackingEnabled() -> boolean. */
object AdvertisingIdLimitTrackingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "isLimitAdTrackingEnabled" && classDef.type == ADVERTISING_ID_INFO_CLASS
    }
)
