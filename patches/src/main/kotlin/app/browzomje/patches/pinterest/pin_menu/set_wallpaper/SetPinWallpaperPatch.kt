package app.template.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST

private const val EXTENSION_CLASS = "Lapp/template/extension/pinterest/WallpaperUtils;"

/**
 * Patch risorse: aggiunge il permesso SET_WALLPAPER al manifest.
 * WallpaperManager.setBitmap() lo richiede; è un permesso "normal" concesso a install time,
 * quindi non serve richiesta runtime. È una dipendenza della patch bytecode.
 */
private val addWallpaperPermissionPatch = resourcePatch(
    description = "Aggiunge android.permission.SET_WALLPAPER al manifest."
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("AndroidManifest.xml").use { document ->
            // Evita duplicati se il permesso è già presente.
            val existing = document.getElementsByTagName("uses-permission")
            for (i in 0 until existing.length) {
                val name = existing.item(i).attributes
                    ?.getNamedItem("android:name")?.nodeValue
                if (name == "android.permission.SET_WALLPAPER") return@use
            }

            val manifest = document.getElementsByTagName("manifest").item(0)
            val permission = document.createElement("uses-permission")
            permission.setAttribute("android:name", "android.permission.SET_WALLPAPER")
            manifest.appendChild(permission)
        }
    }
}

/**
 * Aggiunge la voce "Imposta come sfondo" al menu del pin e ne implementa la logica.
 *
 * Tecnica (best-effort, vedi RECAP.md):
 *  - La logica vive nell'extension WallpaperUtils (download/cattura bitmap + WallpaperManager).
 *  - Iniettiamo in tz0.g.o3 (il builder della OverflowMenu di Pinterest, verificato in questa
 *    build) una chiamata che passa la OverflowMenu all'extension, la quale vi aggiunge la riga
 *    cliccabile. Passiamo `p1` (il parametro `view`, cioè la OverflowMenu/LinearLayout).
 *  - Un secondo hook (opzionale, risolto con methodOrNull così la build non fallisce se non
 *    matcha) cattura il bitmap del pin del closeup e lo passa all'extension, che lo userà
 *    direttamente come sfondo senza riscaricarlo.
 */
@Suppress("unused")
val setPinWallpaperPatch = bytecodePatch(
    name = "Set pin as wallpaper",
    description = "It adds the ‘Set as wallpaper’ option to the pin menu, which downloads uses the image and sets it as the device’s wallpaper.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(addWallpaperPermissionPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) Aggiunge la riga "Imposta come sfondo" ALLA FINE del costruttore di uz0.z,
        //    dopo che tutte le altre voci standard sono già state aggiunte con addView.
        //    p0 = this (uz0.z, che estende LinearLayout → ViewGroup): nessun iget necessario.
        val method = OverflowMenuBuilderFingerprint.method
        val insertIndex = method.implementation!!.instructions.size - 1  // prima di return-void

        val registerCount = method.implementation!!.registerCount
        val parameterRegisterCount = method.parameters.size + 1 // +1 per 'this'
        val p0RegisterIndex = registerCount - parameterRegisterCount

        val instructions = InlineSmaliCompiler.compile(
            "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, $EXTENSION_CLASS->addWallpaperOption(Ljava/lang/Object;)V",
            "",
            registerCount,
            true
        )
        method.addInstructions(insertIndex, instructions)

        // 2) Cattura del bitmap del pin (opzionale finché il fingerprint non è verificato su device).
        PinCloseupBitmapFingerprint.methodOrNull?.let { pinMethod ->
            val pinRegisterCount = pinMethod.implementation!!.registerCount
            val pinParameterRegisterCount = pinMethod.parameters.size + 1 // +1 per 'this'
            val p1RegisterIndex = pinRegisterCount - pinParameterRegisterCount + 1

            val pinInstructions = InlineSmaliCompiler.compile(
                "invoke-static/range { v$p1RegisterIndex .. v$p1RegisterIndex }, $EXTENSION_CLASS->setCurrentPinBitmap(Landroid/graphics/Bitmap;)V",
                "",
                pinRegisterCount,
                true
            )
            pinMethod.addInstructions(0, pinInstructions)
        }
    }
}
