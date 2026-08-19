package app.browzomje.patches.pinterest.account.email_confirm_dialog

import app.morphe.patcher.Fingerprint

/**
 * Il Fragment che ospita il flusso "recovery v2" (conferma email / collega Google / ecc.).
 *
 * Prima era pinnato per nome (`ue2.d` su 14.23/14.24, `rf2.d` su 14.28) e su 14.32.0 la build si
 * fermava qui: quei nomi non esistono più. L'ancora è il letterale
 * `"recovery_v2_back_recovery_password"`, il tag di navigazione con cui il flusso torna al passo
 * della password — presente in **una sola classe** sia su 14.28.0 sia su 14.32.0, che è
 * esattamente il Fragment cercato (14.28.0: `rf2.d`, 14.32.0: `pg2.c`).
 *
 * Il letterale sta in un metodo diverso da quello che si aggancia, ed è il motivo per cui serve
 * un fingerprint di classe separato: prima si trova la classe, poi il membro al suo interno.
 */
private object RecoveryFlowFragmentFingerprint : Fingerprint(
    strings = listOf("recovery_v2_back_recovery_password"),
)

/**
 * `onViewCreated` è il punto in cui il modale prende vita: imposta il ViewPager e avvia il
 * caricamento del primo passo. È anche l'unico metodo del Fragment con un nome NON offuscato,
 * perché è l'override di un metodo di libreria (`androidx.fragment.app.Fragment`) e R8 non può
 * rinominarlo.
 */
object RecoveryFlowViewCreatedFingerprint : Fingerprint(
    classFingerprint = RecoveryFlowFragmentFingerprint,
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)
