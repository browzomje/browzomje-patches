package app.browzomje.patches.pinterest.search.hide_search_history

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val hideSearchHistoryPatch = bytecodePatch(
    name = "Hide search history",
    description = "Nasconde la sezione \"Ricerche recenti\" sia sulla schermata di ricerca sia " +
        "nel carosello sotto la barra di ricerca. Non impedisce a Pinterest di registrare le " +
        "ricerche (lato server), ma fa sì che non vengano più mostrate da nessuna parte nell'app.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // Sostituisce interamente il metodo con invoke-static + return-void: nessuna riga viene
        // più costruita.
        val bindMethod = SlpRecentSearchesBindFingerprint.method
        val bindRegisterCount = bindMethod.implementation!!.registerCount
        val bindP0 = bindRegisterCount - (bindMethod.parameters.size + 1)
        bindMethod.addInstructions(
            0,
            InlineSmaliCompiler.compile(
                "invoke-static/range { v$bindP0 .. v$bindP0 }, $EXTENSION_CLASS->hideRecentSearches(Ljava/lang/Object;)V",
                "",
                bindRegisterCount,
                true,
            ),
        )

        // Qui invece accodiamo la chiamata prima del return, lasciando che init() completi il
        // suo setup originale.
        val carouselMethod = SearchTypeaheadRecentSearchesCarouselInitFingerprint.method
        val carouselInstructions = carouselMethod.implementation!!.instructions
        val carouselReturnIndex = carouselInstructions.indexOfFirst { it.opcode == Opcode.RETURN_VOID }
        val carouselInsertIndex = if (carouselReturnIndex != -1) carouselReturnIndex else carouselInstructions.size - 1
        val carouselRegisterCount = carouselMethod.implementation!!.registerCount
        val carouselP0 = carouselRegisterCount - (carouselMethod.parameters.size + 1)
        carouselMethod.addInstructions(
            carouselInsertIndex,
            InlineSmaliCompiler.compile(
                "invoke-static/range { v$carouselP0 .. v$carouselP0 }, $EXTENSION_CLASS->hideRecentSearches(Ljava/lang/Object;)V",
                "",
                carouselRegisterCount,
                true,
            ),
        )
    }
}
