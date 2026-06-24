package app.template.patches.pinterest

import app.morphe.patcher.Fingerprint

/**
 * Constructor of the feed-page model `o12.e` (implements `o12.d`):
 *
 *     public e(String baseUrl, String bookmark, String s3, List items) {
 *         this.f109367a = items != null ? new ArrayList(items) : new ArrayList();
 *         ...
 *     }
 *
 * Every home-feed page — first load, pagination AND pull-to-refresh — flows through this
 * constructor, and it stores a FRESH mutable ArrayList copy of the items. That makes it the
 * ideal, single choke point to actually REMOVE sponsored pins from the feed (not just unlabel
 * them): we hand `this` to the extension, which strips promoted pins from the stored list.
 *
 * Why not just force `me.I5()` (is_promoted) to false? Because that only removes the "Sponsored"
 * label and, worse, defeats Pinterest's own promoted-pin filters (e.g. s0.W3) — the ad content
 * keeps showing. The pins have to be dropped from the list itself.
 */
object PinterestAdsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
    ),
    custom = { method, classDef ->
        classDef.type == "Lo12/e;" && method.name == "<init>"
    }
)
