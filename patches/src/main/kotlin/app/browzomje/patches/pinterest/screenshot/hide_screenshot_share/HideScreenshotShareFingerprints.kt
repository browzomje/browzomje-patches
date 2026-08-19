package app.browzomje.patches.pinterest.screenshot.hide_screenshot_share

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Issue #32. Pinterest rileva gli screenshot in due modi (FileObserver sulla cartella immagini
// su Android < 14, `Activity.registerScreenCaptureCallback` da Android 14) e, quando ne vede uno,
// apre il pannello "Share screenshot".
//
// La feature esiste in DUE architetture che convivono, scelte da un experiment:
//
//  1. il fragment legacy del closeup, dove tutto converge in un unico metodo statico;
//  2. la nuova architettura "SBA" (stato + effetti + processore), dove gli effetti screenshot
//     passano da un processore dedicato.
//
// Agganciarne una sola darebbe il classico "a me funziona": qui si agganciano entrambe.
//
// Nessuno dei due fingerprint nomina una classe: i nomi offuscati vengono riciclati da R8 su
// classi diverse a ogni release (vedi pinterest/PATCHING_MEMORY.md). Si usano solo letterali,
// verificati presenti sia su 14.28.0 sia su 14.32.0.

/**
 * L'imbuto legacy: il metodo statico che riceve il Pin corrente dopo uno screenshot, registra
 * l'evento `PIN_SCREENSHOT` e apre il pannello di condivisione.
 *
 * 14.28.0: `et.n.Y9(et/n, ve)` — 14.32.0: `jt.l.Z9(jt/l, we)`. Classe, metodo e persino il
 * modello Pin cambiano nome a ogni versione; la stringa no.
 *
 * L'ancora è il letterale `"has_api"`, che il metodo mette nella mappa di analytics e che in
 * tutto il dex compare **in questo solo punto** — verificato su entrambe le versioni.
 *
 * Si vincola solo ciò che serve a distinguerlo (statico, `void`, due parametri): ogni vincolo in
 * più è una cosa in più che si rompe quando l'app aggiunge un argomento.
 */
object LegacyScreenshotFunnelFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("has_api"),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) && method.parameters.size == 2
    },
)

/**
 * Il processore degli effetti screenshot della nuova architettura.
 *
 * 14.28.0: `r81.b.h(f0, hr2.g, t)` — 14.32.0: `k91.c.g(f0, is2.g, y)`.
 *
 * Bloccarlo è più incisivo dell'aggancio legacy: da qui passano **tutti** gli effetti, compresi
 * `StartScreenshotObservation` e `StartScreenshotObserver`, quindi con l'opzione attiva la
 * rilevazione non parte nemmeno invece di partire e venire ignorata.
 *
 * L'ancora è il nome dell'experiment `"sg_android_closeup_screenshot_api_14"`, che il processore
 * legge per decidere se usare l'API di Android 14. Su 14.28.0 compare in una sola classe, su
 * 14.32.0 in tre: il vincolo su `void` + tre parametri lo riporta a uno solo.
 */
object SbaScreenshotProcessorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("sg_android_closeup_screenshot_api_14"),
    custom = { method, _ -> method.parameters.size == 3 },
)

/**
 * Il rilevatore di screenshot **generico**, quello che non appartiene al closeup.
 *
 * È il pezzo che mancava alla prima versione della patch: i due hook qui sopra coprono solo il
 * pin aperto, ma se lo screenshot lo si scatta sul feed o su una bacheca il pannello arriva da
 * qui, e in logcat non compariva **nessuna** riga `[screenshot]` — segno che l'hook non era
 * scattato affatto, non che avesse fallito.
 *
 * È una classe riutilizzabile, usata da diverse schermate, e ha un experiment tutto suo:
 * `"sg_android_new_screenshot_api_14"` — da non confondere con `…closeup_screenshot…` del
 * processore SBA. Il metodo agganciato è quello che **avvia** l'osservazione (registra il
 * `FileObserver` o la callback di Android 14): uscirne subito significa che la rilevazione non
 * parte, che è esattamente ciò che chiede l'issue.
 *
 * L'ancora compare in tre classi su entrambe le versioni: le altre due sono i contenitori degli
 * experiment, che espongono un `boolean D()` senza argomenti. Il vincolo di forma — `void`, due
 * parametri, il secondo una `FragmentActivity` — lascia solo il rilevatore.
 *
 * 14.28.0: `gl2.l.a(h, FragmentActivity)` — 14.32.0: `cm2.o.b(j, FragmentActivity)`.
 */
object GenericScreenshotObserverFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("sg_android_new_screenshot_api_14"),
    custom = { method, _ ->
        method.parameters.size == 2 &&
            method.parameters[1].type == "Landroidx/fragment/app/FragmentActivity;"
    },
)
