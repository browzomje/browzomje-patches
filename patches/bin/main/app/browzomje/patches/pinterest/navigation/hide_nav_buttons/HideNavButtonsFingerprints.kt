package app.browzomje.patches.pinterest.navigation.hide_nav_buttons

import app.morphe.patcher.Fingerprint

// G(): costruisce la bottom nav bar, chiamato una volta dal costruttore di LegoFloatingBottomNavBar
// (classe non offuscata). Aggiunge ogni tasto in un ciclo (H(vc0.h, int)) e poi fa altro setup
// senza più toccarli: sicuro accodare il nostro hook prima del return-void.
// Trovato: fd BottomNav/NavBar sources/ -> rg "addView(.*layoutParamsD" LegoFloatingBottomNavBar.java
object BottomNavBarSetupFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/navigation/view/lego/LegoFloatingBottomNavBar;" && method.name == "G"
    }
)
