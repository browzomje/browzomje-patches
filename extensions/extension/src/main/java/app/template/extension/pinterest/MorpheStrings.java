package app.browzomje.extension.pinterest;

import android.content.Context;
import android.os.LocaleList;

import java.util.Arrays;
import java.util.Locale;

/** Localized extension strings using Pinterest's active resource configuration. */
final class MorpheStrings {

    static final String APP_VERSION_LABEL = "app_version_label";
    static final String PATCHES_VERSION_LABEL = "patches_version_label";

    // ------------------------------------------------------------------ chiavi

    static final String SECTION_CONTENT = "section_content";
    static final String SECTION_DOWNLOAD = "section_download";
    static final String SECTION_NAVBAR = "section_navbar";
    static final String SECTION_BACKUP = "section_backup";
    static final String SECTION_DIAGNOSTICS = "section_diagnostics";

    static final String ADS_DESCRIPTION = "ads_description";
    static final String SHOPPING_PINS_DESCRIPTION = "shopping_pins_description";
    static final String HIDE_AI_PINS_TITLE = "hide_ai_pins_title";
    static final String HIDE_AI_PINS_DESCRIPTION = "hide_ai_pins_description";
    static final String MUTED_PIN_WORDS_TITLE = "muted_pin_words_title";
    static final String MUTED_PIN_WORDS_HINT = "muted_pin_words_hint";
    static final String HIDE_VIDEO_PINS_TITLE = "hide_video_pins_title";
    static final String HIDE_VIDEO_PINS_DESCRIPTION = "hide_video_pins_description";
    static final String ADS_TITLE = "ads_title";
    static final String SHOPPING_PINS_TITLE = "shopping_pins_title";
    static final String SEARCH_HISTORY_TITLE = "search_history_title";
    static final String SEARCH_HISTORY_DESCRIPTION = "search_history_description";
    static final String EMAIL_DIALOG_TITLE = "email_dialog_title";
    static final String SEARCH_MODULES_TITLE = "search_modules_title";
    static final String SEARCH_MODULES_DESCRIPTION = "search_modules_description";
    static final String SCREENSHOT_TITLE = "screenshot_title";
    static final String SCREENSHOT_DESCRIPTION = "screenshot_description";
    static final String SANITIZE_LINKS_TITLE = "sanitize_links_title";
    static final String SANITIZE_LINKS_DESCRIPTION = "sanitize_links_description";
    static final String SHARE_LINK_ONLY_TITLE = "share_link_only_title";
    static final String SHARE_LINK_ONLY_DESCRIPTION = "share_link_only_description";
    static final String SYSTEM_SHARE_SHEET_TITLE = "system_share_sheet_title";
    static final String SYSTEM_SHARE_SHEET_DESCRIPTION = "system_share_sheet_description";
    static final String EXTERNAL_BROWSER_TITLE = "external_browser_title";
    static final String EXTERNAL_BROWSER_DESCRIPTION = "external_browser_description";
    static final String STARTUP_TAB_TITLE = "startup_tab_title";
    static final String STARTUP_TAB_DESCRIPTION = "startup_tab_description";
    static final String STARTUP_HOME = "startup_home";
    static final String STARTUP_PROFILE = "startup_profile";
    static final String STARTUP_SEARCH = "startup_search";
    static final String KEEP_HOME_FEED_TITLE = "keep_home_feed_title";
    static final String KEEP_HOME_FEED_DESCRIPTION = "keep_home_feed_description";
    static final String HIDE_COMMENT_BUTTON_DESCRIPTION = "hide_comment_button_description";
    static final String HIDE_COMMENTS_TITLE = "hide_comments_title";
    static final String HIDE_COMMENTS_DESCRIPTION = "hide_comments_description";

    static final String BOARD_DOWNLOAD_TITLE = "board_download_title";
    static final String BOARD_DOWNLOAD_DESCRIPTION = "board_download_description";
    static final String LONG_PRESS_DOWNLOAD_TITLE = "long_press_download_title";
    static final String LONG_PRESS_DOWNLOAD_DESCRIPTION = "long_press_download_description";

    static final String HIDE_SEARCH_TITLE = "hide_search_title";
    static final String HIDE_CREATE_TITLE = "hide_create_title";
    static final String HIDE_NOTIFICATIONS_TITLE = "hide_notifications_title";
    static final String NAVBAR_NOTE = "navbar_note";

    static final String VERBOSE_TITLE = "verbose_title";
    static final String VERBOSE_DESCRIPTION = "verbose_description";
    static final String DIAGNOSTICS_HINT = "diagnostics_hint";
    static final String DIAGNOSTICS_EMPTY = "diagnostics_empty";

