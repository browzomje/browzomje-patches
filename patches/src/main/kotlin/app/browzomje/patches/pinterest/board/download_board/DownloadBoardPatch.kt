package app.browzomje.patches.pinterest.board.download_board

import app.morphe.patcher.patch.bytecodePatch
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

        val exits = method.addReturnValueTransform(
            "$EXTENSION_CLASS->decorateBoardOptionsMenu(Ljava/lang/Object;)Ljava/lang/Object;",
        )
        PatchLog.hooked(PATCH_NAME, method, "board menu option, $exits exits")
    }
}
