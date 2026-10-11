package app.browzomje.extension.pinterest;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bind to the module's own Pin. Recycled modules update the existing label. */
public final class PinImageDetails {
    private static final java.util.Map<LinearLayout, Object> BOUND = new java.util.WeakHashMap<>();
    private static final String TAG = "morphe_inline_image_info";
    private PinImageDetails() {}

    public static void bind(Object module, Object pin) {
        NativeImageDownload.remember(pin);
        if (!(module instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) module;
        BOUND.put(root, pin);
        PinDownloadQueue.resume(root.getContext());
        root.post(() -> {
            try { if (BOUND.get(root) == pin) render(root, pin); }
            catch (Throwable failure) { MorpheLog.w(MorpheLog.REFLECTION, "inline image information unavailable", failure); }
        });
    }

    static void refreshDownloadInfo(String source,PinImageResolver.Result result) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for(java.util.Map.Entry<LinearLayout,Object> entry:BOUND.entrySet()) {
                Object image=PinImageMenu.bestImage(entry.getValue());
                if(!source.equals(AdDetector.fieldValueBySerializedName(image,"url"))) continue;
                TextView label=entry.getKey().findViewWithTag(TAG);
                if(label!=null) label.setText(MorpheSettingsStore.effective(MorpheSettingsStore.KEY_INFO_REPLACE)
                        ?result.describe().replace(" · ","\n"):result.describe());
            }
        });
    }

    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private static View slot(View view, ViewGroup bar) {
        if (view == null) return null;
        while (view.getParent() instanceof View && view.getParent() != bar) view = (View) view.getParent();
        return view.getParent() == bar ? view : null;
    }

    private static void render(LinearLayout root, Object pin) {
        // Only the closeup action module owns both controls; never alter image/comment modules.
        View react = find(root, "react_icon");
        if (react == null) react = find(root, "action_module_react_icon_sab");
        View comment = find(root, "action_module_comments_icon");
        if (comment == null) comment = find(root, "action_module_comments_wrapper");
        if (react == null || comment == null) return;
        ViewGroup bar = null;
        View ancestor = comment;
        while (ancestor.getParent() instanceof ViewGroup && ancestor.getParent() != root) {
            ViewGroup candidate = (ViewGroup) ancestor.getParent();
            if (slot(react, candidate) != null) { bar = candidate; break; }
            ancestor = candidate;
        }
        if (bar == null) return;
        boolean replace = MorpheSettingsStore.effective(MorpheSettingsStore.KEY_INFO_REPLACE);
        Object image = PinImageMenu.bestImage(pin);
        String details = image == null ? "" : MorpheStrings.get(MorpheStrings.IMAGE_INFO_PENDING);
        boolean show = (replace || MorpheSettingsStore.effective(MorpheSettingsStore.KEY_IMAGE_INFO)) && image != null;
        TextView text = root.findViewWithTag(TAG);
        if (show) {
            if (text == null) {
                text = new TextView(root.getContext());
                text.setTag(TAG);
                TextView reference = PinterestUtils.findTextView(bar);
                if (reference != null) {
                    text.setTypeface(reference.getTypeface());
                    text.setTextSize(TypedValue.COMPLEX_UNIT_PX, reference.getTextSize());
                    text.setTextColor(reference.getTextColors());
                    text.setLetterSpacing(reference.getLetterSpacing());
                    text.setPaintFlags(reference.getPaintFlags());
                    if (android.os.Build.VERSION.SDK_INT >= 26) text.setFontVariationSettings(reference.getFontVariationSettings());
                }
                int spacing = Math.round(8 * root.getResources().getDisplayMetrics().density);
                text.setPadding(spacing, spacing, spacing, spacing);
            }
            ViewGroup destination = replace && bar instanceof LinearLayout ? bar : root;
            if (text.getParent() != destination) {
                if (text.getParent() instanceof ViewGroup) ((ViewGroup) text.getParent()).removeView(text);
                if (destination == root) root.setOrientation(LinearLayout.VERTICAL);
                destination.addView(text, 0, new LinearLayout.LayoutParams(
                        destination == root ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            text.setText(replace && destination == bar ? details.replace(" · ", "\n") : details);
            text.setVisibility(View.VISIBLE);
            Object source = AdDetector.fieldValueBySerializedName(image, "url");
            Object width = AdDetector.fieldValueBySerializedName(image, "width");
            Object height = AdDetector.fieldValueBySerializedName(image, "height");
            final TextView label = text;
            final boolean twoLines = replace && destination == bar;
            if (source instanceof String) PinImageResolver.async(root.getContext(), (String) source,
                    width instanceof Number ? ((Number) width).intValue() : 0,
                    height instanceof Number ? ((Number) height).intValue() : 0,
                    android.os.Build.VERSION.SDK_INT >= 29 && MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIGH_QUALITY_IMAGES), result -> {
                        if (BOUND.get(root) != pin || label.getParent() == null) return;
                        String verified = result == null ? MorpheStrings.get(MorpheStrings.IMAGE_INFO_UNAVAILABLE) : result.describe();
                        label.setText(twoLines ? verified.replace(" · ", "\n") : verified);
                    });
            MorpheLog.d(MorpheLog.REFLECTION, "inline image info: " + details);
        } else if (text != null) text.setVisibility(View.GONE);
        // Do not remove actions if replacement data is unavailable.
        if (MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_LIKE) || (replace && show)) {
            View action = slot(react, bar);
            if (action != null) MorpheViews.hidePersistently(action, MorpheLog.REFLECTION, "like button");
        }
        if (MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_COMMENT_BUTTON) || (replace && show)) {
            View action = slot(comment, bar);
            if (action != null) MorpheViews.hidePersistently(action, MorpheLog.REFLECTION, "comment button");
        }
    }
}
