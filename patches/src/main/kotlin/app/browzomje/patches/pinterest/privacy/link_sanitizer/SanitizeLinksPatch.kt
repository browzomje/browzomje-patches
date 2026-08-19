package app.browzomje.patches.pinterest.privacy.link_sanitizer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SANITIZER = "Lapp/browzomje/extension/pinterest/UrlSanitizer;"

/**
 * I due metodi da agganciare, descritti campo per campo.
 *
 * <p>Il confronto avviene sui campi della [MethodReference] e non sul suo `toString()`: la firma va
 * confrontata **per intero**, tipi dei parametri compresi, e i campi lo rendono verificabile a
 * colpo d'occhio invece di dipendere dal formato con cui dexlib2 stampa un riferimento.
 *
 * <p>Perché sia per intero: `Intent.putExtra` ha una ventina di overload che nel bytecode sono
 * metodi distinti con lo stesso nome. Agganciare quello sbagliato — per esempio
 * `putExtra(String, Parcelable)`, con cui si allega l'immagine da condividere — non dà nessun
 * errore in fase di patch: dà un `VerifyError` al primo caricamento della classe, cioè un crash a
 * runtime lontano dal punto in cui si è sbagliato.
 */
private data class CalleeSignature(
    val definingClass: String,
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
) {
    fun matches(reference: MethodReference) =
        reference.definingClass == definingClass &&
            reference.name == name &&
            reference.returnType == returnType &&
            reference.parameterTypes.map { it.toString() } == parameterTypes
}

private val PUT_EXTRA_STRING_STRING = CalleeSignature(
    definingClass = "Landroid/content/Intent;",
    name = "putExtra",
    parameterTypes = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "Landroid/content/Intent;",
)

private val SET_PRIMARY_CLIP = CalleeSignature(
    definingClass = "Landroid/content/ClipboardManager;",
    name = "setPrimaryClip",
    parameterTypes = listOf("Landroid/content/ClipData;"),
    returnType = "V",
)

/**
 * I registri degli argomenti di una `invoke-virtual`, `this` compreso e in ordine.
 *
 * <p>Esistono due codifiche per la stessa chiamata e vanno lette diversamente: la forma normale
 * tiene fino a cinque registri elencati uno per uno (`{v3, v4, v5}`), la forma `/range` tiene solo
 * il primo registro e quanti sono (`{v20 .. v22}`). Confonderle vuol dire iniettare codice che
 * legge il registro sbagliato: l'APK si costruisce lo stesso e si rompe a runtime, che è il modo
 * peggiore di sbagliare.
 *
 * @return i registri, oppure null se l'istruzione non è una invocazione con registri.
 */
private fun Instruction.argumentRegisters(): List<Int>? = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG)
        .take(registerCount)

    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()

    else -> null
}

/**
 * Applica [inject] a ogni punto in cui [this] chiama **esattamente** [calleeSignature].
 *
 * <p>Il confronto è per uguaglianza sulla firma completa, tipi dei parametri compresi, e non è un
 * dettaglio di stile: `Intent.putExtra` ha una ventina di overload che nel bytecode sono metodi
 * diversi ma condividono il nome. Un confronto per prefisso aggancerebbe anche
 * `putExtra(String, Parcelable)` — la chiamata con cui si allega l'immagine da condividere — e
 * passerebbe un `Uri` a un metodo che dichiara una `String`. Il risultato non è un errore di
 * compilazione ma un `VerifyError` al primo caricamento della classe, cioè un crash a runtime in un
 * punto lontano dalla patch.
 *
 * <p>Si procede **dall'ultima chiamata alla prima**: inserire istruzioni sposta in avanti gli indici
 * di tutto ciò che segue, quindi andando in avanti il secondo punto di chiamata verrebbe patchato
 * nel posto sbagliato. È lo stesso motivo per cui `addInstructionsBeforeEveryReturn` itera a
 * ritroso.
 *
 * @param callee il metodo chiamato, descritto per intero.
 * @param inject riceve i registri degli argomenti della chiamata e restituisce lo smali da inserire
 *     subito prima, oppure null per saltare quel punto di chiamata.
 * @return quanti punti di chiamata sono stati agganciati.
 */
private fun MutableMethod.hookCallsTo(
    callee: CalleeSignature,
    inject: (List<Int>) -> String?,
): Int {
    val instructions = implementation?.instructions?.toList() ?: return 0

    val callSites = instructions
        .mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
            ) {
                return@mapIndexedNotNull null
            }
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            if (reference == null || !callee.matches(reference)) {
                return@mapIndexedNotNull null
            }
            val registers = instruction.argumentRegisters() ?: return@mapIndexedNotNull null
            index to registers
        }
        .reversed()

    var hooked = 0
    for ((index, registers) in callSites) {
        val smali = inject(registers) ?: continue
        addInstructions(index, smali)
        hooked++
    }
    return hooked
}

/**
 * `invoke-static` con registri elencati accetta solo `v0`–`v15`: oltre serve la forma `/range`, che
 * però richiede registri consecutivi — e i registri di una chiamata già compilata non lo sono per
 * forza. Nei rari metodi con un frame così largo si salta il punto di chiamata invece di produrre
 * smali che non compila.
 */
private fun List<Int>.addressableInNonRangeInvoke() = all { it < 16 }

private const val SHARE_PATCH_NAME = "Sanitize shared links"

