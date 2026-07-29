package app.browzomje.patches.pinterest.board.download_board

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Costanti dell'enum delle voci del menu "…" della bacheca (`fs0.a` su 14.28.0).
 *
 * R8 offusca il nome della classe ma non quelli delle costanti di un enum, che devono restare
 * leggibili per `Enum.name()`/`valueOf()`. Se ne cercano cinque insieme: singolarmente sarebbero
 * parole comuni, tutte e cinque insieme identificano solo questo enum.
 */
private val BOARD_OPTION_CONSTANTS = setOf("Edit", "Merge", "Archive", "Unarchive", "PreviewBoard")

/** @return true se la classe è l'enum delle voci del menu della bacheca. */
internal fun ClassDef.isBoardOptionsEnum(): Boolean {
    if (superclass != "Ljava/lang/Enum;") return false
    val fieldNames = staticFields.mapTo(HashSet()) { it.name }
    return fieldNames.containsAll(BOARD_OPTION_CONSTANTS)
}

/**
 * Il builder del menu "…" in alto a destra nella bacheca (`fs0.b.a` su 14.28.0).
 *
 * Riconosciuto per forma invece che per nome: è l'unico metodo statico che prende
 * `(List, Function1, …)` — `kotlin.jvm.functions.Function1` è stdlib Kotlin e non viene
 * offuscata — restituisce un oggetto e nel corpo tocca l'enum delle voci della bacheca.
 * È quest'ultima condizione a rendere il riconoscimento univoco.
 *
 * @param boardOptionsEnum descrittore dell'enum, es. `Lfs0/a;`.
 */
internal fun Method.isBoardOptionsMenuBuilder(boardOptionsEnum: String): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    if (parameters.size < 2) return false
    if (parameters[0].type != "Ljava/util/List;") return false
    if (parameters[1].type != "Lkotlin/jvm/functions/Function1;") return false
    if (!returnType.startsWith("L")) return false

    val instructions = implementation?.instructions ?: return false
    return instructions.any { instruction ->
        when (val reference = (instruction as? ReferenceInstruction)?.reference) {
            is TypeReference -> reference.type == boardOptionsEnum
            is MethodReference -> reference.definingClass == boardOptionsEnum
            else -> false
        }
    }
}
