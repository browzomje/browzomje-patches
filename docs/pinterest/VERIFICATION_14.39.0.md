# Pinterest 14.39.0 — checklist completa delle patch

Aggiornata il 2026-10-11. Include tutte le patch Pinterest già implementate e, in fondo, le richieste nuove. Le patch di Easy Sudoku non appartengono a questo collaudo.

Una casella si spunta solo dopo la verifica funzionale documentata per questo APK. Compilazione, applicazione, presenza di ancore e prove di versioni precedenti **non** verificano il comportamento sulla 14.39.0. Per ogni risultato aggiungere data, build/hash, procedura ed evidenza; una modifica ai sorgenti invalida le prove interessate e richiede regressione.

## Stato della build e del dispositivo

- [x] Originale esplicito: `apks/pinterest_14.39.0.apk`; manifest `com.pinterest`, versione `14.39.0`.
- [x] Controllo iniziale: 58/59, persa la vecchia navbar; dopo aggiornamento dell’ancora al nuovo nome: 59/59.
- [x] Toolchain recuperato: plugin 1.3.2 e patcher 1.5.2 compilati da tag upstream, SDK 36 locale, CLI ufficiale 1.9.1.
- [x] Compilazione finale dopo correzione navbar: build/lint riusciti; unit test NO-SOURCE.
- [x] Applicazione: 31/31 patch, APK ricomposto e firmato, build finale senza `--force`.
- [x] Installazione e login: Xiaomi 11T Pro, Android 14. Reinstallazione autorizzata per cambio firma; connessione USB risolta.
- [ ] Prova funzionale completa: in corso; risultati sotto. Smoke test di avvio e feed riuscito.
- [x] Firma APK v3 valida; manifest, package/versione e iniezioni navbar controllati.
- [x] Controprova di applicazione 14.34.0: 31/31; versione precedente alla sola revisione grafica dell’editor.

## Tutte le patch esistenti

