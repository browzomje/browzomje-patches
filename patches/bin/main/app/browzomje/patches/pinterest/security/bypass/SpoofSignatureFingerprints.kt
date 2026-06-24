package app.template.patches.pinterest.security.bypass

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object AppIntegrityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/security/c;" && method.name == "a"
    }
)
