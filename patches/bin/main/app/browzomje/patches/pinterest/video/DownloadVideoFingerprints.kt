package app.browzomje.patches.pinterest.video

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/** Il modello Pin, per versione. Vedi pinterest/OBFUSCATION_MAP.md. */
private val PIN_MODEL_TYPES = setOf(
    "Lcom/pinterest/api/model/me;", // 14.23.0
    "Lcom/pinterest/api/model/ve;", // 14.28.0
)

/**
 * Il metodo statico che, dato un Pin, ne estrae le tracce video. Lo agganciamo per sapere
 * qual è il pin video correntemente aperto.
 *
 * Cambia sia classe sia nome sia numero di parametri a ogni versione — 14.23.0:
 * `com.bumptech.glide.d.w(Pin, Integer, n)`, 14.28.0:
 * `mj.d.S(Pin, Integer)` — quindi non si cerca nulla di tutto ciò: si cerca l'unico metodo
 * statico che prende un Pin seguito da un `Integer` e restituisce un oggetto.
 */
object VideoTracksBuilderFingerprint : Fingerprint(
    // NB: il parametro `accessFlags` del Fingerprint confronta i flag in modo ESATTO, quindi
    // non si può usare per dire "basta che sia static" — questi metodi sono `public static
    // final`. Il controllo va fatto qui dentro.
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameters.size in 2..3 &&
            method.parameters[0].type in PIN_MODEL_TYPES &&
            method.parameters[1].type == "Ljava/lang/Integer;" &&
            method.returnType.startsWith("L")
    }
)

/** Vedi `CopyDirectLinkFingerprints.OverflowMenuBuilderFingerprint`: stesso target. */
object VideoOverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        method.name == "<init>" && when (classDef.type) {
            "Luz0/z;" -> method.parameters.size == 28
            "Luz0/b0;" -> method.parameters.size == 27
            else -> false
        }
    }
)

/**
 * Il metodo statico che costruisce l'oggetto "tracce video" a partire dalla mappa dei formati:
 * `(String uid, Map formati, boolean, boolean, Integer, …)`. Da qui catturiamo la mappa con i
 * vari MP4/HLS, che è ciò che serve per scaricare.
 *
 * 14.23.0: `com.bumptech.glide.d.p` — 14.28.0: `mj.d.P`. Anche qui il
 * riconoscimento è per firma: statico, 8 parametri, i primi due `String` e `Map`.
 */
object VideoTracksBuilderFingerprint2 : Fingerprint(
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameters.size == 8 &&
            method.parameters[0].type == "Ljava/lang/String;" &&
            method.parameters[1].type == "Ljava/util/Map;"
    }
)
