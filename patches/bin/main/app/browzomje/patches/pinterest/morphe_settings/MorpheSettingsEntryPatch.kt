package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

/**
 * Aggiunge una voce "Morphe" alla lista (un java.util.ArrayList mutabile) delle Impostazioni
 * account, subito sotto l'header "Impostazioni"/"Account". Riusa
 * com.pinterest.feature.settings.menu.model.i1 (stesso tipo di riga di "Teen safety resources").
 * Click verificato in sources (non su device): li1.o.Za(str, p0) -> v32.c.l() -> Uri.parse(str) ->
 * new Intent(ACTION_VIEW).setData(uri) -> startActivity — un Intent generico, non specifico al
 * dominio Pinterest, quindi risolve verso il nostro intent-filter morphe://. Vedi RECAP.md.
 */
@Suppress("unused")
val morpheSettingsEntryPatch = bytecodePatch(
    name = "Morphe settings entry",
    description = "Aggiunge la voce \"Morphe\" alla lista delle Impostazioni account, per aprire " +
        "la schermata dei toggle. Sperimentale: vedi RECAP.md.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    dependsOn(morpheSettingsManifestPatch, morpheSettingsLabelPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val method = SettingsMenuListBuilderFingerprint.method
        val instructions = method.implementation!!.instructions

        // Ancora: invoke-direct sul costruttore di d1(int) (header di sezione, sempre esistente),
        // il primo in ordine nel metodo. Corrisponde a:
        //   arrayList.add(new d1(z14 ? settings_main_header_settings : settings_main_header_account))
        // cioè l'header "Impostazioni"/"Account" — SEMPRE presente, a differenza del vecchio
        // ancoraggio su settings_main_header_support (sezione condizionale, non sempre costruita:
        // la voce Morphe non compariva per gli account dove quella sezione manca). d1 viene
        // costruito anche altrove nello stesso metodo (header "Login", "Support", "Elimina
        // account"), ma questo è il primo in ordine di programma.
        val anchorIndex = instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_DIRECT &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let {
                    it.definingClass == "Lcom/pinterest/feature/settings/menu/model/d1;" &&
                        it.name == "<init>" &&
                        it.parameterTypes == listOf("I")
                } == true
        }
        check(anchorIndex != -1) { "Ancora d1.<init>(I) non trovata in e1.invoke" }

        // Dalla ancora, la prima ArrayList/List.add(Object): aggiunge l'header stesso, e il suo
        // registro receiver è la lista in cui accodare anche la nostra voce.
        val addIndex = instructions.withIndex().drop(anchorIndex + 1).first { (_, instruction) ->
            (instruction.opcode == Opcode.INVOKE_VIRTUAL || instruction.opcode == Opcode.INVOKE_INTERFACE) &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let {
                    it.name == "add" && it.parameterTypes.size == 1
                } == true
        }.index

        val listRegister = (instructions[addIndex] as FiveRegisterInstruction).registerC
        val registerCount = method.implementation!!.registerCount

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
    }
}
