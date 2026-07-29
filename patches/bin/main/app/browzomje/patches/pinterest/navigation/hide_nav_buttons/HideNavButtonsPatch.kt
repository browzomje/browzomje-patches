package app.browzomje.patches.pinterest.navigation.hide_nav_buttons

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.InlineSmaliCompiler
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

// Ordinali di yc0.a (yc0/a.java): HOME=0, CREATE=1, NOTIFICATIONS=2, PROFILE=3, SEARCH=4.
private const val TAB_ORDINAL_CREATE = 1
private const val TAB_ORDINAL_NOTIFICATIONS = 2
private const val TAB_ORDINAL_PROFILE = 3
private const val TAB_ORDINAL_SEARCH = 4

// Accoda hideNavBarTab(this, tabOrdinal) prima del return-void di G(). L'hook è sempre iniettato
// (default = true): è PinterestUtils.hideNavBarTab, gestito dalla schermata Morphe (vedi
// MorpheSettingsStore), a decidere a runtime se nascondere davvero il tasto. Niente toggle per
// HOME: è l'unica via certa per tornare alla home.
private fun MutableMethod.appendHideNavBarTab(tabOrdinal: Int) {
    val instructionsList = implementation!!.instructions
    val returnIndex = instructionsList.indexOfFirst { it.opcode == Opcode.RETURN_VOID }
    val insertIndex = if (returnIndex != -1) returnIndex else instructionsList.size - 1

    val registerCount = implementation!!.registerCount
    val p0 = registerCount - (parameters.size + 1) // G() non ha parametri: p0 = registerCount - 1

    // hideNavBarTab(Object, int) vuole due registri contigui in ordine (navBar, tabOrdinal), ma p0
    // è il registro più alto del frame: non c'è un p0+1 disponibile per l'int che lo segua. Copiamo
    // quindi navBar in v0/v1 (bassi, sempre contigui) invece di usare p0 direttamente nel range.
    // move-object/from16 regge un sorgente oltre v15 (p0 può superarlo), a differenza di move-object.
    // Siamo subito prima del return-void: nessuna istruzione originale segue che legga v0/v1 dopo.
    addInstructions(
        insertIndex,
        InlineSmaliCompiler.compile(
            """
            move-object/from16 v0, v$p0
            const/4 v1, 0x$tabOrdinal
            invoke-static/range { v0 .. v1 }, $EXTENSION_CLASS->hideNavBarTab(Ljava/lang/Object;I)V
            """.trimIndent(),
            "",
            registerCount,
            true,
        ),
    )
}

@Suppress("unused")
val hideCreateNavButtonPatch = bytecodePatch(
    name = "Hide Create nav button",
    description = "Aggancia il tasto \"+\" (crea Pin) della barra di navigazione: nascosto se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute { BottomNavBarSetupFingerprint.method.appendHideNavBarTab(TAB_ORDINAL_CREATE) }
}

@Suppress("unused")
val hideNotificationsNavButtonPatch = bytecodePatch(
    name = "Hide Notifications nav button",
    description = "Aggancia il tasto delle notifiche: nascosto se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute { BottomNavBarSetupFingerprint.method.appendHideNavBarTab(TAB_ORDINAL_NOTIFICATIONS) }
}

@Suppress("unused")
val hideProfileNavButtonPatch = bytecodePatch(
    name = "Hide Profile nav button",
    description = "Aggancia il tasto del profilo: nascosto se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute { BottomNavBarSetupFingerprint.method.appendHideNavBarTab(TAB_ORDINAL_PROFILE) }
}

@Suppress("unused")
val hideSearchNavButtonPatch = bytecodePatch(
    name = "Hide Search nav button",
    description = "Aggancia il tasto Ricerca: nascosto se attivato nelle impostazioni Morphe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")
    execute { BottomNavBarSetupFingerprint.method.appendHideNavBarTab(TAB_ORDINAL_SEARCH) }
}
