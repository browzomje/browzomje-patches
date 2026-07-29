package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.Fingerprint

// e1.invoke(Object) è una classe sintetica generata dal compilatore Kotlin che accorpa 29 lambda
// diverse (una per "case") in un solo Function1: il case 10 costruisce l'ArrayList delle voci
// della schermata Impostazioni account. Offuscata, pinnata a 14.24.0.
// Trovato: rg "new e1(.*, 10)" sources/com/pinterest/feature/settings/menu/model/z.java
object SettingsMenuListBuilderFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/feature/settings/claimredesign/e1;" && method.name == "invoke"
    }
)
