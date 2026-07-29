package app.browzomje.patches.pinterest.morphe_settings

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Package delle righe della schermata Impostazioni: NON offuscato, solo i nomi delle classi lo sono. */
internal const val SETTINGS_ROW_PACKAGE = "Lcom/pinterest/feature/settings/menu/model/"

/**
 * Il metodo che costruisce l'ArrayList delle voci della schermata "Impostazioni account".
 *
 * È una classe sintetica generata da Kotlin che accorpa decine di lambda in un solo `Function1`:
 * su 14.23/14.24 era `…settings.claimredesign.e1`, su 14.28 è
 * `…settings.linkedcreatoraccountstatus.g`. Cambia sia il nome sia il **package**, quindi
 * ancorarsi al nome è inutile.
 *
 * Ci si ancora invece alla forma: `Object invoke(Object)` che costruisce molte righe del
 * package delle Impostazioni, fra cui almeno un header di sezione (`<init>(I)`) e almeno una
 * riga "link esterno" (`<init>(String)`). Nessun altro metodo dell'app ha questo profilo.
 */
object SettingsMenuListBuilderFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, _ -> method.buildsSettingsMenu() }
)

/** Numero minimo di righe delle Impostazioni costruite perché il metodo sia "quello giusto". */
private const val MIN_SETTINGS_ROWS = 5

private fun Method.buildsSettingsMenu(): Boolean {
    val instructions = implementation?.instructions ?: return false

    var rowConstructions = 0
    var hasSectionHeader = false
    var hasExternalLink = false

    for (instruction in instructions) {
        if (instruction.opcode != Opcode.INVOKE_DIRECT) continue
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if (reference.name != "<init>") continue
        if (!reference.definingClass.startsWith(SETTINGS_ROW_PACKAGE)) continue

        rowConstructions++
        when (reference.parameterTypes.map { it.toString() }) {
            listOf("I") -> hasSectionHeader = true
            listOf("Ljava/lang/String;") -> hasExternalLink = true
        }
    }

    return rowConstructions >= MIN_SETTINGS_ROWS && hasSectionHeader && hasExternalLink
}
