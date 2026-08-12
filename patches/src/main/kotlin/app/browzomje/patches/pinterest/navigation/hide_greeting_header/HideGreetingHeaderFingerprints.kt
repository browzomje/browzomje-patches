package app.browzomje.patches.pinterest.navigation.hide_greeting_header

import app.morphe.patcher.Fingerprint

// Issue #24. Da 14.28.0 i tasti "+" (Crea) e Posta/Notifiche non stanno più nella barra in
// basso ma nell'intestazione del feed, in alto a destra: li costruisce e li aggiunge il
// fragment del feed dentro `onViewCreated`, in un `LinearLayout` dichiarato nell'XML
// (`home_feed_greeting_header_icon_container`, in `home_feed_greeting_header_view.xml`).
//
//   rg -n 'home_feed_greeting_header_icon_container' sources/     # -> v01/u.java
//   sed -n '1628,1700p' sources/v01/u.java                        # onViewCreated
//
// La classe del fragment è offuscata e cambia a ogni versione (14.28: v01.u), quindi non la si
// cerca per nome.

/**
 * `onViewCreated(View, Bundle)` del fragment del feed principale — il metodo che, quando
 * l'esperimento dell'intestazione è attivo, crea i due tasti e li aggiunge al contenitore.
 *
 * L'ancora è la stringa `HOME_FEED_VIEW_PAGER_CURRENT_POSITION`, con cui il fragment salva e
 * rilegge la tab corrente: nel dex compare solo in questa classe, e le stringhe non vengono
 * offuscate. Nella classe la usano due metodi — `onSaveInstanceState(Bundle)` e questo — che si
 * distinguono per la firma, da cui i `parameters`.
 *
 * Non si filtra per nome del metodo: `onViewCreated` è un override del framework e R8 non
 * potrebbe rinominarlo, ma la firma più la stringa bastano già a identificarlo da sole.
 */
object HomeFeedFragmentOnViewCreatedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    strings = listOf("HOME_FEED_VIEW_PAGER_CURRENT_POSITION"),
)
