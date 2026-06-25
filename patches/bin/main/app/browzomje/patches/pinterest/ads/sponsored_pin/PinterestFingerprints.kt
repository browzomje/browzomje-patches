package app.template.patches.pinterest

import app.morphe.patcher.Fingerprint

object PinterestAdsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
    custom = { method, classDef ->
        classDef.type == "Lo12/e;" && method.name == "<init>"
    }
)

object PagedResponseConstructorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
    custom = { method, classDef ->
        classDef.type == "Lvr1/i0;" && method.name == "<init>"
    }
)

object ModelListWithBookmarkConstructorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Ljava/lang/String;",
    ),
    custom = { method, classDef ->
        classDef.type == "Lvg2/b;" && method.name == "<init>"
    }
)
