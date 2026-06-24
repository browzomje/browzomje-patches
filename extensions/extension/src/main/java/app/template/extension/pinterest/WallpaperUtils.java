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

    /** Chiamato dall'hook che intercetta il view e il bitmap del pin aperto. */
    public static void setCurrentPinView(Object view, Bitmap bitmap) {
        if (bitmap != null) {
            currentPinBitmap = bitmap;
        }
        if (view != null) {
            try {
                // Scansione riflessiva dei campi String per trovare l'URL dell'immagine in modo robusto rispetto all'offuscamento
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
     * Helper per ottenere le stringhe localizzate in base alla lingua del dispositivo.
     */
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
            if (isFr) return "Aucune image disponible pour ce pin";
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
            if (isJa) return "画像をダウンロード中…";
            if (isZh) return "正在下载图片…";
            if (isKo) return "이미지 다운로드 중…";
            if (isPl) return "Pobieranie obrazu…";
            if (isNl) return "Afbeelding downloaden…";
            if (isTr) return "Resim indiriliyor…";
            if (isAr) return "جاري تنزيل الصورة…";
            if (isHi) return "छवि डाउनलोड हो रही है…";
            if (isIn || isMs) return "Mengunduh gambar…";
            if (isVi) return "Đang tải ảnh xuống…";
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
            if (isIt) return "Sfondo impostato ✓";
            if (isEs) return "Fondo de pantalla establecido ✓";
            if (isFr) return "Fond d'écran défini ✓";
            if (isDe) return "Hintergrundbild festgelegt ✓";
            if (isPt) return "Papel de parede definido ✓";
            if (isRu) return "Обои установлены ✓";
            if (isJa) return "壁紙を設定しました ✓";
            if (isZh) return "壁纸设置成功 ✓";
            if (isKo) return "배경화면 설정 완료 ✓";
            if (isPl) return "Tapeta została ustawiona ✓";
            if (isNl) return "Achtergrond ingesteld ✓";
            if (isTr) return "Duvar kağıdı ayarlandı ✓";
            if (isAr) return "تم تعيين الخلفية ✓";
            if (isHi) return "वॉलपेपर सेट हो गया ✓";
            if (isIn || isMs) return "Wallpaper diatur ✓";
            if (isVi) return "Đã đặt hình nền ✓";
            if (isUk) return "Шпалери встановлено ✓";
            if (isSv) return "Bakgrundsbild ändrad ✓";
            if (isNb) return "Bakgrunnsbilde satt ✓";
            if (isDa) return "Baggrund indstillet ✓";
            if (isFi) return "Taustakuva asetettu ✓";
            if (isFil) return "Naitakda ang wallpaper ✓";
            if (isEl) return "Η ταπετσαρία ορίστηκε ✓";
            if (isCs || isSk) return "Tapeta nastavena ✓";
            if (isHu) return "Háttérkép beállítva ✓";
            if (isRo) return "Fundal setat ✓";
            if (isIw) return "הרקע הוגדר ✓";
            if (isHr) return "Pozadina postavljena ✓";
            return "Wallpaper set ✓";
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
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWallpaperDialog(v.getContext());
                }
            };
            try {
                // Tenta la costruzione tramite reflection per utilizzare i componenti Gestalt nativi di Pinterest
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
                    copyLinkToClipboard(v.getContext());
                }
            };
            try {
                // Tenta la costruzione tramite reflection per utilizzare i componenti Gestalt nativi di Pinterest
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
        Handler main = new Handler(Looper.getMainLooper());
        if (url == null || url.isEmpty()) {
            toast(main, context, getString("no_link"));
            return;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Pinterest Direct Link", url);
            clipboard.setPrimaryClip(clip);
            toast(main, context, getString("link_copied"));
        } catch (Throwable t) {
            Log.e(TAG, "Copia negli appunti fallita", t);
            toast(main, context, getString("failed"));
        }
    }

    /** Costruisce la riga del menu usando reflection per chiamare le API native di Pinterest */
    private static View buildRowReflective(ViewGroup container, String labelText, String iconEnumName, View.OnClickListener onClickListener) throws Exception {
        // 1. Ottiene il viewCreator (uz0.c) chiamando D() su uz0.z (container)
        Method dMethod = container.getClass().getMethod("D");
        Object viewCreator = dMethod.invoke(container);

        // 2. Carica la classe enum delle icone ku1.x e ottiene la costante desiderata
        Class<?> xClass = Class.forName("ku1.x");
        Object imageIcon = Enum.valueOf((Class<Enum>) xClass, iconEnumName);

        // 3. Ottiene il valore del campo booleano B da uz0.z
        Field bField = container.getClass().getField("B");
        boolean z9 = bField.getBoolean(container);

        // 4. Ottiene e invoca il metodo a(CharSequence, String, ku1.x, boolean) su uz0.c
        Method aMethod = viewCreator.getClass().getMethod("a", CharSequence.class, String.class, xClass, boolean.class);
        RelativeLayout row = (RelativeLayout) aMethod.invoke(viewCreator, labelText, null, imageIcon, z9);

        // 5. Imposta il click listener sulla riga
        row.setOnClickListener(onClickListener);
        return row;
    }

    /** Costruisce una riga cliccabile di fallback se la reflection fallisce (copia lo stile da un fratello) */
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
            // Rileva modalità notte per i colori di Pinterest
            boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            final int bgColor = isDark ? 0xFF212121 : 0xFFFFFFFF;
            final int textColor = isDark ? 0xFFFFFFFF : 0xFF111111;
            final int titleColor = isDark ? 0xFFFFFFFF : 0xFF111111;
            final int pressedColor = isDark ? 0xFF3D3D3D : 0xFFF0F0F0;

            final Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);

            // Layout principale a schermo intero (sfondo semi-trasparente)
            RelativeLayout rootLayout = new RelativeLayout(context);
            rootLayout.setBackgroundColor(0x99000000); // 60% opacity black dim

            // Dialog Card
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            
            // Sfondo arrotondato per la card (Pinterest style)
            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(bgColor);
            cardBackground.setCornerRadius(dp(context, 24)); // Bordi molto stondati stile Pinterest
            card.setBackground(cardBackground);
            
            // Padding interno
            int cardPadding = dp(context, 24);
            card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);

            // Titolo
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

            // Opzioni
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                
                final LinearLayout optionView = new LinearLayout(context);
                optionView.setOrientation(LinearLayout.HORIZONTAL);
                optionView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                optionView.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
                optionView.setClickable(true);
                optionView.setFocusable(true);

                // Aggiunge icona monocromatica prima di ogni voce
                ImageView iconView = new ImageView(context);
                iconView.setImageDrawable(createOptionIcon(context, index, textColor, bgColor));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
                iconLp.rightMargin = dp(context, 16);
                optionView.addView(iconView, iconLp);

                // Aggiunge il testo
                TextView textView = new TextView(context);
                textView.setText(options[index]);
                textView.setTextColor(textColor);
                textView.setTextSize(16);
                textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                optionView.addView(textView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                // Drawable di sfondo per il click (ripple o fallback color)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GradientDrawable itemShape = new GradientDrawable();
                    itemShape.setColor(bgColor);
                    itemShape.setCornerRadius(dp(context, 12)); // Bordi arrotondati anche per gli item premuti
                    
                    ColorStateList rippleColor = ColorStateList.valueOf(pressedColor);
                    RippleDrawable ripple = new RippleDrawable(
                        rippleColor,
                        null, // Content (null means it draws on the background)
                        itemShape // Mask (constrains ripple to rounded shape)
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
                itemLp.bottomMargin = dp(context, 4); // Piccolo spazio tra i pulsanti
                card.addView(optionView, itemLp);
            }

            // Aggiunge la card al layout radice, centrata
            RelativeLayout.LayoutParams cardLp = new RelativeLayout.LayoutParams(
                dp(context, 300), // Larghezza fissa per somigliare a un dialog di Pinterest
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.addRule(RelativeLayout.CENTER_IN_PARENT);
            rootLayout.addView(card, cardLp);

            // Chiude il dialog cliccando sullo sfondo oscurato
            rootLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            // Ma impedisce la chiusura cliccando all'interno della card
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Do nothing
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

        if (index == 0) { // Home Screen: clean modern house outline
            Path path = new Path();
            // Roof
            path.moveTo(size * 0.5f, size * 0.18f);
            path.lineTo(size * 0.18f, size * 0.46f);
            path.lineTo(size * 0.82f, size * 0.46f);
            path.close();
            // Walls
            path.moveTo(size * 0.26f, size * 0.46f);
            path.lineTo(size * 0.26f, size * 0.82f);
            path.lineTo(size * 0.74f, size * 0.82f);
            path.lineTo(size * 0.74f, size * 0.46f);
            canvas.drawPath(path, paint);
            // Door
            Path door = new Path();
            door.moveTo(size * 0.44f, size * 0.82f);
            door.lineTo(size * 0.44f, size * 0.62f);
            door.lineTo(size * 0.56f, size * 0.62f);
            door.lineTo(size * 0.56f, size * 0.82f);
            canvas.drawPath(door, paint);
        } else if (index == 1) { // Lock Screen: body + shackle + keyhole
            // Body (rounded rect)
            RectF body = new RectF(size * 0.25f, size * 0.46f, size * 0.75f, size * 0.82f);
            canvas.drawRoundRect(body, dp(context, 3), dp(context, 3), paint);
            // Shackle
            RectF shackle = new RectF(size * 0.34f, size * 0.18f, size * 0.66f, size * 0.52f);
            canvas.drawArc(shackle, 180, 180, false, paint);
            // Keyhole pin
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size * 0.5f, size * 0.6f, dp(context, 2), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(size * 0.5f, size * 0.6f + dp(context, 2), size * 0.5f, size * 0.72f, paint);
        } else { // Both: overlapping home and lock screen elements or two vertical phone screen outlines
            // Screen 1 (back, shifted left/top)
            RectF screen1 = new RectF(size * 0.18f, size * 0.18f, size * 0.52f, size * 0.68f);
            canvas.drawRoundRect(screen1, dp(context, 3), dp(context, 3), paint);
            
            // Screen 2 (front, shifted right/bottom)
            RectF screen2 = new RectF(size * 0.48f, size * 0.32f, size * 0.82f, size * 0.82f);
            // Clear overlapping region using background color
            Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clearPaint.setColor(bgColor);
            clearPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(screen2, dp(context, 3), dp(context, 3), clearPaint);
            
            // Draw front screen stroke
            canvas.drawRoundRect(screen2, dp(context, 3), dp(context, 3), paint);
            
            // Add a small home indicator line at the bottom of the front screen
            canvas.drawLine(size * 0.58f, size * 0.76f, size * 0.72f, size * 0.76f, paint);
        }

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
