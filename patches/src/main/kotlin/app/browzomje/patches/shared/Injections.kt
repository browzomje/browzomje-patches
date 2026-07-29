package app.browzomje.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Inserisce del codice prima di **ogni** uscita del metodo, non solo della prima.
 *
 * Il modo abituale — `instructions.indexOfFirst { it.opcode == RETURN_VOID }` — presuppone che
 * il metodo abbia una sola uscita. Su Pinterest spesso non è vero: per esempio il metodo che
 * costruisce la barra di navigazione (`LegoFloatingBottomNavBar.W0()` su 14.28.0) ha due
 * `return` anticipati in rami d'errore, che nel bytecode vengono *prima* del return finale.
 * Ancorandosi al primo, l'hook finirebbe su un ramo che non viene quasi mai eseguito: la patch
 * risulterebbe applicata ma non farebbe nulla, ed è un caso difficilissimo da diagnosticare.
 *
 * Le iniezioni si fanno dall'ultima alla prima: inserire istruzioni sposta gli indici di tutto
 * ciò che segue, quindi procedendo a ritroso gli indici già calcolati restano validi.
 *
 * @param smali codice da inserire. Deve essere idempotente: su un metodo con più uscite viene
 *     compilato più volte (ma ne viene eseguita una sola per invocazione).
 * @return quante uscite sono state agganciate.
 */
fun MutableMethod.addInstructionsBeforeEveryReturn(smali: String): Int {
    val implementation = implementation
        ?: throw IllegalStateException("$definingClass->$name non ha implementazione")
    val registerCount = implementation.registerCount

    val returnIndices = implementation.instructions
        .mapIndexedNotNull { index, instruction ->
            if (instruction.opcode.isReturn()) index else null
        }
        .reversed()

    if (returnIndices.isEmpty()) {
        throw IllegalStateException(
            "$definingClass->$name non ha nessuna istruzione di return: impossibile agganciarsi"
        )
    }

    for (index in returnIndices) {
        addInstructions(index, InlineSmaliCompiler.compile(smali, "", registerCount, true))
    }
    return returnIndices.size
}

/**
 * Fa passare il valore restituito dal metodo attraverso un metodo dell'extension, che può
 * sostituirlo. Serve per gli oggetti immutabili dell'app — come il menu "…" della bacheca — che
 * non si possono modificare dopo la costruzione.
 *
 * Su ogni `return-object vX` inserisce:
 * ```
 * invoke-static/range { vX .. vX }, <transformer>
 * move-result-object vX
 * check-cast vX, <tipo di ritorno del metodo>
 * ```
 * Si riusa lo stesso registro: vX contiene già il valore di ritorno ed è morto subito dopo,
 * quindi sovrascriverlo è sicuro e non serve un'analisi di liveness per trovarne uno libero.
 *
 * Il `check-cast` è obbligatorio, non prudenziale. Il transformer è dichiarato `(Object) ->
 * Object`, quindi dopo `move-result-object` il verificatore sa solo che in vX c'è un `Object`;
 * se il metodo agganciato dichiara un tipo più specifico, ART rifiuta la classe intera al primo
 * caricamento con `VerifyError: … returning 'Reference java.lang.Object', but expected from
 * declaration 'Reference: wt2.y'`.
 *
 * @param transformer riferimento smali a un metodo statico `(Ljava/lang/Object;)Ljava/lang/Object;`.
 *     Deve restituire il valore originale quando non ha nulla da modificare: un `null` qui
 *     diventerebbe un NPE dentro l'app.
 * @return quante uscite sono state agganciate.
 */
fun MutableMethod.addReturnValueTransform(transformer: String): Int {
    val implementation = implementation
        ?: throw IllegalStateException("$definingClass->$name non ha implementazione")
    val registerCount = implementation.registerCount

    // I registri vanno letti prima di iniettare: ogni inserimento rimaneggia la lista di
    // istruzioni, e rileggerla a metà lavoro darebbe indici e operandi sfasati.
    val returns = implementation.instructions
        .mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_OBJECT) {
                index to (instruction as OneRegisterInstruction).registerA
            } else {
                null
            }
        }
        .reversed()

    if (returns.isEmpty()) {
        throw IllegalStateException(
            "$definingClass->$name non ha nessun return-object: non restituisce un oggetto"
        )
    }

    for ((index, register) in returns) {
        addInstructions(
            index,
            InlineSmaliCompiler.compile(
                """
                invoke-static/range { v$register .. v$register }, $transformer
                move-result-object v$register
                check-cast v$register, $returnType
                """.trimIndent(),
                "",
                registerCount,
                true,
            ),
        )
    }
    return returns.size
}

private fun Opcode.isReturn() = this == Opcode.RETURN_VOID ||
    this == Opcode.RETURN ||
    this == Opcode.RETURN_OBJECT ||
    this == Opcode.RETURN_WIDE