| Verificata | Patch | Applicazione 14.39.0 | Prova dispositivo | Procedura / criterio |
|---|---|---|---|---|
| [ ] | Disable ads | Applicata | PARZIALE: feed e correlati caricano; log rimuove is_promoted (5 pin ultima sessione). Manca controllo OFF e tutte le superfici. | Feed, ricerca, pin correlati e bacheca: nessun promosso; scroll infinito anche su pagine filtrate. Toggle OFF ripristina il comportamento nativo. |
| [ ] | Disable email confirmation dialog | Applicata | Da verificare | Account che mostra il prompt: ON chiude, OFF preserva; login e account funzionanti. |
| [ ] | Hide ad views | Applicata | Da verificare | Pin promosso e schede Shop now: nessuna view o spazio residuo; nessun crash nel closeup. |
| [ ] | Download board | Applicata | Da verificare | Bacheca multipagina: tutti i media, duplicati, annullamento, errori rete e file apribili. |
| [ ] | Open links in the default browser | Applicata | Da verificare | Visita sito, link descrizione e CCT aprono il browser; toggle OFF ripristina Pinterest. |
| [ ] | Hide comments | Applicata | PARZIALE: fumetto standalone ON/OFF verificato; moduli di commenti non vuoti restano manuali. | Anteprima, modulo completo e fumetto nascosti con ON; presenti con OFF; apertura pin sana. |
| [x] | Morphe settings entry | Applicata | PASS: tap reale Profilo → Impostazioni → Morphe; XML settings-via-row.xml. #41 non riprodotta su questo dispositivo. | Profilo → Impostazioni → Morphe apre la schermata al tap (#41); ripetere dopo riavvio. |
| [x] | Morphe settings screen (manifest) | Applicata | PASS: apertura esplicita e morphe://settings, riavvio e persistenza toggle; ritorno al feed. | Apertura esplicita e morphe://settings; back torna a Pinterest; nessun crash. |
| [ ] | Morphe settings screen (label) | Applicata | Da verificare | Voce Morphe corretta nelle lingue italiana e inglese; controllare risorse teen safety. |
| [ ] | Morphe runtime names | Applicata | PASS toast nativo: fingerprint corretto a iz2.e; copia mostra feedback Pinterest senza ResourcesNotFound. | Log delle classi risolte; toast nativi e riga impostazioni funzionanti senza errori reflection. |
| [ ] | Hide greeting header buttons | Applicata | VARIANTE ASSENTE: account con Crea/Notifiche nella navbar. Header privo di questi pulsanti; serve altra variante UI. | ON/OFF: Crea e Notifiche dell’header; feed e layout funzionanti. |
| [x] | Hide Create nav button | Applicata | PASS finale 11/10: Crea da solo ON/OFF, 4 elementi equidistanti senza buco; dettagli e hash sotto. | ON/OFF e riavvio: pulsante Crea nascosto/ripristinato; Home e Profilo raggiungibili. |
| [x] | Hide Notifications nav button | Applicata | PASS finale 11/10: Notifiche da sole ON/OFF, 4 elementi equidistanti senza buco; dettagli e hash sotto. | ON/OFF e riavvio: Notifiche nascoste/ripristinate; barra senza spazi residui. |
| [x] | Hide Search nav button | Applicata | PASS finale 11/10: Ricerca da sola ON/OFF, 4 elementi equidistanti senza buco; Home/Profilo presenti, dettagli sotto. | ON/OFF e riavvio: Ricerca nascosta/ripristinata; navigazione restante funzionante. |
| [ ] | Download pin from long press | Applicata | Da verificare | Pressione lunga su due pin diversi: icona corretta, download del pin scelto, file apribile. |
| [ ] | Copy direct link | Applicata | PARZIALE: voce nativa aggiunta, pin identificato e menu chiuso. Toast nativo corretto e verificato; manca confronto appunti su due pin. | Due pin consecutivi: URL del pin giusto, appunti corretti, menu chiuso e toast visibile. |
| [ ] | Set pin as wallpaper | Applicata | Da verificare | Immagine corretta e selettore sfondo; annullamento sicuro. Applicazione effettiva manuale. |
| [ ] | Sanitize shared links | Applicata | Da verificare | Condivisione pin/bacheca/profilo: rimuove tracking preservando destinazione e testo. |
| [ ] | Sanitize copied links | Applicata | Da verificare | Copia URL lungo e pin.it: tracking rimosso, destinazione corretta; testo normale intatto. |
| [ ] | Neutralize advertising ID | Applicata | Da verificare | Ispezione bytecode patchato e prova runtime del valore ID; non basta assenza di pubblicità. |
| [ ] | Disable AppsFlyer tracking | Applicata | Da verificare | Ispezione hook init/start/isStopped e prova runtime/log o rete; avvio app sano. |
| [ ] | Disable Google Engage worker | Applicata | Da verificare | Ispezione worker patchato, esecuzione/risultato neutro e assenza crash. |
| [ ] | Disable Google Engage | Applicata | Da verificare | Manifest patchato senza componenti Engage; avvio, receiver e job residui. |
| [ ] | Disable third-party trackers | Applicata | Da verificare | Manifest e impostazioni SDK effettive; prova rete separata dall’assenza di crash. |
| [ ] | Hide screenshot share menu | Applicata | PARZIALE: log observer non avviato; serve screenshot reale e controllo OFF. | Screenshot nel closeup: niente upsell con ON; comportamento nativo con OFF. |
| [ ] | Hide search history | Applicata | Da verificare | Ricerche recenti assenti con toggle ON, presenti con OFF; suggerimenti e ricerca funzionano. |
| [ ] | Use the system share sheet | Applicata | PARZIALE: foglio Android su pin, URL corretto e annullamento verificati. | Pin: foglio Android e toggle OFF. Bacheca/profilo conservano il flusso nativo previsto. |
| [ ] | Download video | Applicata | PARZIALE: voce assente su pin statico come previsto; serve pin video. | Pin video: URL e download valido; verificare anche yt-dlp e assenza voce su immagine. |

## Opzioni e comportamenti inclusi nelle patch

