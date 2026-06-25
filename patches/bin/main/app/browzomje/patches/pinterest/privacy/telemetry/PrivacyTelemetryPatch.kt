package app.browzomje.patches.pinterest.privacy.telemetry

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST

@Suppress("unused")
val privacyTelemetryPatch = bytecodePatch(
    name = "Neutralize advertising ID",
    description = "Returns an empty Google Advertising ID and forces ‘limit ad tracking’, disabling ad tracking without causing the app to crash.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        AdvertisingIdGetIdFingerprint.method.addInstructions(
            0,
            """
                const-string v0, ""
                return-object v0
            """
        )

        AdvertisingIdLimitTrackingFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
