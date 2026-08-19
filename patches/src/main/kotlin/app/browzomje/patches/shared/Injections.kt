package app.browzomje.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Quanti registri del frame sono occupati dai parametri di ingresso (`this` incluso per i
 * metodi d'istanza). I restanti — quelli **bassi**, `v0`, `v1`, … — sono i locali.
 *
 * `long` e `double` occupano due registri ciascuno: ignorarlo è l'errore classico che porta a
 * scrivere sopra un parametro credendo di usare un locale libero.
 */
fun MutableMethod.inputRegisterCount(): Int {
    val wideParameters = parameterTypes.count { it.toString() == "J" || it.toString() == "D" }
    val thisRegister = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return parameterTypes.size + wideParameters + thisRegister
}

/**
 * Sostituisce il comportamento del metodo iniettandogli in testa uno stub che esce subito.
 *
 * Il codice originale resta nel dex ma diventa irraggiungibile: è il modo più sicuro di
 * neutralizzare un metodo, perché non tocca né la firma né i chiamanti — l'app continua a
 * invocarlo normalmente e riceve il valore che decidiamo noi.
 *
 * **Contratto sui registri.** Uno stub che esce prima di qualunque istruzione originale può
 * scrivere liberamente sui registri bassi `v0`…`v<registersUsed - 1>`: o sono locali non ancora
 * inizializzati, o sono parametri che nessuno leggerà più. L'unico vincolo è **leggere prima di
 * scrivere**: se lo stub usa un parametro (per passarlo a un'extension) deve copiarlo via prima
 * di sovrascrivere quel registro. Non si può quindi pretendere che esistano locali liberi — R8
 * compila spesso un getter in `return-object p0`, senza nessun locale — e l'unica verifica
 * sensata è che il frame sia abbastanza ampio.
 *
 * @param smali lo stub. Deve terminare con un `return*` ed è compilato con il register count
 *     esistente del metodo, che non cambia.
 * @param registersUsed quanti registri bassi lo stub usa, `v0` compreso.
 */
fun MutableMethod.forceReturn(smali: String, registersUsed: Int = 1) {
    val implementation = implementation
        ?: throw IllegalStateException("$definingClass->$name has no implementation: cannot stub")

    check(implementation.registerCount >= registersUsed) {
        "$definingClass->$name has a ${implementation.registerCount} register frame, " +
            "but the stub needs $registersUsed"
    }

    addInstructions(0, smali.trimIndent())
}

/**
 * Fa uscire subito il metodo (`return-void`) se l'extension dice di sì, altrimenti lo lascia
 * proseguire immutato.
 *
 * È la forma più conservativa di neutralizzazione condizionale: il codice originale resta intatto
 * e viene saltato solo quando l'opzione è attiva, quindi l'interruttore nella schermata Morphe
 * funziona davvero a runtime invece di essere deciso in fase di patch.
 *
 * **Contratto sui registri.** L'iniezione avviene in testa al metodo, dove nessun locale è ancora
 * inizializzato: `v0` è quindi libero — ma solo se il frame ha almeno un registro oltre a quelli
 * occupati dai parametri. R8 compila alcuni metodi con un frame grande esattamente quanto i
 * parametri; lì `v0` *è* un parametro e sovrascriverlo prima di averlo letto lo distruggerebbe.
 * In quel caso non si inietta nulla e si restituisce `false`, così il chiamante può loggarlo
 * invece di produrre un APK che si rompe a runtime.
 *
 * @param condition riferimento smali a un metodo statico che restituisce `Z`, comprensivo di
 *     `invoke-static`. Deve lasciare il risultato pronto per `move-result`.
 * @return true se l'uscita anticipata è stata iniettata.
 */
fun MutableMethod.returnVoidWhen(condition: String): Boolean {
    val implementation = implementation ?: return false
    if (implementation.registerCount - inputRegisterCount() < 1) return false

    addInstructions(
        0,
        """
        $condition
        move-result v0
        if-eqz v0, :morphe_proceed
        return-void
        :morphe_proceed
        nop
        """.trimIndent(),
    )
    return true
}

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
        ?: throw IllegalStateException("$definingClass->$name has no implementation")
    val registerCount = implementation.registerCount

    val returnIndices = implementation.instructions
        .mapIndexedNotNull { index, instruction ->
            if (instruction.opcode.isReturn()) index else null
        }
        .reversed()

    if (returnIndices.isEmpty()) {
        throw IllegalStateException(
            "$definingClass->$name has no return instruction: cannot hook"
        )
    }

    for (index in returnIndices) {
        addInstructions(index, InlineSmaliCompiler.compile(smali, "", registerCount, true))
    }
    return returnIndices.size
}

