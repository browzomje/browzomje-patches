package app.browzomje.extension.pinterest;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("unused")
class WallpaperHandler {
    private static final String TAG = "MorpheWallpaper";

    public static void addWallpaperOption(Object menuContainer) {
        if (!(menuContainer instanceof ViewGroup)) {
            Log.w(TAG, "menuContainer non è un ViewGroup: " + menuContainer);
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            View row = null;
            String labelText = PinterestUtils.getString("label");
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PinterestUtils.dismissMenu();
                    showWallpaperDialog(v.getContext());
                }
            };
            try {
                row = PinterestUtils.buildRowReflective(container, labelText, "IMAGE", onClickListener);
                Log.d(TAG, "Riga sfondo creata con successo tramite reflection");
            } catch (Throwable t) {
                Log.w(TAG, "Errore nella creazione sfondo tramite reflection, uso il fallback", t);
                row = PinterestUtils.buildRowFallback(context, labelText, container, android.R.drawable.ic_menu_gallery, onClickListener);
            }
            if (row != null) {
                container.addView(row);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce sfondo", t);
        }
    }

    private static void showWallpaperDialog(final Context context) {
        final Bitmap captured = PinterestUtils.currentPinBitmap;
        final String url = PinterestUtils.currentPinImageUrl;

        if ((captured == null || captured.isRecycled()) && (url == null || url.isEmpty())) {
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("no_image"));
            return;
        }

        final String[] options = {
            PinterestUtils.getString("option_home"),
            PinterestUtils.getString("option_lock"),
            PinterestUtils.getString("option_both")
        };

        try {
            boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            final int bgColor = isDark ? 0xFF212121 : 0xFFFFFFFF;
            final int textColor = isDark ? 0xFFFFFFFF : 0xFF111111;
            final int titleColor = isDark ? 0xFFFFFFFF : 0xFF111111;
            final int pressedColor = isDark ? 0xFF3D3D3D : 0xFFF0F0F0;

            final Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);

            RelativeLayout rootLayout = new RelativeLayout(context);
            rootLayout.setBackgroundColor(0x99000000);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            
            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(bgColor);
            cardBackground.setCornerRadius(PinterestUtils.dp(context, 24));
            card.setBackground(cardBackground);
            
            int cardPadding = PinterestUtils.dp(context, 24);
            card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);

            TextView titleView = new TextView(context);
            titleView.setText(PinterestUtils.getString("dialog_title"));
            titleView.setTextColor(titleColor);
            titleView.setTextSize(20);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleLp.bottomMargin = PinterestUtils.dp(context, 16);
            card.addView(titleView, titleLp);

            for (int i = 0; i < options.length; i++) {
                final int index = i;
                
                final LinearLayout optionView = new LinearLayout(context);
                optionView.setOrientation(LinearLayout.HORIZONTAL);
                optionView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                optionView.setPadding(PinterestUtils.dp(context, 16), PinterestUtils.dp(context, 14), PinterestUtils.dp(context, 16), PinterestUtils.dp(context, 14));
                optionView.setClickable(true);
                optionView.setFocusable(true);

                ImageView iconView = new ImageView(context);
                iconView.setImageDrawable(createOptionIcon(context, index, textColor, bgColor));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(PinterestUtils.dp(context, 24), PinterestUtils.dp(context, 24));
                iconLp.rightMargin = PinterestUtils.dp(context, 16);
                optionView.addView(iconView, iconLp);

                TextView textView = new TextView(context);
                textView.setText(options[index]);
                textView.setTextColor(textColor);
                textView.setTextSize(16);
                textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                optionView.addView(textView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GradientDrawable itemShape = new GradientDrawable();
                    itemShape.setColor(bgColor);
                    itemShape.setCornerRadius(PinterestUtils.dp(context, 12)); 
                    
                    ColorStateList rippleColor = ColorStateList.valueOf(pressedColor);
                    RippleDrawable ripple = new RippleDrawable(
                        rippleColor,
                        null, 
                        itemShape 
                    );
                    optionView.setBackground(ripple);
                } else {
                    StateListDrawable states = new StateListDrawable();
                    
                    GradientDrawable pressedShape = new GradientDrawable();
                    pressedShape.setColor(pressedColor);
                    pressedShape.setCornerRadius(PinterestUtils.dp(context, 12));
                    
                    GradientDrawable normalShape = new GradientDrawable();
                    normalShape.setColor(bgColor);
                    normalShape.setCornerRadius(PinterestUtils.dp(context, 12));
                    
                    states.addState(new int[] {android.R.attr.state_pressed}, pressedShape);
                    states.addState(new int[] {}, normalShape);
                    optionView.setBackground(states);
                }

                optionView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        
                        int flags = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            if (index == 0) {
                                flags = WallpaperManager.FLAG_SYSTEM;
                            } else if (index == 1) {
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
                });

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                itemLp.bottomMargin = PinterestUtils.dp(context, 4); 
                card.addView(optionView, itemLp);
            }

            RelativeLayout.LayoutParams cardLp = new RelativeLayout.LayoutParams(
                PinterestUtils.dp(context, 300), 
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.addRule(RelativeLayout.CENTER_IN_PARENT);
            rootLayout.addView(card, cardLp);

            rootLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            dialog.setContentView(rootLayout);
            dialog.show();

        } catch (Throwable t) {
            Log.e(TAG, "Impossibile mostrare il custom dialog, uso fallback", t);
            showWallpaperDialogFallback(context, captured, url, options);
        }
    }

    private static void showWallpaperDialogFallback(final Context context, final Bitmap captured, final String url, String[] options) {
        try {
            new AlertDialog.Builder(context)
                .setTitle(PinterestUtils.getString("dialog_title"))
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
            Log.e(TAG, "Fallback fallito", t);
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

    public static void setWallpaperFromBitmap(final Context context, final Bitmap bitmap, final int flags) {
        if (bitmap == null || bitmap.isRecycled()) {
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("no_image"));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (applyWallpaper(context, bitmap, flags)) {
                    PinterestUtils.showNativeToast(context, PinterestUtils.getString("success"));
                } else {
                    PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
                }
            }
        }, "morphe-set-wallpaper-bmp").start();
    }

    public static void setWallpaperFromUrl(final Context context, final String url, final int flags) {
        final Handler main = new Handler(Looper.getMainLooper());

        if (url == null || url.isEmpty()) {
            PinterestUtils.showNativeToast(context, PinterestUtils.getString("no_image"));
            return;
        }

        PinterestUtils.toast(main, context, PinterestUtils.getString("downloading"));

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
                        PinterestUtils.showNativeToast(context, PinterestUtils.getString("invalid_image"));
                        return;
                    }

                    if (applyWallpaper(context, bitmap, flags)) {
                        PinterestUtils.showNativeToast(context, PinterestUtils.getString("success"));
                    } else {
                        PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "setWallpaperFromUrl fallito per " + url, t);
                    PinterestUtils.showNativeToast(context, PinterestUtils.getString("failed"));
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "morphe-set-wallpaper").start();
    }

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

    private static Drawable createOptionIcon(Context context, int index, int color, int bgColor) {
        int size = PinterestUtils.dp(context, 24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(PinterestUtils.dp(context, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (index == 0) {
            Path path = new Path();
            path.moveTo(size * 0.5f, size * 0.18f);
            path.lineTo(size * 0.18f, size * 0.46f);
            path.lineTo(size * 0.82f, size * 0.46f);
            path.close();
            
            path.moveTo(size * 0.26f, size * 0.46f);
            path.lineTo(size * 0.26f, size * 0.82f);
            path.lineTo(size * 0.74f, size * 0.82f);
            path.lineTo(size * 0.74f, size * 0.46f);
            canvas.drawPath(path, paint);
            
            Path door = new Path();
            door.moveTo(size * 0.44f, size * 0.82f);
            door.lineTo(size * 0.44f, size * 0.62f);
            door.lineTo(size * 0.56f, size * 0.62f);
            door.lineTo(size * 0.56f, size * 0.82f);
            canvas.drawPath(door, paint);
        } else if (index == 1) {
            RectF body = new RectF(size * 0.25f, size * 0.46f, size * 0.75f, size * 0.82f);
            canvas.drawRoundRect(body, PinterestUtils.dp(context, 3), PinterestUtils.dp(context, 3), paint);
            
            RectF shackle = new RectF(size * 0.34f, size * 0.18f, size * 0.66f, size * 0.52f);
            canvas.drawArc(shackle, 180, 180, false, paint);
            
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size * 0.5f, size * 0.6f, PinterestUtils.dp(context, 2), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(size * 0.5f, size * 0.6f + PinterestUtils.dp(context, 2), size * 0.5f, size * 0.72f, paint);
        } else { 
            RectF screen1 = new RectF(size * 0.18f, size * 0.18f, size * 0.52f, size * 0.68f);
            canvas.drawRoundRect(screen1, PinterestUtils.dp(context, 3), PinterestUtils.dp(context, 3), paint);
            
            RectF screen2 = new RectF(size * 0.48f, size * 0.32f, size * 0.82f, size * 0.82f);

            Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clearPaint.setColor(bgColor);
            clearPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(screen2, PinterestUtils.dp(context, 3), PinterestUtils.dp(context, 3), clearPaint);
            
            canvas.drawRoundRect(screen2, PinterestUtils.dp(context, 3), PinterestUtils.dp(context, 3), paint);
            
            canvas.drawLine(size * 0.58f, size * 0.76f, size * 0.72f, size * 0.76f, paint);
        }

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
