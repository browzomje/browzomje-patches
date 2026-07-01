package app.browzomje.patches.pinterest.video

import app.morphe.patcher.Fingerprint

object VideoTracksBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Lcom/bumptech/glide/d;" && method.name == "w" && 
         method.returnType == "Lcu2/r;" && method.parameters.size == 3 &&
         method.parameters[0].type == "Lcom/pinterest/api/model/me;" &&
         method.parameters[1].type == "Ljava/lang/Integer;" &&
         method.parameters[2].type == "Lcu2/n;") ||
        (classDef.type == "Lfr/a;" && method.name == "B" && 
         method.returnType == "Lbu2/r;" && method.parameters.size == 3 &&
         method.parameters[0].type == "Lcom/pinterest/api/model/re;" &&
         method.parameters[1].type == "Ljava/lang/Integer;" &&
         method.parameters[2].type == "Lbu2/n;")
    }
)

object VideoOverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Luz0/z;" && method.name == "<init>" && method.parameters.size == 28) ||
        (classDef.type == "Lnz0/b0;" && method.name == "<init>" && method.parameters.size == 27)
    }
)

object VideoTracksBuilderFingerprint2 : Fingerprint(
    custom = { method, classDef ->
        (classDef.type == "Lcom/bumptech/glide/d;" && method.name == "p" && method.parameters.size == 8) ||
        (classDef.type == "Lfr/a;" && method.name == "t" && method.parameters.size == 8)
    }
)