Anche questi comportamenti vanno verificati separatamente: condividono una patch, ma possono rompersi indipendentemente.

- [ ] Hide shopping pins: ON/OFF su risultati di catalogo e pin normali.
- [ ] Hide search board modules: ON/OFF su caroselli shopping/featured, risultati normali preservati.
- [ ] Share link only: ON/OFF, testo condiviso e URL validi per pin/bacheca/profilo.
- [ ] Export/import impostazioni: tutti i valori, file non valido, annullamento e riavvio.
- [ ] Diagnostica e log dettagliati: hook/esiti corretti, switch persistente, assenza di dati sensibili.
- [ ] Toast nativi: feedback visibile per copia e download, anche dopo navigazione.

## Regressioni trasversali

- [ ] Avvio a freddo e riavvio senza crash/VerifyError.
- [ ] Home → ricerca → pin → correlati → bacheca → profilo → impostazioni → indietro.
- [ ] Scroll multipagina su feed, ricerca e bacheca senza blocco/inconsistenza RecyclerView.
- [ ] Toggle ON e OFF, persistenza dopo force-stop e riapertura; export/import impostazioni.
- [ ] Tema chiaro/scuro, rotazione, lingua italiana/inglese.
- [ ] Pin immagine, video, carosello e link esterno; menu appartenente al pin giusto.
- [ ] Annullamento di condivisione/download/sfondo senza effetti indesiderati.
- [ ] Controprova di applicazione su APK originale 14.34.0 dopo modifiche condivise.

## Nuove patch implementate (31 patch totali)

| Richiesta | Implementazione | Verifica |
|---|---|---|
| #41 — tap impostazioni Morphe | Nessuna modifica necessaria sul device attuale | Tap verificato su Android 14; Sony Android 15 resta manuale |
| #42 — Show pin image information | Implementata, opzione disattivata inizialmente | PARZIALE: due pin, 736×805 e 720×1599 px · JPEG; altri formati/carousel manuali |
| #44 — Download images in highest available quality | Implementata, opzione disattivata inizialmente | PARZIALE: download nativo intercettato, JPEG 736×805 valido e coincidente con la variante maggiore del modello; fallback/altri media manuali |
| #45 — Filter pin menu actions | Implementata, lista inizialmente vuota | PASS base: selezione Segnala ON/OFF nel menu reale, Copia link diretto preservata; Salva/Annulla e cambio lingua persistenti. Altre azioni e varianti restano manuali |

## Release

- [ ] Tutte le prove applicabili superate e manuali confermate: release completa.
- [x] Build e smoke test superati, verifiche manuali residue elencate: candidata pre-release dev.

Smoke test sul dispositivo riuscito; nessuna release pubblicata durante il collaudo. Regressione nei toast corretta e verificata; nuove feature implementate dopo lo smoke test. Nessun commento pubblicato sulle issue.

## Evidenze della build candidata

- Originale SHA-256: `4ecc7f9a34c89fd98d0e2133c294517772857b935969847bfd92b3e25b13e78d`.
- APK patchato SHA-256: `825e66b8238fd1f98399e4cca998b21457536dd371e32633ed24f5caf80a9b79`.
- Bundle SHA-256: `8696803d9b70ffc5cf642ab14ebf1755fe4761f33323279a86dd89cedfc14ed9`.

La build corrente è registrata in `pinterest/build_14.39.0_menu_choices_final.log` (31 patch, nessuna fallita). I report iniziali da 28 patch e le screenshot del 9 ottobre documentano il porting precedente; non sono prove della nuova interfaccia. La controprova delle 31 patch su 14.34 è in `pinterest/patch_regression_14.34.0_final.log`. Firma corrente verificata in `build/verification_14.39.0/signature-final.txt`.

## Sessione dispositivo del 2026-10-09

