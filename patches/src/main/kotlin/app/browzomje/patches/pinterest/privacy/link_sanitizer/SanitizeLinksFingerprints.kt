package app.browzomje.patches.pinterest.privacy.link_sanitizer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * I due fingerprint di questo file sono un'eccezione felice rispetto al resto delle patch di
 * Pinterest: non nominano **niente** dell'app.
 *
 * <p>Si ancorano a due chiamate all'SDK Android — `Intent.putExtra` e
 * `ClipboardManager.setPrimaryClip` — i cui nomi sono garantiti stabili da Google su ogni versione
 * di Android e che R8 non può toccare, perché sono nel framework e non nell'APK. Non c'è quindi
 * niente da ri-agganciare quando esce una nuova versione di Pinterest: nemmeno un nome di classe
 * accorciato, nemmeno una stringa.
 *
 * <p>Il prezzo è che il fingerprint non identifica *un* metodo ma *tutti* quelli che fanno quella
 * chiamata: si usano quindi con `matchAll()`, e la patch cicla su ogni punto di chiamata. È il
 * motivo per cui la selezione di cosa ripulire e cosa no non sta qui ma a runtime, dentro
 * `UrlSanitizer`.
 */

/**
 * I metodi che scrivono il testo di condivisione dentro un `Intent`.
 *
 * <p>Due vincoli insieme, e servono entrambi:
 *
 * - **la stringa `android.intent.extra.TEXT`**, che restringe a chi ha davvero a che fare con il
 *   testo di condivisione. `Intent.EXTRA_TEXT` è una costante di compilazione, quindi javac la
 *   incorpora come letterale: un metodo che scrive quell'extra *deve* contenere quella stringa. Su
 *   14.32.0 la contengono otto classi, contro le 131 che chiamano `putExtra` in generale.
 * - **la chiamata a `putExtra(String, String)`**, che è il punto esatto dove iniettare.
 *
 * <p>Il primo da solo non basterebbe a trovare il punto di iniezione, il secondo da solo
 * aggancerebbe mezza app senza motivo.
 *
 * <p>Quale sia la chiave a ciascuna chiamata resta comunque indecidibile in fase di patch — nel
 * bytecode è un registro, non un letterale leggibile — quindi il controllo finale lo fa
 * `UrlSanitizer.sanitizeExtra` a runtime, restituendo il valore intatto per ogni chiave diversa da
 * `EXTRA_TEXT`.
 */
internal object PutExtraStringCallFingerprint : Fingerprint(
    strings = listOf("android.intent.extra.TEXT"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Intent;",
            name = "putExtra",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            returnType = "Landroid/content/Intent;",
        ),
    ),
)

/**
 * Ogni metodo che scrive negli appunti di sistema con
 * `ClipboardManager.setPrimaryClip(ClipData)`.
 *
 * <p>Copre in un colpo solo tutte le strade da cui Pinterest ci arriva: la voce "Copia link" del
 * menu del pin, la pressione lunga su un URL nei commenti, i tasti "copia invito", il "copia
 * ricetta". Su 14.32.0 sono undici punti di chiamata in sei classi diverse.
 */
internal object SetPrimaryClipCallFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/ClipboardManager;",
            name = "setPrimaryClip",
            parameters = listOf("Landroid/content/ClipData;"),
            returnType = "V",
        ),
    ),
)
