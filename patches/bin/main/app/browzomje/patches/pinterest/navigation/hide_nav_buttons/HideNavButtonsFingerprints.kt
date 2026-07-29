package app.browzomje.patches.pinterest.navigation.hide_nav_buttons

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// LegoFloatingBottomNavBar è una custom View inflatata dall'XML col nome completo, quindi R8
// non può rinominarla: è un'ancora stabile. Il metodo che costruisce la barra invece cambia
// nome a ogni versione (14.23/14.24: G() — 14.28: W0()), perciò NON lo cerchiamo per nome ma
// per forma: è l'unico `void <nome>()` della classe che inizia impostando l'orientamento
// (`setOrientation(1)`) prima di aggiungere i tasti.
//   rg -n 'setOrientation\(1\)' sources/com/pinterest/navigation/view/lego/LegoFloatingBottomNavBar.java
private const val NAV_BAR_CLASS = "Lcom/pinterest/navigation/view/lego/LegoFloatingBottomNavBar;"

object BottomNavBarSetupFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == NAV_BAR_CLASS && method.callsSetOrientationEarly()
    }
)

/**
 * @return true se fra le prime istruzioni del metodo c'è una chiamata a
 *     `LinearLayout.setOrientation(int)`.
 *
 * Si guardano solo le prime istruzioni perché nel metodo di setup l'orientamento è la prima
 * cosa impostata; altri metodi della classe che toccano l'orientamento lo fanno molto più in là
 * e non verrebbero confusi con questo.
 */
private fun com.android.tools.smali.dexlib2.iface.Method.callsSetOrientationEarly(): Boolean {
    val instructions = implementation?.instructions ?: return false
    return instructions.take(8).any { instruction ->
        instruction is ReferenceInstruction &&
            (instruction.reference as? MethodReference)?.name == "setOrientation"
    }
}
