package app.browzomje.patches.pinterest.account.email_confirm_dialog

import app.morphe.patcher.Fingerprint

// ue2.d è il Fragment modale del flusso "recovery v2" (conferma email / collega Google / ecc.),
// ea(m) è il suo router centrale (nome Kotlin reale "handleEventFlowEvents", recuperabile da una
// stringa di metadata KFunction in rr1/g.java, ma non presente nel bytecode di ea() stesso).
// Offuscato, pinnato a 14.24.0.
// Trovato: rg gbl_confirm_email sources --include=*.java -> te2/g.java (fragment "conferma email")
//          -> rg "new te2.g(" sources -> ue2/d.java (host del flusso, contiene fa() = chiudi flusso)
object RecoveryFlowRouterFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lve2/m;"),
    custom = { method, classDef ->
        classDef.type == "Lue2/d;" && method.name == "ea"
    }
)

// onViewCreated è il vero punto in cui il modale prende vita: imposta il ViewPager e avvia il
// caricamento del primo step (conferma email). ea() invece scatta solo su eventi interni del
// flusso (es. l'utente completa uno step) e quasi mai al semplice show del modale: per questo
// hookare solo ea() (RecoveryFlowRouterFingerprint) non bastava a sopprimere il dialog.
// Nome del metodo non offuscato: override di androidx.fragment.app.Fragment.onViewCreated,
// R8 non può rinominare gli override di metodi di libreria.
object RecoveryFlowViewCreatedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type == "Lue2/d;" && method.name == "onViewCreated"
    }
)
