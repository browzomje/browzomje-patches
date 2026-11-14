package app.browzomje.extension.pinterest;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.WeakHashMap;

public final class AccountMenuIcons {
    private static final String[][] ICONS = {
        {"settings_menu_teen_safety_resources", "cog"},
        {"settings_main_account_management", "person"},
        {"settings_main_profile_visibility", "eye"},
        {"settings_refine_your_recommendations", "filter_horizontal"},
        {"settings_main_header_link_to_pinterest", "link"},
        {"settings_main_social_permissions", "lock"},
        {"settings_main_content_permissions", "shield_check"},
        {"settings_menu_notifications", "bell"},
        {"settings_main_privacy_data", "shield"},
        {"settings_main_reports_and_violations_center", "flag"},
        {"settings_main_labs", "lab_flask"},
        {"setting_screen_switch_account", "arrows_clockwise"},
        {"setting_screen_add_account", "person_plus"},
        {"settings_main_security", "lock"},
        {"settings_menu_log_out", "logout"},
        {"settings_menu_help_center", "circle_question_mark"},
        {"settings_menu_terms_of_service", "clipboard"},
        {"settings_menu_privacy_policy", "lock"},
        {"about", "circle_information"},
    };
    private static final WeakHashMap<TextView, Drawable> APPLIED = new WeakHashMap<>();

    private AccountMenuIcons() {}

    public static void schedule(View row) {
        row.post(() -> bind(row));
    }

    private static void bind(View row) {
        if (row instanceof TextView) {
            apply((TextView) row);
        } else if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int index = 0; index < group.getChildCount(); index++) bind(group.getChildAt(index));
        }
    }

    private static void apply(TextView label) {
        Drawable[] current = label.getCompoundDrawablesRelative();
        Drawable applied = APPLIED.remove(label);
        if (applied != null && current[0] == applied) {
            label.setCompoundDrawablesRelative(null, current[1], current[2], current[3]);
        } else if (current[0] != null) {
            return;
        }

        Context context = label.getContext();
        Resources resources = context.getResources();
        String packageName = context.getPackageName();
        for (String[] entry : ICONS) {
            int title = resources.getIdentifier(entry[0], "string", packageName);
            if (title == 0 || !android.text.TextUtils.equals(label.getText(), resources.getText(title))) continue;
            int iconId = resources.getIdentifier("ic_vr_" + entry[1] + "_gestalt", "drawable", packageName);
            if (iconId == 0) return;
            Drawable icon = context.getDrawable(iconId).mutate();
            int size = Math.round(18 * resources.getDisplayMetrics().density);
            icon.setBounds(0, 0, size, size);
            icon.setTint(label.getCurrentTextColor());
            label.setCompoundDrawablePadding(Math.round(10 * resources.getDisplayMetrics().density));
            label.setCompoundDrawablesRelative(icon, current[1], current[2], current[3]);
            APPLIED.put(label, icon);
            return;
        }
    }
}
