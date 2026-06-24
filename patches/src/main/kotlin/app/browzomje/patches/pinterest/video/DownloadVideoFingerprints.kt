package app.template.patches.pinterest.video

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object PinterestVideoViewPlayFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcu2/l;", "Lfu2/g;", "Lkotlin/jvm/functions/Function0;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/video/core/view/PinterestVideoView;" && method.name == "u"
    }
)