Le prove del 9 ottobre precedono la build grafica corrente; i risultati invariati sono conservati con questa distinzione. Le tre opzioni navbar sono state ripristinate OFF. XML accessibility conserva alcuni nodi dei tab nascosti, mentre gli screenshot mostrano la barra corretta: resta da approfondire per lettori schermo. Evidenze locali in `pinterest/build/verification_14.39.0/`, escluse da Git per contenuti dell’account. La successiva correzione toast richiede ripetere i test interessati sulla nuova build.

## Sessione del 2026-10-10 — interfaccia filtro

Build/lint e applicazione 31/31 riusciti, APK sopra installato. Editor con PinSans, pannello inferiore arrotondato, campo multilinea e pulsanti Annulla/Salva; riga di apertura allineata alle altre impostazioni nella sezione Menu dei Pin. Screenshot locali `filter-style-editor.png` e `filter-style-keyboard.png`: contenuto e pulsanti raggiungibili anche con tastiera. Controllo statico: 59 gruppi di traduzioni, ognuno completo in EN + 13 lingue, placeholder coerenti. Il controllo automatico dei testi non sostituisce una revisione madrelingua.

Controprova 14.34: 31/31 applicazioni riuscite prima della sola revisione grafica dell’editor; non equivale a collaudo sul dispositivo 14.34. Ispezione dex privacy: ID pubblicitario vuoto e limit tracking=true, AppsFlyer neutralizzato, worker Engage neutralizzato. La verifica del traffico di rete resta manuale, quindi le relative caselle non sono spuntate.

- [x] Interfaccia filtro: titolo, descrizione, Annulla e Salva controllati sul telefono in en/it/es/fr/de/pt/ru/ja/zh/ko/pl/nl/tr/ar; screenshot/XML locali `locale-*-editor`. Arabo con layout RTL controllato visivamente. Lingua applicazione ripristinata a quella di sistema.
- [ ] Revisione madrelingua della qualità delle traduzioni.

## Revisione successiva: selezione delle voci, senza testo libero

L’editor iniziale era una lista di nomi digitati dall’utente: quei nomi non si traducevano
cambiando lingua e rendevano il filtro dipendente dalla lingua. Sostituito con caselle
selezionabili che usano le etichette originali localizzate di Pinterest. Il backup salva
nomi di risorsa stabili (`@resource:...`), non le etichette; il filtro risolve i testi nella
lingua corrente. Migrazione dei nomi noti del prototipo nelle 14 lingue settings.
Le voci Morphe sono escluse e mantengono le loro opzioni separate.
Le prove precedenti del campo testo sono storiche e non verificano questa nuova selezione.

- [x] Selettore finale: Annulla conserva le selezioni; Salva persiste dopo force-stop.
- [x] Scelta Segnala in italiano ancora selezionata con etichetta araba الإبلاغ dopo cambio lingua; deselezione persistente dopo riavvio. Evidenza locale `filter-choices-final.log` e `choices-ar-selected.png`.
- [ ] Tutte le azioni e varianti del menu (incluse quelle disponibili solo ai proprietari del Pin).

- [x] Filtro finale sul menu reale: Segnala assente ON, presente OFF; Copia link diretto preservata. `filter-native-final.log`, `choices-filter-on-menu.png`, `choices-filter-off-menu.png`.
- [x] Tutte le 20 scelte hanno risorse tradotte in en/it/es/fr/de/pt/ru/ja/zh/ko/pl/nl/tr/ar nell’APK madre; nessuna etichetta tecnica o inglese imposta dal selettore.
- [x] Ripristino prova: filtro senza selezioni, lingua applicazione di sistema, opzioni info immagine/qualità massima OFF come default. Selettore italiano pronto sul telefono.

Il tutorial Pinterest “Scarica immagine è stata spostata in Condividi” intercetta il primo tap sui tre puntini: attendere/chiudere il suggerimento e ripetere il tap prima di giudicare il menu. Le asserzioni di collaudo richiedono la presenza di Copia link diretto per assicurarsi che sia davvero aperto l’overflow.

Candidata destinata a dev, non alla release completa. Le caselle aperte richiedono ancora conferma manuale; nessun risultato di applicazione viene contato come prova funzionale.

## Artefatto pubblicato — v1.11.0-dev.1

