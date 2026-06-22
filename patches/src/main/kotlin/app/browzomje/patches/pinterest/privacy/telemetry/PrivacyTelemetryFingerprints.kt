package app.template.patches.pinterest.privacy.telemetry

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val ADVERTISING_ID_INFO_CLASS =
    "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient\$Info;"

object AdvertisingIdGetIdFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "getId" && classDef.type == ADVERTISING_ID_INFO_CLASS
    }
)

object AdvertisingIdLimitTrackingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "isLimitAdTrackingEnabled" && classDef.type == ADVERTISING_ID_INFO_CLASS
    }
)
