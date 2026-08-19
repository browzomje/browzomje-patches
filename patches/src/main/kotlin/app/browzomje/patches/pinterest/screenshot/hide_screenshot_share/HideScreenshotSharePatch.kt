package app.browzomje.patches.pinterest.screenshot.hide_screenshot_share

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.inputRegisterCount
import app.browzomje.patches.shared.returnVoidWhen

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Hide screenshot share menu"

/**
 * Registro del parametro in posizione [index] (0 = il primo dopo `this`, se il metodo è
 * d'istanza). I parametri stanno nei registri **alti** del frame, subito dopo i locali.
 */
private fun MutableMethod.parameterRegister(index: Int): Int =
    implementation!!.registerCount - inputRegisterCount() + index

@Suppress("unused")
val hideScreenshotSharePatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Removes the panel Pinterest opens after a screenshot, and stops it watching for screenshots where possible.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        var hooked = 0

        // 1) Percorso legacy. Il primo parametro è il fragment del closeup: lo si passa
        //    all'extension solo per poterlo nominare nel log, la decisione non dipende da lui.
        LegacyScreenshotFunnelFingerprint.methodOrNull?.let { method ->
            val register = method.parameterRegister(0)
            val injected = method.returnVoidWhen(
                "invoke-static/range { v$register .. v$register }, " +
                    "$EXTENSION_CLASS->blockLegacyScreenshotFlow(Ljava/lang/Object;)Z",
            )
            if (injected) {
                PatchLog.hooked(PATCH_NAME, method, "legacy closeup funnel")
                hooked++
            } else {
                PatchLog.warn(
                    PATCH_NAME,
                    "legacy funnel found (${method.definingClass}->${method.name}) but its " +
                        "register frame has no room for the check: left untouched.",
                )
            }
        } ?: PatchLog.warn(
            PATCH_NAME,
            "legacy screenshot funnel not found: on the old closeup the panel will still " +
                "appear. Anchor: the \"has_api\" literal, see pinterest/PATCHING_MEMORY.md.",
        )

        // 2) Percorso SBA. Si passa l'effetto in arrivo (2° parametro) perché il suo toString()
        //    è leggibile — "StartScreenshotObservation", "ShowScreenshotUpsell" — ed è la cosa
        //    più utile che possa finire in logcat per capire cosa stava per succedere.
        SbaScreenshotProcessorFingerprint.methodOrNull?.let { method ->
            val register = method.parameterRegister(1)
            val injected = method.returnVoidWhen(
                "invoke-static/range { v$register .. v$register }, " +
                    "$EXTENSION_CLASS->blockScreenshotEffect(Ljava/lang/Object;)Z",
            )
            if (injected) {
                PatchLog.hooked(PATCH_NAME, method, "SBA effect processor")
                hooked++
            } else {
                PatchLog.warn(
                    PATCH_NAME,
                    "SBA processor found (${method.definingClass}->${method.name}) but its " +
                        "register frame has no room for the check: left untouched.",
                )
            }
        } ?: PatchLog.warn(
            PATCH_NAME,
            "SBA screenshot processor not found: on the new closeup the panel will still " +
                "appear. Anchor: \"sg_android_closeup_screenshot_api_14\".",
        )

        // 3) Il rilevatore generico, quello usato fuori dal closeup (feed, bacheche). Si passa
        //    `this`, cioè il rilevatore stesso, così il log dice da quale schermata è partito.
        GenericScreenshotObserverFingerprint.methodOrNull?.let { method ->
            val injected = method.returnVoidWhen(
                "invoke-static { }, $EXTENSION_CLASS->blockScreenshotObserver()Z",
            )
            if (injected) {
                PatchLog.hooked(PATCH_NAME, method, "generic screenshot observer")
                hooked++
            } else {
                PatchLog.warn(
                    PATCH_NAME,
                    "generic observer found (${method.definingClass}->${method.name}) but its " +
                        "register frame has no room for the check: left untouched.",
                )
            }
        } ?: PatchLog.warn(
            PATCH_NAME,
            "generic screenshot observer not found: screenshots taken outside an open pin " +
                "will still bring up the panel. Anchor: \"sg_android_new_screenshot_api_14\".",
        )

        check(hooked > 0) {
            "Neither of the two screenshot paths was found: the patch would have no effect. " +
                "See pinterest/PATCHING_MEMORY.md."
        }
    }
}
