package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog

// Scheme dell'intent-filter tramite cui la voce "Morphe" (vedi MorpheSettingsEntryPatch) e/o
// `adb shell am start -a android.intent.action.VIEW -d "morphe://settings"` aprono la schermata.
internal const val MORPHE_SETTINGS_URI = "morphe://settings"
private const val MORPHE_SETTINGS_ACTIVITY = "app.browzomje.extension.pinterest.MorpheSettingsActivity"

/**
 * Tema **del framework**, non di Pinterest, ed è obbligatorio dichiararlo.
 *
 * Senza questo attributo l'Activity eredita il tema di `<application>`,
 * `Theme.Pinterest.NoActionbar`, che sovrascrive gli stili di default dei widget di sistema:
 * `android:buttonStyle` → `@style/button_plain` → `android:textSize="?attr/base_font_size_14"`.
 * Quell'attributo è definito **solo** nei theme overlay dei design token (Calico/Denim…), che
 * Pinterest applica dalla propria Activity base e che la nostra non applica mai: il primo
 * `new Button(this)` moriva quindi con
 * `UnsupportedOperationException: Failed to resolve attribute at index 2`, cioè crash all'apertura.
 *
 * Lo stesso vale per `android:editTextStyle`, `spinnerStyle`, `listViewStyle` e gli altri
 * sovrascritti da quel tema: la schermata Morphe non usa nessuna risorsa di Pinterest, quindi
 * la soluzione giusta è non ereditarne il tema affatto.
 */
private const val MORPHE_SETTINGS_THEME = "@android:style/Theme.Material.NoActionBar"

