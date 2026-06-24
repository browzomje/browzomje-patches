package app.template.patches.pinterest.security.bypass

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Localizza il metodo del check di App Integrity per CONTENUTO, non per nome.
 *
 * In passato era fissato su `com/pinterest/security/c->a()V`, ma a ogni update
 * Pinterest rioffusca: la classe resta `c` ma il metodo che legge la firma può
 * cambiare nome (`a` → `b` → …) o spostarsi in un'altra classe dello stesso
 * package. Il nome fisso si rompeva con
 *     "Could not find Signature->toByteArray call in AppIntegrity check".
 *
 * Qui invece accettiamo QUALSIASI metodo del package `com/pinterest/security/`
 * che invochi `Landroid/content/pm/Signature;->toByteArray()[B`: è la chiamata
 * con cui il client ottiene i byte della firma da validare. È praticamente
 * l'unico punto in cui Pinterest usa quell'API, quindi il match resta preciso
 * pur essendo resiliente ai rinomini.
 */
object AppIntegrityFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type.startsWith("Lcom/pinterest/security/") &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    (instruction.reference as? MethodReference)?.let { ref ->
                        ref.definingClass == "Landroid/content/pm/Signature;" &&
                            ref.name == "toByteArray"
                    } == true
            } == true
    },
)
