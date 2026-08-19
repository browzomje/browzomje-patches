package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.inputRegisterCount
import com.android.tools.smali.dexlib2.AccessFlags

private const val PATCH_NAME = "Morphe runtime names"
private const val RUNTIME_NAMES_CLASS = "Lapp/browzomje/extension/pinterest/MorpheRuntimeNames;"

private const val TOAST_CONTAINER = "Lcom/pinterest/gestalt/toast/PinterestToastContainer;"
private const val BASE_TOAST = "Lcom/pinterest/gestalt/toast/BaseGestaltToast;"

/**
 * `Application.onCreate`, il primo codice dell'app che gira: qui si consegnano al runtime i nomi
 * offuscati che le patch hanno risolto leggendo il dex.
 *
 * Classe e metodo hanno entrambi nomi puliti — la classe perché è dichiarata nel manifest, quindi
 * R8 non può toccarla, il metodo perché è un override del framework.
 */
internal object ApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/ReleaseHiltApplication;",
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
)

/**
 * I modelli di toast di Pinterest costruibili con un testo: `<init>(String testo, int durataMs)`.
 *
 * <p>**Come li si trova senza nominarli.** Un modello di toast è una classe che sa fabbricarsi la
 * propria view: espone `BaseGestaltToast a(PinterestToastContainer)`. Quei due tipi hanno nomi
 * puliti — stanno nella libreria di componenti Gestalt, che R8 non rinomina — quindi il metodo si
 * riconosce dalla firma senza sapere niente della classe che lo definisce. Fra le classi che lo
 * implementano, quelle costruibili con `(String, int)` sono i toast "di testo": è la forma che
 * serve a noi.
 *
 * <p>Su 14.32.0 ne risultano due: il toast normale e la variante d'errore, che estende il normale.
 * Si distinguono in [runtimeNamesPatch] senza guardarne i nomi.
 */
internal object TextToastModelFingerprint : Fingerprint(
    returnType = BASE_TOAST,
    parameters = listOf(TOAST_CONTAINER),
    custom = { _, classDef ->
        classDef.methods.any { method ->
            method.name == "<init>" &&
                method.parameters.size == 2 &&
                method.parameters[0].type == "Ljava/lang/String;" &&
                method.parameters[1].type == "I"
        }
    },
)

/**
 * Consegna al runtime il nome della classe con cui si costruisce un toast nativo di Pinterest.
 *
 * <p>**Perché serve una patch e non basta l'extension.** Il toast di Pinterest non si costruisce
 * direttamente: si crea un *modello* e lo si consegna al contenitore dei toast, che ne ricava la
 * view e la anima. Il contenitore ha un nome pulito e lo si trova a runtime, il modello no. Per
 * anni si è provato a scriverne il nome offuscato a mano, uno per versione: su 14.32.0 quei nomi
 * esistono ancora ma appartengono ad altre classi — `ir2.f` è una classe vuota e `ir2.h` è
 * `SmartBlocksConversation` — quindi non falliva niente, si costruiva l'oggetto sbagliato e non
 * compariva nessun toast.
 *
 * <p>Qui il nome si **ricava dal dex dell'APK che si sta patchando** e si deposita in
 * `MorpheRuntimeNames` con una chiamata iniettata in testa a `Application.onCreate`. A ogni nuova
 * versione di Pinterest il nome viene risolto di nuovo da sé.
 *
 * <p>La distinzione fra il toast normale e la variante d'errore non guarda i nomi: la variante
 * d'errore **estende** quello normale, quindi fra i candidati si tiene quello la cui superclasse
 * non è a sua volta un candidato.
 */
@Suppress("unused")
val runtimeNamesPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Resolves the obfuscated class Pinterest uses to build a text toast and hands it " +
        "to the extension at startup, so Morphe's messages look exactly like the app's own.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val onCreate = ApplicationOnCreateFingerprint.methodOrNull
        if (onCreate == null) {
            PatchLog.warn(
                PATCH_NAME,
                "ReleaseHiltApplication.onCreate() not found: no runtime name can be delivered.",
            )
            return@execute
        }

        val candidates = TextToastModelFingerprint.matchAllOrNull().orEmpty()
        if (candidates.isEmpty()) {
            PatchLog.warn(
                PATCH_NAME,
                "no text toast model found: Morphe will draw its own toast instead.",
            )
            return@execute
        }

        // La variante d'errore estende quella normale: si tiene la classe la cui superclasse non è
        // anch'essa un candidato. Non si guarda nessun nome.
        val candidateTypes = candidates.map { it.classDef.type }.toSet()
        val plain = candidates.firstOrNull { it.classDef.superclass !in candidateTypes }
            ?: candidates.first()

        // Da descrittore smali (`Lzt2/e;`) a nome Java (`zt2.e`), che è quello che vuole
        // Class.forName lato extension.
        val className = plain.classDef.type
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.')

        // v0 deve essere un locale, non un parametro: `onCreate` non ha argomenti, quindi basta un
        // registro oltre a `this`. Se il frame fosse più stretto si rinuncia invece di corrompere
        // `this` prima che il costruttore originale lo usi.
        val implementation = onCreate.implementation
        if (implementation == null ||
            implementation.registerCount - onCreate.inputRegisterCount() < 1
        ) {
            PatchLog.warn(
                PATCH_NAME,
                "Application.onCreate() has no free register: cannot deliver $className.",
            )
            return@execute
        }

        onCreate.addInstructions(
            0,
            """
                const-string v0, "$className"
                invoke-static { v0 }, $RUNTIME_NAMES_CLASS->setTextToastClass(Ljava/lang/String;)V
            """,
        )
        PatchLog.info(
            PATCH_NAME,
            "text toast model resolved: $className (${candidates.size} candidates)",
        )
    }
}
