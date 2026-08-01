package app.browzomje.patches.easysudoku.ads.instant_rewards

import app.morphe.patcher.patch.bytecodePatch
import app.browzomje.patches.shared.Constants.COMPATIBILITY_EASY_SUDOKU
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn
import app.browzomje.patches.shared.inputRegisterCount

private const val PATCH_NAME = "Instant rewards"
private const val EXTENSION_CLASS = "Lapp/browzomje/extension/easysudoku/EasySudokuRewards;"

@Suppress("unused")
val instantRewardsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Grants the reward of every \"watch an ad\" button — hints, streak repairs, " +
        "extra puzzles — immediately, without playing a video. This is what keeps those buttons " +
        "working once \"Remove ads\" is enabled: without it the game believes no video is " +
        "available and hands out nothing.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_EASY_SUDOKU)
    extendWith("extensions/extension.mpe")

    execute {
        val method = RewardedAdShowFingerprint.method

        // Registri di ingresso: il metodo è statico con 5 parametri a registro singolo, quindi
        // p0 è il primo dei 5 registri più alti del frame.
        //   p0 = context, p1 = placement, p2 = from, p3 = scene, p4 = listener
        val p0 = method.implementation!!.registerCount - method.inputRegisterCount()
        val placementRegister = p0 + 1
        val listenerRegister = p0 + 4

        // placement e listener non sono contigui, quindi non si può usare invoke-static/range.
        // Li si copia in v0/v1 — bassi, quindi indirizzabili dalla forma a 4 bit di invoke-static —
        // con move-object/from16, che regge un sorgente oltre v15 (i parametri di un metodo così
        // grande ci finiscono facilmente). Siamo in testa al metodo: nessun locale è ancora vivo.
        method.forceReturn(
            """
            move-object/from16 v0, v$placementRegister
            move-object/from16 v1, v$listenerRegister
            invoke-static { v0, v1 }, $EXTENSION_CLASS->grantRewardInstantly(Ljava/lang/String;Ljava/lang/Object;)V
            const/4 v0, 0x1
            return v0
            """,
            registersUsed = 2,
        )

        // `true` = "l'annuncio è partito": è la risposta che il chiamante (sk.u3.C(),
        // RewardAdHelper) si aspetta per non mostrare il dialogo "nessun video disponibile".
        // La ricompensa vera la consegna l'extension, rieseguendo sul listener la stessa
        // sequenza di callback di un video guardato per intero.
        PatchLog.hooked(PATCH_NAME, method, "rewarded ad short-circuited, reward granted by extension")
    }
}
