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
- Nessuna prova funzionale: il telefono non ha ancora autorizzato adb.
- Nessuna release stabile/pre-release pubblicata, nessun commento alle issue.

La [checklist completa](VERIFICATION_14.39.0.md) distingue applicazione e runtime,
include tutte le patch già presenti, subopzioni e regressioni. #41 resta da
riprodurre. #42, #44 e #45 sono state valutate, ma le feature nuove restano
subordinate al collaudo del porting richiesto dall’utente.
