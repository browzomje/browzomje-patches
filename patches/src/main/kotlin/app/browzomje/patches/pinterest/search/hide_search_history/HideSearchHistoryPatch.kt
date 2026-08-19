package app.browzomje.patches.pinterest.search.hide_search_history

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Hide search history"

/**
 * Fa passare il `boolean` in ingresso attraverso l'extension, che può cambiarlo, prima che il
 * metodo lo usi. Il parametro viene riscritto sul posto: è il primo uso del registro, quindi
 * sovrascriverlo non perde nulla e non serve un registro libero.
 *
 * @param filter riferimento smali a un metodo statico `(Ljava/lang/Object;Z)Z`, che riceve
 *     anche il `this` del metodo agganciato per sapere su quale oggetto sta decidendo.
 */
private fun MutableMethod.filterBooleanParameter(filter: String) {
    val registerCount = implementation!!.registerCount
    val p0 = registerCount - (parameters.size + 1)
    val p1 = p0 + 1

    addInstructions(
        0,
        InlineSmaliCompiler.compile(
            """
            invoke-static/range { v$p0 .. v$p1 }, $filter
            move-result v$p1
            """.trimIndent(),
            "",
            registerCount,
            true,
        ),
    )
}

/**
 * Accoda `hideRecentSearches(this)` prima del return-void, cioè quando la view ha finito di
 * costruirsi. L'hook è sempre iniettato: è l'extension, guidata dalla schermata Morphe, a
 * decidere a runtime se nascondere davvero.
 */
private fun MutableMethod.appendHideRecentSearches(): Int {
    val registerCount = implementation!!.registerCount
    val p0 = registerCount - (parameters.size + 1)

    return addInstructionsBeforeEveryReturn(
        "invoke-static/range { v$p0 .. v$p0 }, " +
            "$EXTENSION_CLASS->hideRecentSearches(Ljava/lang/Object;)V",
    )
}

@Suppress("unused")
val hideSearchHistoryPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Hides the \"Recent searches\" section on the search screen and below the search bar. Pinterest still logs searches server-side.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        var hooked = 0

        // 1) Il taglio principale: si tolgono gli elementi di cronologia dalla lista prima che
        //    l'adapter ne faccia delle righe. Così non nasce né l'intestazione "Ricerche
        //    recenti" né alcuna cella, e non resta nessuno spazio vuoto.
        RecentSearchesItemsFingerprint.methodOrNull?.let { method ->
            val registerCount = method.implementation!!.registerCount
            val itemsRegister = registerCount - method.parameters.size

            method.addInstructions(
                0,
                InlineSmaliCompiler.compile(
                    """
                    invoke-static/range { v$itemsRegister .. v$itemsRegister }, $EXTENSION_CLASS->filterRecentSearches(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$itemsRegister
                    """.trimIndent(),
                    "",
                    registerCount,
                    true,
                ),
            )
            PatchLog.hooked(PATCH_NAME, method, "history items")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "\"recent searches\" items list not found: falling back to hiding " +
                "views, which covers fewer cases.",
        )

        // 2) Le due view restano agganciate come rete di sicurezza: se su una schermata la
        //    cronologia arrivasse per un'altra strada, almeno lì viene comunque nascosta.
        // La lista della schermata di ricerca si ricava dalla fabbrica delle view della SLP.
        //
        // NON basta prendere la prima classe istanziata che sia un LinearLayout con costruttore
        // (Context, X): quella fabbrica ne costruisce una decina, e la prima che combacia è un
        // altro contenitore della stessa schermata (14.28: gm1.q1). Ci si aggancerebbe alla view
        // sbagliata **senza nessun errore**, che è il modo peggiore di rompersi.
        //
        // Il ramo giusto si riconosce dal letterale: Kotlin lo emette subito dopo il costruttore,
        // nel ramo d'errore della proprietà non inizializzata. Si parte quindi dal letterale e si
        // risale alla `new-instance` più vicina che lo precede.
        fun isContextConstructor(candidate: com.android.tools.smali.dexlib2.iface.Method) =
            candidate.name == "<init>" &&
                candidate.parameters.size == 2 &&
                candidate.parameters[0].type == "Landroid/content/Context;"

        val factoryInstructions = SlpViewFactoryFingerprint.methodOrNull
            ?.implementation
            ?.instructions
            ?.toList()

        val anchorIndex = factoryInstructions?.indexOfFirst { instruction ->
            (instruction.opcode == Opcode.CONST_STRING ||
                instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                    ?.string == "searchTypeaheadItemDeserializer"
        } ?: -1

        val slpListType = if (anchorIndex <= 0) {
            null
        } else {
            factoryInstructions!!.take(anchorIndex)
                .asReversed()
                .asSequence()
                .filter { it.opcode == Opcode.NEW_INSTANCE }
                .mapNotNull { ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type }
                .firstOrNull { type ->
                    classDefByOrNull(type)?.let { classDef ->
                        classDef.superclass == "Landroid/widget/LinearLayout;" &&
                            classDef.methods.any(::isContextConstructor)
                    } == true
                }
        }

        val slpListConstructor = slpListType
            ?.let { mutableClassDefByOrNull(it) }
            ?.methods
            ?.firstOrNull(::isContextConstructor)

        if (slpListConstructor == null) {
            PatchLog.warn(
                PATCH_NAME,
                "\"Recent searches\" list on search screen not found: the other two hooks still " +
                    "cover the typeahead. Anchor: \"searchTypeaheadItemDeserializer\".",
            )
        } else {
            val method = slpListConstructor
            val exits = method.appendHideRecentSearches()
            PatchLog.hooked(PATCH_NAME, method, "search screen list, $exits exits")
            hooked++
        }

        SearchTypeaheadRecentSearchesCarouselInitFingerprint.methodOrNull?.let { method ->
            val exits = method.appendHideRecentSearches()
            PatchLog.hooked(PATCH_NAME, method, "typeahead carousel, $exits exits")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "typeahead \"Recent searches\" carousel not found.",
        )

        // 3) Lo spinner della schermata di ricerca. Togliendo la cronologia la lista dei
        //    suggerimenti resta in uno stato che l'app legge come "sto ancora caricando", e lo
        //    spinner a tutta pagina gira all'infinito (issue #11). L'extension lo spegne solo su
        //    quella schermata e solo a opzione attiva.
        PinterestLoadingLayoutFingerprint.methodOrNull?.let { method ->
            method.filterBooleanParameter(
                "$EXTENSION_CLASS->filterLoadingSpinner(Ljava/lang/Object;Z)Z",
            )
            PatchLog.hooked(PATCH_NAME, method, "search screen spinner")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "loading spinner not found: the search screen may keep spinning after the " +
                "history is removed.",
        )

        check(hooked > 0) {
            "Neither of the two points where Pinterest shows recent searches was found: " +
                "the patch would have no effect. See pinterest/OBFUSCATION_MAP.md."
        }
    }
}
