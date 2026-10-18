package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * Schermata impostazioni di Morphe: attivabile da una voce nelle Impostazioni di Pinterest
 * (vedi patch "Morphe settings entry") o direttamente via {@code adb shell am start -a
 * android.intent.action.VIEW -d "morphe://settings"}. UI costruita interamente a codice,
 * nessuna dipendenza da layout di Pinterest.
 *
 * <p>Dell'app si riusa una cosa sola: il <b>font</b>. Pin Sans è dentro l'APK come risorsa e si
 * carica per nome, così la schermata non stona accanto alle Impostazioni native. Tutto il resto
 * — colori, spaziature, componenti — è nostro, perché dipendere dal tema di Pinterest è ciò che
 * faceva crashare questa schermata (vedi `MorpheSettingsResourcePatch`).
 *
 * <p>In fondo c'è un pannello di diagnostica che elenca quali hook sono scattati e con quale
 * esito: serve a capire cosa funziona anche senza un PC collegato in adb.
 */
public final class MorpheSettingsActivity extends Activity {

    /** Celeste dei titoli di sezione. */
    private static final int ACCENT = Color.parseColor("#7FD4F5");

    private static final int TEXT_PRIMARY = Color.WHITE;
    private static final int TEXT_SECONDARY = Color.parseColor("#9A9A9A");
    private static final int DIVIDER = Color.parseColor("#2A2A2A");
    private static final int BUTTON_BACKGROUND = Color.parseColor("#2A2A2A");

    /**
     * Codici di richiesta per il selettore di file di sistema. Servono solo a distinguere le due
     * risposte in {@link #onActivityResult}, il valore in sé è arbitrario.
     */
    private static final int REQUEST_EXPORT = 4701;
    private static final int REQUEST_IMPORT = 4702;

    private final java.util.Map<String, Switch> settingToggles = new java.util.HashMap<>();
    private boolean refreshingDependencies;
    private TextView diagnosticsView;
    private TextView restartNotice;
    private final java.util.Set<View> sectionHeaders = new java.util.HashSet<>();
    private final Runnable hideRestartNotice = () -> restartNotice.setVisibility(View.GONE);

    /** Pin Sans, il font di Pinterest, o null se l'APK non lo espone più con questo nome. */
    private Typeface fontRegular;
    private Typeface fontBold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Traccia esplicita che la schermata si è aperta. Se l'utente tocca la voce "Morphe"
        // nelle Impostazioni e questa riga NON compare in logcat, il problema è che Pinterest
        // non sta risolvendo l'intent morphe:// — non che la schermata sia rotta.
        MorpheLog.i(MorpheLog.SETTINGS_ENTRY, "Morphe screen opened (intent: "
                + (getIntent() != null ? String.valueOf(getIntent().getData()) : "none") + ")");