/**
 * Toglie il tracciamento dai link che escono dall'app attraverso il foglio di condivisione di
 * Android.
 *
 * <p>**Il problema.** Il testo che Pinterest passa a WhatsApp, Telegram, alle mail o agli SMS non è
 * il link del pin: è il link del pin più una coda di parametri (`utm_source`, `utm_campaign`,
 * `sender`, `sfo`, …) con cui Pinterest misura quanto e da chi viene ri-condiviso fuori dalla
 * piattaforma. Peggio, spesso non è nemmeno un link a pinterest.com ma uno short link `pin.it`, in
 * cui l'identificatore di chi ha condiviso sta *dentro lo slug*: togliere i parametri non servirebbe
 * a niente, il link va risolto.
 *
 * <p>**Dove si aggancia, e perché lì.** Non alla fabbrica dell'intent di condivisione — ce n'è più
 * d'una (testo, immagine con didascalia, deep link) e ognuna ha una forma diversa — ma all'unico
 * gesto che hanno tutte in comune: scrivere il testo dentro l'intent con
 * `Intent.putExtra(String, String)`. È una chiamata al framework Android, non a codice di Pinterest:
 * non è offuscata, non cambia nome, e ogni strada di condivisione presente o futura ci passa.
 *
 * <p>La contropartita è che l'aggancio è largo — `putExtra` serve a mille cose diverse — quindi il
 * lavoro vero lo fa `UrlSanitizer.sanitizeExtra`, che guarda la chiave a runtime e restituisce il
 * valore intatto per qualunque extra che non sia `EXTRA_TEXT`. La chiave in fase di patch non è
 * leggibile: nel bytecode è un registro.
 *
 * <p>Si può spegnere dalla schermata Morphe: risolvere uno short link costa una richiesta di rete
 * mentre l'utente aspetta il foglio di condivisione.
 */
@Suppress("unused")
val sanitizeSharedLinksPatch = bytecodePatch(
    name = SHARE_PATCH_NAME,
    description = "Strips tracking parameters from the link on the Android share sheet and resolves pin.it short links to the plain pin link. Switchable from Morphe settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val matches = PutExtraStringCallFingerprint.matchAllOrNull()
        if (matches.isNullOrEmpty()) {
            PatchLog.warn(
                SHARE_PATCH_NAME,
                "no call to Intent.putExtra(String, String) found: shared links are NOT cleaned.",
            )
            return@execute
        }

        var hooked = 0
        var skipped = 0
        for (match in matches) {
            hooked += match.method.hookCallsTo(PUT_EXTRA_STRING_STRING) { registers ->
                // invoke-virtual putExtra(String, String): (this, chiave, valore).
                if (registers.size < 3) return@hookCallsTo null
                val keyRegister = registers[1]
                val valueRegister = registers[2]

                if (!listOf(keyRegister, valueRegister).addressableInNonRangeInvoke()) {
                    skipped++
                    return@hookCallsTo null
                }

                """
                    invoke-static { v$keyRegister, v$valueRegister }, ${SANITIZER}->sanitizeExtra(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$valueRegister
                """
            }
        }

        PatchLog.info(
            SHARE_PATCH_NAME,
            "hooked $hooked Intent.putExtra call sites in ${matches.size} methods" +
                if (skipped > 0) " ($skipped skipped: registers above v15)" else "",
        )
    }
}

private const val COPY_PATCH_NAME = "Sanitize copied links"

/**
 * Come [sanitizeSharedLinksPatch], ma per gli appunti di sistema.
 *
 * <p>"Copia link" nel menu del pin produce uno short link `pin.it/<slug>`: chi lo incolla in una
 * chat sta passando, senza saperlo, un identificatore di sé stesso. Qui ogni scrittura negli appunti
 * passa prima da `UrlSanitizer.sanitizeClipData`, che risolve lo short link nel link canonico del
 * pin.
 *
 * <p>L'aggancio è su `ClipboardManager.setPrimaryClip`, cioè su tutte le strade insieme: il menu del
 * pin, la pressione lunga su un URL nei commenti, i tasti "copia invito". Il sanificatore
 * restituisce **lo stesso oggetto** quando non c'è niente da ripulire, quindi tutti gli usi non-URL
 * degli appunti (testo di ricerca, bozze di commento) non pagano nulla.
 *
 * <p>Non va confuso con "Copy direct link", che è un'altra cosa: quella *aggiunge* una voce al menu
 * del pin per copiare il link diretto al file multimediale. Questa ripulisce quello che Pinterest
 * copia già di suo.
 */
@Suppress("unused")
val sanitizeCopiedLinksPatch = bytecodePatch(
    name = COPY_PATCH_NAME,
    description = "Turns the pin.it link that \"Copy link\" copies, whose slug identifies who shared it, into the plain pin link. Switchable from Morphe settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val matches = SetPrimaryClipCallFingerprint.matchAllOrNull()
        if (matches.isNullOrEmpty()) {
            PatchLog.warn(
                COPY_PATCH_NAME,
                "no call to ClipboardManager.setPrimaryClip found: copied links are NOT cleaned.",
            )
            return@execute
        }

        var hooked = 0
        var skipped = 0
        for (match in matches) {
            hooked += match.method.hookCallsTo(SET_PRIMARY_CLIP) { registers ->
                // invoke-virtual setPrimaryClip(ClipData): (this, clip).
                if (registers.size < 2) return@hookCallsTo null
                val clipRegister = registers[1]

                if (clipRegister >= 16) {
                    skipped++
                    return@hookCallsTo null
                }

                """
                    invoke-static { v$clipRegister }, ${SANITIZER}->sanitizeClipData(Landroid/content/ClipData;)Landroid/content/ClipData;
                    move-result-object v$clipRegister
                """
            }
        }

        PatchLog.info(
            COPY_PATCH_NAME,
            "hooked $hooked setPrimaryClip call sites in ${matches.size} methods" +
                if (skipped > 0) " ($skipped skipped: registers above v15)" else "",
        )
    }
}
