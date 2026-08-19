package app.browzomje.patches.pinterest.privacy.tracking

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.AccessFlags

private const val PATCH_NAME = "Disable AppsFlyer tracking"
private const val APPS_FLYER_LIB_CLASS = "Lcom/appsflyer/AppsFlyerLib;"

/**
 * Perché qui i fingerprint sono facili, per una volta.
 *
 * AppsFlyer è una libreria di terze parti, e Pinterest la incorpora già compilata: R8 accorcia i
 * nomi della *sottoclasse concreta* (`com.appsflyer.internal.AFa1tSDK` su 14.32.0) ma non può
 * toccare né il nome della classe astratta `AppsFlyerLib` né i nomi dei suoi metodi, che sono API
 * pubblica dell'SDK. Ci si ancora quindi alla superclasse — `custom` guarda `classDef.superclass` —
 * e non al nome della sottoclasse, così la patch regge anche se AppsFlyer rimescola i propri
 * package interni.
 */
internal object AppsFlyerIsStoppedFingerprint : Fingerprint(
    name = "isStopped",
    returnType = "Z",
    parameters = emptyList(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    custom = { _, classDef -> classDef.superclass == APPS_FLYER_LIB_CLASS },
)

/** `AppsFlyerLib.init(String, AppsFlyerConversionListener, Context)` sulla sottoclasse concreta. */
internal object AppsFlyerInitFingerprint : Fingerprint(
    name = "init",
    returnType = APPS_FLYER_LIB_CLASS,
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/appsflyer/AppsFlyerConversionListener;",
        "Landroid/content/Context;",
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    custom = { _, classDef -> classDef.superclass == APPS_FLYER_LIB_CLASS },
)

/**
 * Spegne AppsFlyer, l'SDK di attribuzione delle installazioni che Pinterest usa per sapere da quale
 * campagna pubblicitaria arriva ogni nuovo utente e cosa fa poi dentro l'app.
 *
 * <p>Si riscrivono due metodi soli, ma sono i due giusti:
 *
 * - **`isStopped()` → `true`.** L'SDK controlla questo flag prima di ogni invio: eventi in-app,
 *   callback di conversione, token di disinstallazione, risoluzione dei deep link. Dichiararsi
 *   "fermo" li disattiva tutti in un colpo, per una via che l'SDK stesso prevede — è quello che
 *   succederebbe chiamando `AppsFlyerLib.stop()`.
 * - **`init(devKey, listener, context)` → `return-object p0`.** Diventa un no-op che restituisce
 *   `this`. Restituire `this` e non `null` è la parte importante: il codice di Pinterest concatena
 *   le chiamate (`getInstance().init(…).start(…)`), quindi un `null` qui diventerebbe un NPE
 *   all'avvio.
 *
 * <p>Non si tocca l'inizializzazione dal lato Pinterest: il chiamante continua a fare quello che
 * faceva, semplicemente non succede niente.
 */
@Suppress("unused")
val disableAppsFlyerPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Neutralises the AppsFlyer attribution SDK: init() becomes a no-op and isStopped() " +
        "always returns true, so no install, event or uninstall data is transmitted.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)

    execute {
        val isStopped = AppsFlyerIsStoppedFingerprint.methodOrNull
        if (isStopped == null) {
            PatchLog.warn(PATCH_NAME, "AppsFlyerLib.isStopped() not found: events are NOT blocked.")
        } else {
            isStopped.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
            PatchLog.hooked(PATCH_NAME, isStopped, "isStopped() -> true")
        }

        val init = AppsFlyerInitFingerprint.methodOrNull
        if (init == null) {
            PatchLog.warn(PATCH_NAME, "AppsFlyerLib.init() not found: the SDK still initializes.")
        } else {
            init.addInstructions(0, "return-object p0")
            PatchLog.hooked(PATCH_NAME, init, "init() -> no-op")
        }

        if (isStopped == null && init == null) {
            PatchLog.warn(
                PATCH_NAME,
                "no AppsFlyerLib subclass found at all: either Pinterest dropped the SDK " +
                    "or it is no longer a subclass of com.appsflyer.AppsFlyerLib.",
            )
        }
    }
}
