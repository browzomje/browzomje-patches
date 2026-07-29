package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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

    private TextView diagnosticsView;

    /** Pin Sans, il font di Pinterest, o null se l'APK non lo espone più con questo nome. */
    private Typeface fontRegular;
    private Typeface fontBold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Traccia esplicita che la schermata si è aperta. Se l'utente tocca la voce "Morphe"
        // nelle Impostazioni e questa riga NON compare in logcat, il problema è che Pinterest
        // non sta risolvendo l'intent morphe:// — non che la schermata sia rotta.
        MorpheLog.i(MorpheLog.SETTINGS_ENTRY, "schermata Morphe aperta (intent: "
                + (getIntent() != null ? String.valueOf(getIntent().getData()) : "nessuno") + ")");

        fontRegular = pinterestFont("pin_sans_regular");
        fontBold = pinterestFont("pin_sans_bold");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("Morphe");
        title.setTextSize(28);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title);

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_CONTENT), "ic_vr_sparkle_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.ADS_TITLE), null,
                MorpheSettingsStore.KEY_DISABLE_ADS,
                MorpheSettingsStore.isAdsDisabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SEARCH_HISTORY_TITLE),
                MorpheStrings.get(MorpheStrings.SEARCH_HISTORY_DESCRIPTION),
                MorpheSettingsStore.KEY_HIDE_SEARCH_HISTORY,
                MorpheSettingsStore.isSearchHistoryHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.EMAIL_DIALOG_TITLE), null,
                MorpheSettingsStore.KEY_DISABLE_EMAIL_CONFIRM_DIALOG,
                MorpheSettingsStore.isEmailConfirmDialogDisabled());

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_DOWNLOAD), "ic_vr_download_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.BOARD_DOWNLOAD_TITLE),
                MorpheStrings.get(MorpheStrings.BOARD_DOWNLOAD_DESCRIPTION),
                MorpheSettingsStore.KEY_BOARD_DOWNLOAD,
                MorpheSettingsStore.isBoardDownloadEnabled());

        // Home e Profilo non compaiono qui: vedi MorpheSettingsStore.isNavTabHidden. Nasconderli
        // renderebbe irraggiungibile questa stessa schermata.
        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_NAVBAR), "ic_vr_home_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_SEARCH_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_SEARCH_BUTTON,
                MorpheSettingsStore.isSearchButtonHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_CREATE_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_CREATE_BUTTON,
                MorpheSettingsStore.isCreateButtonHidden());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.HIDE_NOTIFICATIONS_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_NOTIFICATIONS_BUTTON,
                MorpheSettingsStore.isNotificationsButtonHidden());
        addNote(root, MorpheStrings.get(MorpheStrings.NAVBAR_NOTE));

        addDiagnosticsSection(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(root);
        setContentView(scrollView);
        setTitle("Morphe");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostics();
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
            MorpheLog.d(MorpheLog.SETTINGS_ENTRY, "font " + name + " non presente nell'APK");
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SETTINGS_ENTRY, "font " + name + " non caricabile", t);
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
            MorpheLog.d(MorpheLog.SETTINGS_ENTRY, "icona " + name + " non presente nell'APK");
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.SETTINGS_ENTRY, "icona " + name + " non caricabile", t);
        }
        return null;
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

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
                MorpheSettingsStore.set(prefKey, isChecked);
                Toast.makeText(MorpheSettingsActivity.this,
                        MorpheStrings.get(MorpheStrings.TOAST_RESTART), Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(toggle);
        parent.addView(row);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(DIVIDER);
        parent.addView(divider);
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
                    Toast.LENGTH_SHORT).show();
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
