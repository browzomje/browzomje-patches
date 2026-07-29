package app.browzomje.patches.pinterest.account.email_confirm_dialog

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val SETTINGS_CLASS = "Lapp/browzomje/extension/pinterest/MorpheSettingsStore;"

@Suppress("unused")
val emailConfirmDialogPatch = bytecodePatch(
    name = "Disable email confirmation dialog",
    description = "Chiude subito il modale \"conferma la tua email\" (e simili: collega Google, " +
        "ecc.) se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // Hook primario: onViewCreated, prima che il modale carichi il primo step. Z6() (chiudi
        // fragment) non dipende da f130454s0 come fa(), quindi è chiamabile qui, subito dopo
        // super.onViewCreated(), senza NPE.
        val viewCreatedMethod = RecoveryFlowViewCreatedFingerprint.method
        val vcRegisterCount = viewCreatedMethod.implementation!!.registerCount
        val vcP0 = vcRegisterCount - (viewCreatedMethod.parameters.size + 1)
        val superIndex = viewCreatedMethod.implementation!!.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
        val vcInsertIndex = if (superIndex != -1) superIndex + 1 else 0

        // v0 come scratch: siamo appena dopo l'unica invoke-super del metodo, nessuna istruzione
        // originale ha ancora scritto/letto un registro locale (write-before-read del verifier fa
        // sì che il codice originale sovrascriva v0 prima di leggerlo più avanti).
        viewCreatedMethod.addInstructions(
            vcInsertIndex,
            InlineSmaliCompiler.compile(
                """
                invoke-static { }, $SETTINGS_CLASS->isEmailConfirmDialogDisabled()Z
                move-result v0
                if-eqz v0, :skip_dismiss
                invoke-virtual { v$vcP0 }, Lue2/d;->Z6()V
                return-void
                :skip_dismiss
                """.trimIndent(),
                "",
                vcRegisterCount,
                true,
            ),
        )

        // Difesa in profondità: se il flusso viene comunque raggiunto tramite un evento ve2.a
        // (es. l'utente lo completa), ea() chiude anche quello, riusando fa() come già faceva
        // il codice originale.
        val routerMethod = RecoveryFlowRouterFingerprint.method
        val routerRegisterCount = routerMethod.implementation!!.registerCount
        val routerP0 = routerRegisterCount - (routerMethod.parameters.size + 1)

        routerMethod.addInstructions(
            0,
            InlineSmaliCompiler.compile(
                """
                invoke-static { }, $SETTINGS_CLASS->isEmailConfirmDialogDisabled()Z
                move-result v0
                if-eqz v0, :skip_dismiss
                invoke-virtual { v$routerP0 }, Lue2/d;->fa()V
                return-void
                :skip_dismiss
                """.trimIndent(),
                "",
                routerRegisterCount,
                true,
            ),
        )
    }
}
