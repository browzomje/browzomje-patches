package app.browzomje.patches.pinterest.search.hide_search_history

import app.morphe.patcher.Fingerprint

// je1.m.b(x5): ricostruisce le righe "Recent searches" sulla search landing page. Offuscato,
// pinnato a 14.24.0. Trovato da: rg 'name="recent_search' public.xml -> rg RecentSearch sources.
object SlpRecentSearchesBindFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/pinterest/api/model/x5;"),
    custom = { method, classDef ->
        classDef.type == "Lje1/m;" && method.name == "b"
    }
)

// Carosello "Recent searches" del typeahead. Classe non offuscata (custom View inflate per nome
// completo da XML), quindi ancora più stabile del target sopra.
object SearchTypeaheadRecentSearchesCarouselInitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/feature/search/typeahead/view/SearchTypeaheadRecentSearchesCarouselView;" &&
            method.name == "init"
    }
)
