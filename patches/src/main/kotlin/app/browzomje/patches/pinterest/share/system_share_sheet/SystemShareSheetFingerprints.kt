package app.browzomje.patches.pinterest.share.system_share_sheet

import app.morphe.patcher.Fingerprint

// Issue #38. Il foglio di condivisione interno è un modale con in cima i contatti e sotto una
// striscia di icone di app:
//
//   rg -n 'type="layout" name="[^"]*sharesheet[^"]*"' resources/res/values/public.xml
//   rg -rn "view_lego_sharesheet_modal" sources -g '*.java'      # -> im1/p0, im1/m1 (14.34.0)
//   fd . sources/com/pinterest/feature/sharesheet/view          # -> SharesheetModalAppListView
//
// Il modale nel suo insieme ha nomi che cambiano a ogni versione (`ml1.q0`/`ml1.n1` su 14.32.0,
// `im1.p0`/`im1.m1` su 14.34.0), mentre la striscia di icone no: è una view custom dichiarata
// negli XML di layout, quindi il nome sta in una risorsa.
//
// I nomi delle risorse **non** sono un'ancora utilizzabile qui: nel dex i campi della classe R
// sono offuscati e il valore numerico dell'id cambia a ogni build, quindi né
// `fieldAccess(name = "view_lego_sharesheet_modal")` né un confronto sul letterale funzionerebbero
// (verificato leggendo la tabella dei field id della 14.34.0: nessuno dei nomi di risorsa c'è).
// Restano le classi con il nome vero.

/**
 * Il costruttore della striscia di icone di app dentro il foglio di condivisione interno.
 *
 * <p>`SharesheetModalAppListView` è inflatata dagli XML di layout, quindi R8 non può accorciarne
 * il nome — è la stessa categoria di ancora di `ContextMenuView` e `ContextMenuItemView`. Su
 * 14.32.0 e 14.34.0 il nome è identico, package compreso.
 *
 * <p>Non si vincola la firma: su 14.34.0 il costruttore è `(Context, AttributeSet, int)`, ma
 * l'elenco dei parametri è proprio la cosa che cambia più spesso — la fabbrica delle righe di menu
 * è passata da quattro a cinque parametri fra due versioni, e il fingerprint che ne pretendeva
 * esattamente quattro ha smesso di trovarla in silenzio. Qui la classe è già univoca, quindi il
 * solo `<init>` basta a identificare il punto; se un giorno i costruttori diventassero due,
 * `matchAllOrNull` li aggancia entrambi e l'extension è idempotente.
 */
internal object SharesheetAppListConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/feature/sharesheet/view/SharesheetModalAppListView;",
    name = "<init>",
)

/**
 * Tutti i costruttori di `SendableObject`.
 *
 * <p>`SendableObject` è l'oggetto Parcelable che Pinterest costruisce ogni volta che avvia una
 * condivisione (pin, bacheca o profilo). Non è offuscata perché viaggia tra componenti Android
 * via Parcelable/Bundle. Agganciando i suoi costruttori intercettiamo l'id e il tipo dell'oggetto
 * prima che compaia il foglio di condivisione a schermo.
 */
internal object SendableObjectConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/sendshare/model/SendableObject;",
    name = "<init>",
)