Pre-release pubblicata da semantic-release su dev; workflow `38038823117` concluso
con successo, inclusa verifica delle note e attestazione. Nessun aggiornamento di main.
Corretto preset conventionalcommits 10 incompatibile con writer 8: ripristinato 9.3.1,
lock aggiornato, verifica reale feat/fix/bump nella pipeline e guard Dependabot per major.

Bundle pubblico SHA256 `676d6c18c3ff3d32e801da331e2f001278de4450440e78cc3840e826f9d11a2d`,
confrontato con digest dell’asset GitHub. Applicazione dell’asset scaricato all’originale
14.39: **31/31**, rebuild/firma riusciti senza force, report locale
`patch_result_release_1.11.0-dev.1.json`. APK risultante SHA256
`982c149d19db39cb39b37f4e4b471b64d5a47c419ce4528b6364a2134122aa66`,
firma v3 valida, stesso certificato dei collaudi precedenti; installato senza perdere login.
Questo è ora `pinterest/pinterest_patched.apk` e la build sul telefono per il collaudo manuale.
Le prove più estese sopra riguardano le candidate locali indicate; il bundle pubblico
contiene lo stesso codice funzionale e metadata di release aggiornati. Smoke test corrente:
apertura a freddo delle impostazioni, caricamento preferenze e selettore italiano.

Release: https://github.com/browzomje/browzomje-patches/releases/tag/v1.11.0-dev.1
Workflow: https://github.com/browzomje/browzomje-patches/actions/runs/38038823117
Policy del proprietario: lavoro e pre-release su dev; main dopo completamento del collaudo.

## Build locale del 2026-10-11 — regressione UI e default

APK 14.39.0 ricostruito dai sorgenti aggiornati (non coincide con l’asset pubblicato v1.11.0-dev.1): SHA-256 `b65a2c1b3d5bd3694c8ee2191b5f81805b65662f21433dc6fff7a3f4d64b4c35`. Build/lint riusciti, 31/31 patch applicate, firma verificata con lo stesso certificato Morphe; installazione `adb install -r` riuscita senza cancellare login o preferenze. Log locale `build_14.39.0_ui_oct11_final.log`.

- [x] Default dei 19 toggle: 14 attivi, disattivi solo commenti, massima qualità e i tre pulsanti della navigazione. Verifica configurazione sorgente; preferenze già salvate preservate.
- [x] Tutte le 61 stringhe Morphe hanno 14 traduzioni non mancanti, comprese le nuove descrizioni pubblicità/prodotti. Rendering delle due descrizioni verificato in italiano e arabo (`oct11-description-it.xml`, `oct11-description-ar.xml`).
- [x] Scrollbar dell’editor sempre visibile senza gesto recente: screenshot `oct11-scrollbar-it.png` e `oct11-scrollbar-ar.png`, anche RTL. Selezioni esistenti preservate.
- [ ] Variante header con Crea/Notifiche in alto: collasso del figlio diretto implementato, ma questo account usa i pulsanti nella barra inferiore; richiede prova manuale su quella variante.

La precedente prova combinata dei tre pulsanti non copriva i buchi con un solo pulsante nascosto, segnalati dall’utente. La prima build dell’11 ottobre ha riprodotto il difetto su Ricerca; la correzione finale usa il View accessor del contratto del tab invece di un getter Gestalt della sola icona. Le prove specifiche della build finale sono riportate sotto e sostituiscono il precedente criterio generico “nessuno spazio residuo”. Le altre verifiche manuali della checklist restano aperte. In seguito l’utente ha richiesto esplicitamente la pubblicazione stabile su main; vedere la sezione dedicata sotto.

- [x] Ricerca, Crea e Notifiche: ciascun toggle provato da solo ON/OFF con force-stop, sulla build finale. La barra passa da 5 a 4 elementi reali; gli elementi rimasti hanno centri x = 168/416/664/912, distanza uniforme 248 px. Nessun contenitore accessibile residuo per il tab nascosto. Ripristino: di nuovo 5 elementi, distanze 198–199 px. Evidenze `nav-oct11-hidden-0/1/2.xml` e `.png`, `nav-oct11-restored.xml`, `nav-oct11-bounds.json`; screenshot controllati e assert geometrici superati. I tre toggle sono stati lasciati OFF al termine.

