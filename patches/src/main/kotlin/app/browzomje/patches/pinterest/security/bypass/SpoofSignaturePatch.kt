package app.template.patches.pinterest.security.bypass

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val spoofSignaturePatch = bytecodePatch(
    name = "Spoof signature",
    description = "Spoofs the application signature SHA-1 sent during App Integrity checks to bypass server-side signature validation.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val method = AppIntegrityFingerprint.method
        val implementation = method.implementation ?: throw Exception("AppIntegrity method has no implementation")
        val instructions = implementation.instructions
        
        val targetIndex = instructions.indexOfFirst { instruction ->
            if (instruction is ReferenceInstruction) {
                val ref = instruction.reference
                ref is MethodReference && ref.definingClass == "Landroid/content/pm/Signature;" && ref.name == "toByteArray"
            } else false
        }
        
        if (targetIndex != -1) {
            val instr = instructions.elementAt(targetIndex)
            val registerNumber = when (instr) {
                is FiveRegisterInstruction -> instr.registerC
                is RegisterRangeInstruction -> instr.startRegister
                else -> throw Exception("Unsupported instruction format for register extraction: ${instr.javaClass.name}")
            }
            val register = "v$registerNumber"
            
            method.replaceInstruction(
                targetIndex,
                "invoke-static { $register }, Lapp/template/extension/pinterest/PinterestUtils;->getSignatureBytes(Landroid/content/pm/Signature;)[B"
            )
        } else {
            throw Exception("Could not find Signature->toByteArray call in AppIntegrity check")
        }
    }
}
