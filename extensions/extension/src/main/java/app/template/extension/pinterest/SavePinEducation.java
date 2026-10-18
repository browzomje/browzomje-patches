package app.browzomje.extension.pinterest;

import android.view.View;

/** Suppresses Save teaching bubbles unconditionally; never changes the Save action. */
public final class SavePinEducation {
    private SavePinEducation() {}

    /** The unused boolean keeps the caller's p0..p3 contiguous, including wide frames. */
    public static boolean suppressSaveAnchor(Object tooltip, Object anchor, boolean unused, Object anchorView) {
        boolean save = anchor instanceof Enum &&
                ("PIN_IT_BUTTON".equals(((Enum<?>) anchor).name()) ||
                 "IAB_PINIT_BUTTON".equals(((Enum<?>) anchor).name()));
        if (!save && anchorView instanceof View) {
            View view = (View) anchorView;
            try {
                save = "save_pinit_bt".equals(view.getResources().getResourceEntryName(view.getId()));
            } catch (android.content.res.Resources.NotFoundException ignored) {
                // No resource ID: only the explicit enum anchor can identify this target.
            }
        }
        if (!save) return false;
        if (tooltip instanceof View) ((View) tooltip).setVisibility(View.GONE);
        MorpheLog.i("save-education", "suppressed Save tooltip at placement");
        return true;
    }
}
