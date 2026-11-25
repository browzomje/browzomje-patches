package app.browzomje.patches.pinterest.board.download_board

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addReturnValueTransform

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Download board"

/**
 * Aggiunge una voce di download al menu "…" della bacheca, quello in alto a destra che apre il
 * foglio "Opzioni" con "Unisci bacheche" e "Archivia bacheca".
 *
 * Il menu della bacheca è l'unico punto in cui il contesto è certo: se è aperto, siamo dentro
 * una bacheca. Il menu del pin invece dichiara la schermata di provenienza, e aprendo un pin
 * dichiara di solito `RELATED_PINS` anche quando lo si apre da una bacheca.
 *
 * Il menu è immutabile — intestazione, voci e callback vanno tutti al costruttore — quindi se ne
 * intercetta il valore di ritorno e lo si sostituisce con una copia che ha una riga in più e una
 * callback che riconosce quella riga. Vedi `BoardMenuDecorator` lato extension.
 *
 * I pin da scaricare li raccoglie l'hook già esistente sulle risposte del feed (vedi
 * `BoardDownloadHandler`): nessuna patch tocca la rete.
 */
@Suppress("unused")
val downloadBoardPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Adds an option to the board's \"…\" menu to bulk download the images and videos of the loaded pins.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) L'enum delle voci del menu bacheca, riconosciuto dai nomi delle sue costanti:
        //    è il punto fisso da cui si arriva a tutto il resto.
        val optionsEnum = classDefByOrNull { it.isBoardOptionsEnum() }
        checkNotNull(optionsEnum) {
            "Board menu options enum not found (expected constants: Edit, Merge, " +
                "Archive, Unarchive, PreviewBoard). See pinterest/OBFUSCATION_MAP.md."
        }
        PatchLog.info(PATCH_NAME, "board menu options enum: ${optionsEnum.type}")

        // 2) Il builder che quell'enum lo consuma.
        val builderClass = classDefByOrNull { classDef ->
            classDef.methods.any { it.isBoardOptionsMenuBuilder(optionsEnum.type) }
        }
        checkNotNull(builderClass) {
            "Board menu builder not found: no static method " +
                "(List, Function1, …) using ${optionsEnum.type}."
        }

        val method = mutableClassDefBy(builderClass).methods
            .first { it.isBoardOptionsMenuBuilder(optionsEnum.type) }

        if (packageMetadata.versionName == "14.45.0") {
            val instructions = method.implementation!!.instructions
            val rowConstructor = instructions.indices.single { index ->
                val instruction = instructions[index]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_DIRECT_RANGE && ref?.toString() ==
                    "Lt93/y;-><init>(IILt93/b0;Ljava/lang/String;Ljava/lang/String;Lt93/u;" +
                    "Ljava/lang/Integer;Ljava/lang/String;Ll72/t;Ld52/o0;I)V"
            }
            val range = instructions[rowConstructor] as RegisterRangeInstruction
            check(range.startRegister == 9 && range.registerCount == 12 && optionsEnum.type == "Ldy0/a;")
            // v3 is the BoardOption enum. v5 is dead after the title ID is copied to v10.
            // Clear the constructor's default-icon bit after supplying the native Gestalt icon.
            method.addInstructions(rowConstructor, """
                const-class v5, Ld52/o0;
                invoke-static {v3, v5}, Lapp/browzomje/extension/pinterest/BoardMenuRowFactory;->boardOptionIcon(Ljava/lang/Enum;Ljava/lang/Class;)Ljava/lang/Object;
                move-result-object v19
                check-cast v19, Ld52/o0;
                move/from16 v5, v20
                and-int/lit16 v5, v5, -0x1001
                move/from16 v20, v5
            """.trimIndent())
            PatchLog.hooked(PATCH_NAME, method, "native board option icons")
        }

        val exits = method.addReturnValueTransform(
            "$EXTENSION_CLASS->decorateBoardOptionsMenu(Ljava/lang/Object;)Ljava/lang/Object;",
        )
        PatchLog.hooked(PATCH_NAME, method, "board menu option, $exits exits")
    }
}
