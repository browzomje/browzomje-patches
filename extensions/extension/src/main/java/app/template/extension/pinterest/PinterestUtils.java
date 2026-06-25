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
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

@SuppressWarnings("unused")
public final class PinterestUtils {

    static final String TAG = "MorpheWallpaper";

    private PinterestUtils() {}

    public static volatile String currentPinImageUrl = null;

    public static volatile Bitmap currentPinBitmap = null;

    public static void setCurrentPinImageUrl(String url) {
        if (url != null && !url.isEmpty()) {
            currentPinImageUrl = url;
        }
    }

    public static void setCurrentPinView(Object view, Bitmap bitmap) {
        if (bitmap != null) {
            currentPinBitmap = bitmap;
        }
        if (view != null) {
            try {
                Class<?> clazz = view.getClass();
                while (clazz != null) {
                    for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            f.setAccessible(true);
                            String val = (String) f.get(view);
                            if (val != null && (val.startsWith("http://") || val.startsWith("https://"))) {
                                currentPinImageUrl = val;
                                Log.d(TAG, "Catturato URL immagine via scansione campi: " + currentPinImageUrl);
                                return;
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            } catch (Throwable t) {
                Log.e(TAG, "Impossibile recuperare URL dal view", t);
            }
        }
    }

    // Delegates for video download
    public static void setCurrentVideoTracks(String uid, java.util.Map<?, ?> videoList) {
        VideoDownloadHandler.setCurrentVideoTracks(uid, videoList);
    }

    public static void setCurrentVideoPin(Object pin) {
        VideoDownloadHandler.setCurrentVideoPin(pin);
    }

    public static void addDownloadVideoOption(final Object menuContainer) {
        VideoDownloadHandler.addDownloadVideoOption(menuContainer);
    }

    // Delegate for wallpaper
    public static void addWallpaperOption(Object menuContainer) {
        WallpaperHandler.addWallpaperOption(menuContainer);
    }

    // Copy Link Logic
    public static void addCopyLinkOption(Object menuContainer) {
        if (!(menuContainer instanceof ViewGroup)) {
            Log.w(TAG, "menuContainer non è un ViewGroup: " + menuContainer);
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            View row = null;
            String labelText = getString("copy_link_label");
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMenu();
                    copyLinkToClipboard(v.getContext());
                }
            };
            try {
                row = buildRowReflective(container, labelText, "LINK", onClickListener);
                Log.d(TAG, "Riga copia link creata con successo tramite reflection");
            } catch (Throwable t) {
                Log.w(TAG, "Errore nella creazione copia link tramite reflection, uso il fallback", t);
                row = buildRowFallback(context, labelText, container, android.R.drawable.ic_menu_share, onClickListener);
            }
            if (row != null) {
                container.addView(row);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce copia link", t);
        }
    }

    private static void copyLinkToClipboard(Context context) {
        String url = currentPinImageUrl;
        if (url == null || url.isEmpty()) {
            showNativeToast(context, getString("no_link"));
            return;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Pinterest Direct Link", url);
            clipboard.setPrimaryClip(clip);
            showNativeToast(context, getString("direct_link_copied"));
        } catch (Throwable t) {
            Log.e(TAG, "Copia negli appunti fallita", t);
            showNativeToast(context, getString("failed"));
        }
    }

    // Ads filtering logic
    public static void filterSponsoredPinsFromFeed(Object feedPage) {
        if (feedPage == null) {
            return;
        }
        filterSponsoredPinsFromFeedRecursive(feedPage, 0);
    }

    private static void filterSponsoredPinsFromFeedRecursive(Object obj, int depth) {
        if (obj == null || depth > 6) {
            return;
        }
        String className = obj.getClass().getName();
        if (className.equals("com.pinterest.api.model.me") || className.endsWith(".Pin") || className.equals("com.pinterest.api.model.Pin")) {
            return;
        }

        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                String cn = clazz.getName();
                if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("kotlin.") || cn.startsWith("androidx.")) {
                    break;
                }

                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object value = f.get(obj);
                        if (value instanceof java.util.List) {
                            java.util.List<?> items = (java.util.List<?>) value;
                            if (items != null && !items.isEmpty()) {
                                boolean isMainFeedList = className.equals("o12.e");
                                if (isMainFeedList) {
                                    int total = items.size();
                                    int promoted = 0;
                                    for (Object item : items) {
                                        if (isPromotedPin(item)) {
                                            promoted++;
                                        }
                                    }
                                    if (promoted >= total) {
                                        continue;
                                    }
                                }

                                int removed = 0;
                                java.util.Iterator<?> it = items.iterator();
                                while (it.hasNext()) {
                                    Object item = it.next();
                                    if (isPromotedPin(item)) {
                                        try {
                                            it.remove();
                                            removed++;
                                        } catch (Throwable ignored) {
                                        }
                                    } else {
                                        filterSponsoredPinsFromFeedRecursive(item, depth + 1);
                                    }
                                }
                                if (removed > 0) {
                                    Log.d(TAG, "Rimossi " + removed + " pin sponsorizzati (profondità " + depth + ")");
                                }
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Filtro ads ricorsivo fallito a profondità " + depth, t);
        }
    }

    private static boolean isPromotedPin(Object item) {
        if (item == null) {
            return false;
        }
        Class<?> clazz = item.getClass();

        try {
            java.lang.reflect.Method m = clazz.getMethod("I5");
            Object result = m.invoke(item);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method m = clazz.getMethod("getIsPromoted");
            Object result = m.invoke(item);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method m = clazz.getMethod("isPromoted");
            Object result = m.invoke(item);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {}

        while (clazz != null) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                String name = f.getName();
                if (name.equals("isPromoted") || name.equals("is_promoted")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(item);
                        if (val instanceof Boolean) {
                            return (Boolean) val;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }



    // Common Package-Private Helpers
    static Object invokeNoArg(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    static void toast(Handler main, final Context context, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    static void showNativeToast(final Context context, final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> tClass = Class.forName("fb0.t");
                    java.lang.reflect.Field aField = tClass.getField("a");
                    Object eventManager = aField.get(null);
                    
                    Class<?> fClass = Class.forName("ir2.f");
                    java.lang.reflect.Constructor<?> fCtor = fClass.getConstructor(String.class, int.class);
                    Object toastObj = fCtor.newInstance(message, 7000);
                    
                    Class<?> hClass = Class.forName("ir2.h");
                    Class<?> oClass = Class.forName("ww1.o");
                    java.lang.reflect.Constructor<?> hCtor = hClass.getConstructor(oClass);
                    Object eventObj = hCtor.newInstance(toastObj);
                    
                    java.lang.reflect.Method dMethod = eventManager.getClass().getMethod("d", Object.class);
                    dMethod.invoke(eventManager, eventObj);
                    Log.d(TAG, "Native toast shown: " + message);
                } catch (Throwable t) {
                    Log.e(TAG, "Errore nella visualizzazione del native toast, uso fallback", t);
                    Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    static void dismissMenu() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> tClass = Class.forName("fb0.t");
                    java.lang.reflect.Field aField = tClass.getField("a");
                    Object eventManager = aField.get(null);
                    
                    Class<?> uClass = Class.forName("ai0.u");
                    java.lang.reflect.Constructor<?> constructor = uClass.getConstructor(int.class, boolean.class);
                    Object dismissEvent = constructor.newInstance(0, true);
                    
                    java.lang.reflect.Method dMethod = eventManager.getClass().getMethod("d", Object.class);
                    dMethod.invoke(eventManager, dismissEvent);
                    Log.d(TAG, "Menu dismissed via EventManager.");
                } catch (Throwable t) {
                    Log.e(TAG, "Errore nella dismissione del menu tramite EventManager", t);
                }
            }
        });
    }

    static View buildRowReflective(ViewGroup container, String labelText, String iconEnumName, View.OnClickListener onClickListener) throws Exception {
        Method dMethod = container.getClass().getMethod("D");
        Object viewCreator = dMethod.invoke(container);

        Class<?> xClass = Class.forName("ku1.x");
        Object imageIcon = Enum.valueOf((Class<Enum>) xClass, iconEnumName);

        Field bField = container.getClass().getField("B");
        boolean z9 = bField.getBoolean(container);

        Method aMethod = viewCreator.getClass().getMethod("a", CharSequence.class, String.class, xClass, boolean.class);
        RelativeLayout row = (RelativeLayout) aMethod.invoke(viewCreator, labelText, null, imageIcon, z9);

        row.setOnClickListener(onClickListener);
        return row;
    }

    static View buildRowFallback(Context context, String labelText, ViewGroup container, int iconResId, View.OnClickListener onClickListener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(iconResId);
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

        row.setOnClickListener(onClickListener);

        return row;
    }

    static View findReferenceRow(ViewGroup container) {
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

    static TextView findTextView(View v) {
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

    static ImageView findImageView(View v) {
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

    // Localization maps
    static String getString(String key) {
        String lang = Locale.getDefault().getLanguage();
        boolean isIt = "it".equals(lang);
        boolean isEs = "es".equals(lang);
        boolean isFr = "fr".equals(lang);
        boolean isDe = "de".equals(lang);
        boolean isPt = "pt".equals(lang);
        boolean isRu = "ru".equals(lang);
        boolean isJa = "ja".equals(lang);
        boolean isZh = "zh".equals(lang);
        boolean isKo = "ko".equals(lang);
        boolean isPl = "pl".equals(lang);
        boolean isNl = "nl".equals(lang);
        boolean isTr = "tr".equals(lang);
        boolean isAr = "ar".equals(lang);
        boolean isHi = "hi".equals(lang);
        boolean isIn = "in".equals(lang) || "id".equals(lang);
        boolean isMs = "ms".equals(lang);
        boolean isVi = "vi".equals(lang);
        boolean isUk = "uk".equals(lang);
        boolean isSv = "sv".equals(lang);
        boolean isNb = "nb".equals(lang) || "no".equals(lang);
        boolean isDa = "da".equals(lang);
        boolean isFi = "fi".equals(lang);
        boolean isFil = "fil".equals(lang) || "tl".equals(lang);
        boolean isEl = "el".equals(lang);
        boolean isCs = "cs".equals(lang);
        boolean isSk = "sk".equals(lang);
        boolean isHu = "hu".equals(lang);
        boolean isRo = "ro".equals(lang);
        boolean isIw = "iw".equals(lang) || "he".equals(lang);
        boolean isHr = "hr".equals(lang);

        if ("copy_link_label".equals(key)) {
            if (isIt) return "Copia link diretto";
            if (isEs) return "Copiar enlace directo";
            if (isFr) return "Copier le lien direct";
            if (isDe) return "Direkten Link kopieren";
            if (isPt) return "Copiar link direto";
            if (isRu) return "Копировать прямую ссылку";
            if (isJa) return "直接リンクをコピー";
            if (isZh) return "复制直链";
            if (isKo) return "직접 링크 복사";
            if (isPl) return "Kopiuj bezpośredni link";
            if (isNl) return "Directe link gekopiëerd";
            if (isTr) return "Doğrudan bağlantıyı kopyala";
            if (isAr) return "نسخ الرابط المباشر";
            return "Copy direct link";
        }
        if ("download_video_label".equals(key)) {
            if (isIt) return "Scarica video";
            if (isEs) return "Descargar vídeo";
            if (isFr) return "Télécharger la vidéo";
            if (isDe) return "Video herunterladen";
            if (isPt) return "Baixar vídeo";
            if (isRu) return "Скачать видео";
            if (isJa) return "動画をダウンロード";
            if (isZh) return "下载视频";
            if (isKo) return "동영상 다운로드";
            if (isPl) return "Pobierz wideo";
            if (isNl) return "Video downloaden";
            if (isTr) return "Videoyu indir";
            if (isAr) return "تنزيل الفيديو";
            return "Download video";
        }
        if ("download_video_external_label".equals(key)) {
            if (isIt) return "Scarica con app esterna (yt-dlp)";
            if (isEs) return "Descargar con app externa (yt-dlp)";
            if (isFr) return "Télécharger avec app externe (yt-dlp)";
            if (isDe) return "Mit externer App herunterladen (yt-dlp)";
            if (isPt) return "Baixar com app externo (yt-dlp)";
            if (isRu) return "Скачать через внешнее приложение (yt-dlp)";
            if (isJa) return "外部アプリでダウンロード (yt-dlp)";
            if (isZh) return "使用外部应用下载 (yt-dlp)";
            if (isKo) return "외부 앱으로 다운로드 (yt-dlp)";
            if (isPl) return "Pobierz za pomocą zewnętrznej aplikacji (yt-dlp)";
            if (isNl) return "Downloaden met externe app (yt-dlp)";
            if (isTr) return "Harici uygulama ile indir (yt-dlp)";
            if (isAr) return "(yt-dlp) التنزيل باستخدام تطبيق خارجي";
            return "Download with external app (yt-dlp)";
        }
        if ("video_download_started".equals(key)) {
            if (isIt) return "Download del video avviato…";
            if (isEs) return "Descarga del vídeo iniciada…";
            if (isFr) return "Téléchargement de la vidéo lancé…";
            if (isDe) return "Video-Download gestartet…";
            if (isPt) return "Download do vídeo iniciado…";
            if (isRu) return "Загрузка видео начата…";
            if (isJa) return "動画のダウンロードを開始しました…";
            if (isZh) return "已开始下载视频…";
            if (isKo) return "동영상 다운로드를 시작했습니다…";
            if (isNl) return "Video downloaden gestart…";
            if (isTr) return "Video indirme başladı…";
            if (isAr) return "بدأ تنزيل الفيديو…";
            return "Video download started…";
        }
        if ("no_video".equals(key)) {
            if (isIt) return "Nessun video disponibile per questo pin";
            if (isEs) return "No hay vídeo disponible para este pin";
            if (isFr) return "Aucune vidéo disponible pour ce pin";
            if (isDe) return "Kein Video für diesen Pin verfügbar";
            return "No video available for this pin";
        }
        if ("downloading".equals(key)) {
            if (isIt) return "Scaricamento dell'immagine…";
            if (isEs) return "Descargando imagen…";
            if (isFr) return "Téléchargement de l'image…";
            if (isDe) return "Bild wird heruntergeladen…";
            if (isPt) return "Baixando imagem…";
            if (isRu) return "Скачивание изображения…";
            if (isJa) return "画像をダウンロード中…";
            if (isZh) return "正在下载图片…";
            if (isKo) return "이미지 다운로드 중…";
            if (isPl) return "Pobieranie obrazu…";
            if (isNl) return "Afbeelding downloaden…";
            if (isTr) return "Resim indiriliyor…";
            if (isAr) return "جاري تنزيل الصورة…";
            if (isHi) return "छви डाउनलोड हो रही है…";
            if (isIn || isMs) return "Mengunduh gambar…";
            if (isVi) return "Đang tải hình ảnh xuống…";
            if (isUk) return "Завантаження зображення…";
            if (isSv) return "Laddar ner bild…";
            if (isNb) return "Laster ned bilde…";
            if (isDa) return "Downloader billede…";
            if (isFi) return "Ladataan kuvaa…";
            if (isFil) return "Dina-download ang larawan…";
            if (isEl) return "Λήψη εικόνας…";
            if (isCs || isSk) return "Stahování obrázku…";
            if (isHu) return "Kép letöltése…";
            if (isRo) return "Se descarcă imaginea…";
            if (isIw) return "מוריד תמונה…";
            if (isHr) return "Preuzimanje slike…";
            return "Downloading image…";
        }
        if ("success".equals(key)) {
            if (isIt) return "Sfondo impostato.";
            if (isEs) return "Fondo de pantalla establecido.";
            if (isFr) return "Fond d'écran défini.";
            if (isDe) return "Hintergrundbild festgelegt.";
            if (isPt) return "Papel de parede definido.";
            if (isRu) return "Обои установлены.";
            if (isJa) return "壁紙を設定しました。";
            if (isZh) return "壁纸设置成功。";
            if (isKo) return "배경화면 설정 완료.";
            if (isPl) return "Tapeta została ustawiona.";
            if (isNl) return "Achtergrond ingesteld.";
            if (isTr) return "Duvar kağıdı ayarlandı.";
            if (isAr) return "تم تعيين الخلفية.";
            if (isHi) return "वॉलपेपर सेट हो गया.";
            if (isIn || isMs) return "Wallpaper diatur.";
            if (isVi) return "Đã đặt hình nền.";
            if (isUk) return "Шпалери встановлено.";
            if (isSv) return "Bakgrundsbild ändrad.";
            if (isNb) return "Bakgrunnsbilde satt.";
            if (isDa) return "Baggrund indstillet.";
            if (isFi) return "Taustakuva asetettu.";
            if (isFil) return "Naitakda ang wallpaper.";
            if (isEl) return "Η ταπετσαρία ορίστηκε.";
            if (isCs || isSk) return "Tapeta nastavena.";
            if (isHu) return "Háttérkép beállítva.";
            if (isRo) return "Fundal setat.";
            if (isIw) return "הרקע הוגדר.";
            if (isHr) return "Pozadina postavljena.";
            return "Wallpaper set.";
        }
        if ("failed".equals(key)) {
            if (isIt) return "Impossibile impostare lo sfondo";
            if (isEs) return "Error al establecer el fondo de pantalla";
            if (isFr) return "Impossible de définir le fond d'écran";
            if (isDe) return "Hintergrundbild konnte nicht festgelegt werden";
            if (isPt) return "Não foi possibile definir o papel de parede";
            if (isRu) return "Не удалось установить обои";
            if (isJa) return "壁紙の設定に失敗しました";
            if (isZh) return "设置壁纸失败";
            if (isKo) return "배경화면 설정 실패";
            if (isPl) return "Nie udało się ustawić tapety";
            if (isNl) return "Instellen van achtergrond mislukt";
            if (isTr) return "Duvar kağıdı ayarlanamadı";
            if (isAr) return "فشل تعيين الخلفية";
            if (isHi) return "वॉलपेपर सेट करने में विफल";
            if (isIn || isMs) return "Gagal mengatur wallpaper";
            if (isVi) return "Không thể đặt hình nền";
            if (isUk) return "Не вдалося встановити шпалери";
            if (isSv) return "Misslyckades att ändra bakgrundsbild";
            if (isNb) return "Kunne ikke sette bakgrunnsbilde";
            if (isDa) return "Kunne ikke indstille baggrund";
            if (isFi) return "Taustakuvan asettaminen epäonnistui";
            if (isFil) return "Bigo sa pagtatakda ng wallpaper";
            if (isEl) return "Αποτυχία ορισμού ταπετσαρίας";
            if (isCs || isSk) return "Nepodařilo se nastavit tapetu";
            if (isHu) return "Háttérkép beállítása sikertelen";
            if (isRo) return "Eroare la setarea fundalului";
            if (isIw) return "הגדרת הרקע נכשله";
            if (isHr) return "Postavljanje pozadine nije uspjelo";
            return "Failed to set wallpaper";
        }
        if ("dialog_title".equals(key)) {
            if (isIt) return "Imposta sfondo";
            if (isEs) return "Establecer fondo de pantalla";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Hintergrundbild festlegen";
            if (isPt) return "Definir papel de parede";
            if (isRu) return "Установить обои";
            if (isJa) return "壁紙を設定";
            if (isZh) return "设置壁纸";
            if (isKo) return "배경화면 설정";
            if (isPl) return "Ustaw tapetę";
            if (isNl) return "Achtergrond instellen";
            if (isTr) return "Duvar kağıdını ayarla";
            if (isAr) return "تعيين الخلفية";
            if (isHi) return "वॉलपेपर सेट करें";
            if (isIn || isMs) return "Atur wallpaper";
            if (isVi) return "Đặt hình nền";
            if (isUk) return "Встановити шпалери";
            if (isSv) return "Ange bakgrundsbild";
            if (isNb) return "Sett bakgrunnsbilde";
            if (isDa) return "Indstil baggrund";
            if (isFi) return "Aseta taustakuva";
            if (isFil) return "Itakda ang wallpaper";
            if (isEl) return "Ορισμός ταπετσαρίας";
            if (isCs || isSk) return "Nastavit tapetu";
            if (isHu) return "Háttérkép beállítása";
            if (isRo) return "Setează fundalul";
            if (isIw) return "הגדר רקע";
            if (isHr) return "Postavi pozadinu";
            return "Set wallpaper";
        }
        if ("option_home".equals(key)) {
            if (isIt) return "Schermata Home";
            if (isEs) return "Pantalla de inicio";
            if (isFr) return "Écran d'accueil";
            if (isDe) return "Startbildschirm";
            if (isPt) return "Tela inicial";
            if (isRu) return "Экран \"Домой\"";
            if (isJa) return "ホーム画面";
            if (isZh) return "主屏幕";
            if (isKo) return "홈 화면";
            if (isPl) return "Ekran startowy";
            if (isNl) return "Beginscherm";
            if (isTr) return "Ana ekran";
            if (isAr) return "الشاشة الرئيسية";
            if (isHi) return "होम स्क्रीन";
            if (isIn || isMs) return "Layar Utama";
            if (isVi) return "Màn hình chính";
            if (isUk) return "Домашній екран";
            if (isSv) return "Hemskärm";
            if (isNb) return "Hjem-skjerm";
            if (isDa) return "Startskærm";
            if (isFi) return "Alkunäyttö";
            if (isFil) return "Home screen";
            if (isEl) return "Αρχική οθόνη";
            if (isCs || isSk) return "Domovská obrazovka";
            if (isHu) return "Kezdőképernyő";
            if (isRo) return "Ecran de pornire";
            if (isIw) return "מסך הבית";
            if (isHr) return "Početni zaslon";
            return "Home screen";
        }
        if ("option_lock".equals(key)) {
            if (isIt) return "Schermata di blocco";
            if (isEs) return "Pantalla de bloqueo";
            if (isFr) return "Écran de verrouillage";
            if (isDe) return "Sperrbildschirm";
            if (isPt) return "Tela de bloqueio";
            if (isRu) return "Экран блокировки";
            if (isJa) return "ロック画面";
            if (isZh) return "锁定屏幕";
            if (isKo) return "잠금 화면";
            if (isPl) return "Ekran blokady";
            if (isNl) return "Vergrendelscherm";
            if (isTr) return "Kilit ekranı";
            if (isAr) return "شاشة القفل";
            if (isHi) return "लॉक स्क्रीन";
            if (isIn || isMs) return "Layar Kunci";
            if (isVi) return "Màn hình khóa";
            if (isUk) return "Екран блокування";
            if (isSv) return "Låsskärm";
            if (isNb) return "Låseskjerm";
            if (isDa) return "Låseskærm";
            if (isFi) return "Lukitusnäyttö";
            if (isFil) return "Lock screen";
            if (isEl) return "Οθόνη κλειδώματος";
            if (isCs || isSk) return "Uzamykací obrazovka";
            if (isHu) return "Képernyőzár";
            if (isRo) return "Ecran de blocare";
            if (isIw) return "מסך הנעילה";
            if (isHr) return "Zaslon zaključavanja";
            return "Lock screen";
        }
        if ("option_both".equals(key)) {
            if (isIt) return "Entrambe";
            if (isEs) return "Ambas";
            if (isFr) return "Les deux";
            if (isDe) return "Beide";
            if (isPt) return "Ambos";
            if (isRu) return "Оба экрана";
            if (isJa) return "両方";
            if (isZh) return "两者";
            if (isKo) return "둘 다";
            if (isPl) return "Oba";
            if (isNl) return "Beide";
            if (isTr) return "Her ikisi";
            if (isAr) return "كليهma";
            if (isHi) return "दोनों";
            if (isIn || isMs) return "Keduanya";
            if (isVi) return "Cả hai";
            if (isUk) return "Обидва";
            if (isSv) return "Båda";
            if (isNb) return "Begge";
            if (isDa) return "Begge";
            if (isFi) return "Molemmat";
            if (isFil) return "Pareho";
            if (isEl) return "Και τα δύο";
            if (isCs || isSk) return "Obe";
            if (isHu) return "Mindkettő";
            if (isRo) return "Ambele";
            if (isIw) return "שניהם";
            if (isHr) return "Oba";
            return "Both";
        }
        if ("invalid_image".equals(key)) {
            if (isIt) return "Immagine non valida";
            if (isEs) return "Imagen no válida";
            if (isFr) return "Image non valide";
            if (isDe) return "Ungültiges Bild";
            if (isPt) return "Imagem inválida";
            if (isRu) return "Неверное изображение";
            if (isJa) return "無効な画像";
            if (isZh) return "无效图片";
            if (isKo) return "유효하지 않은 이미지";
            if (isPl) return "Nieprawidłowy obraz";
            if (isNl) return "Ongeldige afbeelding";
            if (isTr) return "Geçersiz resim";
            if (isAr) return "صورة غير صالحة";
            if (isHi) return "अमान्य छवि";
            if (isIn || isMs) return "Gambar tidak valid";
            if (isVi) return "Hình ảnh không hợp lệ";
            if (isUk) return "Неприпустиме зображення";
            if (isSv) return "Ogiltig bild";
            if (isNb) return "Ugyldig bilde";
            if (isDa) return "Ugyldigt billede";
            if (isFi) return "Virheellinen kuva";
            if (isFil) return "Hindi wastong larawan";
            if (isEl) return "Μη έγκυρη εικόνα";
            if (isCs || isSk) return "Neplatný obrázek";
            if (isHu) return "Érvénytelen kép";
            if (isRo) return "Imagine invalidă";
            if (isIw) return "תמונה לא תקינה";
            if (isHr) return "Nevaljana slika";
            return "Invalid image";
        }
        if ("label".equals(key)) {
            if (isIt) return "Imposta come sfondo";
            if (isEs) return "Establecer como fondo";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Als Hintergrundbild festlegen";
            if (isPt) return "Definir como fundo";
            if (isRu) return "Установить как обои";
            if (isJa) return "壁紙として設定";
            if (isZh) return "设为壁纸";
            if (isKo) return "배경화면으로 설정";
            if (isPl) return "Ustaw jako tapetę";
            if (isNl) return "Als achtergrond instellen";
            if (isTr) return "Duvar kağıdı yap";
            if (isAr) return "تعيين كخلفية";
            return "Set as wallpaper";
        }
        if ("no_link".equals(key)) {
            if (isIt) return "Nessun indirizzo immagine trovato";
            if (isEs) return "No se encontró enlace de imagen";
            if (isFr) return "Aucun lien d'image trouvé";
            if (isDe) return "Kein Bildlink gefunden";
            if (isPt) return "Nenhum link de imagem encontrado";
            if (isRu) return "Ссылка на изображение не найдена";
            if (isJa) return "画像リンクが見つかりません";
            if (isZh) return "未找到图片链接";
            if (isKo) return "이미지 링크를 찾을 수 없습니다";
            if (isPl) return "Nie znaleziono linku do obrazu";
            if (isNl) return "Geen afbeeldingslink gevonden";
            if (isTr) return "Resim bağlantısı bulunamadı";
            if (isAr) return "لم يتم العثور على رابط الصورة";
            return "No image link found";
        }
        if ("direct_link_copied".equals(key)) {
            if (isIt) return "Link diretto copiato!";
            if (isEs) return "¡Enlace directo copiado!";
            if (isFr) return "Lien direct copié !";
            if (isDe) return "Direkter Link kopiert!";
            if (isPt) return "Link direto copiado!";
            if (isRu) return "Прямая ссылка скопирована!";
            if (isJa) return "直接リンクをコピーしました！";
            if (isZh) return "直链已复制！";
            if (isKo) return "직접 링크가 복사되었습니다!";
            if (isPl) return "Bezpośredni link skopiowany!";
            if (isNl) return "Directe link gekopieerd!";
            if (isTr) return "Doğrudan bağlantı kopyalandı!";
            if (isAr) return "تم نسخ الرابط المباشr!";
            return "Direct link copied!";
        }
        if ("no_image".equals(key)) {
            if (isIt) return "Nessuna immagine disponibile per questo pin";
            if (isEs) return "No hay imagen disponible para este pin";
            if (isFr) return "Aucune image disponible pour ce pin";
            if (isDe) return "Kein Bild für diesen Pin verfügbar";
            return "No image available for this pin";
        }
        return "";
    }

    static String getLocalizedError(String key) {
        String lang = Locale.getDefault().getLanguage();
        boolean isIt = "it".equals(lang);
        boolean isEs = "es".equals(lang);
        boolean isFr = "fr".equals(lang);
        boolean isDe = "de".equals(lang);
        boolean isPt = "pt".equals(lang);
        boolean isRu = "ru".equals(lang);
        boolean isJa = "ja".equals(lang);
        boolean isZh = "zh".equals(lang);
        boolean isKo = "ko".equals(lang);
        boolean isPl = "pl".equals(lang);
        boolean isNl = "nl".equals(lang);
        boolean isTr = "tr".equals(lang);
        boolean isAr = "ar".equals(lang);
        boolean isHi = "hi".equals(lang);
        boolean isIn = "in".equals(lang) || "id".equals(lang);
        boolean isMs = "ms".equals(lang);
        boolean isVi = "vi".equals(lang);
        boolean isUk = "uk".equals(lang);
        boolean isSv = "sv".equals(lang);
        boolean isNb = "nb".equals(lang) || "no".equals(lang);
        boolean isDa = "da".equals(lang);
        boolean isFi = "fi".equals(lang);
        boolean isFil = "fil".equals(lang) || "tl".equals(lang);
        boolean isEl = "el".equals(lang);
        boolean isCs = "cs".equals(lang);
        boolean isSk = "sk".equals(lang);
        boolean isHu = "hu".equals(lang);
        boolean isRo = "ro".equals(lang);
        boolean isIw = "iw".equals(lang) || "he".equals(lang);
        boolean isHr = "hr".equals(lang);

        if ("video_err_hls".equals(key)) {
            if (isIt) return "Errore: formato streaming (HLS/DASH) non supportato direttamente";
            if (isEs) return "Error: formato de streaming (HLS/DASH) no admitido directamente";
            if (isFr) return "Erreur : format de streaming (HLS/DASH) non pris en charge directement";
            if (isDe) return "Fehler: Streaming-Format (HLS/DASH) wird nicht direkt unterstützt";
            if (isPt) return "Erro: formato de streaming (HLS/DASH) não suportado direttamente";
            if (isRu) return "Ошибка: потоковый формат (HLS/DASH) не поддерживается напрямую";
            if (isJa) return "エラー: ストリーミング形式 (HLS/DASH) は直接サポートされていません";
            if (isZh) return "错误：暂不支持直接下载流媒体格式 (HLS/DASH)";
            if (isKo) return "오류: 스트리밍 형식(HLS/DASH)은 직접 지원되지 않습니다";
            if (isPl) return "Błąd: format strumieniowy (HLS/DASH) nie jest bezpośrednio obsługiwany";
            if (isNl) return "Fout: streamingformaat (HLS/DASH) wordt niet direct ondersteund";
            if (isTr) return "Hata: akış formatı (HLS/DASH) doğrudan desteklenmiyor";
            if (isAr) return "خطأ: تنسيق البث (HLS/DASH) غير مدعوم مباشرة";
            if (isHi) return "त्रुटि: स्ट्रीमिंग प्रारूप (HLS/DASH) सीधे समर्थित नहीं है";
            if (isIn || isMs) return "Kesalahan: format streaming (HLS/DASH) tidak didukung secara langsung";
            if (isVi) return "Lỗi: định dạng phát trực tuyến (HLS/DASH) không được hỗ trợ trực tiếp";
            if (isUk) return "Помилка: потоковий формат (HLS/DASH) не підтримується напряму";
            if (isSv) return "Fel: strömmande format (HLS/DASH) stöds inte direkt";
            if (isNb) return "Feil: strømmeformat (HLS/DASH) støttes ikke direkte";
            if (isDa) return "Fejl: streamingformat (HLS/DASH) understøttes ikke direkte";
            if (isFi) return "Virhe: suoratoistomuotoa (HLS/DASH) ei tueta suoraan";
            if (isFil) return "Error: hindi direktang suportado ang streaming format (HLS/DASH)";
            if (isEl) return "Σφάλμα: η μορφή ροής (HLS/DASH) δεν υποστηρίζεται απευθείας";
            if (isCs) return "Chyba: formát streamování (HLS/DASH) není přímo podporován";
            if (isHu) return "Hiba: a streaming formátum (HLS/DASH) közvetlenül nem támogatott";
            if (isRo) return "Eroare: formatul de streaming (HLS/DASH) nu este acceptat direct";
            if (isIw) return "שגיאה: פורמט הזרמה (HLS/DASH) אינו נתמך ישירות";
            if (isHr) return "Greška: streaming format (HLS/DASH) nije izravno podržan";
            return "Error: streaming format (HLS/DASH) is not directly supported";
        }
        if ("video_err_no_mp4".equals(key)) {
            if (isIt) return "Errore: impossibile estrarre un link scaricabile (MP4/HLS/DASH) dai metadati";
            if (isEs) return "Error: no se pudo extraer un enlace descargable (MP4/HLS/DASH) de los metadatos";
            if (isFr) return "Erreur : impossible d'extraire un lien téléchargeable (MP4/HLS/DASH) des métadonnées";
            if (isDe) return "Fehler: kein herunterladbarer Link (MP4/HLS/DASH) aus den Metadaten extrahierbar";
            if (isPt) return "Erro: não foi possível extrair um link para download (MP4/HLS/DASH) dos metadados";
            if (isRu) return "Ошибка: не удалось извлечь ссылку для скачивания (MP4/HLS/DASH) из метаданных";
            if (isJa) return "エラー: メタデータからダウンロード可能なリンク (MP4/HLS/DASH) を抽出できませんでした";
            if (isZh) return "错误：无法从元数据中提取可下载链接 (MP4/HLS/DASH)";
            if (isKo) return "오류: 메타데이터에서 다운로드 가능한 링크(MP4/HLS/DASH)를 추출할 수 없습니다";
            if (isPl) return "Błąd: nie udało się wyodrębnić linku do pobrania (MP4/HLS/DASH) z metadanych";
            if (isNl) return "Fout: kan geen downloadbare link (MP4/HLS/DASH) uit metadata halen";
            if (isTr) return "Hata: meta verilerden indirilebilir bağlantı (MP4/HLS/DASH) ayıklanamadı";
            if (isAr) return "خطأ: تعذّر استخراج رابط قابل للتنزيل (MP4/HLS/DASH) من البيانات التعريفية";
            if (isHi) return "त्रुटि: मेटाडेटा से डाउनलोड करने योग्य लिंक (MP4/HLS/DASH) निकालने में विफल";
            if (isIn || isMs) return "Kesalahan: gagal mengekstrak tautan yang dapat diunduh (MP4/HLS/DASH) dari metadata";
            if (isVi) return "Lỗi: không thể trích xuất liên kết tải xuống (MP4/HLS/DASH) từ siêu dữ liệu";
            if (isUk) return "Помилка: не вдалося витягти посилання для завантаження (MP4/HLS/DASH) з метаданих";
            if (isSv) return "Fel: kunde inte extrahera en nedladdningsbar länk (MP4/HLS/DASH) från metadata";
            if (isNb) return "Feil: kunne ikke hente ut en nedlastbar lenke (MP4/HLS/DASH) fra metadata";
            if (isDa) return "Fejl: kunne ikke hente et downloadbart link (MP4/HLS/DASH) fra metadata";
            if (isFi) return "Virhe: ladattavaa linkkiä (MP4/HLS/DASH) ei voitu hakea metatiedoista";
            if (isFil) return "Error: bigong i-extract ang nada-download na link (MP4/HLS/DASH) mula sa metadata";
            if (isEl) return "Σφάλμα: αποτυχία εξαγωγής συνδέσμου λήψης (MP4/HLS/DASH) από τα μεταδεδομένα";
            if (isCs) return "Chyba: z metadat se nepodařilo extrahovat odkaz ke stažení (MP4/HLS/DASH)";
            if (isHu) return "Hiba: nem sikerült letölthető hivatkozást (MP4/HLS/DASH) kinyerni a metaadatokból";
            if (isRo) return "Eroare: nu s-a putut extrage un link descărcabil (MP4/HLS/DASH) din metadate";
            if (isIw) return "שגיאה: כשל בחילוץ קישור להורדה (MP4/HLS/DASH) מהמטא-דאטה";
            if (isHr) return "Greška: nije moguće izdvojiti poveznicu za preuzimanje (MP4/HLS/DASH) iz metapodataka";
            return "Error: failed to extract a downloadable link (MP4/HLS/DASH) from metadata";
        }
        if ("video_err_no_tracking".equals(key)) {
            if (isIt) return "Errore: nessun tracciamento video in memoria";
            if (isEs) return "Error: no hay seguimiento de video en memoria";
            if (isFr) return "Erreur : aucun suivi vidéo en mémoire";
            if (isDe) return "Fehler: keine Videoverfolgung im Speicher";
            if (isPt) return "Erro: sem rastreamento de vídeo na memória";
            if (isRu) return "Ошибка: в памяти нет отслеживаемого видео";
            if (isJa) return "エラー: メモリ内に追跡された動画がありません";
            if (isZh) return "错误：内存中没有缓存视频信息";
            if (isKo) return "오류: 메모리에 추적된 동영상이 없습니다";
            if (isPl) return "Błąd: brak śledzenia wideo w pamięci";
            if (isNl) return "Fout: geen videotracking in geheugen";
            if (isTr) return "Hata: bellekte izlenen video yok";
            if (isAr) return "خطأ: لا يوجد تتبع للفيديو في الذاكرة";
            if (isHi) return "त्रुटि: मेमोरी में कोई ट्रैक किया गया वीडियो नहीं है";
            if (isIn || isMs) return "Kesalahan: tidak ada pelacakan video di memori";
            if (isVi) return "Lỗi: không có theo dõi video trong bộ nhớ";
            if (isUk) return "Помилка: немає відстежуваного відео в пам'яті";
            if (isSv) return "Fel: ingen videospårning i minnet";
            if (isNb) return "Feil: ingen videosporing i minnet";
            if (isDa) return "Fejl: ingen videosporing i hukommelsen";
            if (isFi) return "Virhe: videoseurantaa ei ole muistissa";
            if (isFil) return "Error: walang natunton na video sa memorya";
            if (isEl) return "Σφάλμα: δεν υπάρχει παρακολούθηση βίντεο στη μνήμη";
            if (isCs) return "Chyba: v paměti není sledováno žádné video";
            if (isHu) return "Hiba: nincs videó nyomon követése a memóriában";
            if (isRo) return "Eroare: nu există urmărire video în memorie";
            if (isIw) return "שגיאה: אין מעקב וידאו בזיכרון";
            if (isHr) return "Greška: nema praćenja videozapisa u memoriji";
            return "Error: no video tracking in memory";
        }
        if ("video_err_image_post".equals(key)) {
            if (isIt) return "Errore: questo post è un'immagine statica";
            if (isEs) return "Error: esta publicación es una imagen estática";
            if (isFr) return "Erreur : cette publication est une image statique";
            if (isDe) return "Fehler: Dieser Beitrag ist ein statisches Bild";
            if (isPt) return "Erro: esta publicação é uma imagem estática";
            if (isRu) return "Ошибка: эта публикация является статичным изображением";
            if (isJa) return "エラー: この投稿は静止画です";
            if (isZh) return "错误：此帖子是一张静态图片";
            if (isKo) return "오류: 이 게시물은 정적 이미지입니다";
            if (isPl) return "Błąd: ten post to statyczny obraz";
            if (isNl) return "Fout: dit bericht is een statische afbeelding";
            if (isTr) return "Hata: bu gönderi statik bir resim";
            if (isAr) return "خطأ: هذا المنشور عبارة عن صورة ثابتة";
            if (isHi) return "त्रुटि: यह पोस्ट एक स्थिर छवि है";
            if (isIn || isMs) return "Kesalahan: postingan ini adalah gambar statis";
            if (isVi) return "Lỗi: bài đăng này là một hình ảnh tĩnh";
            if (isUk) return "Помилка: ця публікація є статичним зображенням";
            if (isSv) return "Fel: det här inlägget är en statisk bild";
            if (isNb) return "Feil: dette innlegget er et statisk bilde";
            if (isDa) return "Fejl: dette opslag er et statisk billede";
            if (isFi) return "Virhe: tämä julkaisu on staattinen kuva";
            if (isFil) return "Error: ang post na ito ay isang statikong larawan";
            if (isEl) return "Σφάλμα: αυτή η ανάρτηση είναι μια στατική εικόνα";
            if (isCs) return "Chyba: tento příspěvek je statický obrázek";
            if (isHu) return "Hiba: ez a bejegyzés egy statikus kép";
            if (isRo) return "Eroare: această postare este o imagine statică";
            if (isIw) return "שגיאה: פוست זה הוא תמונה סטטית";
            if (isHr) return "Greška: ova objava je statična slika";
            return "Error: this post is a static image";
        }
        return "";
    }
}
