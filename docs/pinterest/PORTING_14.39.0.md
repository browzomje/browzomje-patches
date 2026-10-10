# Pinterest 14.39.0 — porting navbar e stato del collaudo

Il layout `activity_main_bottom_nav_hide_floating_nav_view.xml` usa ora
`com.pinterest.navigation.view.FloatingBottomNavBar`. Il fingerprint accetta
anche il vecchio `navigation.view.lego.LegoFloatingBottomNavBar`, presente negli
originali 14.28 e 14.34, per conservarne la compatibilità.

Il setup 14.39 è `X0(): void`, con `setOrientation(1)` alla seconda istruzione.
Il dex originale ha 13 registri: p0=v12 viene riusato per oggetti e booleani
prima dei return. L’iniezione precedente poteva quindi passare un ricevitore
sbagliato o provocare un VerifyError. Ora si cattura `this` all’ingresso usando
v0/v1, che sono locali liberi; l’extension legge i tab con `View.post` a setup
concluso. Il guard del patcher richiede due locali e nessun argomento.

La lista dei tab espone ancora elementi con un accessor enum e uno View;
i nomi HOME, CREATE, NOTIFICATIONS, PROFILE e SEARCH sono invariati.
Verificato il dex patchato: tre chiamate deferred con receiver originale e
ordinali 4, 2, 1. Il ramo S0() e tab aggiunti successivamente richiedono prove
su dispositivo: l’analisi non dimostra che ogni variante della UI sia coperta.

## Reverse engineering riproducibile

Sul dump dell’originale 14.39, dopo il controllo ancore:

```bash
rg -n 'FloatingBottom' resources/res/layout/activity_main*
rg -n 'setOrientation|Y0\(' sources/com/pinterest/navigation/view/FloatingBottomNavBar.java
```

Leggere anche `sources/x82/y0.java` (accessor enum/View) e `sources/le0/a.java`
(enum tab). I nomi offuscati sono evidenza di questa versione, non nuovi vincoli
nel fingerprint. jadx 1.5.6 riporta 447 errori globali: per i registri di X0 è
stato letto direttamente il dex, non dedotto il risultato dal Java decompilato.

## Verifiche

- Build e lint riusciti; unit test NO-SOURCE.
- 28/28 patch applicate su 14.39, APK prodotto e firma v3 verificata.
- Metadata 14.39 aggiunto; build finale applicata senza force.
- Controprova 14.34 con la correzione navbar: 28/28 e APK prodotto.
- Xiaomi 11T Pro / Android 14: installazione e login riusciti; impostazioni e navbar ON/OFF verificati. Collaudo completo in corso.
- Nessuna release stabile/pre-release pubblicata, nessun commento alle issue.

La [checklist completa](VERIFICATION_14.39.0.md) distingue applicazione e runtime,
include tutte le patch già presenti, subopzioni e regressioni. #41 non si
riproduce al tap sul dispositivo attuale; Sony Android 15 resta da verificare. #42, #44 e #45 sono state valutate, ma le feature nuove restano
subordinate al collaudo del porting richiesto dall’utente.

## Regressioni trovate dal collaudo

Il fingerprint toast `(String,int)` selezionava `yt.u4`, un modello in cui int
è una risorsa stringa, causando ResourcesNotFound per 5000. Ora si richiede nel
builder la scrittura IPUT del campo intero di `gestalt.toast.c`: due candidati
`iz2.e/d`, scelta la base `iz2.e`. Verificato toast nativo dopo copia sul device.

La variante social action bar costruisce un pulsante commenti `eb1.i` a sé,
con `action_module_comments_icon`, fuori da UnifiedPinActionBarView. Agganciato
il costruttore dopo super per nome del campo risorsa e presenza GestaltIcon;
HideComments cerca sia wrapper storico sia icon della variante nuova.

Stile menu: il primo TextView può essere intestazione/preview, non una voce
azione. Copiare solo una RelativeLayout cliccabile e visibile evita il font
più piccolo/sottile nelle righe Morphe. Si copiano anche spaziatura e proprietà
font variabile (API26 guardata), oltre a dimensione, typeface e colori.

Traduzioni: rivisti backup, diagnostica, cronologia e download prolungato in
EN + tutte le 13 lingue delle impostazioni. Il backup spiega disinstallazione/
cancellazione dati, non un generico aggiornamento. Corretto imperativo olandese
“Directe link kopiëren”, completati feedback download polacchi e distinti
messaggi di download malese/indonesiano e ceco/slovacco.

## Nuove opzioni e interfaccia — 2026-10-10

31 patch applicate e APK installato: informazioni immagine dal modello del pin corrente, download nativo collegato alla variante di maggiore width disponibile, filtro di sole righe native per etichette localizzate esatte. Le righe Morphe sono marcate e escluse dal filtro. Nessuna riscrittura arbitraria degli URL e nessuna dimensione file inventata.

Filtro impostazioni: sezione Menu dei Pin, riga coerente con i toggle, editor inferiore arrotondato con PinSans, campo multilinea e Annulla/Salva. Prova visiva con tastiera e senza; tutte le nuove etichette presenti in 14 lingue. Backup conserva anche la lista filtro e valida il payload prima di modificare preferenze. Stato del collaudo completo nella checklist.

La revisione finale elimina il campo testo: checkbox con etichette delle risorse native
Pinterest, memorizzazione per nome risorsa indipendente dalla lingua, risoluzione locale
al filtro e migrazione dei nomi noti del prototipo. Annulla non salva; Salva persiste la
selezione. Il filtro attraversa anche i gruppi delle azioni See more/See less, escludendo
interi sottoalberi di righe Morphe marcate. Collaudo corrente nella checklist.

## Revisione UI del 2026-10-11

L’editor delle voci del menu mantiene la scrollbar verticale visibile (`setScrollbarFadingEnabled(false)`, larghezza 4 dp, inset interno). La selezione esistente resta invariata.

La ricerca del View accessor dei tab deve partire dal contratto dell’interfaccia: su 14.39 `x82.y0.i(): View` restituisce l’intero elemento. Scegliere il primo getter View della classe concreta può restituire solo l’icona Gestalt, lasciando il contenitore vuoto. Il fallback alle classi concrete resta per varianti precedenti. Per i contenitori orizzontali si azzerano larghezza, margini start/end e peso, oltre ad altezza e visibilità; un listener mantiene il collasso dopo i layout nativi. Nell’header si risale al figlio diretto di `home_feed_greeting_header_icon_container`, senza alterare la lista/model dei tab.

Default: tutte le 19 opzioni booleane sono attive tranne commenti, massima qualità e i tre pulsanti di navigazione. Il filtro menu conserva la selezione vuota come default. Nessuna migrazione sovrascrive preferenze già salvate. Aggiunte descrizioni per pubblicità e prodotti: tutte le 61 stringhe Morphe hanno 14 traduzioni (en/it/es/fr/de/pt/ru/ja/zh/ko/pl/nl/tr/ar).

La massima qualità intercetta la voce nativa `download_image` nel menu overflow del Pin, se disponibile, e usa la variante del modello `images` con `width` maggiore. DownloadManager scarica direttamente l’URL in `Download/Pinterest/Pins`; nessun ingrandimento, ricompressione o ricerca di un originale non esposto dal modello. Il download da una bacheca sceglie già la stessa variante; l’intercettazione non copre automaticamente ogni download dal foglio Condividi.
