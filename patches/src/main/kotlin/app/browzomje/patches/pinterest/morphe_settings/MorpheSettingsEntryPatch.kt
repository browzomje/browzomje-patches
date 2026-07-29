package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val RUNTIME_NAMES_CLASS = "Lapp/browzomje/extension/pinterest/MorpheRuntimeNames;"
private const val PATCH_NAME = "Morphe settings entry"

/**
 * Aggiunge una voce "Morphe" alla lista (un `java.util.ArrayList` mutabile) delle Impostazioni
 * account, subito sotto l'header "Impostazioni"/"Account".
 *
 * La riga riusa la classe che Pinterest impiega per i link esterni (quella di "Teen safety
 * resources"), il cui costruttore prende l'URL da aprire: cliccandola l'app fa
 * `Uri.parse(url)` → `new Intent(ACTION_VIEW)` → `startActivity`, un Intent generico che
 * risolve verso il nostro intent-filter `morphe://` (vedi MorpheSettingsResourcePatch).
 *
 * Il nome di quella classe è offuscato e cambia a ogni versione (`i1` su 14.24, `j1` su 14.28):
 * lo si ricava qui dal dex e lo si deposita in `MorpheRuntimeNames` lato extension, così a
 * runtime non c'è nessun nome hardcodato. Vedi pinterest/OBFUSCATION_MAP.md.
 */
@Suppress("unused")
val morpheSettingsEntryPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Aggiunge la voce \"Morphe\" alla lista delle Impostazioni account, per aprire " +
        "la schermata dei toggle.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(morpheSettingsManifestPatch, morpheSettingsLabelPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val method = SettingsMenuListBuilderFingerprint.method
        val instructions = method.implementation!!.instructions
        PatchLog.hooked(PATCH_NAME, method, "builder della lista Impostazioni")

        // 1) Ancora: la costruzione del primo header di sezione, `new <header>(int)`.
        //    Corrisponde a `arrayList.add(new Header(settings_main_header_settings|account))`,
        //    l'header "Impostazioni"/"Account", SEMPRE presente. Il vecchio ancoraggio su
        //    "Support" era una sezione condizionale: per certi account non veniva costruita e
        //    la voce Morphe non compariva.
        //
        //    Attenzione: NON basta prendere il primo `<init>(int)` del package. Ci sono altre
        //    righe con quella firma — su 14.28.0 il primo in ordine di programma è uno
        //    spaziatore costruito dentro un `if`, e ancorandosi lì la voce Morphe compariva
        //    solo per alcuni account. L'header si distingue perché è l'unica classe usata per
        //    PIÙ sezioni ("Impostazioni", "Login", "Supporto", …): si sceglie quindi la classe
        //    `<init>(int)` costruita più volte nel metodo, e di quella la prima occorrenza.
        val intConstructorCounts = instructions
            .mapNotNull { it.settingsRowConstructorWithParameters(listOf("I")) }
            .groupingBy { it }
            .eachCount()
        check(intConstructorCounts.isNotEmpty()) {
            "Nessuna riga delle Impostazioni con costruttore (int) nel builder"
        }

        val headerClass = intConstructorCounts.maxByOrNull { it.value }!!
        if (headerClass.value < 2) {
            PatchLog.warn(
                PATCH_NAME,
                "l'header di sezione candidato (${headerClass.key}) compare una volta sola: " +
                    "potrebbe essere uno spaziatore dentro un blocco condizionale e la voce " +
                    "Morphe potrebbe non comparire per tutti gli account.",
            )
        }
        PatchLog.info(
            PATCH_NAME,
            "header di sezione: ${headerClass.key} (${headerClass.value} sezioni)",
        )

        val anchorIndex = instructions.indexOfFirst {
            it.settingsRowConstructorWithParameters(listOf("I")) == headerClass.key
        }
        check(anchorIndex != -1) { "Header di sezione delle Impostazioni non trovato nel builder" }

        // 2) Dalla ancora, la prima `List.add(Object)`: aggiunge l'header stesso, e il suo
        //    registro receiver è la lista in cui accodare anche la nostra voce.
        val addIndex = instructions.withIndex().drop(anchorIndex + 1).first { (_, instruction) ->
            (instruction.opcode == Opcode.INVOKE_VIRTUAL || instruction.opcode == Opcode.INVOKE_INTERFACE) &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let {
                    it.name == "add" && it.parameterTypes.size == 1
                } == true
        }.index

        val listRegister = (instructions[addIndex] as FiveRegisterInstruction).registerC
        val registerCount = method.implementation!!.registerCount

        // 3) Il nome offuscato della classe della riga "link esterno": è l'unico costruttore
        //    del package delle righe che prende una sola String (l'URL di destinazione).
        val rowClassType = instructions
            .firstNotNullOfOrNull { it.settingsRowConstructorWithParameters(listOf("Ljava/lang/String;")) }
        check(rowClassType != null) {
            "Riga \"link esterno\" delle Impostazioni non trovata: senza di essa non si può " +
                "costruire la voce Morphe. Vedi pinterest/OBFUSCATION_MAP.md."
        }

        // Da descrittore JVM (Lcom/pinterest/...;) a nome per Class.forName (com.pinterest....).
        val rowClassName = rowClassType.removePrefix("L").removeSuffix(";").replace('/', '.')
        PatchLog.info(PATCH_NAME, "classe della riga risolta dal dex: $rowClassName")

        // 4) L'iniezione della voce: una sola invoke, un solo registro (la lista), quindi
        //    nessun registro d'appoggio da trovare in mezzo a un metodo enorme.
        //
        //    ATTENZIONE all'ordine: si inserisce PRIMA questa, che sta più avanti nel metodo.
        //    Inserire in testa (punto 5) sposterebbe tutti gli indici successivi e addIndex
        //    non sarebbe più valido.
        method.addInstructions(
            addIndex + 1,
            InlineSmaliCompiler.compile(
                "invoke-static/range { v$listRegister .. v$listRegister }, " +
                    "$EXTENSION_CLASS->appendMorpheSettingsEntry(Ljava/lang/Object;)V",
                "",
                registerCount,
                true,
            ),
        )

        // 5) Comunicazione del nome risolto all'extension, iniettata IN TESTA al metodo: è
        //    l'unico punto in cui un registro basso è certamente libero, perché non c'è nessuna
        //    istruzione prima che possa averci messo qualcosa di vivo. Al punto 4, in mezzo al
        //    metodo, non avremmo potuto: lì tutti i registri bassi possono contenere valori vivi
        //    e servirebbe un'analisi di liveness per sceglierne uno.
        method.addInstructions(
            0,
            InlineSmaliCompiler.compile(
                """
                const-string v0, "$rowClassName"
                invoke-static { v0 }, $RUNTIME_NAMES_CLASS->setSettingsRowClass(Ljava/lang/String;)V
                """.trimIndent(),
                "",
                registerCount,
                true,
            ),
        )
    }
}

/**
 * @return il descrittore della classe costruita, se l'istruzione è un `invoke-direct` su un
 *     `<init>` del package delle righe delle Impostazioni con esattamente quei parametri.
 */
private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.settingsRowConstructorWithParameters(
    parameters: List<String>,
): String? {
    if (opcode != Opcode.INVOKE_DIRECT) return null
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return null
    if (reference.name != "<init>") return null
    if (!reference.definingClass.startsWith(SETTINGS_ROW_PACKAGE)) return null
    if (reference.parameterTypes.map { it.toString() } != parameters) return null
    return reference.definingClass
}
