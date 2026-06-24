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
public final class WallpaperUtils {

    private static final String TAG = "MorpheWallpaper";

    private WallpaperUtils() {}

    public static volatile String currentPinImageUrl = null;

    public static volatile Bitmap currentPinBitmap = null;

    /**
     * URL del video del Pin attualmente in riproduzione. Impostato dall'hook su
     * PinterestVideoView.u() e azzerato a ogni cambio Pin (setCurrentPinView), così la voce
     * "Scarica video" compare solo sui Pin che sono effettivamente dei video.
     * volatile: scritto dal thread UI, letto dal thread di download.
     */
    public static volatile String currentVideoUrl = null;

    public static void setCurrentPinImageUrl(String url) {
        if (url != null && !url.isEmpty()) {
            currentPinImageUrl = url;
        }
    }

    public static void setCurrentPinView(Object view, Bitmap bitmap) {
        // Nuovo Pin in primo piano: azzera l'URL video stantio. L'hook sul player lo reimposta
        // subito dopo se questo Pin contiene un video.
        currentVideoUrl = null;
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

    /**
     * Riceve l'oggetto VideoMetadata (cu2.l) dall'hook su PinterestVideoView.u() ed estrae l'URL
     * del video. Scansiona i campi String dell'oggetto cercando un URL http(s): cercare per nome di
     * campo sarebbe fragile rispetto all'offuscamento, mentre la scansione per tipo+valore è robusta.
     * Preferisce un .mp4 (scaricabile direttamente) rispetto a eventuali playlist .m3u8.
     */
    public static void setCurrentVideoMetadata(Object metadata) {
        if (metadata == null) {
            return;
        }
        try {
            String fallback = null;
            Class<?> clazz = metadata.getClass();
            while (clazz != null) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        f.setAccessible(true);
                        Object value = f.get(metadata);
                        if (value instanceof String) {
                            String s = (String) value;
                            if (s.startsWith("http") && (s.contains(".mp4") || s.contains(".m3u8") || s.contains("/video"))) {
                                if (s.contains(".mp4")) {
                                    currentVideoUrl = s;
                                    Log.d(TAG, "URL video catturato: " + s);
                                    return;
                                }
                                if (fallback == null) {
                                    fallback = s;
                                }
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            if (fallback != null) {
                currentVideoUrl = fallback;
                Log.d(TAG, "URL video catturato (fallback): " + fallback);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Cattura URL video fallita", t);
        }
    }

    private static String getString(String key) {
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
        boolean isHu = "hu".equals(lang);
        boolean isRo = "ro".equals(lang);
        boolean isSk = "sk".equals(lang);
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
            if (isNl) return "Directe link kopiëren";
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
            if (isPt) return "Nenhum vídeo disponível para este pin";
            if (isRu) return "Видео недоступно для этого пина";
            if (isJa) return "このピンに動画はありません";
            if (isZh) return "此Pin图没有可用视频";
            if (isKo) return "이 핀에 사용할 수 있는 동영상이 없습니다";
            if (isNl) return "Geen video beschikbaar voor deze pin";
            if (isTr) return "Bu pin için video yok";
            if (isAr) return "لا يوجد فيديو متاح لهذا الدبوس";
            return "No video available for this pin";
        }
        if ("link_copied".equals(key)) {
            if (isIt) return "Link copiato ✓";
            if (isEs) return "Enlace copiado ✓";
            if (isFr) return "Lien copié ✓";
            if (isDe) return "Link kopiert ✓";
            if (isPt) return "Link copiado ✓";
            if (isRu) return "Ссылка скопирована ✓";
            if (isJa) return "リンクをコピーしました ✓";
            if (isZh) return "链接已复制 ✓";
            if (isKo) return "링크가 복사되었습니다 ✓";
            if (isPl) return "Skopiowano link ✓";
            if (isNl) return "Link gekopieerd ✓";
            if (isTr) return "Bağlantı kopyalandı ✓";
            if (isAr) return "تم نسخ الرابط ✓";
            return "Link copied ✓";
        }
        if ("direct_link_copied".equals(key)) {
            if (isIt) return "Link diretto copiato.";
            if (isEs) return "Enlace directo copiado.";
            if (isFr) return "Lien direct copié.";
            if (isDe) return "Direkter Link kopiert.";
            if (isPt) return "Link direto copiado.";
            if (isRu) return "Прямая ссылка скопирована.";
            if (isJa) return "直接リンクをコピーしました。";
            if (isZh) return "直链已复制。";
            if (isKo) return "직접 링크가 복사되었습니다.";
            if (isPl) return "Skopiowano bezpośredni link.";
            if (isNl) return "Directe link gekopieerd.";
            if (isTr) return "Doğrudan bağlantı kopyalandı.";
            if (isAr) return "تم نسخ الرابط المباشر.";
            if (isHi) return "सीधा लिंक कॉपी किया गया.";
            if (isIn || isMs) return "Tautan langsung disalin.";
            if (isVi) return "Đã sao chép liên kết trực tiếp.";
            if (isUk) return "Пряме посилання скопійовано.";
            if (isSv) return "Direktlänk kopierad.";
            if (isNb) return "Direkte lenke kopiert.";
            if (isDa) return "Direkte link kopieret.";
            if (isFi) return "Suora linkki kopioitu.";
            if (isFil) return "Nakopya ang direktang link.";
            if (isEl) return "Το απευθείας link αντιγράφηκε.";
            if (isCs || isSk) return "Přímý odkaz zkopírován.";
            if (isHu) return "Közvetlen link másolva.";
            if (isRo) return "Link direct copiat.";
            if (isIw) return "הקישור הישיר הועתק.";
            if (isHr) return "Izravna poveznica je kopirana.";
            return "Direct link copied.";
        }
        if ("no_link".equals(key)) {
            if (isIt) return "Nessun link disponibile per questo pin";
            if (isEs) return "No hay enlace disponible para este pin";
            if (isFr) return "Aucun lien disponible pour ce pin";
            if (isDe) return "Kein Link für diesen Pin verfügbar";
            if (isPt) return "Nenhum link disponível para este pin";
            if (isRu) return "Ссылка недоступна для этого пина";
            if (isJa) return "このピンのリンクはありません";
            if (isZh) return "此Pin图没有可用链接";
            if (isKo) return "이 핀에 사용할 수 있는 링크가 없습니다";
            if (isPl) return "Brak dostępnego linku dla tego pina";
            if (isNl) return "Geen link beschikbaar voor deze pin";
            if (isTr) return "Bu pin için kullanılabilir bağlantı yok";
            if (isAr) return "لا يوجد رابط متاح لهذا الدبوس";
            return "No link available for this pin";
        }

        if ("label".equals(key)) {
            if (isIt) return "Imposta come sfondo";
            if (isEs) return "Establecer como fondo de pantalla";
            if (isFr) return "Définir comme fond d'écran";
            if (isDe) return "Als Hintergrundbild festlegen";
            if (isPt) return "Definir como papel de parede";
            if (isRu) return "Установить как обои";
            if (isJa) return "壁紙に設定";
            if (isZh) return "设为壁纸";
            if (isKo) return "배경화면으로 설정";
            if (isPl) return "Ustaw jako tapetę";
            if (isNl) return "Als achtergrond instellen";
            if (isTr) return "Duvar kağıdı olarak ayarla";
            if (isAr) return "تعيين كخلفية";
            if (isHi) return "वॉलपेपर के रूप में सेट करें";
            if (isIn || isMs) return "Atur sebagai wallpaper";
            if (isVi) return "Đặt làm hình nền";
            if (isUk) return "Встановити як шпалери";
            if (isSv) return "Ange som bakgrundsbild";
            if (isNb) return "Bruk som bakgrunnsbilde";
            if (isDa) return "Indstil som baggrund";
            if (isFi) return "Aseta taustakuvaksi";
            if (isFil) return "Gawing wallpaper";
            if (isEl) return "Ορισμός ως ταπετσαρία";
            if (isCs || isSk) return "Nastavit jako tapetu";
            if (isHu) return "Beállítás háttérképként";
            if (isRo) return "Setează ca fundal";
            if (isIw) return "הגדר כרקע";
            if (isHr) return "Postavi kao pozadinu";
            return "Set as wallpaper";
        }
        if ("no_image".equals(key)) {
            if (isIt) return "Nessuna immagine disponibile per questo pin";
            if (isEs) return "No hay imagen disponible para este pin";
            if (isFr) return "Aucune image disponibile pour ce pin";
            if (isDe) return "Kein Bild für diesen Pin verfügbar";
            if (isPt) return "Nenhuma imagem disponível para este pin";
            if (isRu) return "Изображение недоступно для этого пина";
            if (isJa) return "このピンの画像はありません";
            if (isZh) return "此Pin图没有可用图片";
            if (isKo) return "이 핀에 사용할 수 있는 이미지가 없습니다";
            if (isPl) return "Brak dostępnego obrazu dla tego pina";
            if (isNl) return "Geen afbeelding beschikbaar voor deze pin";
            if (isTr) return "Bu pin için kullanılabilir resim yok";
            if (isAr) return "لا توجد صورة متاحة لهذا الدبوس";
            if (isHi) return "इस पिन के लिए कोई छवि उपलब्ध नहीं है";
            if (isIn || isMs) return "Tidak ada gambar untuk pin ini";
            if (isVi) return "Không có ảnh cho ghim này";
            if (isUk) return "Немає доступного зображення для цього піна";
            if (isSv) return "Ingen bild tillgänglig för den här nålen";
            if (isNb) return "Ingen bilde tilgjengelig for denne pin-koden";
            if (isDa) return "Intet billede tilgængeligt for denne pin";
            if (isFi) return "Kuvaa ei ole saatavilla tälle pinnille";
            if (isFil) return "Walang available na larawan para sa pin na ito";
            if (isEl) return "Δεν υπάρχει διαθέσιμη εικόνα για αυτήν την καρφίτσα";
            if (isCs || isSk) return "Pro tento pin není k dispozici žádný obrázek";
            if (isHu) return "Nem érhető el kép ehhez a pinhez";
            if (isRo) return "Nicio imagine disponibilă pentru acest pin";
            if (isIw) return "אין תמונה זמינה עבור סיכה זו";
            if (isHr) return "Nema dostupne slike za ovaj pribadač";
            return "No image available for this pin";
        }
        if ("downloading".equals(key)) {
            if (isIt) return "Scarico l'immagine…";
            if (isEs) return "Descargando imagen…";
            if (isFr) return "Téléchargement de l'image…";
            if (isDe) return "Bild wird heruntergeladen…";
            if (isPt) return "Baixando imagem…";
            if (isRu) return "Загрузка изображения…";
            if (isJa) return "画像をダウンロード중…";
            if (isZh) return "正在下载图片…";
            if (isKo) return "이미지 다운로드 중…";
            if (isPl) return "Pobieranie obrazu…";
            if (isNl) return "Afbeelding downloaden…";
            if (isTr) return "Resim indiriliyor…";
            if (isAr) return "جاري تنزيل الصورة…";
            if (isHi) return "छवि डाउनलोड हो रही है…";
            if (isIn || isMs) return "Mengunduh gambar…";
            if (isVi) return "Đã đặt hình nền…";
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
            if (isPt) return "Não foi possível definir o papel de parede";
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
            if (isIw) return "הגדרת הרקע נכשלה";
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
            if (isCs || isSk) return "Uzamknutá obrazovka";
            if (isHu) return "Zárolási képernyő";
            if (isRo) return "Ecran de blocare";
            if (isIw) return "מסך הנעילה";
            if (isHr) return "Zaslon zaključavanja";
            return "Lock screen";
        }
        if ("option_both".equals(key)) {
            if (isIt) return "Entrambi";
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
            if (isAr) return "كلتاهما";
            if (isHi) return "दोनों";
            if (isIn || isMs) return "Keduanya";
            if (isVi) return "Cả hai";
            if (isUk) return "Обидва";
            if (isSv) return "Båda";
            if (isNb || isDa) return "Begge";
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
        return "";
    }


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
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMenu();
                    showWallpaperDialog(v.getContext());
                }
            };
            try {
                row = buildRowReflective(container, labelText, "IMAGE", onClickListener);
                Log.d(TAG, "Riga sfondo creata con successo tramite reflection");
            } catch (Throwable t) {
                Log.w(TAG, "Errore nella creazione sfondo tramite reflection, uso il fallback", t);
                row = buildRowFallback(context, labelText, container, android.R.drawable.ic_menu_gallery, onClickListener);
            }
            if (row != null) {
                container.addView(row);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce sfondo", t);
        }
    }

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

    public static void addDownloadVideoOption(Object menuContainer) {
        if (!(menuContainer instanceof ViewGroup)) {
            Log.w(TAG, "menuContainer non è un ViewGroup: " + menuContainer);
            return;
        }
        // La voce compare SOLO se il Pin corrente è un video: l'hook sul player ha popolato
        // currentVideoUrl, che setCurrentPinView azzera a ogni cambio Pin.
        final String videoUrl = currentVideoUrl;
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }
        final ViewGroup container = (ViewGroup) menuContainer;
        final Context context = container.getContext();

        try {
            View row = null;
            String labelText = getString("download_video_label");
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMenu();
                    downloadVideo(v.getContext());
                }
            };
            try {
                row = buildRowReflective(container, labelText, "ARROW_DOWN", onClickListener);
                Log.d(TAG, "Riga scarica video creata con successo tramite reflection");
            } catch (Throwable t) {
                Log.w(TAG, "Errore nella creazione scarica video tramite reflection, uso il fallback", t);
                row = buildRowFallback(context, labelText, container, android.R.drawable.ic_menu_save, onClickListener);
            }
            if (row != null) {
                container.addView(row);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Impossibile aggiungere la voce scarica video", t);
        }
    }

    /**
     * Scarica il video corrente nella cartella Download tramite il DownloadManager di sistema.
     *
     * Si usa il DownloadManager (anziché HttpURLConnection a mano) perché gestisce da solo il thread
     * di rete, i redirect, la notifica di avanzamento e la registrazione del file in MediaStore su
     * Android 10+ (scoped storage) — eliminando gli errori "impossibile scaricare" del tentativo
     * precedente, dovuti a download sul main thread / permessi di storage.
     */
    private static void downloadVideo(Context context) {
        final String url = currentVideoUrl;
        if (url == null || url.isEmpty()) {
            showNativeToast(context, getString("no_video"));
            return;
        }
        try {
            android.app.DownloadManager dm =
                (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                showNativeToast(context, getString("failed"));
                return;
            }
            String fileName = "pinterest_" + System.currentTimeMillis() + ".mp4";
            android.app.DownloadManager.Request request =
                new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("Pinterest");
            request.setMimeType("video/mp4");
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            // Diversi CDN Pinterest rispondono 403 senza uno User-Agent da browser.
            request.addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36");
            dm.enqueue(request);
            showNativeToast(context, getString("video_download_started"));
        } catch (Throwable t) {
            Log.e(TAG, "Download video fallito", t);
            showNativeToast(context, getString("failed"));
        }
    }

    private static View buildRowReflective(ViewGroup container, String labelText, String iconEnumName, View.OnClickListener onClickListener) throws Exception {
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

    private static View buildRowFallback(Context context, String labelText, ViewGroup container, int iconResId, View.OnClickListener onClickListener) {
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

    private static void showWallpaperDialog(final Context context) {
        final Bitmap captured = currentPinBitmap;
        final String url = currentPinImageUrl;

        if ((captured == null || captured.isRecycled()) && (url == null || url.isEmpty())) {
            showNativeToast(context, getString("no_image"));
            return;
        }

        final String[] options = {
            getString("option_home"),
            getString("option_lock"),
            getString("option_both")
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
            rootLayout.setBackgroundColor(0x99000000); // 60% opacity black dim

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            
            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(bgColor);
            cardBackground.setCornerRadius(dp(context, 24));
            card.setBackground(cardBackground);
            
            int cardPadding = dp(context, 24);
            card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);

            TextView titleView = new TextView(context);
            titleView.setText(getString("dialog_title"));
            titleView.setTextColor(titleColor);
            titleView.setTextSize(20);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleLp.bottomMargin = dp(context, 16);
            card.addView(titleView, titleLp);

            for (int i = 0; i < options.length; i++) {
                final int index = i;
                
                final LinearLayout optionView = new LinearLayout(context);
                optionView.setOrientation(LinearLayout.HORIZONTAL);
                optionView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                optionView.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
                optionView.setClickable(true);
                optionView.setFocusable(true);

                ImageView iconView = new ImageView(context);
                iconView.setImageDrawable(createOptionIcon(context, index, textColor, bgColor));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
                iconLp.rightMargin = dp(context, 16);
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
                    itemShape.setCornerRadius(dp(context, 12)); 
                    
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
                    pressedShape.setCornerRadius(dp(context, 12));
                    
                    GradientDrawable normalShape = new GradientDrawable();
                    normalShape.setColor(bgColor);
                    normalShape.setCornerRadius(dp(context, 12));
                    
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
                itemLp.bottomMargin = dp(context, 4); 
                card.addView(optionView, itemLp);
            }

            RelativeLayout.LayoutParams cardLp = new RelativeLayout.LayoutParams(
                dp(context, 300), 
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
            showNativeToast(context, getString("no_image"));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (applyWallpaper(context, bitmap, flags)) {
                    showNativeToast(context, getString("success"));
                } else {
                    showNativeToast(context, getString("failed"));
                }
            }
        }, "morphe-set-wallpaper-bmp").start();
    }


    public static void setWallpaperFromUrl(final Context context, final String url, final int flags) {
        final Handler main = new Handler(Looper.getMainLooper());

        if (url == null || url.isEmpty()) {
            showNativeToast(context, getString("no_image"));
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
                        showNativeToast(context, getString("invalid_image"));
                        return;
                    }

                    if (applyWallpaper(context, bitmap, flags)) {
                        showNativeToast(context, getString("success"));
                    } else {
                        showNativeToast(context, getString("failed"));
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "setWallpaperFromUrl fallito per " + url, t);
                    showNativeToast(context, getString("failed"));
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

    private static void toast(Handler main, final Context context, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void dismissMenu() {
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

    private static void showNativeToast(final Context context, final String message) {
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

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static Drawable createOptionIcon(Context context, int index, int color, int bgColor) {
        int size = dp(context, 24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(context, 2));
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
            canvas.drawRoundRect(body, dp(context, 3), dp(context, 3), paint);
            
            RectF shackle = new RectF(size * 0.34f, size * 0.18f, size * 0.66f, size * 0.52f);
            canvas.drawArc(shackle, 180, 180, false, paint);
            
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size * 0.5f, size * 0.6f, dp(context, 2), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(size * 0.5f, size * 0.6f + dp(context, 2), size * 0.5f, size * 0.72f, paint);
        } else { 
            RectF screen1 = new RectF(size * 0.18f, size * 0.18f, size * 0.52f, size * 0.68f);
            canvas.drawRoundRect(screen1, dp(context, 3), dp(context, 3), paint);
            
            RectF screen2 = new RectF(size * 0.48f, size * 0.32f, size * 0.82f, size * 0.82f);

            Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clearPaint.setColor(bgColor);
            clearPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(screen2, dp(context, 3), dp(context, 3), clearPaint);
            
            canvas.drawRoundRect(screen2, dp(context, 3), dp(context, 3), paint);
            
            canvas.drawLine(size * 0.58f, size * 0.76f, size * 0.72f, size * 0.76f, paint);
        }

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    /**
     * Rimuove i Pin sponsorizzati (promoted) dalla pagina di feed appena costruita.
     *
     * Chiamato dalla patch bytecode in coda al costruttore di o12.e (il modello di pagina del feed),
     * che memorizza una COPIA ArrayList mutabile degli elementi: la attraversiamo e togliamo i Pin
     * il cui flag is_promoted è true. Agendo sul costruttore copriamo primo caricamento, paginazione
     * e pull-to-refresh con un unico aggancio. Eventuali errori vengono assorbiti: nel peggiore dei
     * casi l'annuncio resta, ma il feed non va mai in crash.
     */
    public static void filterSponsoredPinsFromFeed(Object feedPage) {
        if (feedPage == null) {
            return;
        }
        try {
            java.util.List<?> items = null;
            Class<?> clazz = feedPage.getClass();
            while (clazz != null && items == null) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object value = f.get(feedPage);
                        if (value instanceof java.util.List) {
                            items = (java.util.List<?>) value;
                            break;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            if (items == null || items.isEmpty()) {
                return;
            }
            int removed = 0;
            java.util.Iterator<?> it = items.iterator();
            while (it.hasNext()) {
                if (isPromotedPin(it.next())) {
                    try {
                        it.remove();
                        removed++;
                    } catch (Throwable ignored) {
                        // lista non modificabile: lascia l'elemento, niente crash
                    }
                }
            }
            if (removed > 0) {
                Log.d(TAG, "Rimossi " + removed + " pin sponsorizzati dal feed");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Filtro ads del feed fallito", t);
        }
    }

    /**
     * True se l'elemento del feed è un Pin promosso. Identifichiamo il Pin chiamando in reflection
     * il getter is_promoted `me.I5()` (lasciato intatto dalla patch, così riporta il valore reale);
     * gli elementi che non sono Pin non hanno quel metodo → eccezione catturata → mantenuti.
     */
    private static boolean isPromotedPin(Object item) {
        if (item == null) {
            return false;
        }
        try {
            java.lang.reflect.Method m = item.getClass().getMethod("I5");
            Object result = m.invoke(item);
            return (result instanceof Boolean) && ((Boolean) result);
        } catch (Throwable t) {
            return false;
        }
    }

    public static byte[] getSignatureBytes(android.content.pm.Signature sig) {
        try {
            return new android.content.pm.Signature("3082024f308201b8a00302010202044f96d518300d06092a864886f70d0101050500306c310b3009060355040613025553310b3009060355040813024341311230100603550407130950616c6f20416c746f31163014060355040a130d50696e74657265737420496e633110300e060355040b1307416e64726f696431123010060355040313094361726c2052696365301e170d3132303432343136333031365a170d3337303431383136333031365a306c310b3009060355040613025553310b3009060355040813024341311230100603550407130950616c6f20416c746f31163014060355040a130d50696e74657265737420496e633110300e060355040b1307416e64726f696431123010060355040313094361726c205269636530819f300d06092a864886f70d010101050003818d0030818902818100bd8b325a2eb8ade0e16e44971e75130ec98f2c37c8a477044382a1c5c18aa3078bede3c1a49776441617f3bb6711d1a7d764785ea20bf8c694d78fdc82d575f88f340fc87b948558385636f80dba536481a9c8bf03505781adbbca1ef65b2f59281ca92e352d9f685d04024c19cb3b4e3e14e6eb69ca113e55b55d766ea860170203010001300d06092a864886f70d0101050500038181009e6766c1071e383b75c520221b502e4701d7a110933a9fe7e7417679be71581ad24a09c42bb5190acfb7e487969f843a634eac015424adc4380cdc0eb21b47616b4459f11a018b4f5185bfb75764d95c1d8bd01c21932911578a3406caf8d317bc65f2d4d5caef1b59e59ed695e235a672460b2ccff2d0a8f3c3b2604c599714").toByteArray();
        } catch (Exception e) {
            Log.e("MorpheSignature", "Errore nel spoofing della firma", e);
            return sig != null ? sig.toByteArray() : new byte[0];
        }
    }
}
