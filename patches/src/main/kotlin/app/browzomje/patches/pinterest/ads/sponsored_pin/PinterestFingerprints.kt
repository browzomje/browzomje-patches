package app.template.patches.pinterest

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

object PinterestAdsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/pinterest/api/model/me;"),

    filters = listOf(
        methodCall(
            definingClass = "Lcom/pinterest/api/model/me;",
            returnType = "Ljava/lang/Boolean;"
        ),

        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Ljava/lang/Boolean;",
            name = "TRUE",
            type = "Ljava/lang/Boolean;"
        ),

        methodCall(
            definingClass = "Lkotlin/jvm/internal/Intrinsics;",
            name = "e" 
        )
    ),

    custom = { _, classDef ->
        classDef.type == "Lcom/pinterest/api/model/AdCheckUtils;" || 
        classDef.type == "Lcom/pinterest/api/model/ue;"
    }
)