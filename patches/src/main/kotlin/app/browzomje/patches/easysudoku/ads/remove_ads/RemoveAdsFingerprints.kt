package app.browzomje.patches.easysudoku.ads.remove_ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

// Easy Sudoku non offusca i nomi delle CLASSI dell'app (com.meevii.*), solo quelli dei metodi.
// Tutti i fingerprint qui sotto si ancorano quindi alla classe + firma + chiamate contenute nel
// metodo, e MAI al nome del metodo: così sopravvivono a un aggiornamento che rimescola solo i
// nomi (che è ciò che R8 fa a ogni build).
//
// Nemmeno `accessFlags` viene dichiarato: il patcher lo confronta per uguaglianza esatta, quindi
// un flag in più aggiunto dal compilatore (synthetic, bridge, …) farebbe fallire il match senza
// aggiungere nulla — classe e firma bastano già a rendere unico il metodo.
//
// Come sono stati trovati (da easy_sudoku/easy_sudoku_extracted/sources):
//   rg -n 'no_ads_state|removeAd' com/meevii                    -> com/meevii/common/utils/AdUtil.java
//   rg -n 'classic_sudoku_remove_ads' -g '*.java'               -> com/meevii/iap/hepler/d.java
//   rg -ln 'qf\.b\.d\(com\.meevii\.iap\.hepler\.d\.class\)\)\.w\(\)' -g '*.java' | wc -l  -> 45 usi

private const val AD_UTIL = "Lcom/meevii/common/utils/AdUtil;"
private const val IAP_HELPER = "Lcom/meevii/iap/hepler/d;"

/**
 * `IapHelper.w()` → "l'utente ha già tolto la pubblicità" (acquisto una tantum o abbonamento).
 *
 * È il vero interruttore: 45 punti dell'app lo interrogano, fra cui tutti i percorsi
 * pubblicitari di `AdUtil` e gli sblocchi delle modalità Fun/Ice/Killer Sudoku
 * (`com.meevii.common.utils.c0`).
 *
 * La classe ha quattro metodi `public boolean` senza parametri (`t`, `u`, `v`, `w`). Quello che
 * ci serve è l'unico che **chiama un altro metodo `()Z` della stessa classe** (`v()`, "acquisto
 * locale registrato") e poi legge un `Boolean` dalla `LiveData` dello stato:
 *
 * ```java
 * public boolean w() {
 *     …
 *     if (v()) return true;                                    // <- primo filtro
 *     return this.f51595a.getValue() != null
 *         && this.f51595a.getValue().booleanValue();           // <- secondo filtro
 * }
 * ```
 */
object AdFreeStateFingerprint : Fingerprint(
    definingClass = IAP_HELPER,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = IAP_HELPER, parameters = emptyList(), returnType = "Z"),
        methodCall(definingClass = "Ljava/lang/Boolean;", name = "booleanValue", returnType = "Z"),
    ),
)

/**
 * `AdUtil.G()` → la stessa domanda, ma passando dal service locator:
 *
 * ```java
 * public static boolean G() {
 *     com.meevii.iap.hepler.d dVar = (…) qf.b.d(com.meevii.iap.hepler.d.class);
 *     return dVar != null && dVar.w();
 * }
 * ```
 *
 * Va patchato **anche** questo, non solo [AdFreeStateFingerprint]: se il service locator non ha
 * ancora registrato l'IapHelper, `G()` restituisce `false` senza mai arrivare a `w()`, e in
 * quella finestra (l'avvio dell'app, cioè proprio quando parte l'annuncio di apertura) la
 * pubblicità passerebbe lo stesso.
 *
 * In `AdUtil` ci sono altri `static boolean` senza parametri (`A`, `B`, `E`, `y`, `z`), ma è
 * l'unico che chiama un metodo `()Z` sull'IapHelper.
 */
object AdUtilIsAdFreeFingerprint : Fingerprint(
    definingClass = AD_UTIL,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = IAP_HELPER, parameters = emptyList(), returnType = "Z"),
    ),
)

/**
 * `AdUtil.n(AdPositionType, boolean)` → "posso mostrare un interstitial adesso?".
 *
 * Unico metodo di `AdUtil` con questa firma. L'enum annidato `AdUtil$AdPositionType` **non** è
 * offuscato (i nomi delle costanti di un enum finiscono nel dex), quindi è un'ancora solida.
 *
 * È la rete di sicurezza degli interstitial: li spegne a prescindere dallo stato dell'IAP, così
 * un percorso che leggesse il flag "ads rimossi" da un'altra parte resta comunque muto.
 */
object AdUtilShouldShowInterstitialFingerprint : Fingerprint(
    definingClass = AD_UTIL,
    returnType = "Z",
    parameters = listOf("Lcom/meevii/common/utils/AdUtil\$AdPositionType;", "Z"),
)

/**
 * `AdUtil.R(String placement, ViewGroup container)` → carica il banner nel contenitore.
 *
 * Unico metodo `static void (String, ViewGroup)` della classe. Chiamato da `MainActivity.o5()`
 * con `findViewById(R.id.adBanner)`.
 */
object AdUtilShowBannerFingerprint : Fingerprint(
    definingClass = AD_UTIL,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Landroid/view/ViewGroup;"),
)