/**
 * Inserisce codice **subito dopo la chiamata al costruttore della superclasse**, cioè nel primo
 * punto di un costruttore in cui `p0` è utilizzabile.
 *
 * **Il problema che risolve.** In un costruttore `p0` è `this`, ma solo *all'inizio*: i registri dei
 * parametri sono registri come gli altri, e appena il parametro è morto R8 li riusa come spazio di
 * lavoro. Nel costruttore del menu "…" del pin (94 registri, migliaia di istruzioni) `p0` alla fine
 * del metodo non contiene più la view ma un campo di analytics — motivo per cui le tre voci di menu
 * risultavano "agganciate" e non comparivano, con l'extension che riceveva l'oggetto sbagliato.
 * Nessun calcolo di indici può rimediare: non è l'indice a essere sbagliato, è il *momento*.
 *
 * **Perché dopo il super e non all'indice 0.** Prima della chiamata al costruttore della superclasse
 * l'oggetto non è inizializzato: chiamarci sopra un metodo di `View` è un errore. Subito dopo,
 * invece, la parte `View` esiste ed è usabile — ed è anche il punto in cui `p0` è certamente ancora
 * `this`, perché tutto il resto del costruttore lo userà per scrivere i propri campi.
 *
 * Chi si aggancia qui deve però tenere conto che l'oggetto è **appena nato**: i campi non sono
 * ancora valorizzati e le view figlie non sono ancora state aggiunte. Il lavoro va rimandato, per
 * esempio con `View.post(...)`.
 *
 * @return l'indice a cui è stato inserito il codice.
 * @throws IllegalStateException se il metodo non chiama nessun costruttore di un'altra classe.
 */
fun MutableMethod.addInstructionsAfterSuperConstructor(smali: String): Int {
    val implementation = implementation
        ?: throw IllegalStateException("$definingClass->$name has no implementation")

    val superCallIndex = implementation.instructions
        .withIndex()
        .indexOfFirst { (_, instruction) ->
            if (instruction.opcode != Opcode.INVOKE_DIRECT &&
                instruction.opcode != Opcode.INVOKE_DIRECT_RANGE
            ) {
                false
            } else {
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference != null &&
                    reference.name == "<init>" &&
                    reference.definingClass != definingClass
            }
        }

    if (superCallIndex < 0) {
        throw IllegalStateException(
            "$definingClass->$name does not call a super constructor: cannot hook after it"
        )
    }

    addInstructions(superCallIndex + 1, smali.trimIndent())
    return superCallIndex + 1
}

/**
 * Come [addInstructionsBeforeEveryReturn], ma lo smali è compilato **nel contesto del metodo
 * agganciato** invece che contro un frame anonimo. La differenza pratica è una sola, ed è grossa:
 * qui si possono usare i registri dei parametri per nome — `p0`, `p1`, … — e in particolare `p0`,
 * che nei metodi d'istanza è `this`.
 *
 * **Perché conta.** La versione con il frame anonimo obbliga a scrivere numeri di registro assoluti,
 * e quindi a calcolare a mano dove sia `this`: `registerCount - (parametri + 1)`. Quel conto ha due
 * modi di sbagliare — i parametri `long` e `double` occupano due registri ciascuno, e il frame non è
 * detto che sia largo esattamente quanto gli input — e quando sbaglia **non fallisce**: passa un
 * registro qualsiasi, cioè un oggetto a caso preso dai locali del metodo. La patch risulta applicata
 * e l'APK si costruisce; il difetto salta fuori solo a runtime, come un `ClassCastException` o un
 * silenzioso "non succede niente".
 *
 * È esattamente quello che era successo alle tre voci del menu "…" del pin su 14.32.0: `addView`
 * veniva chiamata su un oggetto di analytics invece che sulla view del menu. Da qui la regola:
 * **quando serve `this`, si scrive `p0` e si lascia decidere all'assemblatore**, non lo si calcola.
 *
 * @param smali codice da inserire. Deve essere idempotente: su un metodo con più uscite viene
 *     compilato più volte (ma ne viene eseguita una sola per invocazione).
 * @return quante uscite sono state agganciate.
 */
fun MutableMethod.addInstructionsBeforeEveryReturnUsingParameters(smali: String): Int {
    val implementation = implementation
        ?: throw IllegalStateException("$definingClass->$name has no implementation")

    val returnIndices = implementation.instructions
        .mapIndexedNotNull { index, instruction ->
            if (instruction.opcode.isReturn()) index else null
        }
        .reversed()

    if (returnIndices.isEmpty()) {
        throw IllegalStateException(
            "$definingClass->$name has no return instruction: cannot hook"
        )
    }

    // A ritroso: inserire istruzioni sposta in avanti gli indici di tutto ciò che segue.
    for (index in returnIndices) {
        addInstructions(index, smali.trimIndent())
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
        ?: throw IllegalStateException("$definingClass->$name has no implementation")
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
            "$definingClass->$name has no return-object: does not return an object"
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
