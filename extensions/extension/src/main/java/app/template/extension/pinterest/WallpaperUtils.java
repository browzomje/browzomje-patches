package app.template.extension.pinterest;

import android.app.WallpaperManager;
import android.content.Context;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
            container.addView(buildRow(context));
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce sfondo", t);
        }
    }

    /** Costruisce una riga cliccabile coerente col resto del menu (icona + etichetta). */
    private static View buildRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        int padH = dp(context, 16);
        int padV = dp(context, 14);
        row.setPadding(padH, padV, padH, padV);
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        try {
            // Icona di sistema sempre disponibile; sostituibile con una drawable di Pinterest.
            icon.setImageResource(android.R.drawable.ic_menu_gallery);
        } catch (Throwable ignored) {}
        int iconSize = dp(context, 24);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = dp(context, 16);
        icon.setLayoutParams(iconLp);
        row.addView(icon);

        TextView label = new TextView(context);
        label.setText("Imposta come sfondo");
        label.setTextSize(16);
        row.addView(label);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context ctx = v.getContext();
                // Preferisci il bitmap già in memoria (closeup); altrimenti scarica dall'URL.
                Bitmap captured = currentPinBitmap;
                if (captured != null && !captured.isRecycled()) {
                    setWallpaperFromBitmap(ctx, captured);
                } else {
                    setWallpaperFromUrl(ctx, currentPinImageUrl);
                }
            }
        });
        return row;
    }

    /** Imposta lo sfondo da un bitmap già decodificato (nessun download). */
    public static void setWallpaperFromBitmap(final Context context, final Bitmap bitmap) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (bitmap == null || bitmap.isRecycled()) {
            toast(main, context, "Nessuna immagine disponibile per questo pin");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (applyWallpaper(context, bitmap)) {
                    toast(main, context, "Sfondo impostato ✓");
                } else {
                    toast(main, context, "Impossibile impostare lo sfondo");
                }
            }
        }, "morphe-set-wallpaper-bmp").start();
    }

    /**
     * Scarica l'immagine all'URL dato e la imposta come sfondo del dispositivo.
     * Tutto il lavoro pesante (rete + decode) gira su un thread di background;
     * i Toast tornano sul main thread.
     */
    public static void setWallpaperFromUrl(final Context context, final String url) {
        final Handler main = new Handler(Looper.getMainLooper());

        if (url == null || url.isEmpty()) {
            toast(main, context, "Nessuna immagine disponibile per questo pin");
            return;
        }

        toast(main, context, "Scarico l'immagine…");

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
                        toast(main, context, "Immagine non valida");
                        return;
                    }

                    if (applyWallpaper(context, bitmap)) {
                        toast(main, context, "Sfondo impostato ✓");
                    } else {
                        toast(main, context, "Impossibile impostare lo sfondo");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "setWallpaperFromUrl fallito per " + url, t);
                    toast(main, context, "Impossibile impostare lo sfondo");
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "morphe-set-wallpaper").start();
    }

    /** Applica il bitmap come sfondo (home + lock su N+). Ritorna true se riuscito. */
    @android.annotation.SuppressLint("MissingPermission")
    private static boolean applyWallpaper(Context context, Bitmap bitmap) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(context.getApplicationContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true,
                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
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
