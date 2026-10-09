# Pinterest 14.39.0 — checklist completa delle patch

Aggiornata il 2026-10-09. Include tutte le patch Pinterest già implementate e, in fondo, le richieste nuove. Le patch di Easy Sudoku non appartengono a questo collaudo.

Una casella si spunta solo dopo la verifica funzionale documentata per questo APK. Compilazione, applicazione, presenza di ancore e prove di versioni precedenti **non** verificano il comportamento sulla 14.39.0. Per ogni risultato aggiungere data, build/hash, procedura ed evidenza; una modifica ai sorgenti invalida le prove interessate e richiede regressione.

## Stato della build e del dispositivo

- [x] Originale esplicito: `apks/pinterest_14.39.0.apk`; manifest `com.pinterest`, versione `14.39.0`.
- [x] Controllo iniziale: 58/59, persa la vecchia navbar; dopo aggiornamento dell’ancora al nuovo nome: 59/59.
- [x] Toolchain recuperato: plugin 1.3.2 e patcher 1.5.2 compilati da tag upstream, SDK 36 locale, CLI ufficiale 1.9.1.
- [x] Compilazione finale dopo correzione navbar: build/lint riusciti; unit test NO-SOURCE.
- [x] Applicazione: 28/28 patch, APK ricomposto e firmato, build finale senza `--force`.
- [ ] Installazione: tentata, bloccata da `adb: device unauthorized`.
- [ ] Prova funzionale completa: nessuna eseguita; necessario accettare debug USB sul telefono.
- [x] Firma APK v3 valida; manifest, package/versione e iniezioni navbar controllati.
- [x] Controprova di applicazione 14.34.0: 28/28, APK prodotto con la correzione navbar prima dell’aggiunta del metadata finale 14.39.

## Tutte le patch esistenti

