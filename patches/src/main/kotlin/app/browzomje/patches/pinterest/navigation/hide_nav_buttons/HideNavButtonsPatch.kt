package app.browzomje.patches.pinterest.navigation.hide_nav_buttons

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

// Ordinali dell'enum dei tab (14.24: yc0.a — 14.28: fd0.a; identici in entrambe):
// HOME=0, CREATE=1, NOTIFICATIONS=2, PROFILE=3, SEARCH=4.
// HOME (0) e PROFILE (3) non hanno una patch, di proposito: vedi il commento sotto.
private const val TAB_ORDINAL_CREATE = 1
private const val TAB_ORDINAL_NOTIFICATIONS = 2
private const val TAB_ORDINAL_SEARCH = 4

// Cattura this all'ingresso del setup e rimanda hideNavBarTab fino a barra costruita.
// L'hook è sempre iniettato (default = true): è PinterestUtils.hideNavBarTab, guidato dalla
// schermata Morphe (vedi MorpheSettingsStore), a decidere a runtime se nascondere davvero il
// tasto.
//
// Non esiste una patch per HOME né per PROFILE: la schermata Morphe si raggiunge solo da
// Profilo → Impostazioni → Morphe, quindi nascondere Profilo chiuderebbe fuori dall'opzione
// stessa per rimetterlo. HOME resta l'unico modo garantito di tornare al feed.
private fun MutableMethod.appendHideNavBarTab(patchName: String, tabOrdinal: Int) {
    val registerCount = implementation!!.registerCount
    check(parameters.isEmpty() && registerCount >= 3) {
        "Navigation setup needs two local registers and no parameters"
    }

    // hideNavBarTab(Object, int) vuole due registri contigui in ordine (navBar, tabOrdinal), ma
    // p0 è il registro più alto del frame: non c'è un p0+1 disponibile per l'int che lo segua.
    // Su 14.39 X0() riusa p0 per boolean/oggetti prima del return. Catturiamo this in
    // testa, quando i due locali non contengono ancora dati vivi; l'extension rimanda
    // la lettura dei tab con View.post, fino a costruzione della barra conclusa.
    addInstructions(
        0,
        """
        move-object/from16 v0, p0
        const/4 v1, 0x$tabOrdinal
        invoke-static/range { v0 .. v1 }, $EXTENSION_CLASS->hideNavBarTabWhenReady(Ljava/lang/Object;I)V
        """.trimIndent(),
    )
    PatchLog.hooked(patchName, this, "tab ordinal $tabOrdinal, deferred from setup entry")
}

private fun hideNavButtonPatch(name: String, description: String, tabOrdinal: Int) =
    bytecodePatch(name = name, description = description, default = true) {
        compatibleWith(COMPATIBILITY_PINTEREST)
        extendWith("extensions/extension.mpe")
        execute {
            BottomNavBarSetupFingerprint.method.appendHideNavBarTab(name, tabOrdinal)
        }
    }

@Suppress("unused")
val hideCreateNavButtonPatch = hideNavButtonPatch(
    "Hide Create nav button",
    "Hides the \"+\" (create Pin) navigation button, when enabled in Morphe settings.",
    TAB_ORDINAL_CREATE,
)

@Suppress("unused")
val hideNotificationsNavButtonPatch = hideNavButtonPatch(
    "Hide Notifications nav button",
    "Hides the notifications navigation button, when enabled in Morphe settings.",
    TAB_ORDINAL_NOTIFICATIONS,
)

@Suppress("unused")
val hideSearchNavButtonPatch = hideNavButtonPatch(
    "Hide Search nav button",
    "Hides the search navigation button, when enabled in Morphe settings.",
    TAB_ORDINAL_SEARCH,
)
