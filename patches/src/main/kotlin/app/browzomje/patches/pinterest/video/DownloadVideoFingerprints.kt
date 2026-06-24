package app.template.patches.pinterest.video

import app.morphe.patcher.Fingerprint

/**
 * The video-tracks builder, invoked with the Pin model whenever a video is set up for playback
 * (grid autoplay AND closeup):
 *
 *     public static final cu2.r w(me pin, Integer num, cu2.n videoSurfaceType)
 *
 * Crucially it receives the Pin (`me`), so the extension can read `me.v7().g()` (the `video_list`)
 * and pick a real progressive **.mp4** URL. The player metadata (cu2.l) only carries the HLS/DASH
 * streaming track (`.m3u8`) — downloading that yielded a few-KB, unplayable manifest. We pass the
 * Pin to the extension keyed by its uid (`me.getSnapshotUid()` == the menu's pinUid).
 */
object VideoTracksBuilderFingerprint : Fingerprint(
    returnType = "Lcu2/r;",
    parameters = listOf("Lcom/pinterest/api/model/me;", "Ljava/lang/Integer;", "Lcu2/n;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/bumptech/glide/d;" && method.name == "w"
    }
)

/**
 * Constructor of the pin overflow ("three dots") menu builder `uz0.z`. Same anchor the
 * "Copy direct link" patch uses; we inject a second call to add the "Download video" row.
 * Matched by its very specific 28-parameter constructor signature.
 */
object VideoOverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Luz0/z;" &&
            method.name == "<init>" &&
            method.parameters.size == 28
    }
)
