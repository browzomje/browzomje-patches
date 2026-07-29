package app.browzomje.patches.pinterest.account.email_confirm_dialog

import app.morphe.patcher.Fingerprint

// Il Fragment che ospita il flusso "recovery v2" (conferma email / collega Google / ecc.).
// Offuscato e senza una forma riconoscibile, quindi va pinnato per nome. Come ritrovarlo su una
// nuova versione:
//
//   rg -l gbl_confirm_email sources/            -> la classe del passo "conferma email"
//   rg "new <quella classe>\(" sources/         -> il Fragment che la costruisce = il target
//
// 14.23.0 / 14.24.0: ue2.d — 14.28.0: rf2.d.
private val RECOVERY_FLOW_FRAGMENTS = setOf("Lue2/d;", "Lrf2/d;")

/**
 * `onViewCreated` è il punto in cui il modale prende vita: imposta il ViewPager e avvia il
 * caricamento del primo passo. È anche l'unico metodo del Fragment con un nome NON offuscato,
 * perché è l'override di un metodo di libreria (`androidx.fragment.app.Fragment`) e R8 non può
 * rinominarlo.
 */
object RecoveryFlowViewCreatedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type in RECOVERY_FLOW_FRAGMENTS && method.name == "onViewCreated"
    }
)
