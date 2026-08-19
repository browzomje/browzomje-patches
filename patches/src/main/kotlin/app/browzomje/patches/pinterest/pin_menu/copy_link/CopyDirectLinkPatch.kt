package app.browzomje.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.addInstructionsAfterSuperConstructor
import app.browzomje.patches.pinterest.OverflowMenuBuilderFingerprint

private const val EXTENSION_CLASS = "Lapp/browzomje/extension/pinterest/PinterestUtils;"

@Suppress("unused")
val copyDirectLinkPatch = bytecodePatch(
    name = "Copy direct link",
    description = "Adds a 'Copy direct link' option to the pin menu to copy a link under the direct CDN media format instead of the standard Pinterest web link.",
    default = true
) {
    compatibleWith(COMPATIBILITY_PINTEREST)
    extendWith("extensions/extension.mpe")

    execute {
        val method = OverflowMenuBuilderFingerprint.method

        // Subito dopo il super, non in coda al costruttore: vedi
        // addInstructionsAfterSuperConstructor. L'extension riceve la view appena nata e rimanda
        // il proprio lavoro con View.post.
        val at = method.addInstructionsAfterSuperConstructor(
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->addCopyLinkOption(Ljava/lang/Object;)V",
        )
        PatchLog.hooked("Copy direct link", method, "pin menu option, after super at $at")
    }
}
