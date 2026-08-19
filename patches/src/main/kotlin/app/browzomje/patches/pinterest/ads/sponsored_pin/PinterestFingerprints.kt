package app.browzomje.patches.pinterest

import app.morphe.patcher.Fingerprint

// Le tre risposte di rete che portano pin nel feed — e, come si è scoperto per l'issue #30, anche
// i moduli della schermata di ricerca, che viaggiano sulla stessa ModelListWithBookmark.
//
// Sono data class / contenitori con nomi offuscati che cambiano a OGNI versione. Fino a 14.28 i
// nomi erano elencati a mano; non regge: fra 14.28.0 e 14.32.0 tutti e tre sono cambiati, e due
// dei tre nomi vecchi esistono ancora puntando a classi senza alcun rapporto (R8 ricicla i nomi
// corti che si liberano). Un elenco di nomi non fallisce rumorosamente: rischia di agganciare la
// classe sbagliata.
//
// Si trova quindi prima la CLASSE, tramite una stringa che l'app non può rinominare — il
// `toString()` che il compilatore Kotlin genera con il nome ORIGINALE della data class — e poi il
// costruttore per forma, dentro quella classe. Verificato su 14.23.0, 14.28.0 e 14.32.0:
//
//   Feed                    w12.d  -> t22.f
//   PagedResponse           pr1.k0 -> ls1.h0
//   ModelListWithBookmark   vh2.b  -> qi2.c
//
// Vedi pinterest/PATCHING_MEMORY.md per la misura completa.

/** La classe `Feed`, trovata dal suo `toString()`. */
private object FeedClassFingerprint : Fingerprint(
    strings = listOf(", _items count:"),
)

/** La data class `PagedResponse`, trovata dal suo `toString()`. */
private object PagedResponseClassFingerprint : Fingerprint(
    strings = listOf("PagedResponse(bookmark="),
)

/** La data class `ModelListWithBookmark`, trovata dal suo `toString()`. */
private object ModelListWithBookmarkClassFingerprint : Fingerprint(
    strings = listOf("ModelListWithBookmark(models="),
)

/**
 * `Feed(baseUrl, bookmark, ?, items)`.
 *
 * Esiste anche un costruttore a 3 parametri, ma delega a questo: agganciare quello a 4 li copre
 * entrambi. La forma è identica su 14.23, 14.28 e 14.32, quindi qui i parametri si possono
 * vincolare per intero senza rischi.
 */
object PinterestAdsFingerprint : Fingerprint(
    classFingerprint = FeedClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
)

/** `PagedResponse(bookmark, models, url)`. Forma invariata su tutte le versioni viste. */
object PagedResponseConstructorFingerprint : Fingerprint(
    classFingerprint = PagedResponseClassFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
)

/**
 * `ModelListWithBookmark(models, bookmark[, …])`.
 *
 * Qui i parametri **non** si vincolano per intero, ed è una lezione pagata: su 14.32.0 il
 * costruttore ha guadagnato un terzo parametro (`(List, String)` → `(List, String, hj)`). Un
 * fingerprint che ne pretendeva esattamente due sarebbe fallito pur avendo trovato la classe
 * giusta. Si chiede quindi solo ciò che serve a riconoscerlo: il primo parametro è la lista dei
 * modelli, che è anche l'unica cosa che la patch usa davvero.
 */
object ModelListWithBookmarkConstructorFingerprint : Fingerprint(
    classFingerprint = ModelListWithBookmarkClassFingerprint,
    name = "<init>",
    returnType = "V",
    custom = { method, _ ->
        method.parameters.firstOrNull()?.type == "Ljava/util/List;"
    },
)