@Suppress("unused")
val morpheSettingsManifestPatch = resourcePatch(
    name = "Morphe settings screen (manifest)",
    description = "Registers the Morphe settings Activity in the manifest, with an intent-filter for the morphe:// scheme.",
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
            activity.setAttribute("android:theme", MORPHE_SETTINGS_THEME)

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

/**
 * Nome della string resource **nuova** con l'etichetta della voce di download nel menu bacheca.
 *
 * Perché una stringa vera e non un testo passato a runtime: la view che disegna quelle righe
 * mostra l'icona solo dentro un `instanceof` sulla classe concreta della voce. Un oggetto nostro
 * (un `Proxy`) avrebbe il testo libero ma nessuna icona; una voce autentica di Pinterest ha
 * l'icona ma prende il titolo **solo** da un id di risorsa. Aggiungendo la risorsa si ottengono
 * entrambe le cose, e per giunta le traduzioni le gestisce Android come per qualunque altra
 * stringa dell'app.
 *
 * L'id non viene mai scritto da nessuna parte: l'extension lo risolve per nome con
 * `getIdentifier`, quindi non importa quale valore gli assegni aapt2 a ogni build.
 */
internal const val BOARD_DOWNLOAD_STRING = "morphe_download_board"

/**
 * Traduzioni dell'etichetta, per cartella `res/values-*`.
 *
 * Le lingue non elencate ricadono su `res/values/strings.xml`, cioè sull'inglese: è il
 * comportamento normale di Android e garantisce che nessuno veda l'italiano per sbaglio.
 */
private val BOARD_DOWNLOAD_TRANSLATIONS = mapOf(
    "values" to "Download all the board's Pins",
    "values-it" to "Scarica tutti i pin della bacheca",
    "values-es" to "Descargar todos los pines del tablero",
    "values-fr" to "Télécharger tous les pins du tableau",
    "values-de" to "Alle Pins der Pinnwand herunterladen",
    "values-pt" to "Baixar todos os pins da pasta",
    "values-pt-rBR" to "Baixar todos os Pins da pasta",
    "values-ru" to "Скачать все пины доски",
    "values-ja" to "ボードのピンをすべてダウンロード",
    "values-zh-rCN" to "下载画板中的所有 Pin",
    "values-ko" to "보드의 모든 핀 다운로드",
    "values-pl" to "Pobierz wszystkie piny z tablicy",
    "values-nl" to "Alle pins van het bord downloaden",
    "values-tr" to "Panodaki tüm pinleri indir",
    "values-ar" to "تنزيل كل دبابيس اللوحة",
)

/** La stringa riusata come etichetta della voce "Morphe". */
private const val REUSED_STRING = "settings_menu_teen_safety_resources"

/**
 * Le lingue in cui Pinterest traduce la stringa riusata.
 *
 * Serve perché **non basta rinominare `res/values/strings.xml`**: quello è solo il fallback
 * (inglese). Un telefono in italiano legge `res/values-it/strings.xml` e continuava a vedere
 * "Risorse per la sicurezza degli adolescenti" — la voce Morphe c'era ma sembrava una voce
 * normale di Pinterest, ed era impossibile trovarla.
 *
 * L'elenco è ricavato dall'APK 14.28.0 cercando la stringa in tutte le cartelle `res/values-*`.
 * Le cartelle assenti vengono semplicemente saltate, quindi tenerlo largo non fa danno.
 */
private val LOCALISED_VALUES_DIRS = listOf(
    "values",
    "values-af", "values-ar", "values-bg", "values-bn", "values-cs", "values-da", "values-de",
    "values-el", "values-en-rAU", "values-en-rGB", "values-en-rIN", "values-es", "values-es-rES",
    "values-fi", "values-fil", "values-fr", "values-hi", "values-hr", "values-hu", "values-in",
    "values-it", "values-iw", "values-ja", "values-kk", "values-ko", "values-ky", "values-ms",
    "values-nb", "values-nl", "values-pl", "values-pt", "values-pt-rBR", "values-pt-rPT",
    "values-ro", "values-ru", "values-sk", "values-sv", "values-te", "values-th", "values-tl",
    "values-tr", "values-uk-rUA", "values-vi", "values-zh-rCN", "values-zh-rTW",
)

// Riusiamo l'etichetta "Teen safety resources" (usata SOLO dalla riga "link esterno" delle
// Impostazioni, vedi MorpheSettingsEntryPatch) rinominandola "Morphe": più sicuro che aggiungere
// una string resource nuova, perché eventuali riferimenti a id di risorsa non presenti nell'ARSC
// originale possono far fallire l'aapt2 in fase di ripackaging. Se sul tuo account questa voce
// reale esiste (rara: compare solo per alcuni account minorenni), la vedrai etichettata "Morphe"
// anche lei.
@Suppress("unused")
val morpheSettingsLabelPatch = resourcePatch(
    name = "Morphe settings screen (label)",
    description = "Renames the reused string resource for the \"Morphe\" entry in Settings, across all languages.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        var renamed = 0
        val missing = mutableListOf<String>()

        for (dir in LOCALISED_VALUES_DIRS) {
            val path = "res/$dir/strings.xml"
            try {
                document(path).use { doc ->
                    val strings = doc.getElementsByTagName("string")
                    for (i in 0 until strings.length) {
                        val node = strings.item(i)
                        if (node.attributes?.getNamedItem("name")?.nodeValue == REUSED_STRING) {
                            node.textContent = "Morphe"
                            renamed++
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Cartella assente in questa versione dell'APK: normale, si prosegue.
                missing.add(dir)
            }
        }

        check(renamed > 0) {
            "String $REUSED_STRING was not found in any res/values*/strings.xml: " +
                "the Morphe entry would remain without a recognizable label."
        }
        PatchLog.info(
            "Morphe settings screen (label)",
            "\"Morphe\" label applied in $renamed languages" +
                if (missing.isEmpty()) "" else " (${missing.size} missing folders, skipped)",
        )

        // Etichetta della voce di download nel menu bacheca. Va aggiunta come risorsa vera:
        // vedi il commento su BOARD_DOWNLOAD_STRING.
        var added = 0
        for ((dir, text) in BOARD_DOWNLOAD_TRANSLATIONS) {
            val path = "res/$dir/strings.xml"
            try {
                document(path).use { doc ->
                    val root = doc.getElementsByTagName("resources").item(0) ?: return@use

                    // Idempotenza: ripatchare un APK già patchato non deve duplicare la stringa,
                    // perché due <string> con lo stesso name fanno fallire aapt2.
                    val existing = doc.getElementsByTagName("string")
                    for (i in 0 until existing.length) {
                        if (existing.item(i).attributes?.getNamedItem("name")?.nodeValue
                            == BOARD_DOWNLOAD_STRING
                        ) {
                            return@use
                        }
                    }

                    val element = doc.createElement("string")
                    element.setAttribute("name", BOARD_DOWNLOAD_STRING)
                    element.textContent = text
                    root.appendChild(element)
                    added++
                }
            } catch (_: Exception) {
                // Cartella assente in questa versione dell'APK: la lingua ricadrà sull'inglese.
            }
        }

        check(added > 0) {
            "Could not add string $BOARD_DOWNLOAD_STRING: the download option in " +
                "the board menu would remain without a label."
        }
        PatchLog.info(
            "Morphe settings screen (label)",
            "board download label added in $added languages",
        )
    }
}
