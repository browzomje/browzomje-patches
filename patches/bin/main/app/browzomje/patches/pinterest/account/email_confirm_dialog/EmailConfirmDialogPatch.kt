package app.browzomje.patches.pinterest.account.email_confirm_dialog

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"
private const val PATCH_NAME = "Disable email confirmation dialog"

/**
 * Chiude subito il modale "conferma la tua email" (e i suoi fratelli: collega Google, ecc.).
 *
 * La versione precedente chiamava i metodi di chiusura interni di Pinterest (`Z6()`, `fa()`):
 * nomi offuscati, diversi a ogni release e per giunta dipendenti dallo stato del Fragment
 * (`fa()` va in NPE se il flusso non è ancora inizializzato). Ora si passa `this` e la View
 * all'extension, che usa solo API AndroidX (`DialogFragment.dismissAllowingStateLoss()` oppure
 * una `FragmentTransaction.remove()`), mai rinominate da R8.
 */
@Suppress("unused")
val emailConfirmDialogPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Chiude subito il modale \"conferma la tua email\" (e simili: collega Google, " +
        "ecc.) se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val method = RecoveryFlowViewCreatedFingerprint.method
        val implementation = method.implementation!!
        val registerCount = implementation.registerCount

        // p0 = this (il Fragment), p1 = la View appena creata.
        val p0 = registerCount - (method.parameters.size + 1)
        val p1 = p0 + 1

        // Si inserisce subito DOPO l'invoke-super, non prima: il Fragment deve completare
        // l'inizializzazione di base, altrimenti la rimozione può avvenire su uno stato non
        // ancora valido.
        val superIndex = implementation.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
        val insertIndex = if (superIndex != -1) superIndex + 1 else 0
        if (superIndex == -1) {
            PatchLog.warn(
                PATCH_NAME,
                "nessun invoke-super in onViewCreated: l'hook viene messo in testa al metodo.",
            )
        }

        // p0 e p1 sono contigui e in ordine: si possono passare direttamente con invoke-static/range,
        // senza registri d'appoggio (p0 può superare v15, quindi la forma /range è obbligatoria).
        method.addInstructions(
            insertIndex,
            InlineSmaliCompiler.compile(
                "invoke-static/range { v$p0 .. v$p1 }, " +
                    "$EXTENSION_CLASS->suppressRecoveryFlow(Ljava/lang/Object;Ljava/lang/Object;)V",
                "",
                registerCount,
                true,
            ),
        )
        PatchLog.hooked(PATCH_NAME, method, "onViewCreated del flusso recovery")
    }
}
