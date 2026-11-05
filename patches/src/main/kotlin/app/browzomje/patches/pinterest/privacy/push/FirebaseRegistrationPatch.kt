package app.browzomje.patches.pinterest.privacy.push

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val PATCH_NAME = "Repair Firebase registration"
private const val CERT_HEADER = "X-Android-Cert"
private const val API_KEY_HEADER = "x-goog-api-key"
private const val INSTALLATIONS_URL = "https://firebaseinstallations.googleapis.com/v1/"
private const val PINTEREST_CERTIFICATE_SHA1 = "B6A74DBCB894B0F73D8C485C72EB1247A8F027CA"

private fun Instruction.string() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

private fun Instruction.headerWrite(): FiveRegisterInstruction? =
    (this as? FiveRegisterInstruction)?.takeIf {
        opcode == Opcode.INVOKE_VIRTUAL && it.registerCount == 3 &&
            (this as? ReferenceInstruction)?.reference.toString() ==
            "Ljava/net/URLConnection;->addRequestProperty(Ljava/lang/String;Ljava/lang/String;)V"
    }

private object InstallationsHeaders : Fingerprint(
    returnType = "Ljava/net/HttpURLConnection;",
    parameters = listOf("Ljava/net/URL;", "Ljava/lang/String;"),
    strings = listOf(CERT_HEADER, "X-Android-Package", API_KEY_HEADER),
    custom = { _, owner ->
        owner.methods.any { method ->
            method.returnType == "Ljava/net/URL;" &&
                method.implementation?.instructions?.any { it.string() == INSTALLATIONS_URL } == true
        }
    },
)

@Suppress("unused")
val firebaseRegistrationPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Uses the original Pinterest certificate fingerprint in Firebase Installations requests to help a re-signed app register for push notifications.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = COMPATIBILITY_PINTEREST.apkFileType,
        targets = COMPATIBILITY_PINTEREST.targets!!.filter { it.version in setOf("14.39.0", "14.43.0") },
    ))

    execute {
        val method = InstallationsHeaders.matchAll(1..1).single().method
        val instructions = method.implementation!!.instructions.toList()
        val index = instructions.indices.single { instructions[it].string() == CERT_HEADER }
        val certificateName = instructions[index] as OneRegisterInstruction
        val certificateWrite = checkNotNull(instructions.getOrNull(index + 1)?.headerWrite())
        val apiKeyName = instructions.getOrNull(index + 2) as? OneRegisterInstruction
        val apiKeyWrite = checkNotNull(instructions.getOrNull(index + 3)?.headerWrite())
        val connectionReturn = instructions.getOrNull(index + 4) as? OneRegisterInstruction
        check(
            certificateName.registerA == certificateWrite.registerD &&
                instructions[index + 2].string() == API_KEY_HEADER &&
                apiKeyName?.registerA == apiKeyWrite.registerD &&
                certificateWrite.registerC == apiKeyWrite.registerC &&
                certificateWrite.registerE !in setOf(
                    certificateWrite.registerC, certificateWrite.registerD, apiKeyWrite.registerE,
                ) &&
                connectionReturn?.opcode == Opcode.RETURN_OBJECT &&
                connectionReturn.registerA == certificateWrite.registerC,
        ) { "Unexpected Firebase Installations header register layout" }

        // Replace the header-name load so incoming branches also execute the new value load.
        method.replaceInstruction(index, "const-string v${certificateWrite.registerE}, \"$PINTEREST_CERTIFICATE_SHA1\"")
        method.addInstructions(index + 1, "const-string v${certificateName.registerA}, \"$CERT_HEADER\"")
        PatchLog.hooked(PATCH_NAME, method, "restore the Firebase Installations certificate header")
    }
}