    static final String BACKUP_HINT = "backup_hint";
    static final String BUTTON_EXPORT = "button_export";
    static final String BUTTON_IMPORT = "button_import";
    static final String TOAST_EXPORTED = "toast_exported";
    static final String TOAST_IMPORTED = "toast_imported";
    static final String TOAST_EXPORT_FAILED = "toast_export_failed";
    static final String TOAST_IMPORT_FAILED = "toast_import_failed";
    static final String TOAST_NO_FILE_PICKER = "toast_no_file_picker";

    static final String BUTTON_REFRESH = "button_refresh";
    static final String BUTTON_COPY = "button_copy";
    static final String SETTINGS_SEARCH = "settings_search";
    static final String SETTINGS_SEARCH_EMPTY = "settings_search_empty";
    static final String TOAST_RESTART = "toast_restart";
    static final String TOAST_COPIED = "toast_copied";
    static final String TOAST_COPY_FAILED = "toast_copy_failed";

    static final String SETTINGS_RECOMMENDED = "settings_recommended";
    static final String IMAGE_INFO_PENDING = "image_info_pending";
    static final String IMAGE_INFO_UNAVAILABLE = "image_info_unavailable";
    static final String IMAGE_INFO_TITLE = "image_info_title";
    static final String IMAGE_INFO_DESCRIPTION = "image_info_description";
    static final String HIGH_QUALITY_TITLE = "high_quality_title";
    static final String HIGH_QUALITY_DESCRIPTION = "high_quality_description";

    static final String MENU_FILTER_TITLE = "menu_filter_title";
    static final String MENU_FILTER_HINT = "menu_filter_hint";
    static final String MENU_FILTER_DESCRIPTION = "menu_filter_description";

    static final String SECTION_PIN_MENU = "section_pin_menu";
    static final String BUTTON_SAVE = "button_save";
    static final String BUTTON_CANCEL = "button_cancel";

    static final String MENU_HIDE_ALL = "menu_hide_all";

    static final String MENU_SHOW_ALL = "menu_show_all";

    static final String MENU_SUGGESTED = "menu_suggested";

    static final String HIDE_LIKE_TITLE = "hide_like_title";

    static final String HIDE_COMMENT_BUTTON_TITLE = "hide_comment_button_title";

    static final String INFO_REPLACE_TITLE = "info_replace_title";

    static final String INFO_REPLACE_DESCRIPTION = "info_replace_description";

    static final String IMAGE_DOWNLOAD_CHECKING = "image_download_checking";

    static final String IMAGE_DOWNLOAD_FALLBACK = "image_download_fallback";

    static final String IMAGE_DOWNLOAD_SAVED = "image_download_saved";

    private MorpheStrings() {}

    static Locale locale() {
        Context context = MorpheSettingsStore.appContext();
        if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            LocaleList locales = context.getResources().getConfiguration().getLocales();
            if (locales != null && !locales.isEmpty()) return locales.get(0);
        }
        return Locale.getDefault();
    }

    static String catalogLanguage(Locale locale) {
        if ("zh".equals(locale.getLanguage())) {
            String script = locale.getScript();
            String region = locale.getCountry();
            if ("Hant".equals(script) || (script.isEmpty()
                    && ("TW".equals(region) || "HK".equals(region) || "MO".equals(region)))) {
                return "zh-TW";
            }
            return "zh-CN";
        }
        return PinterestStringCatalog.resolve(locale.toLanguageTag(), locale.getLanguage());
    }

    static String normalizeSearch(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("(?<=\\p{IsLatin})\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Add aliases for every supported translation of the same label. */
    static String searchAliases(String visibleText) {
        StringBuilder result = new StringBuilder(visibleText);
        String[] active = PinterestStringCatalog.forLanguage(catalogLanguage(locale()));
        for (int index = 0; index < active.length; index++) {
            if (!visibleText.equals(active[index])) continue;
            for (String language : PinterestStringCatalog.LANGUAGES) {
                String alias = PinterestStringCatalog.forLanguage(language)[index];
                if (!visibleText.equals(alias)) result.append(' ').append(alias);
            }
        }
        return result.toString();
    }

    static String number(int value) {
        java.text.NumberFormat format = java.text.NumberFormat.getIntegerInstance(locale());
        format.setGroupingUsed(false);
        return format.format(value);
    }

    static String get(String key) {
        String value = find(key);
        return value == null ? key : value;
    }

    static String find(String key) {
        int index = Arrays.binarySearch(PinterestStringCatalog.KEYS, key);
        if (index < 0) return null;
        return PinterestStringCatalog.forLanguage(catalogLanguage(locale()))[index];
    }
}
