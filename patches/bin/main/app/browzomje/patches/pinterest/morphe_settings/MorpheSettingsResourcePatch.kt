package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST

// Scheme dell'intent-filter tramite cui la voce "Morphe" (vedi MorpheSettingsEntryPatch) e/o
// `adb shell am start -a android.intent.action.VIEW -d "morphe://settings"` aprono la schermata.
internal const val MORPHE_SETTINGS_URI = "morphe://settings"
private const val MORPHE_SETTINGS_ACTIVITY = "app.browzomje.extension.pinterest.MorpheSettingsActivity"

@Suppress("unused")
val morpheSettingsManifestPatch = resourcePatch(
    name = "Morphe settings screen (manifest)",
    description = "Registra l'Activity delle impostazioni Morphe nel manifest, con un intent-filter " +
        "per lo scheme morphe://.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0)

            val activity = doc.createElement("activity")
            activity.setAttribute("android:name", MORPHE_SETTINGS_ACTIVITY)
            activity.setAttribute("android:exported", "true")
            activity.setAttribute("android:label", "Morphe")

            val intentFilter = doc.createElement("intent-filter")

            val action = doc.createElement("action")
            action.setAttribute("android:name", "android.intent.action.VIEW")
            intentFilter.appendChild(action)

            val categoryDefault = doc.createElement("category")
            categoryDefault.setAttribute("android:name", "android.intent.category.DEFAULT")
            intentFilter.appendChild(categoryDefault)

            val categoryBrowsable = doc.createElement("category")
            categoryBrowsable.setAttribute("android:name", "android.intent.category.BROWSABLE")
            intentFilter.appendChild(categoryBrowsable)

            val data = doc.createElement("data")
            data.setAttribute("android:scheme", "morphe")
            intentFilter.appendChild(data)

            activity.appendChild(intentFilter)
            application.appendChild(activity)
        }
    }
}

// Riusiamo l'etichetta "Teen safety resources" (usata SOLO da com.pinterest.feature.settings.menu.model.i1,
// vedi MorpheSettingsEntryPatch) rinominandola "Morphe": più sicuro che aggiungere una string resource
// nuova, perché eventuali riferimenti a id di risorsa non presenti nell'ARSC originale possono far
// fallire l'aapt2 in fase di ripackaging. Se sul tuo account questa voce reale esiste (rara: compare
// solo per alcuni account minorenni), la vedrai etichettata "Morphe" anche lei — vedi RECAP.md.
@Suppress("unused")
val morpheSettingsLabelPatch = resourcePatch(
    name = "Morphe settings screen (label)",
    description = "Rinomina la string resource riusata per la voce \"Morphe\" nelle Impostazioni.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("res/values/strings.xml").use { doc ->
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val node = strings.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (name == "settings_menu_teen_safety_resources") {
                    node.textContent = "Morphe"
                    break
                }
            }
        }
    }
}