| Verificata | Patch | Applicazione 14.39.0 | Prova dispositivo | Procedura / criterio |
|---|---|---|---|---|
| [ ] | Disable ads | Applicata | Bloccata: adb unauthorized | Feed, ricerca, pin correlati e bacheca: nessun promosso; scroll infinito anche su pagine filtrate. Toggle OFF ripristina il comportamento nativo. |
| [ ] | Disable email confirmation dialog | Applicata | Bloccata: adb unauthorized | Account che mostra il prompt: ON chiude, OFF preserva; login e account funzionanti. |
| [ ] | Hide ad views | Applicata | Bloccata: adb unauthorized | Pin promosso e schede Shop now: nessuna view o spazio residuo; nessun crash nel closeup. |
| [ ] | Download board | Applicata | Bloccata: adb unauthorized | Bacheca multipagina: tutti i media, duplicati, annullamento, errori rete e file apribili. |
| [ ] | Open links in the default browser | Applicata | Bloccata: adb unauthorized | Visita sito, link descrizione e CCT aprono il browser; toggle OFF ripristina Pinterest. |
| [ ] | Hide comments | Applicata | Bloccata: adb unauthorized | Anteprima, modulo completo e fumetto nascosti con ON; presenti con OFF; apertura pin sana. |
| [ ] | Morphe settings entry | Applicata | Bloccata: adb unauthorized | Profilo → Impostazioni → Morphe apre la schermata al tap (#41); ripetere dopo riavvio. |
| [ ] | Morphe settings screen (manifest) | Applicata | Bloccata: adb unauthorized | Apertura esplicita e morphe://settings; back torna a Pinterest; nessun crash. |
| [ ] | Morphe settings screen (label) | Applicata | Bloccata: adb unauthorized | Voce Morphe corretta nelle lingue italiana e inglese; controllare risorse teen safety. |
| [ ] | Morphe runtime names | Applicata | Bloccata: adb unauthorized | Log delle classi risolte; toast nativi e riga impostazioni funzionanti senza errori reflection. |
| [ ] | Hide greeting header buttons | Applicata | Bloccata: adb unauthorized | ON/OFF: Crea e Notifiche dell’header; feed e layout funzionanti. |
| [ ] | Hide Create nav button | Applicata | Bloccata: adb unauthorized | ON/OFF e riavvio: pulsante Crea nascosto/ripristinato; Home e Profilo raggiungibili. |
| [ ] | Hide Notifications nav button | Applicata | Bloccata: adb unauthorized | ON/OFF e riavvio: Notifiche nascoste/ripristinate; barra senza spazi residui. |
| [ ] | Hide Search nav button | Applicata | Bloccata: adb unauthorized | ON/OFF e riavvio: Ricerca nascosta/ripristinata; navigazione restante funzionante. |
| [ ] | Download pin from long press | Applicata | Bloccata: adb unauthorized | Pressione lunga su due pin diversi: icona corretta, download del pin scelto, file apribile. |
| [ ] | Copy direct link | Applicata | Bloccata: adb unauthorized | Due pin consecutivi: URL del pin giusto, appunti corretti, menu chiuso e toast visibile. |
| [ ] | Set pin as wallpaper | Applicata | Bloccata: adb unauthorized | Immagine corretta e selettore sfondo; annullamento sicuro. Applicazione effettiva manuale. |
| [ ] | Sanitize shared links | Applicata | Bloccata: adb unauthorized | Condivisione pin/bacheca/profilo: rimuove tracking preservando destinazione e testo. |
| [ ] | Sanitize copied links | Applicata | Bloccata: adb unauthorized | Copia URL lungo e pin.it: tracking rimosso, destinazione corretta; testo normale intatto. |
| [ ] | Neutralize advertising ID | Applicata | Bloccata: adb unauthorized | Ispezione bytecode patchato e prova runtime del valore ID; non basta assenza di pubblicità. |
| [ ] | Disable AppsFlyer tracking | Applicata | Bloccata: adb unauthorized | Ispezione hook init/start/isStopped e prova runtime/log o rete; avvio app sano. |
| [ ] | Disable Google Engage worker | Applicata | Bloccata: adb unauthorized | Ispezione worker patchato, esecuzione/risultato neutro e assenza crash. |
| [ ] | Disable Google Engage | Applicata | Bloccata: adb unauthorized | Manifest patchato senza componenti Engage; avvio, receiver e job residui. |
| [ ] | Disable third-party trackers | Applicata | Bloccata: adb unauthorized | Manifest e impostazioni SDK effettive; prova rete separata dall’assenza di crash. |
| [ ] | Hide screenshot share menu | Applicata | Bloccata: adb unauthorized | Screenshot nel closeup: niente upsell con ON; comportamento nativo con OFF. |
| [ ] | Hide search history | Applicata | Bloccata: adb unauthorized | Ricerche recenti assenti con toggle ON, presenti con OFF; suggerimenti e ricerca funzionano. |
| [ ] | Use the system share sheet | Applicata | Bloccata: adb unauthorized | Pin/bacheca/profilo: foglio Android, URL corretto, annullamento e toggle OFF. |
| [ ] | Download video | Applicata | Bloccata: adb unauthorized | Pin video: URL e download valido; verificare anche yt-dlp e assenza voce su immagine. |

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

## Richieste da valutare dopo il porting

| Richiesta | Implementazione | Verifica |
|---|---|---|
| #41 — tap impostazioni Morphe | Indagine da completare | Non verificata |
| #42 — dimensioni e formato immagine | Da valutare | Non verificata |
| #44 — download qualità massima | Da valutare, confrontare anche download già esistenti | Non verificata |
| #45 — filtro voci menu ⋮ | Da valutare | Non verificata |

## Release

- [ ] Tutte le prove applicabili superate e manuali confermate: release completa.
- [ ] Build e smoke test superati, verifiche manuali residue elencate: candidata pre-release dev.

Build candidata pronta; nessuna release pubblicata perché manca anche lo smoke test sul dispositivo. Le nuove feature sono subordinate alla verifica del porting. Nessun commento pubblicato sulle issue.

## Evidenze della build candidata

- Originale SHA-256: `4ecc7f9a34c89fd98d0e2133c294517772857b935969847bfd92b3e25b13e78d`.
- APK patchato SHA-256: `f07ed214f0f43352da2c8cb1f2b64a7160cb0009652909d12c3627de5041addf`.
- Bundle SHA-256: `4145f01d526bd5b5129e08d513c89f29ac63cc65ed3bb7c3180485dbade5821d`.

La compilazione finale è registrata nel workspace in `pinterest/build_14.39.0_final.log`;
il primo report di applicazione in `pinterest/build/verification_14.39.0/patch_result.json`
(28 patch, nessuna fallita), la controprova in `patch_result_14.34.0.json`.
Il report JSON iniziale precede solo l’aggiornamento dei metadata di compatibilità;
il log finale conferma le stesse 28 applicazioni senza force. Hash sopra della build finale.
Firma, manifest e dump dell’iniezione sono nella stessa cartella locale di verifica.

Indagini sulle issue: #42 ha dati width/height/type/url nel modello immagine;
#44 i download Morphe scelgono già la maggiore width, quello nativo resta da studiare;
#45 fattibile nel builder del menu con filtro di sole righe native. #41 non è ancora
riprodotta: presenza della voce e intent-filter non verificano il tap.
