package app.template.patches.pinterest.video

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Hook used to capture the currently-playing video URL.
 *
 *     public final void u(cu2.l metadata, fu2.g gVar, Function0 onFailure)   // PinterestVideoView
 *
 * `metadata` (cu2.l, the "VideoMetadata") carries the raw track URL in its String field `g`
 * (= videoTracks.f73567b.url). We pass the whole metadata object to the extension, which scans
 * its String fields for an http(s) video URL — robust against field renaming across versions.
 */
object PinterestVideoViewPlayFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcu2/l;", "Lfu2/g;", "Lkotlin/jvm/functions/Function0;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/video/core/view/PinterestVideoView;" && method.name == "u"
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
