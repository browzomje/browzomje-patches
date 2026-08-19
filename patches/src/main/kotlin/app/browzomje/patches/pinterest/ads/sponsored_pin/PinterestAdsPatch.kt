package app.browzomje.patches.pinterest

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Disable ads"

@Suppress("unused")
val pinterestAdsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Removes sponsored (promoted) pins from the home feed and from search/related/board feeds. " +
        "Also carries two more filters turned on from the Morphe settings screen: \"Hide product pins\", " +
        "off by default, and \"Hide board modules in search\" (sponsored carousel and featured boards), " +
        "on by default. They ride on the same three hooks because the search landing page is delivered " +
        "by the very same feed responses.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1) Costruttori delle risposte di rete: filtrano la prima pagina appena arriva.
        for (fingerprint in listOf(
            PinterestAdsFingerprint,
            PagedResponseConstructorFingerprint,
            ModelListWithBookmarkConstructorFingerprint,
        )) {
            val method = fingerprint.methodOrNull
            if (method == null) {
                PatchLog.warn(
                    PATCH_NAME,
                    "${fingerprint.javaClass.simpleName} not found: that feed path " +
                        "will not be filtered. See pinterest/OBFUSCATION_MAP.md to re-pin it.",
                )
                continue
            }

            val registerCount = method.implementation!!.registerCount
            val p0RegisterIndex = registerCount - (method.parameters.size + 1)

            val exits = method.addInstructionsBeforeEveryReturn(
                "invoke-static/range { v$p0RegisterIndex .. v$p0RegisterIndex }, " +
                    "$EXTENSION_CLASS->filterSponsoredPinsFromFeed(Ljava/lang/Object;)V",
            )
            PatchLog.hooked(PATCH_NAME, method, "feed response constructor, $exits exits")
        }

        // 2) Accessor della lista del feed: rifiltra a ogni lettura.
        //
        //    Senza questo, le pagine caricate scorrendo vengono accodate alla lista già
        //    costruita e non ripassano mai dal filtro: è il motivo per cui, nell'issue #15,
        //    gli annunci "aumentavano man mano che si scorre".
        //
        //    L'accessor si cerca DENTRO la classe Feed appena agganciata, non con un
        //    fingerprint a sé: cercandolo globalmente si finiva su una classe omonima di un
        //    altro package (su 14.28.0 esiste sia w12.d sia un o12.e non correlato).
        val accessor = PinterestAdsFingerprint.classDefOrNull?.methods?.firstOrNull { candidate ->
            candidate.returnType == "Ljava/util/List;" &&
                candidate.parameters.isEmpty() &&
                candidate.implementation != null
        }
        if (accessor == null) {
            PatchLog.warn(
                PATCH_NAME,
                "feed list accessor not found: ads will only be filtered " +
                    "on the first page and may reappear when scrolling (issue #15).",
            )
        } else {
            val accessorRegisterCount = accessor.implementation!!.registerCount
            val accessorInstructions = accessor.implementation!!.instructions.toList()

            // Si inserisce prima di OGNI return-object, partendo dall'ultimo: inserire sposta
            // gli indici successivi, quindi si procede a ritroso per non invalidarli.
            val returnIndices = accessorInstructions
                .mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
                }
                .reversed()

            check(returnIndices.isNotEmpty()) {
                "Feed list accessor has no return-object"
            }

            for (index in returnIndices) {
                val listRegister = (accessorInstructions[index] as OneRegisterInstruction).registerA
                accessor.addInstructions(
                    index,
                    InlineSmaliCompiler.compile(
                        "invoke-static/range { v$listRegister .. v$listRegister }, " +
                            "$EXTENSION_CLASS->filterSponsoredPinsFromList(Ljava/lang/Object;)V",
                        "",
                        accessorRegisterCount,
                        true,
                    ),
                )
            }
            PatchLog.hooked(
                PATCH_NAME,
                accessor,
                "feed list accessor, ${returnIndices.size} exit points",
            )
        }
    }
}
