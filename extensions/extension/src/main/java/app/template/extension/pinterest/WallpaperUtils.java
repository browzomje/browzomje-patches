package app.template.extension.pinterest;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Logica "complessa" della feature "Imposta pin come sfondo".
 *
 * Vive nella extension (DEX precompilato che il patcher fonde nell'APK) perché qui possiamo
 * scrivere Java normale con accesso pieno all'SDK Android, invece di iniettare smali a mano.
 * La patch bytecode chiama solo i metodi statici pubblici di questa classe.
 *
 * Flusso:
 *   1) un hook salva l'URL immagine del pin correntemente aperto in {@link #currentPinImageUrl}
 *      (vedi SetPinWallpaperPatch / RECAP per il punto di cattura);
 *   2) la voce di menu iniettata chiama {@link #addWallpaperOption(Object)} per disegnare la riga;
 *   3) al tap, {@link #setWallpaperFromUrl(Context, String)} scarica e imposta lo sfondo.
 *
 * Richiede il permesso android.permission.SET_WALLPAPER, aggiunto al manifest dalla patch.
 */
@SuppressWarnings("unused")
public final class WallpaperUtils {

    private static final String TAG = "MorpheWallpaper";

    private WallpaperUtils() {}

    /**
     * URL dell'immagine del pin attualmente visualizzato.
     * Aggiornato dall'hook di cattura installato dalla patch bytecode.
     * volatile: scritto dal thread UI, letto dal thread di download.
     */
    public static volatile String currentPinImageUrl = null;

    /**
     * Bitmap del pin attualmente visualizzato, se già in memoria.
     * Sul closeup l'immagine è già decodificata (es. WebImageView.C): se la catturiamo
     * possiamo impostare lo sfondo SENZA riscaricare nulla — più veloce e affidabile.
     */
    public static volatile Bitmap currentPinBitmap = null;

    /** Chiamato dall'hook che intercetta l'URL del pin aperto. */
    public static void setCurrentPinImageUrl(String url) {
        if (url != null && !url.isEmpty()) {
            currentPinImageUrl = url;
        }
    }

    /** Chiamato dall'hook che intercetta il bitmap del pin aperto. */
    public static void setCurrentPinBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            currentPinBitmap = bitmap;
        }
    }

    /**
     * Helper per ottenere le stringhe localizzate in base alla lingua del dispositivo.
     */
    private static String getString(String key) {
        String lang = Locale.getDefault().getLanguage();
        boolean isIt = "it".equals(lang);
        boolean isEs = "es".equals(lang);
        boolean isFr = "fr".equals(lang);
        boolean isDe = "de".equals(lang);
        boolean isPt = "pt".equals(lang);

        if ("label".equals(key)) {
            if (isIt) return "Imposta come sfondo";
            if (isEs) return "Establecer como fondo de pantalla";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Als Hintergrundbild festlegen";
            if (isPt) return "Definir como papel de parede";
            return "Set as wallpaper";
        }
        if ("no_image".equals(key)) {
            if (isIt) return "Nessuna immagine disponibile per questo pin";
            if (isEs) return "No hay imagen disponible para este pin";
            if (isFr) return "Aucune image disponible pour ce pin";
            if (isDe) return "Kein Bild für diesen Pin verfügbar";
            if (isPt) return "Nenhuma imagem disponível para este pin";
            return "No image available for this pin";
        }
        if ("downloading".equals(key)) {
            if (isIt) return "Scarico l'immagine…";
            if (isEs) return "Descargando imagen…";
            if (isFr) return "Téléchargement de l'image…";
            if (isDe) return "Bild wird heruntergeladen…";
            if (isPt) return "Baixando imagem…";
            return "Downloading image…";
        }
        if ("success".equals(key)) {
            if (isIt) return "Sfondo impostato ✓";
            if (isEs) return "Fondo de pantalla establecido ✓";
            if (isFr) return "Fond d'écran défini ✓";
            if (isDe) return "Hintergrundbild festgelegt ✓";
            if (isPt) return "Papel de parede definido ✓";
            return "Wallpaper set ✓";
        }
        if ("failed".equals(key)) {
            if (isIt) return "Impossibile impostare lo sfondo";
            if (isEs) return "Error al establecer el fondo de pantalla";
            if (isFr) return "Impossible de définir le fond d'écran";
            if (isDe) return "Hintergrundbild konnte nicht festgelegt werden";
            if (isPt) return "Não foi possível definir o papel de parede";
            return "Failed to set wallpaper";
        }
        if ("dialog_title".equals(key)) {
            if (isIt) return "Imposta sfondo";
            if (isEs) return "Establecer fondo de pantalla";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Hintergrundbild festlegen";
            if (isPt) return "Definir papel de parede";
            return "Set wallpaper";
        }
        if ("option_home".equals(key)) {
            if (isIt) return "Schermata Home";
            if (isEs) return "Pantalla de inicio";
            if (isFr) return "Écran d'accueil";
            if (isDe) return "Startbildschirm";
            if (isPt) return "Tela inicial";
            return "Home screen";
        }
        if ("option_lock".equals(key)) {
            if (isIt) return "Schermata di blocco";
            if (isEs) return "Pantalla de bloqueo";
            if (isFr) return "Écran de verrouillage";
            if (isDe) return "Sperrbildschirm";
            if (isPt) return "Tela de bloqueio";
            return "Lock screen";
        }
        if ("option_both".equals(key)) {
            if (isIt) return "Entrambi";
            if (isEs) return "Ambas";
            if (isFr) return "Les deux";
            if (isDe) return "Beide";
            if (isPt) return "Ambos";
            return "Both";
        }
        if ("invalid_image".equals(key)) {
            if (isIt) return "Immagine non valida";
            if (isEs) return "Imagen no válida";
            if (isFr) return "Image non valide";
            if (isDe) return "Ungültiges Bild";
            if (isPt) return "Imagem inválida";
            return "Invalid image";
        }
        return "";
    }

    /**
     * Aggiunge la riga "Imposta come sfondo" al contenitore del menu del pin.
     *
     * Accetta {@link Object} (non {@link View}) di proposito: il chiamante è codice offuscato
     * il cui registro ha tipo statico di interfaccia (es. qz0.c), non View. Prendere Object
     * evita errori del verifier Dalvik; il cast a ViewGroup avviene a runtime, dove l'oggetto
     * reale è una LinearLayout (la OverflowMenu di Pinterest).
     */
    public static void addWallpaperOption(Object menuContainer) {
        if (!(menuContainer instanceof ViewGroup)) {
            Log.w(TAG, "menuContainer non è un ViewGroup: " + menuContainer);
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            View row = null;
            String labelText = getString("label");
            try {
                // Tenta la costruzione tramite reflection per utilizzare i componenti Gestalt nativi di Pinterest
                row = buildRowReflective(container, labelText);
                Log.d(TAG, "Riga creata con successo tramite reflection");
            } catch (Throwable t) {
                Log.w(TAG, "Errore nella creazione tramite reflection, uso il fallback", t);
                row = buildRowFallback(context, labelText, container);
            }
            if (row != null) {
                container.addView(row);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce sfondo", t);
        }
    }

    /** Costruisce la riga del menu usando reflection per chiamare le API native di Pinterest */
    private static View buildRowReflective(ViewGroup container, String labelText) throws Exception {
        // 1. Ottiene il viewCreator (uz0.c) chiamando D() su uz0.z (container)
        Method dMethod = container.getClass().getMethod("D");
        Object viewCreator = dMethod.invoke(container);

        // 2. Carica la classe enum delle icone ku1.x e ottiene la costante IMAGE
        Class<?> xClass = Class.forName("ku1.x");
        Object imageIcon = Enum.valueOf((Class<Enum>) xClass, "IMAGE");

        // 3. Ottiene il valore del campo booleano B da uz0.z
        Field bField = container.getClass().getField("B");
        boolean z9 = bField.getBoolean(container);

        // 4. Ottiene e invoca il metodo a(CharSequence, String, ku1.x, boolean) su uz0.c
        Method aMethod = viewCreator.getClass().getMethod("a", CharSequence.class, String.class, xClass, boolean.class);
        RelativeLayout row = (RelativeLayout) aMethod.invoke(viewCreator, labelText, null, imageIcon, z9);

        // 5. Imposta il click listener sulla riga
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWallpaperDialog(v.getContext());
            }
        });
        return row;
    }

    /** Costruisce una riga cliccabile di fallback se la reflection fallisce (copia lo stile da un fratello) */
    private static View buildRowFallback(Context context, String labelText, ViewGroup container) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(android.R.drawable.ic_menu_gallery);
        } catch (Throwable ignored) {}

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(16);

        View refRow = findReferenceRow(container);
        if (refRow != null) {
            try {
                if (refRow.getBackground() != null) {
                    row.setBackground(refRow.getBackground().getConstantState().newDrawable().mutate());
                }
            } catch (Throwable ignored) {}

            row.setPadding(refRow.getPaddingLeft(), refRow.getPaddingTop(), refRow.getPaddingRight(), refRow.getPaddingBottom());

            TextView refText = findTextView(refRow);
            if (refText != null) {
                label.setTextColor(refText.getTextColors());
                label.setTextSize(0, refText.getTextSize());
                label.setTypeface(refText.getTypeface());
            } else {
                label.setTextColor(0xFFFFFFFF);
            }

            ImageView refImage = findImageView(refRow);
            if (refImage != null) {
                if (refImage.getColorFilter() != null) {
                    icon.setColorFilter(refImage.getColorFilter());
                } else {
                    icon.setColorFilter(0xFFFFFFFF);
                }
                ViewGroup.LayoutParams lp = refImage.getLayoutParams();
                if (lp != null) {
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(lp.width, lp.height);
                    iconLp.rightMargin = dp(icon.getContext(), 16);
                    iconLp.gravity = Gravity.CENTER_VERTICAL;
                    icon.setLayoutParams(iconLp);
                } else {
                    int iconSize = dp(context, 24);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                    iconLp.rightMargin = dp(context, 16);
                    icon.setLayoutParams(iconLp);
                }
            } else {
                icon.setColorFilter(0xFFFFFFFF);
                int iconSize = dp(context, 24);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.rightMargin = dp(context, 16);
                icon.setLayoutParams(iconLp);
            }
        } else {
            row.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
            label.setTextColor(0xFFFFFFFF);
            icon.setColorFilter(0xFFFFFFFF);
            int iconSize = dp(context, 24);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.rightMargin = dp(context, 16);
            icon.setLayoutParams(iconLp);
        }

        row.addView(icon);
        row.addView(label);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWallpaperDialog(v.getContext());
            }
        });

        return row;
    }

    private static View findReferenceRow(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof RelativeLayout) {
                TextView tv = findTextView(child);
                if (tv != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private static TextView findTextView(View v) {
        if (v instanceof TextView) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findTextView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ImageView findImageView(View v) {
        if (v instanceof ImageView) {
            return (ImageView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView found = findImageView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Mostra un dialog per scegliere dove impostare lo sfondo. */
    private static void showWallpaperDialog(final Context context) {
        final Bitmap captured = currentPinBitmap;
        final String url = currentPinImageUrl;

        if ((captured == null || captured.isRecycled()) && (url == null || url.isEmpty())) {
            final Handler main = new Handler(Looper.getMainLooper());
            toast(main, context, getString("no_image"));
            return;
        }

        final String[] options = {
            getString("option_home"),
            getString("option_lock"),
            getString("option_both")
        };

        try {
            new AlertDialog.Builder(context)
                .setTitle(getString("dialog_title"))
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int flags = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            if (which == 0) {
                                flags = WallpaperManager.FLAG_SYSTEM;
                            } else if (which == 1) {
                                flags = WallpaperManager.FLAG_LOCK;
                            } else {
                                flags = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                            }
                        }
                        
                        if (captured != null && !captured.isRecycled()) {
                            setWallpaperFromBitmap(context, captured, flags);
                        } else {
                            setWallpaperFromUrl(context, url, flags);
                        }
                    }
                })
                .show();
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile mostrare il dialog di scelta sfondo", t);
            // Fallback: imposta entrambi come prima
            int flags = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
            }
            if (captured != null && !captured.isRecycled()) {
                setWallpaperFromBitmap(context, captured, flags);
            } else {
                setWallpaperFromUrl(context, url, flags);
            }
        }
    }

    /** Imposta lo sfondo da un bitmap già decodificato (nessun download). */
    public static void setWallpaperFromBitmap(final Context context, final Bitmap bitmap, final int flags) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (bitmap == null || bitmap.isRecycled()) {
            toast(main, context, getString("no_image"));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (applyWallpaper(context, bitmap, flags)) {
                    toast(main, context, getString("success"));
                } else {
                    toast(main, context, getString("failed"));
                }
            }
        }, "morphe-set-wallpaper-bmp").start();
    }

    /**
     * Scarica l'immagine all'URL dato e la imposta come sfondo del dispositivo.
     */
    public static void setWallpaperFromUrl(final Context context, final String url, final int flags) {
        final Handler main = new Handler(Looper.getMainLooper());

        if (url == null || url.isEmpty()) {
            toast(main, context, getString("no_image"));
            return;
        }

        toast(main, context, getString("downloading"));

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL parsed = new URL(url);
                    conn = (HttpURLConnection) parsed.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.connect();

                    InputStream in = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    in.close();

                    if (bitmap == null) {
                        toast(main, context, getString("invalid_image"));
                        return;
                    }

                    if (applyWallpaper(context, bitmap, flags)) {
                        toast(main, context, getString("success"));
                    } else {
                        toast(main, context, getString("failed"));
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "setWallpaperFromUrl fallito per " + url, t);
                    toast(main, context, getString("failed"));
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "morphe-set-wallpaper").start();
    }

    /** Applica il bitmap come sfondo (home + lock su N+). Ritorna true se riuscito. */
    @android.annotation.SuppressLint("MissingPermission")
    private static boolean applyWallpaper(Context context, Bitmap bitmap, int flags) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(context.getApplicationContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, flags);
            } else {
                wm.setBitmap(bitmap);
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "applyWallpaper fallito", t);
            return false;
        }
    }

    private static void toast(Handler main, final Context context, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
