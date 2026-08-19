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
        MorpheLog.i(MorpheLog.SETTINGS_ENTRY, "Morphe screen opened (intent: "
                + (getIntent() != null ? String.valueOf(getIntent().getData()) : "none") + ")");

        fontRegular = pinterestFont("pin_sans_regular");
        fontBold = pinterestFont("pin_sans_bold");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, 0, pad, pad);
        root.setBackgroundColor(Color.BLACK);

        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_CONTENT), "ic_vr_sparkle_gestalt");
        addToggleRow(root, MorpheStrings.get(MorpheStrings.ADS_TITLE), null,
                MorpheSettingsStore.KEY_DISABLE_ADS,
                MorpheSettingsStore.isAdsDisabled());
        addToggleRow(root, MorpheStrings.get(MorpheStrings.SHOPPING_PINS_TITLE), null,
                MorpheSettingsStore.KEY_HIDE_SHOPPING_PINS,
                MorpheSettingsStore.isShoppingPinsHidden());
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
        // Ogni riga porta l'icona del tasto che nasconde: dal nome non è ovvio quale sia, e da
        // 14.28 "Crea" e "Notifiche" non stanno nemmeno più nella barra ma in alto a destra.
        addSectionHeader(root, MorpheStrings.get(MorpheStrings.SECTION_NAVBAR), "ic_vr_home_gestalt");
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
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(screen);
        setTitle("Morphe");
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
        title.setText("Morphe");
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
                    Toast.LENGTH_SHORT).show();
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
                    Toast.LENGTH_SHORT).show();
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
