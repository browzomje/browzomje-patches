package app.browzomje.patches.pinterest

import app.morphe.patcher.Fingerprint

// Le tre risposte di rete che portano pin nel feed. Sono data class / contenitori con nomi
// offuscati che cambiano a ogni versione; il modo per ritrovarli è il loro toString(), che il
// compilatore Kotlin genera con il nome ORIGINALE della classe (vedi OBFUSCATION_MAP.md):
//   rg -l 'PagedResponse\(bookmark='       -> PagedResponseConstructorFingerprint
//   rg -l 'ModelListWithBookmark\(models=' -> ModelListWithBookmarkConstructorFingerprint
//   rg -l '_items count:'                  -> PinterestAdsFingerprint (la classe "Feed")

/** Le classi "Feed" note, per versione: 14.23 → o12.e, 14.28 → w12.d. */
private val FEED_CLASSES = setOf("Lo12/e;", "Lw12/d;")

/**
 * `Feed(baseUrl, bookmark, ?, items)`.
 *
 * Su 14.28 esiste anche un costruttore a 3 parametri, ma delega a questo: agganciare quello a
 * 4 li copre entrambi.
 */
object PinterestAdsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
    custom = { method, classDef ->
        classDef.type in FEED_CLASSES && method.name == "<init>"
    }
)

/** `PagedResponse(bookmark, models, url)`. 14.23: vr1.i0 — 14.28: pr1.k0. */
object PagedResponseConstructorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
    custom = { method, classDef ->
        classDef.type in setOf("Lvr1/i0;", "Lpr1/k0;") && method.name == "<init>"
    }
)

/** `ModelListWithBookmark(models, bookmark)`. 14.23: vg2.b — 14.28: vh2.b. */
object ModelListWithBookmarkConstructorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Ljava/lang/String;",
    ),
    custom = { method, classDef ->
        classDef.type in setOf("Lvg2/b;", "Lvh2/b;") && method.name == "<init>"
    }
)
