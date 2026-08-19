package app.browzomje.patches.pinterest.privacy.tracking

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.AccessFlags
import org.w3c.dom.Element

private const val MANIFEST_PATCH_NAME = "Disable Google Engage"
private const val WORKER_PATCH_NAME = "Disable Google Engage worker"

private const val ENGAGE_RECEIVER = "com.pinterest.engage.GoogleEngageBroadcastReceiver"
private const val ENGAGE_ENV_METADATA = "com.google.android.engage.service.ENV"

/**
 * Toglie il ricevitore con cui Pinterest pubblica contenuti verso Google Engage.
 *
 * <p>Google Engage è il canale con cui un'app manda a Google i propri contenuti perché li mostri
 * altrove: Discover, Assistant, Play Store, Entertainment Space. Pinterest registra
 * `GoogleEngageBroadcastReceiver` su tre azioni — la richiesta di contenuti freschi da parte di
 * Google, il login e il logout — e a ogni scatto mette in coda un `GoogleEngageWorker` che
 * impacchetta i pin dell'utente e li consegna a Google.
 *
 * <p>Senza il `<receiver>` nel manifest il sistema non risolve più nessuna di quelle azioni: il
 * worker non viene mai messo in coda da questa strada, e niente esce dall'app.
 *
 * <p>Va insieme a [disableGoogleEngageWorkerPatch], che chiude l'altra strada: il worker è
 * schedulato anche periodicamente da `onCreate` dell'Application, che il manifest non governa.
 */
@Suppress("unused")
val disableGoogleEngagePatch = resourcePatch(
    name = MANIFEST_PATCH_NAME,
    description = "Removes the Google Engage broadcast receiver, so Pinterest cannot publish your " +
        "content to Google (Discover, Assistant, Play Store).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element

            var removed = 0

            // All'indietro: rimuovere un nodo sposta indietro gli indici di tutti i successivi.
            val receivers = application.getElementsByTagName("receiver")
            for (i in receivers.length - 1 downTo 0) {
                val receiver = receivers.item(i) as Element
                if (receiver.getAttribute("android:name") == ENGAGE_RECEIVER) {
                    receiver.parentNode.removeChild(receiver)
                    removed++
                }
            }

            // Il metadata dell'ambiente Engage ha senso solo con il ricevitore presente.
            val metas = application.getElementsByTagName("meta-data")
            for (i in metas.length - 1 downTo 0) {
                val meta = metas.item(i) as Element
                if (meta.getAttribute("android:name") == ENGAGE_ENV_METADATA) {
                    meta.parentNode.removeChild(meta)
                    removed++
                }
            }

            if (removed == 0) {
                PatchLog.warn(MANIFEST_PATCH_NAME, "no Google Engage entry found in the manifest.")
            } else {
                PatchLog.info(MANIFEST_PATCH_NAME, "$removed manifest entries removed")
            }
        }
    }
}

/**
 * `GoogleEngageWorker.createWork()`, l'override di `RxWorker` che confeziona i contenuti da mandare
 * a Google.
 *
 * <p>La classe è di Pinterest e R8 le conserva il nome; il *metodo* invece diventa una lettera
 * sola (`g()` su 14.32.0) e il tipo di ritorno — `Single` di RxJava — cambia package a ogni build
 * (`oz2.v` su 14.32.0, `jx2.v` su 14.25.0). Ci si ancora quindi solo a quello che regge: la classe
 * che la definisce, `public final`, zero parametri, e un tipo di ritorno che sia un riferimento —
 * `Single` è sempre un oggetto, mai un primitivo o `void`. Basta a individuarlo senza ambiguità.
 */
internal object GoogleEngageWorkerCreateWorkFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/engage/GoogleEngageWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    custom = { method, _ ->
        val returnType = method.returnType
        returnType.startsWith("L") && returnType.endsWith(";")
    },
)

/**
 * Fa fallire il lavoro periodico che pubblica contenuti verso Google Engage.
 *
 * <p>Serve anche togliendo il ricevitore ([disableGoogleEngagePatch]): il worker è schedulato pure
 * dall'`onCreate` dell'Application, che nel manifest non compare.
 *
 * <p>`createWork()` riscritto restituisce `null`. WorkManager lo interpreta come un
 * `NullPointerException` incapsulato e segna il tentativo come fallimento ritentabile: essendo un
 * lavoro periodico con backoff esponenziale, si assesta in fretta su "un tentativo ogni qualche ora,
 * sempre fallito". Niente arriva mai a Google.
 *
 * <p>L'alternativa più "pulita" — restituire `Single.just(Result.success())` — richiederebbe di
 * scrivere nel patch il nome accorciato di `Single`, che cambia a ogni build di Pinterest.
 * Restituire `null` non nomina nessun tipo, e per questo è identico su ogni versione.
 */
@Suppress("unused")
val disableGoogleEngageWorkerPatch = bytecodePatch(
    name = WORKER_PATCH_NAME,
    description = "Makes the periodic Google Engage job fail, so no content recommendation is ever " +
        "published to Google even when the job is scheduled from outside the manifest.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        val method = GoogleEngageWorkerCreateWorkFingerprint.methodOrNull
        if (method == null) {
            PatchLog.warn(
                WORKER_PATCH_NAME,
                "GoogleEngageWorker.createWork() not found: the periodic job still runs.",
            )
            return@execute
        }

        method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )
        PatchLog.hooked(WORKER_PATCH_NAME, method, "createWork() -> null")
    }
}