        fontRegular = pinterestFont("pin_sans_regular");
        fontBold = pinterestFont("pin_sans_bold");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, 0, pad, pad);
        root.setBackgroundColor(Color.BLACK);

        try {
            String appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            addNote(root, MorpheStrings.get(MorpheStrings.APP_VERSION_LABEL) + ": " + appVersion
                    + "\n" + MorpheStrings.get(MorpheStrings.PATCHES_VERSION_LABEL) + ": "
                    + app.template.extension.BuildConfig.PATCHES_VERSION);
        } catch (android.content.pm.PackageManager.NameNotFoundException impossible) {
            MorpheLog.w(MorpheLog.SETTINGS_ENTRY, "could not read installed app version", impossible);
        }

        Button recommended = newButton(MorpheStrings.get(MorpheStrings.SETTINGS_RECOMMENDED));
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetParams.topMargin = dp(12);
        presetParams.bottomMargin = dp(16);
        recommended.setOnClickListener(v -> {
            MorpheSettingsStore.applyRecommended();
            Intent refresh = new Intent(this, MorpheSettingsActivity.class);
            refresh.putExtra("show_restart_notice", true);
            startActivity(refresh);
            finish();
        });
        root.addView(recommended, presetParams);

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_CONTENT), "ic_vr_sparkle_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.ADS_TITLE), MorpheStrings.get(MorpheStrings.ADS_DESCRIPTION),
                MorpheSettingsStore.KEY_DISABLE_ADS,
                MorpheSettingsStore.isAdsDisabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SHOPPING_PINS_TITLE), MorpheStrings.get(MorpheStrings.SHOPPING_PINS_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_SHOPPING_PINS,
                MorpheSettingsStore.isShoppingPinsHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_VIDEO_PINS_TITLE),
                MorpheStrings.get(MorpheStrings.HIDE_VIDEO_PINS_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_VIDEO_PINS,
                MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS));
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SEARCH_MODULES_TITLE),
                MorpheStrings.get(MorpheStrings.SEARCH_MODULES_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_SEARCH_BOARD_MODULES,
                MorpheSettingsStore.isSearchBoardModulesHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SEARCH_HISTORY_TITLE),
                MorpheStrings.get(MorpheStrings.SEARCH_HISTORY_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_SEARCH_HISTORY,
                MorpheSettingsStore.isSearchHistoryHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SCREENSHOT_TITLE),
                MorpheStrings.get(MorpheStrings.SCREENSHOT_DESCRIPTION),
                MorpheSettingsStore.KEY_DISABLE_SCREENSHOT_SHARE,
                MorpheSettingsStore.isScreenshotShareDisabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SANITIZE_LINKS_TITLE),
                MorpheStrings.get(MorpheStrings.SANITIZE_LINKS_DESCRIPTION),
                MorpheSettingsStore.KEY_SANITIZE_LINKS,
                MorpheSettingsStore.isLinkSanitizerEnabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SHARE_LINK_ONLY_TITLE),
                MorpheStrings.get(MorpheStrings.SHARE_LINK_ONLY_DESCRIPTION),
                MorpheSettingsStore.KEY_SHARE_LINK_ONLY,
                MorpheSettingsStore.isShareLinkOnlyEnabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SYSTEM_SHARE_SHEET_TITLE),
                MorpheStrings.get(MorpheStrings.SYSTEM_SHARE_SHEET_DESCRIPTION),
                MorpheSettingsStore.KEY_SYSTEM_SHARE_SHEET,
                MorpheSettingsStore.isSystemShareSheetEnabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.EXTERNAL_BROWSER_TITLE),
                MorpheStrings.get(MorpheStrings.EXTERNAL_BROWSER_DESCRIPTION),
                MorpheSettingsStore.KEY_EXTERNAL_BROWSER,
                MorpheSettingsStore.isExternalBrowserEnabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.EMAIL_DIALOG_TITLE), null,
                MorpheSettingsStore.KEY_DISABLE_EMAIL_CONFIRM_DIALOG,
                MorpheSettingsStore.isEmailConfirmDialogDisabled());

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_DOWNLOAD), "ic_vr_download_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.LONG_PRESS_DOWNLOAD_TITLE),
                MorpheStrings.get(MorpheStrings.LONG_PRESS_DOWNLOAD_DESCRIPTION),
                MorpheSettingsStore.KEY_LONG_PRESS_DOWNLOAD,
                MorpheSettingsStore.isLongPressDownloadEnabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.BOARD_DOWNLOAD_TITLE),
                MorpheStrings.get(MorpheStrings.BOARD_DOWNLOAD_DESCRIPTION),
                MorpheSettingsStore.KEY_BOARD_DOWNLOAD,
                MorpheSettingsStore.isBoardDownloadEnabled());

        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIGH_QUALITY_TITLE),
                MorpheStrings.get(MorpheStrings.HIGH_QUALITY_DESCRIPTION),
                MorpheSettingsStore.KEY_HIGH_QUALITY_IMAGES,
                MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIGH_QUALITY_IMAGES));

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_PIN_MENU),
                "ic_vr_ellipsis_gestalt");
        addMenuFilterRow(root);
        addToggleRow(root, MorpheStrings.get(MorpheStrings.IMAGE_INFO_TITLE),
                MorpheStrings.get(MorpheStrings.IMAGE_INFO_DESCRIPTION),
                MorpheSettingsStore.KEY_IMAGE_INFO,
                MorpheSettingsStore.effective(MorpheSettingsStore.KEY_IMAGE_INFO));
        addToggleRow(root, MorpheStrings.get(MorpheStrings.INFO_REPLACE_TITLE), MorpheStrings.get(MorpheStrings.INFO_REPLACE_DESCRIPTION),
                MorpheSettingsStore.KEY_INFO_REPLACE, MorpheSettingsStore.effective(MorpheSettingsStore.KEY_INFO_REPLACE));
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_LIKE_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_LIKE, MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_LIKE));
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_COMMENTS_TITLE),
                MorpheStrings.get(MorpheStrings.HIDE_COMMENTS_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_COMMENTS,
                MorpheSettingsStore.isCommentsHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_COMMENT_BUTTON_TITLE), MorpheStrings.get(MorpheStrings.HIDE_COMMENT_BUTTON_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_COMMENT_BUTTON, MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_COMMENT_BUTTON));
        refreshPinDependencies();

        // Home e Profilo non compaiono qui: vedi MorpheSettingsStore.isNavTabHidden. Nasconderli
        // renderebbe irraggiungibile questa stessa schermata.
        // Ogni riga porta l'icona del tasto che nasconde: dal nome non è ovvio quale sia, e da
        // 14.28 "Crea" e "Notifiche" non stanno nemmeno più nella barra ma in alto a destra.
        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_NAVBAR), "ic_vr_home_gestalt");
        if (HomeFeedRefresh.isAvailable()) addToggleRow(root, MorpheStrings.get(MorpheStrings.KEEP_HOME_FEED_TITLE),
                MorpheStrings.get(MorpheStrings.KEEP_HOME_FEED_DESCRIPTION),
                MorpheSettingsStore.KEY_KEEP_HOME_FEED,
                MorpheSettingsStore.effective(MorpheSettingsStore.KEY_KEEP_HOME_FEED));
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_SEARCH_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_SEARCH_BUTTON,
                MorpheSettingsStore.isSearchButtonHidden(), "ic_vr_magnifying_glass_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_CREATE_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_CREATE_BUTTON,
                MorpheSettingsStore.isCreateButtonHidden(), "ic_vr_plus_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_NOTIFICATIONS_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_NOTIFICATIONS_BUTTON,
                MorpheSettingsStore.isNotificationsButtonHidden(), "ic_vr_speech_ellipsis_gestalt");
        addNote(root, MorpheStrings.get(MorpheStrings.NAVBAR_NOTE));

        addBackupSection(root);
        addDiagnosticsSection(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(root);

        // Barra e contenuto sono fratelli, non annidati: la freccia "indietro" deve restare a
        // schermo mentre si scorre, come nelle altre schermate di Pinterest.
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.BLACK);
        screen.addView(buildTopBar());
        screen.addView(buildSettingsSearch(root, scrollView));
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout overlay = new FrameLayout(this);
        overlay.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        restartNotice = new TextView(this);
        restartNotice.setText(MorpheStrings.get(MorpheStrings.TOAST_RESTART));
        restartNotice.setTextColor(TEXT_PRIMARY);
        restartNotice.setTextSize(14);
        restartNotice.setTypeface(fontRegular);
        restartNotice.setPadding(dp(20), dp(16), dp(20), dp(16));
        restartNotice.setBackgroundColor(BUTTON_BACKGROUND);
        restartNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        restartNotice.setVisibility(View.GONE);
        FrameLayout.LayoutParams noticeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        noticeParams.setMargins(dp(12), 0, dp(12), dp(12));
        overlay.addView(restartNotice, noticeParams);
        setContentView(overlay);
        setTitle("Morphe - browzomje");
        if (getIntent().getBooleanExtra("show_restart_notice", false)) {
            getIntent().removeExtra("show_restart_notice");
            showRestartNotice();
        }
    }

    /**
     * Barra superiore: freccia indietro e titolo, come nelle altre schermate di Pinterest.
     *
     * <p>La freccia è disegnata a mano e non è la ActionBar del sistema: il tema di questa
     * Activity non ne ha una (vedi {@code MorpheSettingsResourcePatch}), e adottare quello di
     * Pinterest per averla è esattamente ciò che faceva crashare la schermata.
     */
    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12), dp(20), dp(12));

        int touch = dp(10);
        View back = backControl();
        back.setPadding(touch, touch, touch, touch);
        back.setContentDescription(pinterestString("back", "Back"));
        back.setBackground(getDrawable(android.R.drawable.list_selector_background));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(24) + touch * 2, dp(24) + touch * 2));

        TextView title = new TextView(this);
        title.setText("Morphe - browzomje");
        title.setTextSize(24);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = dp(4);
        bar.addView(title, titleParams);

        return bar;
    }

    /**
     * @return la freccia indietro: quella di Pinterest se l'APK la espone ancora con questo nome,
     *     altrimenti un chevron disegnato col font. Un tasto invisibile sarebbe peggio di uno
     *     brutto — l'unica via d'uscita dalla schermata resterebbe il tasto indietro di sistema,
     *     senza che niente lo suggerisca.
     */
    private View backControl() {
        Drawable arrow = pinterestIcon("ic_vr_arrow_left_gestalt");
        if (arrow != null) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(arrow);
            icon.setColorFilter(TEXT_PRIMARY);
            return icon;
        }
        TextView chevron = new TextView(this);
        chevron.setText("←");
        chevron.setTextSize(22);
        chevron.setTextColor(TEXT_PRIMARY);
        chevron.setGravity(Gravity.CENTER);
        return chevron;
    }

    /**
     * @param name nome di una risorsa stringa di Pinterest, es. {@code back}.
     * @param fallback testo da usare se quella risorsa non esiste più con quel nome.
     * @return il testo già tradotto da Pinterest nella lingua dell'app: per un'etichetta che
     *     esiste di sicuro nell'APK non vale la pena tradurla una seconda volta noi.
     */
    private String pinterestString(String name, String fallback) {
        try {
            int id = getResources().getIdentifier(name, "string", getPackageName());
            if (id != 0) {
                return getString(id);
            }
        } catch (Throwable ignored) {
            // risorsa non leggibile: si usa il ripiego
        }
        return fallback;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostics();
    }

    private void showRestartNotice() {
        if (restartNotice == null) return;
        restartNotice.removeCallbacks(hideRestartNotice);
        restartNotice.setVisibility(View.VISIBLE);
        restartNotice.postDelayed(hideRestartNotice, 5000);
    }

    @Override
    protected void onDestroy() {
        if (restartNotice != null) restartNotice.removeCallbacks(hideRestartNotice);
        super.onDestroy();
    }

    private String searchableText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(MorpheStrings.searchAliases(
                ((TextView) view).getText().toString()));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                text.append(' ').append(searchableText(group.getChildAt(i)));
        }
        return MorpheStrings.normalizeSearch(text.toString());
    }

    private View buildSettingsSearch(LinearLayout root, ScrollView scroll) {
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(TEXT_PRIMARY);
        search.setHintTextColor(TEXT_SECONDARY);
        search.setHint(MorpheStrings.get(MorpheStrings.SETTINGS_SEARCH));
        search.setTextSize(16);
        search.setTypeface(fontRegular != null ? fontRegular : Typeface.DEFAULT);
        search.setMinHeight(dp(48));
        search.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        android.graphics.drawable.StateListDrawable searchBackground =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(BUTTON_BACKGROUND);
        focused.setCornerRadius(dp(24));
        focused.setStroke(dp(1), ACCENT);
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(BUTTON_BACKGROUND);
        idle.setCornerRadius(dp(24));
        searchBackground.addState(new int[]{android.R.attr.state_focused}, focused);
        searchBackground.addState(new int[]{}, idle);
        search.setBackground(searchBackground);
        Drawable magnifier = pinterestIcon("ic_vr_magnifying_glass_gestalt");
        if (magnifier == null) magnifier = getDrawable(android.R.drawable.ic_menu_search);
        if (magnifier != null) {
            magnifier = magnifier.mutate();
            magnifier.setTint(TEXT_SECONDARY);
            magnifier.setBounds(0, 0, dp(20), dp(20));
            search.setCompoundDrawablesRelative(magnifier, null, null, null);
            search.setCompoundDrawablePadding(dp(10));
        }
        search.setContentDescription(MorpheStrings.get(MorpheStrings.SETTINGS_SEARCH));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        java.util.Map<View, String> index = new java.util.HashMap<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            index.put(child, searchableText(child));
        }
        TextView empty = new TextView(this);
        empty.setText(MorpheStrings.get(MorpheStrings.SETTINGS_SEARCH_EMPTY));
        empty.setTextColor(TEXT_SECONDARY);
        empty.setPadding(0, dp(20), 0, dp(20));
        empty.setVisibility(View.GONE);
        root.addView(empty);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = MorpheStrings.normalizeSearch(s.toString()).trim();
                String[] words = query.split("\\s+");
                View header = null;
                boolean sectionMatch = false;
                boolean anyMatch = false;
                boolean previousMatch = false;
                for (int i = 0; i < root.getChildCount() - 1; i++) {
                    View child = root.getChildAt(i);
                    if (sectionHeaders.contains(child)) {
                        if (header != null) header.setVisibility(query.isEmpty() || sectionMatch ? View.VISIBLE : View.GONE);
                        header = child;
                        sectionMatch = false;
                        previousMatch = false;
                        continue;
                    }
                    String text = index.get(child);
                    boolean match = query.isEmpty();
                    if (!match && text != null && !text.trim().isEmpty()) {
                        match = true;
                        for (String word : words) if (!text.contains(word)) { match = false; break; }
                    }
                    // Dividers follow the visibility of their setting.
                    if (text == null || text.trim().isEmpty()) match = query.isEmpty() || previousMatch;
                    else { previousMatch = match; anyMatch |= match; sectionMatch |= match; }
                    child.setVisibility(match ? View.VISIBLE : View.GONE);
                }
                if (header != null) header.setVisibility(query.isEmpty() || sectionMatch ? View.VISIBLE : View.GONE);
                empty.setVisibility(!query.isEmpty() && !anyMatch ? View.VISIBLE : View.GONE);
                scroll.scrollTo(0, 0);
            }
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout container = new LinearLayout(this);
        container.setPaddingRelative(dp(20), dp(8), dp(20), dp(12));
        container.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return container;
    }

    /**
     * Carica un font di Pinterest dalle risorse dell'APK.
     *
     * @param name nome della risorsa font, es. {@code pin_sans_regular}.
     * @return il Typeface, o null se la risorsa non esiste più con quel nome; in quel caso la
     *     schermata resta leggibile col font di sistema.
     *
     * <p>Il controllo su {@code Resources#getFont} (API 26) serve solo perché il modulo
     * dell'extension si compila con un minSdk più basso di quello di Pinterest, che è 29.
     */
    private Typeface pinterestFont(String name) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return null;
        }
        try {
            int id = getResources().getIdentifier(name, "font", getPackageName());
            if (id != 0) {
                return getResources().getFont(id);
            }
            MorpheLog.d(MorpheLog.SETTINGS_ENTRY, "font " + name + " not present in the APK");
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SETTINGS_ENTRY, "font " + name + " could not be loaded", t);
        }
        return null;
    }

    /**
     * @param iconName nome di un drawable vettoriale di Pinterest, o null per nessuna icona.
     *     Si riusano le icone Gestalt già nell'APK invece di disegnarne di nostre: sono
     *     vettoriali, coerenti col resto dell'app e non pesano nulla. Se un nome non esiste più,
     *     l'intestazione esce senza icona e basta.
     */
    private void addSectionHeader(LinearLayout parent, String title, String iconName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(26), 0, dp(6));

        Drawable icon = pinterestIcon(iconName);
        if (icon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            iconView.setColorFilter(ACCENT);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
            iconParams.rightMargin = dp(8);
            row.addView(iconView, iconParams);
        }

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(13);
        header.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        header.setLetterSpacing(0.04f);
        header.setTextColor(ACCENT);
        row.addView(header);

        sectionHeaders.add(row);
        parent.addView(row);
    }

    /**
     * @param name nome del drawable, es. {@code ic_vr_download_gestalt}.
     * @return il drawable, o null se non esiste con quel nome in questa versione dell'APK.
     */
    private Drawable pinterestIcon(String name) {
        if (name == null) {
            return null;
        }
        try {
            int id = getResources().getIdentifier(name, "drawable", getPackageName());
            if (id != 0) {
                return getDrawable(id);
            }
            MorpheLog.d(MorpheLog.SETTINGS_ENTRY, "icon " + name + " not present in the APK");
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SETTINGS_ENTRY, "icon " + name + " could not be loaded", t);
        }
        return null;
    }

    private void addMenuFilterRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = menuText(MorpheStrings.get(MorpheStrings.MENU_FILTER_TITLE), 16,
                TEXT_PRIMARY, false);
        labels.addView(title);
        TextView description = menuText(MorpheStrings.get(MorpheStrings.MENU_FILTER_HINT), 12,
                TEXT_SECONDARY, false);
        description.setPadding(0, dp(4), dp(12), 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = menuText("›", 26, TEXT_SECONDARY, false);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(DIVIDER), null, null));
        row.setOnClickListener(v -> showMenuFilterEditor());
        parent.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        parent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private TextView menuText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(bold ? (fontBold != null ? fontBold : Typeface.DEFAULT_BOLD)
                : (fontRegular != null ? fontRegular : Typeface.DEFAULT));
        return view;
    }

    private void showMenuFilterEditor() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1A1A1A);
        background.setCornerRadii(new float[] { dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0 });
        card.setBackground(background);
        TextView title = menuText(MorpheStrings.get(MorpheStrings.MENU_FILTER_TITLE), 22,
                TEXT_PRIMARY, true);
        card.addView(title);
        TextView description = menuText(MorpheStrings.get(MorpheStrings.MENU_FILTER_DESCRIPTION),
                14, TEXT_SECONDARY, false);
        description.setPadding(0, dp(12), 0, dp(20));
        description.setLineSpacing(dp(2), 1f);
        card.addView(description);
        java.util.Set<String> selected = PinMenuFilter.selected(this);
        java.util.Map<String, android.widget.CheckBox> checks = new java.util.LinkedHashMap<>();
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (String action : PinMenuFilter.ACTIONS) {
            String label = PinMenuFilter.label(this, action);
            if (label == null) continue;
            android.widget.CheckBox choice = new android.widget.CheckBox(this);
            choice.setText(label);
            choice.setTextSize(16);
            choice.setTextColor(TEXT_PRIMARY);
            choice.setTypeface(fontRegular != null ? fontRegular : Typeface.DEFAULT);
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            choice.setPadding(dp(8), dp(10), dp(8), dp(10));
            choice.setMinHeight(dp(52));
            choice.setChecked(selected.contains(action));
            choice.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(action); else selected.remove(action);
            });
            checks.put(action, choice);
            choices.addView(choice, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.VERTICAL);
        LinearLayout selectAllRow = new LinearLayout(this);
        selectAllRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams selectAllParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectAllParams.bottomMargin = dp(12);
        presets.addView(selectAllRow, selectAllParams);
        String[] presetTitles = { MorpheStrings.MENU_HIDE_ALL, MorpheStrings.MENU_SHOW_ALL, MorpheStrings.MENU_SUGGESTED };
        for (int index = 0; index < presetTitles.length; index++) {
            final int preset = index;
            Button button = newButton(MorpheStrings.get(presetTitles[index]));
            button.setOnClickListener(v -> {
                java.util.Set<String> suggested = PinMenuFilter.recommended();
                for (java.util.Map.Entry<String, android.widget.CheckBox> check : checks.entrySet())
                    check.getValue().setChecked(preset == 0 || (preset == 2 && suggested.contains(check.getKey())));
            });
            if (index < 2) {
                LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (index == 1) half.setMarginStart(dp(12));
                selectAllRow.addView(button, half);
            } else {
                LinearLayout.LayoutParams full = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                full.bottomMargin = dp(12);
                presets.addView(button, full);
            }
        }
        card.addView(presets);
        ScrollView options = new ScrollView(this);
        options.setVerticalScrollBarEnabled(true);
        options.setScrollbarFadingEnabled(false);
        options.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        options.setScrollBarSize(dp(4));
        options.addView(choices);
        card.addView(options, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(360), getResources().getDisplayMetrics().heightPixels / 3)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(20), 0, 0);
        Button cancel = newButton(MorpheStrings.get(MorpheStrings.BUTTON_CANCEL));
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel);
        Button save = newButton(MorpheStrings.get(MorpheStrings.BUTTON_SAVE));
        GradientDrawable saveBackground = new GradientDrawable();
        saveBackground.setColor(TEXT_PRIMARY);
        saveBackground.setCornerRadius(dp(24));
        save.setBackground(saveBackground);
        save.setTextColor(Color.BLACK);
        save.setOnClickListener(v -> {
            MorpheSettingsStore.setMenuFilter(PinMenuFilter.encode(selected));
            dialog.dismiss();
            showRestartNotice();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.setMarginStart(dp(12));
        actions.addView(save, saveParams);
        card.addView(actions);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(card);
        dialog.setContentView(scroll);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Testo esplicativo che non appartiene a nessun toggle in particolare. */
    private void addNote(LinearLayout parent, String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextSize(12);
        note.setTypeface(fontRegular != null ? fontRegular : Typeface.DEFAULT);
        note.setTextColor(TEXT_SECONDARY);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, dp(12), 0, 0);
        parent.addView(note);
    }

    /**
     * @param description testo esplicativo sotto l'etichetta, o null se non serve. Serve per i
     *     toggle il cui effetto non è ovvio (o ha limiti) — meglio dirlo qui che lasciare
     *     l'utente a chiedersi perché una cosa non succede.
     */
    private void addToggleRow(LinearLayout parent, String label, String description,
                              final String prefKey, boolean initialValue) {
        addToggleRow(parent, label, description, prefKey, initialValue, null);
    }

    /**
     * @param iconName drawable di Pinterest da mostrare a sinistra dell'etichetta, o null. Si
     *     usa per i toggle che nascondono un tasto: il nome ("Crea", "Notifiche") non basta a
     *     riconoscerlo nell'app, l'icona sì — è la stessa che l'utente vede sullo schermo.
     */
    private void addToggleRow(LinearLayout parent, String label, String description,
                              final String prefKey, boolean initialValue, String iconName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        Drawable icon = pinterestIcon(iconName);
        if (icon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            iconView.setColorFilter(TEXT_PRIMARY);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconParams.rightMargin = dp(12);
            row.addView(iconView, iconParams);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(TEXT_PRIMARY);
        labelView.setTextSize(16);
        labelView.setTypeface(fontRegular != null ? fontRegular : Typeface.DEFAULT);
        labels.addView(labelView);

        if (description != null) {
            TextView descriptionView = new TextView(this);
            descriptionView.setText(description);
            descriptionView.setTextColor(TEXT_SECONDARY);
            descriptionView.setTextSize(12);
            descriptionView.setTypeface(fontRegular != null ? fontRegular : Typeface.DEFAULT);
            descriptionView.setLineSpacing(dp(2), 1f);
            descriptionView.setPadding(0, dp(4), dp(12), 0);
            labels.addView(descriptionView);
        }

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(labels, labelParams);

        Switch toggle = new Switch(this);
        toggle.setChecked(initialValue);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (refreshingDependencies) return;
                MorpheSettingsStore.set(prefKey, isChecked);
                refreshPinDependencies();
                showRestartNotice();
            }
        });
        settingToggles.put(prefKey, toggle);
        row.addView(toggle);
        parent.addView(row);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(DIVIDER);
        parent.addView(divider);
    }

    private void refreshPinDependencies() {
        refreshingDependencies = true;
        try {
            for (String key : new String[] {MorpheSettingsStore.KEY_INFO_REPLACE,
                    MorpheSettingsStore.KEY_HIDE_LIKE, MorpheSettingsStore.KEY_HIDE_COMMENT_BUTTON}) {
                Switch toggle = settingToggles.get(key);
                if (toggle == null) continue;
                toggle.setChecked(MorpheSettingsStore.effective(key));
                boolean enabled = MorpheSettingsStore.isToggleEnabled(key);
                toggle.setEnabled(enabled);
                if (toggle.getParent() instanceof View) {
                    View row = (View) toggle.getParent();
                    row.setEnabled(enabled);
                    row.setAlpha(enabled ? 1f : 0.45f);
                }
            }
        } finally { refreshingDependencies = false; }
    }

    /**
     * Sezione di backup: salva gli interruttori in un file JSON e li rimette da un file.
     *
     * <p>Il file lo sceglie l'utente attraverso il selettore di sistema (Storage Access
     * Framework): non serve alcun permesso di archiviazione, e il file finisce dove l'utente
     * vuole — cartella locale, scheda SD o un cloud — invece che in un percorso deciso da noi.
     */
    private void addBackupSection(LinearLayout parent) {
        addSectionHeader(parent, MorpheStrings.get(MorpheStrings.SECTION_BACKUP),
                "ic_vr_folder_gestalt");
        addNote(parent, MorpheStrings.get(MorpheStrings.BACKUP_HINT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);

        Button export = newButton(MorpheStrings.get(MorpheStrings.BUTTON_EXPORT));
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExport();
            }
        });
        buttons.addView(export);

        Button importButton = newButton(MorpheStrings.get(MorpheStrings.BUTTON_IMPORT));
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startImport();
            }
        });
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        importParams.leftMargin = dp(10);
        buttons.addView(importButton, importParams);

        parent.addView(buttons);
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, MorpheSettingsBackup.fileName());
        launch(intent, REQUEST_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Non "application/json": diversi file manager non associano quel tipo ai .json e li
        // mostrerebbero in grigio, non selezionabili. Con "*/*" il file è sempre scegliibile, e
        // se non è un backup valido se ne accorge MorpheSettingsBackup.apply.
        intent.setType("*/*");
        launch(intent, REQUEST_IMPORT);
    }

    /** Apre il selettore di sistema, dicendolo con garbo se su questo telefono non ce n'è uno. */
    private void launch(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_NO_FILE_PICKER),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || uri == null) {
            return; // l'utente ha annullato
        }
        if (requestCode == REQUEST_EXPORT) {
            exportTo(uri);
        } else if (requestCode == REQUEST_IMPORT) {
            importFrom(uri);
        }
    }

    private void exportTo(Uri uri) {
        try {
            String json = MorpheSettingsBackup.export(this);
            OutputStream output = getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new java.io.IOException("file not writable");
            }
            try {
                output.write(json.getBytes("UTF-8"));
            } finally {
                output.close();
            }
            MorpheLog.ok(MorpheLog.SETTINGS_ENTRY, "settings exported to " + uri);
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_EXPORTED),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "export failed", t);
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_EXPORT_FAILED) + t,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void importFrom(Uri uri) {
        try {
            String json = MorpheSettingsBackup.readAll(getContentResolver().openInputStream(uri));
            int applied = MorpheSettingsBackup.apply(json);
            Toast.makeText(this,
                    MorpheStrings.get(MorpheStrings.TOAST_IMPORTED)
                            .replace("%d", String.valueOf(applied)),
                    Toast.LENGTH_LONG).show();
            // Gli interruttori a schermo sono stati costruiti coi valori di prima: senza
            // ricostruire la schermata mostrerebbero il contrario di ciò che è appena stato
            // importato, e il primo tocco su uno di essi riscriverebbe il valore vecchio.
            recreate();
        } catch (Throwable t) {
            MorpheLog.e(MorpheLog.SETTINGS_ENTRY, "import failed", t);
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_IMPORT_FAILED) + t,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Pannello di diagnostica. Mostra, per ogni feature, se il suo hook è mai stato eseguito e
     * con quale esito. Se una feature non compare affatto, il suo hook non è mai partito: il
     * problema è nel fingerprint della patch, non nell'extension.
     */
    private void addDiagnosticsSection(LinearLayout parent) {
        addSectionHeader(parent, MorpheStrings.get(MorpheStrings.SECTION_DIAGNOSTICS), "ic_vr_wrench_gestalt");
        addToggleRow(parent, MorpheStrings.get(MorpheStrings.VERBOSE_TITLE),
                MorpheStrings.get(MorpheStrings.VERBOSE_DESCRIPTION),
                MorpheSettingsStore.KEY_VERBOSE_LOGGING,
                MorpheSettingsStore.isVerboseLoggingEnabled());

        addNote(parent, MorpheStrings.get(MorpheStrings.DIAGNOSTICS_HINT));

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextSize(12);
        diagnosticsView.setTextColor(Color.parseColor("#B8E986"));
        // Monospace di proposito: è un dump, e le colonne allineate si leggono meglio.
        diagnosticsView.setTypeface(Typeface.MONOSPACE);
        diagnosticsView.setPadding(0, dp(12), 0, 0);
        parent.addView(diagnosticsView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);

        Button refresh = newButton(MorpheStrings.get(MorpheStrings.BUTTON_REFRESH));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDiagnostics();
            }
        });
        buttons.addView(refresh);

        Button copy = newButton(MorpheStrings.get(MorpheStrings.BUTTON_COPY));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyDiagnostics();
            }
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = dp(10);
        buttons.addView(copy, copyParams);

        parent.addView(buttons);
    }

    private void refreshDiagnostics() {
        if (diagnosticsView != null) {
            diagnosticsView.setText(MorpheLog.dumpStatus());
        }
    }

    private void copyDiagnostics() {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Morphe diagnostics",
                    MorpheLog.dumpStatus()));
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_COPIED),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, MorpheStrings.get(MorpheStrings.TOAST_COPY_FAILED) + t,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Bottone costruito con uno stile esplicito del framework invece che con quello del tema
     * corrente.
     *
     * <p>Col costruttore a un argomento, {@code android.widget.Button} legge
     * {@code ?android:attr/buttonStyle} dal tema dell'Activity: col tema di Pinterest quello
     * punta a {@code button_plain}, che usa {@code ?attr/base_font_size_14}, definito solo negli
     * overlay dei design token che questa schermata non applica — e l'Activity crasha in
     * {@code onCreate}. Con {@code defStyleAttr = 0} il tema viene ignorato del tutto.
     */
    private Button newButton(String text) {
        Button button = new Button(this, null, 0, android.R.style.Widget_Material_Button);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TEXT_PRIMARY);
        button.setTextSize(14);
        button.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        button.setPadding(dp(22), dp(10), dp(22), dp(10));
        button.setMinWidth(0);
        button.setMinimumWidth(0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(BUTTON_BACKGROUND);
        background.setCornerRadius(dp(24));
        button.setBackground(background);

        return button;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
