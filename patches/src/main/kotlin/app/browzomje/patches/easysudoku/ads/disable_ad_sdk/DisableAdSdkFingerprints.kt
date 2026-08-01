package app.browzomje.patches.easysudoku.ads.disable_ad_sdk

import app.morphe.patcher.Fingerprint

/**
 * `AdModule.a(KitConfig)` — l'inizializzazione vera del modulo pubblicitario:
 *
 * ```java
 * private static void a(ya.b bVar) {
 *     …
 *     hc.f.g(new hc.b.a((Application) ctx.getApplicationContext())
 *             .e(bVar.o()).d("config/ad_config.json")…, new a(aVarM));
 * }
 * ```
 *
 * `hc.f.g(...)` (MeeviiAd.init) è ciò che accende AppLovin MAX e tutta la sua mediazione
 * (Pangle, Amazon APS, Chartboost, InMobi, Mintegral, Fyber, Bigo, …).
 *
 * Il package `xa` e il nome `a` sono offuscati e cambiano a ogni release: l'ancora vera è il
 * nome del file di configurazione, `config/ad_config.json`, che nell'APK compare **solo** qui.
 *
 * ```bash
 * rg -rn '"config/ad_config.json"' -g '*.java' sources/     # -> xa/b.java, un solo risultato
 * rg -n 'AdModule' sources/xa/b.java                        # conferma: è la classe AdModule
 * ```
 *
 * Si aggancia deliberatamente `a(...)` e non il suo chiamante `d(...)`: `d` avvolge tutto in un
 * try/catch, e stubbandolo si lascerebbe nel dex un blocco `try` mai raggiungibile. Stubbando
 * `a` il flusso di `d` resta intatto — segna il modulo come inizializzato e non solleva nulla —
 * mentre nessuna SDK parte davvero.
 */
object AdModuleInitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf("config/ad_config.json"),
)
