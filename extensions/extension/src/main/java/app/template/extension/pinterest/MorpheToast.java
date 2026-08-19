package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Il messaggio a comparsa di Morphe: una striscia scura arrotondata in alto, come i toast di
 * Pinterest, al posto del toast grigio di sistema.
 *
 * <p><b>Perché disegnarlo invece di riusare quello di Pinterest.</b> Ci avevamo provato, e il modo
 * in cui ci si provava è proprio quello che il {@code PATCHING_MEMORY} dice di non fare: una lista
 * di nomi di classe offuscati, uno per versione. Su 14.32.0 quei nomi <em>risolvono</em> — ma
 * puntano ad altro: {@code ir2.f} è diventata una classe vuota e {@code ir2.h} è
 * {@code SmartBlocksConversation}. È il modo peggiore di rompersi, perché non fallisce: costruisce
 * l'oggetto sbagliato e non si vede niente.
 *
 * <p>Il toast vero di Pinterest non si può nemmeno ancorare per forma con poca spesa: è un modello
 * astratto che sa fabbricarsi una View, consegnato a un contenitore attraverso un evento, e sia il
 * modello sia l'evento sono classi offuscate senza stringhe dentro. Disegnare la striscia da noi
 * costa meno codice di quanto costerebbe ritrovarli, e soprattutto **non nomina niente
 * dell'app**: non c'è nulla che una versione futura possa spostare.
 *
 * <p>L'unica cosa che prendiamo da Pinterest è il carattere, Pin Sans, cercato per nome fra gli
 * asset e ignorato se non c'è.
 */
final class MorpheToast {

    private MorpheToast() {}

    private static final long VISIBLE_MS = 3_200L;
    private static final long FADE_MS = 180L;

    /** Un toast alla volta: il secondo sostituisce il primo, come fa Pinterest. */
    private static View current;

    /**
     * Mostra {@code message} in alto allo schermo.
     *
     * @return false se non c'è un'Activity a cui attaccarlo — il chiamante deve allora ripiegare sul
     *     toast di sistema.
     */
    static boolean show(Context context, final String message) {
        final Activity activity = PinterestUtils.activityOf(context);
        if (activity == null || message == null || message.isEmpty()) {
            return false;
        }
        try {
            final ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
            if (root == null) {
                return false;
            }

            dismissCurrent();

            final View toast = build(activity, message);
            toast.setAlpha(0f);
            root.addView(toast);
            current = toast;

            toast.animate().alpha(1f).setDuration(FADE_MS).start();
            // Toccandolo se ne va subito: è il gesto che tutti si aspettano da un messaggio così.
            toast.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    remove(v);
                }
            });

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    remove(toast);
                }
            }, VISIBLE_MS);

            return true;
        } catch (Throwable t) {
            MorpheLog.w(MorpheLog.REFLECTION, "non sono riuscito a mostrare il toast Morphe", t);
            return false;
        }
    }

    private static View build(Context context, String message) {
        TextView text = new TextView(context);
        text.setText(message);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setMaxLines(3);

        Typeface pinSans = pinterestFont(context, "pin_sans_regular");
        if (pinSans != null) {
            text.setTypeface(pinSans);
        }

        int paddingH = dp(context, 16);
        int paddingV = dp(context, 14);
        text.setPadding(paddingH, paddingV, paddingH, paddingV);

        GradientDrawable background = new GradientDrawable();
        // Il grigio molto scuro dei toast di Pinterest, non nero pieno: sul feed scuro un nero
        // pieno sparirebbe nello sfondo.
        background.setColor(Color.parseColor("#E6111111"));
        background.setCornerRadius(dp(context, 16));
        text.setBackground(background);
        text.setElevation(dp(context, 8));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        int margin = dp(context, 12);
        // In alto sotto la status bar, come i toast nativi.
        params.setMargins(margin, dp(context, 8) + statusBarHeight(context), margin, 0);
        text.setLayoutParams(params);

        return text;
    }

    private static void dismissCurrent() {
        View previous = current;
        if (previous != null) {
            remove(previous);
        }
    }

    private static void remove(final View toast) {
        if (toast == null || toast.getParent() == null) {
            return;
        }
        if (current == toast) {
            current = null;
        }
        toast.animate().alpha(0f).setDuration(FADE_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                ViewGroup parent = (ViewGroup) toast.getParent();
                if (parent != null) {
                    parent.removeView(toast);
                }
            }
        }).start();
    }

    /** Pin Sans, cercato per nome come fa la schermata Morphe. Null se l'APK non lo espone più. */
    private static Typeface pinterestFont(Context context, String name) {
        // Resources.getFont esiste da API 26; sotto si resta col carattere di sistema, come fa già
        // la schermata Morphe.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return null;
        }
        try {
            int id = context.getResources().getIdentifier(name, "font", context.getPackageName());
            return id == 0 ? null : context.getResources().getFont(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int statusBarHeight(Context context) {
        try {
            int id = context.getResources()
                    .getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) {
                return context.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
            // si usa il valore di ripiego
        }
        return dp(context, 24);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
