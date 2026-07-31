package app.browzomje.patches.pinterest.search.hide_search_history

import app.morphe.patcher.Fingerprint

// Issue #11. Ci sono due punti in cui Pinterest mostra le ricerche recenti:
//
//  1. la lista sulla "search landing page" (je1.m, un LinearLayout);
//  2. il carosello sotto la barra di ricerca (SearchTypeaheadRecentSearchesCarouselView).
//
// Su 14.24 la prima veniva agganciata al metodo che ricostruiva le righe, `je1.m.b(x5)`. Su
// 14.28 quel metodo non esiste più: il compilatore Kotlin ha spostato il corpo in una classe
// sintetica che accorpa decine di lambda (`…nux.pinpicker.p.d(…)`, case 11), impossibile da
// agganciare in modo stabile.
//
// Si aggancia quindi il **costruttore** della view: esiste in tutte le versioni, scatta prima
// che qualunque riga venga costruita e non dipende da come l'app decide di popolarla.
// L'extension nasconde la view in modo persistente (vedi MorpheViews), quindi anche i ri-bind
// successivi restano senza effetto.

/**
 * Il metodo che riceve gli elementi caricati della lista "ricerche recenti", prima che l'adapter
 * li trasformi in righe.
 *
 * Si aggancia il dato e non le view perché su 14.28 la sezione non è una view unica: è
 * un'intestazione `SearchTypeaheadHeader` più N celle `SearchTypeaheadTextCell`, e quest'ultima
 * classe è usata anche da "di tendenza" e dai suggerimenti, quindi non è nascondibile in blocco.
 * Togliendo gli elementi dalla lista non viene costruito niente, nemmeno lo spazio vuoto.
 *
 * L'ancora è il letterale `"multimodal_search_recently_viewed_pins"`, che in tutto il dex compare
 * in una sola classe. I letterali stringa non vengono offuscati.
 */
object RecentSearchesItemsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    strings = listOf("multimodal_search_recently_viewed_pins"),
)

/** La lista "Ricerche recenti" della schermata di ricerca. 14.23/14.24/14.28: je1.m. */
object SlpRecentSearchesViewFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lje1/m;" && method.name == "<init>" && method.parameters.size == 2
    }
)

/**
 * Il carosello "Ricerche recenti" del typeahead. Classe non offuscata (custom View inflatata
 * per nome completo dall'XML), quindi ancora più stabile del target sopra.
 */
object SearchTypeaheadRecentSearchesCarouselInitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/feature/search/typeahead/view/SearchTypeaheadRecentSearchesCarouselView;" &&
            method.name == "init"
    }
)

/**
 * Il metodo che accende e spegne lo spinner a tutta pagina di `PinterestLoadingLayout`, il
 * contenitore (`@id/loading_container`) dentro cui `fragment_search_typeahead` avvolge la lista
 * dei suggerimenti.
 *
 * Serve perché togliendo la cronologia la schermata del typeahead resta a girare: Pinterest la
 * costruisce proprio con i suggerimenti che noi rimuoviamo, e la lista che ne risulta viene letta
 * dal framework di liste (`x32.d.onStateUpdated`) come "sto ancora caricando".
 *
 * La classe non è offuscata, il nome del metodo sì: è `a(Z)V`, l'unico membro dell'interfaccia
 * `fl0.c` che la classe implementa. Si aggancia quindi per firma — un solo metodo della classe
 * prende un boolean — e non per nome, così un rename di R8 non lo fa saltare.
 */
object PinterestLoadingLayoutFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    custom = { _, classDef ->
        classDef.type == "Lcom/pinterest/design/brio/widget/progress/PinterestLoadingLayout;"
    }
)
