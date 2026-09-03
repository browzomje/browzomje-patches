package app.browzomje.patches.pinterest.comments.hide_comments

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Issue #36. Il closeup del pin è fatto di "moduli", e ognuno decide da sé se comparire:
//
//   rg -n "class d extends" sources/st/d.java          # -> extends PinCloseupBaseModule
//   rg -n "shouldShowForPin|hasContent" sources/com/pinterest/activity/pin/view/modules/PinCloseupBaseModule.java
//
// `PinCloseupBaseModule` ha il nome vero, e con esso il nome del metodo `shouldShowForPin()`: è
// il punto giusto dove intervenire, perché è la stessa strada che l'app percorre quando un pin non
// ha commenti. Quali moduli siano quelli dei commenti si trova dai layout:
//
//   rg -n 'name="pin_closeup_(unified|new)_comments_module"' resources/res/values/public.xml
//   rg -rn "pin_closeup_unified_comments_module" sources -g '*.java'    # -> st/x3 (14.34.0)
//   rg -rn "pin_closeup_new_comments_module"     sources -g '*.java'    # -> st/e3 (14.34.0)
//
// I due nomi cambiano a ogni versione (su 14.32.0 erano `pt.v3` e `pt.b3`), quindi non si nominano.

/**
 * Le classi non offuscate che compaiono **solo** dentro la sezione commenti completa.
 *
 * <p>Sono view custom dichiarate negli XML di layout: il nome sta in una risorsa, quindi R8 non
 * può accorciarlo. È la stessa categoria di ancora di `ContextMenuView` e `GenericWebImageView`.
 *
 * <p>Ne bastano tre su cinque: il modulo ne casta almeno una nel proprio costruttore, e chiederne
 * tutte vorrebbe dire rompersi se Pinterest ne toglie una — il vincolo va tenuto al minimo
 * indispensabile.
 */
private val COMMENT_VIEW_TYPES = setOf(
    "Lcom/pinterest/activity/pin/view/unifiedcomments/CommentComposerView;",
    "Lcom/pinterest/activity/pin/view/unifiedcomments/CommentPreviewView;",
    "Lcom/pinterest/feature/unifiedcomments/view/PinCommentReactionHeaderView;",
)

/**
 * L'ancora dell'**anteprima** commenti, che invece di view custom usa solo componenti Gestalt
 * generici (un'icona, un testo, uno spinner) e non sarebbe quindi distinguibile per tipo.
 *
 * <p>`aggregatedComment` è il nome con cui il commento aggregato viaggia nel protocollo di rete:
 * è un valore di annotazione Gson, cioè la categoria di ancora più stabile che ci sia — Pinterest
 * non può rinominarlo senza rompere il proprio backend.
 *
 * <p>Su 14.34.0 la stringa compare in tre classi (`CommentPreviewView`, `qq1.e0` e il modulo), ma
 * qui non serve che sia univoca da sola: il fingerprint chiede **anche** che la classe abbia un
 * `shouldShowForPin()`, cosa che solo i moduli del closeup hanno. Insieme, le due condizioni
 * selezionano esattamente i due moduli commenti — verificato sul dump 14.34.0.
 */
private const val COMMENT_PREVIEW_MARKER = "aggregatedComment"

/**
 * @return true se la classe nomina, da qualche parte, una delle ancore dei commenti.
 *
 * <p>Si guardano sia i tipi dei campi sia i riferimenti dentro i metodi: la stessa classe può
 * comparire come tipo di un campo (`iget-object`), come bersaglio di un `check-cast` o come
 * destinataria di una chiamata, e limitarsi a una sola di queste forme vorrebbe dire dipendere da
 * come il compilatore ha deciso di scrivere il costruttore quel giorno.
 */
private fun ClassDef.mentionsComments(): Boolean {
    if (fields.any { it.type in COMMENT_VIEW_TYPES }) {
        return true
    }
    return methods.any { method ->
        method.implementation?.instructions?.any { instruction ->
            when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                is TypeReference -> reference.type in COMMENT_VIEW_TYPES
                is FieldReference ->
                    reference.type in COMMENT_VIEW_TYPES ||
                        reference.definingClass in COMMENT_VIEW_TYPES

                is MethodReference -> reference.definingClass in COMMENT_VIEW_TYPES
                is StringReference -> reference.string == COMMENT_PREVIEW_MARKER
                else -> false
            }
        } == true
    }
}

/**
 * `shouldShowForPin()` dei due moduli commenti del closeup — la sezione completa e l'anteprima.
 *
 * <p>Si usa con `matchAllOrNull()` perché i moduli sono due e convivono: quale dei due venga
 * mostrato lo decide un experiment lato server, quindi agganciarne uno solo darebbe il classico
 * "a me funziona" (è la struttura ricorrente di Pinterest: quasi ogni feature ha due percorsi).
 *
 * <p>Il nome del metodo è un'ancora legittima anche se la classe che lo dichiara è offuscata:
 * `shouldShowForPin` è dichiarato da `PinCloseupBaseModule`, che il nome vero ce l'ha, e un
 * override non può che chiamarsi come il metodo che sovrascrive.
 */
internal object CommentsModuleShouldShowForPinFingerprint : Fingerprint(
    name = "shouldShowForPin",
    returnType = "Z",
    parameters = emptyList(),
    custom = { _, classDef -> classDef.mentionsComments() },
)

/**
 * I costruttori della barra delle azioni del pin, quella con "Salva" e il fumetto dei commenti.
 *
 * <p>`UnifiedPinActionBarView` ha il nome vero — è una view inflatata dagli XML — ed è l'unico
 * posto in cui il fumetto vive: non sta dentro i moduli commenti, quindi non sparisce insieme a
 * loro. Senza questo secondo aggancio la sezione resterebbe raggiungibile con un tocco, mentre la
 * issue chiede esplicitamente che non lo sia.
 *
 * <p>Si usa con `matchAllOrNull()`: la classe ha due costruttori indipendenti — `(Context,
 * AttributeSet)` e `(Context, AttributeSet, int)` — che chiamano entrambi `super()` per conto
 * proprio invece di delegare l'uno all'altro. Non si sa in anticipo quale delle due strade prenda
 * l'inflater, quindi si agganciano entrambe; nascondere due volte lo stesso tasto è innocuo,
 * perché `MorpheViews` è idempotente.
 */
internal object PinActionBarConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/feature/pin/closeup/view/UnifiedPinActionBarView;",
    name = "<init>",
)
