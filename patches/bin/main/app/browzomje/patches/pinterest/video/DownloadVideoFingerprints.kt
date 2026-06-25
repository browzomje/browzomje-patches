package app.browzomje.patches.pinterest.video

import app.morphe.patcher.Fingerprint

object VideoTracksBuilderFingerprint : Fingerprint(
    returnType = "Lcu2/r;",
    parameters = listOf("Lcom/pinterest/api/model/me;", "Ljava/lang/Integer;", "Lcu2/n;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/bumptech/glide/d;" && method.name == "w"
    }
)

object VideoOverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Luz0/z;" &&
            method.name == "<init>" &&
            method.parameters.size == 28
    }
)

object VideoTracksBuilderFingerprint2 : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/bumptech/glide/d;" && method.name == "p"
    }
)
