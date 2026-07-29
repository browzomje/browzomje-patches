package app.browzomje.patches.pinterest.search.hide_search_history

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsBeforeEveryReturn

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Hide search history"

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
    description = "Hides the \"Recent searches\" section both on the search screen and in the carousel below the search bar. It does not prevent Pinterest from logging searches (server-side), but stops them from being shown anywhere in the app.",
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
            PatchLog.hooked(PATCH_NAME, method, "elementi della cronologia")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "lista degli elementi \"ricerche recenti\" non trovata: si ripiega sul nascondere " +
                "le view, che copre meno casi.",
        )

        // 2) Le due view restano agganciate come rete di sicurezza: se su una schermata la
        //    cronologia arrivasse per un'altra strada, almeno lì viene comunque nascosta.
        SlpRecentSearchesViewFingerprint.methodOrNull?.let { method ->
            val exits = method.appendHideRecentSearches()
            PatchLog.hooked(PATCH_NAME, method, "lista nella schermata di ricerca, $exits uscite")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "lista \"Ricerche recenti\" della schermata di ricerca non trovata.",
        )

        SearchTypeaheadRecentSearchesCarouselInitFingerprint.methodOrNull?.let { method ->
            val exits = method.appendHideRecentSearches()
            PatchLog.hooked(PATCH_NAME, method, "carosello del typeahead, $exits uscite")
            hooked++
        } ?: PatchLog.warn(
            PATCH_NAME,
            "carosello \"Ricerche recenti\" del typeahead non trovato.",
        )

        check(hooked > 0) {
            "Nessuno dei due punti in cui Pinterest mostra le ricerche recenti è stato trovato: " +
                "la patch non avrebbe alcun effetto. Vedi pinterest/OBFUSCATION_MAP.md."
        }
    }
}
