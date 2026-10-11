package app.browzomje.extension.pinterest;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/** Persist resource names, resolve labels from Pinterest for the current locale. */
final class PinMenuFilter {
    private static final Set<View> CUSTOM = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private PinMenuFilter() {}

    static void markCustom(View row) { if (row != null) CUSTOM.add(row); }
    static boolean isCustom(View row) { return CUSTOM.contains(row); }

    static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static final String[] ACTIONS = {
        "pin_overflow_follow_user", "pin_overflow_unfollow_user", "morphe_wallpaper", "morphe_copy_link",
        "save_pin", "overflow_menu_share", "download_image",
        "overflow_menu_add_to_collage", "grid_actions_report_pin", "copy_link",
        "overflow_menu_see_more", "overflow_menu_see_less", "overflow_menu_see_more_ai",
        "overflow_menu_see_less_ai", "hide_pin_option", "overflow_menu_edit",
        "pin_overflow_visit_site", "overflow_menu_pin_boost", "overflow_menu_remix_collage",
        "contextmenu_visual_search_image", "contextmenu_comment", "contextmenu_react",
        "contextmenu_add_note", "contextmenu_edit_note"
    };
    static Set<String> recommended() {
        return new HashSet<>(java.util.Arrays.asList(
            "grid_actions_report_pin", "overflow_menu_add_to_collage", "pin_overflow_follow_user",
            "pin_overflow_unfollow_user", "overflow_menu_pin_boost", "overflow_menu_remix_collage",
            "overflow_menu_see_more", "overflow_menu_see_less", "overflow_menu_see_more_ai", "overflow_menu_see_less_ai"));
    }
    private static final String PREFIX = "@resource:";

    static String label(Context context, String action) {
        if (action.equals("morphe_wallpaper")) return PinterestUtils.getString("label");
        if (action.equals("morphe_copy_link")) return PinterestUtils.getString("copy_link_label");
        if (action.equals("pin_overflow_follow_user")) action = "follow";
        if (action.equals("pin_overflow_unfollow_user")) action = "unfollow";
        return nativeLabel(context, action);
    }

    private static String nativeLabel(Context context, String action) {
        int id = context.getResources().getIdentifier(action, "string", context.getPackageName());
        return id == 0 ? null : context.getString(id);
    }

    static Set<String> selected(Context context) {
        Set<String> selected = new HashSet<>();
        for (String line : MorpheSettingsStore.menuFilter().split("\\r?\\n")) {
            if (line.startsWith(PREFIX)) {
                String key = line.substring(PREFIX.length());
                if (key.equals("save_to_device")) key = "download_image";
                for (String action : ACTIONS) if (action.equals(key)) selected.add(action);
            } else if (!line.trim().isEmpty()) {
                // Migrate the previous text-list prototype, including after a language change.
                for (String lang : new String[] { "en", "it", "es", "fr", "de", "pt", "ru",
                        "ja", "zh", "ko", "pl", "nl", "tr", "ar" }) {
                    Configuration config = new Configuration(context.getResources().getConfiguration());
                    config.setLocale(Locale.forLanguageTag(lang));
                    Context localized = context.createConfigurationContext(config);
                    for (String action : ACTIONS) {
                        String text = label(localized, action);
                        if (text != null && normalize(text).equals(normalize(line))) selected.add(action);
                    }
                }
            }
        }
        return selected;
    }

    static String encode(Set<String> selected) {
        StringBuilder result = new StringBuilder();
        for (String action : ACTIONS) if (selected.contains(action)) {
            if (result.length() > 0) result.append('\n');
            result.append(PREFIX).append(action);
        }
        return result.toString();
    }

    static void apply(Object menu) {
        if (!(menu instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) menu;
        Set<String> hidden = new HashSet<>();
        java.util.List<java.util.regex.Pattern> templates = new java.util.ArrayList<>();
        for (String action : selected(group.getContext())) {
            String text = label(group.getContext(), action);
            if (text != null) hidden.add(normalize(text));
            if (action.equals("pin_overflow_follow_user") || action.equals("pin_overflow_unfollow_user")) {
                String template = nativeLabel(group.getContext(), action);
                if (template != null && template.contains("%1$s")) {
                    String[] parts = normalize(template).split("%1\\$s", -1);
                    if (parts.length == 2) templates.add(java.util.regex.Pattern.compile(
                            java.util.regex.Pattern.quote(parts[0]) + ".+" + java.util.regex.Pattern.quote(parts[1])));
                }
            }
            if (action.equals("download_image")) {
                String alias = label(group.getContext(), "save_to_device");
                if (alias != null) hidden.add(normalize(alias));
            }
        }
        if (!hidden.isEmpty()) group.post(() -> hideActions(group, hidden, templates));
    }

    private static boolean matches(String text, Set<String> hidden, java.util.List<java.util.regex.Pattern> templates) {
        String normalized = normalize(text);
        if (hidden.contains(normalized)) return true;
        for (java.util.regex.Pattern template : templates) if (template.matcher(normalized).matches()) return true;
        return false;
    }

    private static void hideActions(ViewGroup group, Set<String> hidden, java.util.List<java.util.regex.Pattern> templates) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View row = group.getChildAt(i);
            if (isCustom(row)) {
                TextView customText = PinterestUtils.findTextView(row);
                if (customText != null && hidden.contains(normalize(customText.getText().toString()))) row.setVisibility(View.GONE);
                continue;
            }
            TextView text = PinterestUtils.findTextView(row);
            if (row.isClickable() && text != null
                    && matches(text.getText().toString(), hidden, templates)) {
                row.setVisibility(View.GONE);
                MorpheLog.i(MorpheLog.REFLECTION, "native menu action hidden: " + text.getText());
            } else if (row instanceof ViewGroup) {
                hideActions((ViewGroup) row, hidden, templates);
            }
        }
    }
}
