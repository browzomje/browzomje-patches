package app.browzomje.patches.pinterest.video

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Il metodo statico che costruisce l'oggetto "tracce video" a partire dalla mappa dei formati:
 * `(String uid, Map formati, boolean, boolean, Integer, …)`. Da qui catturiamo la mappa con i
 * vari MP4/HLS, che è ciò che serve per scaricare.
 *
 * 14.23.0: `com.bumptech.glide.d.p` — 14.28.0: `mj.d.P` — 14.32.0: `l52.a.q`. Il riconoscimento
 * è puramente per firma: statico, 8 parametri, i primi due `String` e `Map`. È una forma così
 * particolare da essere **unica in tutto il dex** su entrambe le versioni verificate, il che la
 * rende anche un'ottima ancora per la classe (vedi sotto).
 */
object VideoTracksBuilderFingerprint2 : Fingerprint(
    // NB: il parametro `accessFlags` del Fingerprint confronta i flag in modo ESATTO, quindi
    // non si può usare per dire "basta che sia static" — questi metodi sono `public static
    // final`. Il controllo va fatto qui dentro.
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameters.size == 8 &&
            method.parameters[0].type == "Ljava/lang/String;" &&
            method.parameters[1].type == "Ljava/util/Map;"
    }
)

/**
 * Il metodo statico che, dato un Pin, ne estrae le tracce video. Lo agganciamo per sapere qual è
 * il pin video correntemente aperto.
 *
 * Cambia classe, nome e numero di parametri a ogni versione — 14.23.0
 * `com.bumptech.glide.d.w(Pin, Integer, n)`, 14.28.0 `mj.d.S(Pin, Integer)`, 14.32.0
 * `l52.a.B(Pin, Integer)` — e prima elencava a mano i nomi del **modello Pin**
 * (`…model.me`, `…model.ve`), che su 14.32.0 è diventato `…model.we`: la build si fermava qui.
 *
 * Non si nomina più nessuno dei due. Si sfrutta il fatto che questo metodo vive nella stessa
 * classe del builder a 8 parametri qui sopra — vero su 14.23, 14.28 e 14.32 — e la si usa come
 * ambito di ricerca. Ristretto a quella classe, "statico, primo parametro un modello dell'API,
 * secondo un Integer" basta a individuarlo: senza quel vincolo di classe i candidati sarebbero
 * quattro o cinque in tutto il dex.
 */
object VideoTracksBuilderFingerprint : Fingerprint(
    classFingerprint = VideoTracksBuilderFingerprint2,
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameters.size in 2..3 &&
            method.parameters[0].type.startsWith("Lcom/pinterest/api/model/") &&
            method.parameters[1].type == "Ljava/lang/Integer;" &&
            method.returnType.startsWith("L")
    }
)
