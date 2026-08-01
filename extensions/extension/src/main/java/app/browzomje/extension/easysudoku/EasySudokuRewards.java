package app.browzomje.extension.easysudoku;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * Concede subito la ricompensa dei "rewarded ad" senza mostrare nessun video.
 *
 * <h2>Perché serve</h2>
 *
 * La patch "Remove ads" spegne la pubblicità facendo credere all'app di aver già comprato la
 * rimozione ads. Quel flag però passa anche davanti ai <i>rewarded</i> ("guarda un video per un
 * suggerimento"): {@code AdUtil.V(...)} esce subito con {@code false}, il chiamante lo legge
 * come "nessun video disponibile" e la ricompensa non arriva mai. Senza questa patch, togliere
 * gli annunci significherebbe perdere hint, ripristini della serie, ecc.
 *
 * <h2>Come</h2>
 *
 * L'hook bytecode sostituisce il corpo di {@code AdUtil.V(...)} con una chiamata a
 * {@link #grantRewardInstantly(String, Object)} seguita da {@code return true}. Qui dentro
 * rieseguiamo la stessa sequenza di callback che l'app riceverebbe da un video guardato per
 * intero, sul listener che ci viene passato (l'{@code IADListener} dell'app):
 *
 * <ol>
 *   <li>{@code d(placement)} — onAdShow
 *   <li>{@code f(placement)} — onRewarded (è qui che il gioco segna "ricompensa maturata")
 *   <li>{@code b(placement)} — onAdClose (chiude il dialogo e fa scattare la consegna)
 * </ol>
 *
 * Non è una sequenza inventata: è esattamente ciò che fa il fallback già presente nell'app
 * ({@code sk.u3$b.onFinish()}, il countdown di 6 secondi mostrato quando nessun video è
 * disponibile), che chiama nell'ordine {@code B()}, {@code onAdClose()} e poi consegna il
 * premio. Riproducendola, il gioco resta in uno stato coerente.
 *
 * <h2>Perché reflection</h2>
 *
 * Il tipo del listener ({@code vc.k}) è una classe dell'APK, non esiste a compile time in
 * questa extension. Il metodo accetta quindi {@code Object} — che è anche ciò che serve per non
 * far arrabbiare il verifier di Dalvik, visto che il registro sorgente ha tipo offuscato — e il
 * dispatch avviene per nome a runtime.
 *
 * <h2>Perché sul main thread e in differita</h2>
 *
 * Le callback toccano dialoghi e view: devono girare sul thread UI. In più vengono rimandate di
 * {@value #CALLBACK_DELAY_MS} ms, così il metodo agganciato fa in tempo a restituire
 * {@code true} al chiamante prima che arrivi "l'annuncio è finito": invocarle in modo sincrono
 * significherebbe consegnare la ricompensa <i>prima</i> che il gioco abbia finito di registrare
 * che un annuncio è partito.
 */
public final class EasySudokuRewards {

    /**
     * Nomi dei metodi di callback su {@code vc.k} (IADListener), nell'ordine in cui vanno
     * invocati.
     *
     * <p>⚠️ Sono <b>offuscati</b>: validi per la versione a cui le patch sono pinnate
     * (5.70.0). Il modo per rimapparli è in
     * {@code patches/.../easysudoku/ads/instant_rewards/RECAP.md}. Se in una versione futura
     * cambiassero, l'unico sintomo sarebbe che il premio non arriva: per questo un nome non
     * trovato viene loggato come warning esplicito invece di essere ignorato.
     */
    private static final String[] CALLBACK_SEQUENCE = { "d", "f", "b" };

    private static final long CALLBACK_DELAY_MS = 120L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EasySudokuRewards() {}

    /**
     * @param placement id del placement ("hint", "mainPage", …). Viene solo rigirato alle
     *     callback, che lo usano per l'analytics interna: passarlo invariato tiene i contatori
     *     dell'app coerenti.
     * @param listener l'{@code IADListener} dell'app. Può essere {@code null}: l'app chiama
     *     {@code V(...)} anche da un percorso di ricarica in cui non c'è nessuno da avvisare.
     */
    public static void grantRewardInstantly(final String placement, final Object listener) {
        if (listener == null) {
            // Non è un errore: è il percorso di ricarica (sk.u3$a.c()), che non attende premi.
            EasySudokuLog.i(EasySudokuLog.REWARDS,
                    "no listener for placement '" + placement + "': nothing to grant");
            return;
        }

        EasySudokuLog.i(EasySudokuLog.REWARDS,
                "granting '" + placement + "' via " + listener.getClass().getName());

        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                fireCallbacks(placement, listener);
            }
        }, CALLBACK_DELAY_MS);
    }

    private static void fireCallbacks(String placement, Object listener) {
        for (String name : CALLBACK_SEQUENCE) {
            Method method;
            try {
                method = listener.getClass().getMethod(name, String.class);
            } catch (NoSuchMethodException e) {
                // Il listener non ha quella callback: con i nomi giusti non capita, quindi è il
                // segnale che l'offuscamento è cambiato. Si prosegue con le altre: una sequenza
                // parziale è comunque meglio di nessuna ricompensa.
                EasySudokuLog.w(EasySudokuLog.REWARDS,
                        "callback '" + name + "(String)' not found on "
                                + listener.getClass().getName()
                                + " — obfuscated names changed? see instant_rewards/RECAP.md");
                continue;
            }

            try {
                // Il listener concreto è spesso una classe anonima package-private: senza questo
                // l'invoke fallirebbe con IllegalAccessException anche se il metodo è public.
                method.setAccessible(true);
                method.invoke(listener, placement);
            } catch (Throwable t) {
                EasySudokuLog.e(EasySudokuLog.REWARDS,
                        "callback '" + name + "' failed for placement '" + placement + "'", t);
            }
        }
    }
}
