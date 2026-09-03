package app.browzomje.patches.pinterest.browser.external_browser

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/ExternalBrowser;"

private const val PATCH_NAME = "Open links in the default browser"

/**
 * Apre nel browser predefinito del telefono i link che Pinterest aprirebbe nel proprio browser
 * interno — il tasto "Visita il sito" del pin e tutti gli altri che portano fuori (issue #35).
 *
 * L'hook è **sempre** iniettato (`default = true`): a decidere se dirottare davvero è
 * l'interruttore "Apri i link nel browser predefinito" della schermata Morphe, letto a runtime da
 * `ExternalBrowser`. È la stessa scelta della barra di navigazione e dell'intestazione del feed —
 * un solo APK che sa fare entrambe le cose, invece di due build diverse.
 *
 * Si inietta **in testa** al metodo, e questo è il punto in cui è più facile sbagliare:
 *
 * - `p0` è `this` (il fragment) e `p1` è la view, **solo all'inizio del metodo**. I registri dei
 *   parametri sono registri come gli altri e R8 li riusa come spazio di lavoro appena il parametro
 *   è morto: iniettando prima del `return` si passerebbe all'extension un oggetto qualunque preso
 *   dai locali, senza nessun errore in fase di patch. È già successo, su 14.32.0, con le voci del
 *   menu del pin.
 * - i registri si scrivono `p0`/`p1` e **non si calcolano**: `addInstructions` compila lo smali nel
 *   contesto del metodo, quindi l'assemblatore sa già dove stanno. Il conto a mano
 *   (`registerCount - parametri - 1`) sbaglia in silenzio quando ci sono parametri `long`/`double`
 *   o quando il frame è più largo degli input.
 * - la forma è `/range` perché in un frame ampio `p0` può superare `v15`, che è il limite di
 *   `invoke-static` con i registri elencati.
 */
@Suppress("unused")
val openLinksInDefaultBrowserPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Opens links in the phone's default browser instead of Pinterest's in-app browser, " +
        "when the matching Morphe setting is enabled.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Hook sul launcher delle Chrome Custom Tabs (CCT) usato per "Visita il sito" e link esterni
        val cctMethod = CustomTabsOpenFingerprint.methodOrNull
        if (cctMethod != null) {
            cctMethod.addInstructions(
                0,
                """
                    invoke-static/range { p0 .. p1 }, $EXTENSION_CLASS->redirectCct(Ljava/lang/Object;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :cct_proceed
                    return-void
                    :cct_proceed
                """.trimIndent(),
            )
            PatchLog.hooked(PATCH_NAME, cctMethod, "entry of CCT open method, cctManager in p0, url in p1")
        } else {
            PatchLog.warn(PATCH_NAME, "CustomTabs open method not found: external links may open in CCT")
        }

        // 2. Hook sul WebView (InAppBrowserFragment) come fallback
        InAppBrowserOnViewCreatedFingerprint.methodOrNull?.apply {
            addInstructions(
                0,
                "invoke-static/range { p0 .. p1 }, " +
                    "$EXTENSION_CLASS->redirectToDefaultBrowser(Ljava/lang/Object;Ljava/lang/Object;)V",
            )
            PatchLog.hooked(PATCH_NAME, this, "entry of onViewCreated, fragment in p0, view in p1")
        }
    }
}
