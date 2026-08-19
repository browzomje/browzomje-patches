package app.browzomje.patches.pinterest.privacy.tracking

import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import org.w3c.dom.Element

private const val PATCH_NAME = "Disable third-party trackers"

/**
 * `<meta-data>` e `<property>` da togliere: ognuno è la chiave d'accensione di un SDK.
 *
 * Nessuno di questi SDK va in crash quando non trova il proprio dato di configurazione — è la
 * ragione per cui questa è la strada giusta invece di stubbare bytecode. Tutti e tre lo trattano
 * come "l'app non mi ha configurato", loggano un avviso e si fermano prima di partire.
 */
private val METADATA_TO_REMOVE = setOf(
    // AdMob / Google Mobile Ads: senza APPLICATION_ID l'SDK abortisce l'inizializzazione.
    "com.google.android.gms.ads.APPLICATION_ID",
    "com.google.android.gms.ads.flag.NATIVE_AD_DEBUGGER_ENABLED",
    "com.google.android.gms.ads.flag.DISABLE_CRASH_REPORTING",
    // Bugsnag: senza API_KEY l'SDK non si inizializza e non carica né stack trace né telemetria
    // di sistema.
    "com.bugsnag.android.API_KEY",
)

/**
 * Interruttori ufficiali della famiglia Firebase, letti all'avvio del processo.
 *
 * Il valore dipende dalla forma del nome: i flag `_deactivated` vogliono `true` ("disattivalo"), i
 * flag `_enabled` vogliono `false`. Sbagliare verso qui è un errore silenzioso — il flag risulta
 * scritto e l'SDK resta acceso — quindi la tabella tiene le due cose accoppiate.
 *
 * Firebase Messaging **non** è toccato: è il canale delle notifiche push, che devono continuare ad
 * arrivare.
 */
private val FIREBASE_FLAGS = mapOf(
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_performance_collection_deactivated" to "true",
    "google_analytics_adid_collection_enabled" to "false",
)

/**
 * Consensi di Google Analytics già dichiarati da Pinterest nel manifest: qui si ribaltano a
 * `false`.
 *
 * Sono le porte di consenso documentate da Google
 * (https://developers.google.com/tag-platform/security/guides/app-consent): a `false` l'SDK di
 * misurazione si inizializza ma non raccoglie niente finché l'utente non dà il consenso — cosa che
 * da qui in poi non succede più.
 */
private val ANALYTICS_CONSENT_FLAGS = setOf(
    "google_analytics_default_allow_analytics_storage",
    "google_analytics_default_allow_ad_storage",
    "google_analytics_default_allow_ad_user_data",
    "google_analytics_default_allow_ad_personalization_signals",
)

/**
 * Spegne, dal manifest, gli SDK di terze parti che Pinterest incorpora per pubblicità e telemetria.
 *
 * <p>**Perché una patch di risorse e non di bytecode.** Ogni riga di questa patch tocca
 * `AndroidManifest.xml`, non il dex. È la differenza che conta per la manutenzione: i nomi qui
 * dentro sono contratti pubblici di Google, Bugsnag e del framework Android, non nomi accorciati da
 * R8. Non cambiano quando esce una nuova versione di Pinterest, e infatti sono identici da 14.23.0
 * a 14.32.0. Una patch di bytecode che facesse lo stesso lavoro andrebbe ricontrollata a ogni
 * release.
 *
 * <p>Le quattro cose che fa:
 *
 * 1. **Toglie le chiavi di configurazione** di AdMob e Bugsnag ([METADATA_TO_REMOVE]): senza, quei
 *    due SDK non partono.
 * 2. **Toglie il permesso `AD_ID`**: da lì in poi `AdvertisingIdClient.getAdvertisingIdInfo()`
 *    restituisce un id azzerato con `limitAdTracking = true` a chiunque lo chieda. È il
 *    complemento a monte di `Neutralize advertising ID`, che agisce invece dentro il processo.
 * 3. **Toglie la `<property> AD_SERVICES_CONFIG`**: è l'adesione dell'app alla Privacy Sandbox di
 *    Android 13+ (Topics, Attribution Reporting, Custom Audiences). Senza quella proprietà il
 *    sistema tratta l'app come non aderente, senza bisogno di toccare l'XML referenziato.
 * 4. **Scrive gli interruttori di Firebase e i consensi di Google Analytics**
 *    ([FIREBASE_FLAGS], [ANALYTICS_CONSENT_FLAGS]). I due gruppi sono complementari e servono
 *    entrambi: i primi impediscono all'SDK di inizializzarsi, i secondi gli negano il consenso se
 *    si inizializza lo stesso.
 *
 * <p>Non tocca Firebase Messaging: le notifiche push continuano a funzionare.
 */
