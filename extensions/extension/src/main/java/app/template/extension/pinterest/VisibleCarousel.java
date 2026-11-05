package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/** Reads the selected page from the carousel on the downloading Pin's screen. */
final class VisibleCarousel {
    private VisibleCarousel() {}

    static String selectedImage(Context context, String pinId) {
        Activity activity = PinterestUtils.activityOf(context);
        if (activity == null) return null;
        int carouselId = activity.getResources().getIdentifier(
                "carouselView", "id", activity.getPackageName());
        if (carouselId == 0) return null;
        View root = activity.getWindow().getDecorView();
        Selection selection = new Selection();
        scan(root, carouselId, pinId, selection);
        return selection.ambiguous ? null : selection.url;
    }

    private static void scan(View view, int carouselId, String pinId, Selection selection) {
        if (!view.isShown() || selection.ambiguous) return;
        if (view.getId() == carouselId && view.getGlobalVisibleRect(new Rect())) {
            String url = readImage(view, pinId);
            if (url != null) {
                selection.ambiguous = selection.url != null && !selection.url.equals(url);
                selection.url = url;
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scan(group.getChildAt(i), carouselId, pinId, selection);
            }
        }
    }

    private static String readImage(View carousel, String pinId) {
        for (View view = carousel; view != null;
                view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            try {
                Class<?> type = view.getClass();
                java.lang.reflect.Method image = type.getMethod("getCurrentImageUrl");
                Object pin = type.getMethod("getPin").invoke(view);
                if (!pinId.equals(AdDetector.fieldValueBySerializedName(pin, "id"))) return null;
                Object url = image.invoke(view);
                return url instanceof String && ((String) url).startsWith("https://i.pinimg.com/")
                        ? (String) url : null;
            } catch (NoSuchMethodException ignored) {
                // Walk up from the carousel widget to the native closeup module.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class Selection {
        String url;
        boolean ambiguous;
    }
}