## Versioni nella schermata e pubblicazione su main — 2026-10-11

- [x] Versione app e bundle applicato visibili in testa alla schermata Morphe. Build locale con bundle 1.11.0-dev.2: app 14.39.0 e patch 1.11.0-dev.2 verificate sul telefono in italiano e arabo, dopo aggiornamento senza cancellazione dati (`versions-dev.log`, `versions-it/ar.xml` e screenshot). Build/lint e 31/31 patch riusciti; tutte le 63 stringhe complete nelle 14 lingue.
- La pubblicazione stabile su main è richiesta esplicitamente dall’utente in questo turno. Le caselle manuali ancora aperte restano tali: l’autorizzazione alla release non è una prova funzionale di quelle patch.

- [x] Release stabile `v1.11.0` pubblicata: workflow `38093527159` completato con successo (build, verifica release notes, pubblicazione e attestazione). Main sorgente `102146a`, commit release `6897c6f`; dev aggiornato automaticamente alla release.
- [x] Asset ufficiale `patches-1.11.0.mpp` scaricato e digest verificato contro GitHub: SHA-256 `0c734944287c97b1225295c25883c04b743f8148ac90c76a5081970e2d73bc75`. Applicazione all’originale esplicito 14.39.0: 31/31, ricomposizione e firma riuscite (`patch_result_release_1.11.0.json`).
- [x] APK ricavato dall’asset stabile installato con update, stessa firma e dati preservati: SHA-256 `ff5b5db0f89c346278f3b37ac4a63dccec52c80859f8dad37dbeb74cbe4da808`. La schermata mostra effettivamente app 14.39.0 / patch 1.11.0 sia IT sia AR (`versions-release-1.11.0.log`, XML/screenshot `versions-it/ar`); lingua ripristinata al sistema.

## Verified image downloads and recommended settings — 2026-10-11

Final implementation for the next minor release:

- Shared image resolver reads a bounded HTTP prefix to verify dimensions and actual format.
  The inline label describes the selected download variant, with checking/unavailable states.
- Single images and captured board image batches use a persisted queue and Android
  DownloadManager. Failed original transfers retry the app variant; duplicate filenames
  preserve existing files. Board batches are persisted before network work.
- Optional like/comment hiding and inline metadata replacement; expanded pin menu filters,
  including dynamic Follow/Unfollow labels, wallpaper and direct image links.
- General creator preset after version information, with 20 enabled options and Search/search
  history disabled. It also applies the shared recommended pin menu selection.
- A single in-app restart notice replaces queued toasts. The title is “Morphe - browzomje”.

Validation on Pinterest 14.39.0, Android 14:

- Build/lint successful, all 31 patches applied and signed APK installed with data preserved.
- JPEG fixture: app model 736×805; displayed and downloaded original 1199×1312, 98010 bytes.
- Board: 18 loaded images downloaded in the background; all extracted files verified.
- GIF: 500×210, 818523 bytes, all 14 frames preserved.
- All 22 preset switches checked: 20 ON, Search and search history OFF.
- Restart notice absent after repeated restarts, including after applying the preset.
- JVM tests exercise production resolver/cache with PNG, GIF, WebP, HTTP failures, invalid
  ranges and smaller originals; production dynamic menu filtering and native bridge also pass.

Run from the repository root:

```bash
python3 tests/pinterest/test_image_download.py
python3 tests/pinterest/test_pin_actions.py
```

Tests require a JDK, Python and Pillow. Android stubs in the JVM harness do not replace
on-device verification. WebP and the native share-panel download action still need a device
fixture; this account does not expose that download action. Boards cover captured/loaded pins,
without independently fetching unvisited pages. Android force-stop can defer jobs and completion
receivers until the app is reopened; ordinary backgrounding keeps system downloads running.