@Suppress("unused")
val trackingManifestPatch = resourcePatch(
    name = PATCH_NAME,
    description = "Turns off the advertising and telemetry SDKs Pinterest embeds — Google Ads (AdMob), " +
        "Bugsnag crash reporting, Firebase Analytics/Crashlytics/Performance and the Android Privacy " +
        "Sandbox ad APIs — and strips the Advertising ID permission. Push notifications are unaffected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("AndroidManifest.xml").use { document ->
            val root = document.documentElement
            val application = document.getElementsByTagName("application").item(0) as Element

            // 1) meta-data di configurazione degli SDK.
            //
            //    Si itera all'indietro: rimuovere un nodo accorcia la NodeList e sposta indietro
            //    tutti gli indici successivi, quindi andando in avanti si salterebbe l'elemento
            //    dopo ogni rimozione.
            var removedMeta = 0
            val metas = application.getElementsByTagName("meta-data")
            for (i in metas.length - 1 downTo 0) {
                val meta = metas.item(i) as Element
                if (meta.getAttribute("android:name") in METADATA_TO_REMOVE) {
                    meta.parentNode.removeChild(meta)
                    removedMeta++
                }
            }

            // 2) permesso AD_ID.
            var removedPermissions = 0
            val permissions = root.getElementsByTagName("uses-permission")
            for (i in permissions.length - 1 downTo 0) {
                val permission = permissions.item(i) as Element
                if (permission.getAttribute("android:name") == "com.google.android.gms.permission.AD_ID") {
                    permission.parentNode.removeChild(permission)
                    removedPermissions++
                }
            }

            // 3) adesione alla Privacy Sandbox.
            var removedProperties = 0
            val properties = application.getElementsByTagName("property")
            for (i in properties.length - 1 downTo 0) {
                val property = properties.item(i) as Element
                if (property.getAttribute("android:name") == "android.adservices.AD_SERVICES_CONFIG") {
                    property.parentNode.removeChild(property)
                    removedProperties++
                }
            }

            // 4) interruttori Firebase e consensi Analytics.
            //
            //    Prima passata: si aggiornano sul posto quelli che l'app dichiara già (i quattro
            //    `google_analytics_default_allow_*` ci sono, i `firebase_*` no). Seconda passata:
            //    si aggiungono i mancanti. Fatto così, la patch resta valida anche se una versione
            //    futura di Pinterest cominciasse a dichiarare da sé uno dei flag Firebase — non
            //    finirebbe duplicato.
            val declared = mutableSetOf<String>()
            val remainingMetas = application.getElementsByTagName("meta-data")
            for (i in 0 until remainingMetas.length) {
                val meta = remainingMetas.item(i) as Element
                when (val name = meta.getAttribute("android:name")) {
                    in FIREBASE_FLAGS.keys -> {
                        meta.setAttribute("android:value", FIREBASE_FLAGS.getValue(name))
                        declared += name
                    }

                    in ANALYTICS_CONSENT_FLAGS -> meta.setAttribute("android:value", "false")
                }
            }

            for ((name, value) in FIREBASE_FLAGS) {
                if (name in declared) continue
                val meta = document.createElement("meta-data")
                meta.setAttribute("android:name", name)
                meta.setAttribute("android:value", value)
                application.appendChild(meta)
            }

            PatchLog.info(
                PATCH_NAME,
                "manifest: $removedMeta meta-data removed, $removedPermissions permission(s) removed, " +
                    "$removedProperties property removed, ${FIREBASE_FLAGS.size} Firebase flag(s) set, " +
                    "${ANALYTICS_CONSENT_FLAGS.size} Analytics consent flag(s) denied",
            )

            if (removedMeta == 0 && removedPermissions == 0 && removedProperties == 0) {
                PatchLog.warn(
                    PATCH_NAME,
                    "nothing was removed from the manifest: either the SDKs are already gone " +
                        "or their metadata names have changed.",
                )
            }
        }
    }
}
