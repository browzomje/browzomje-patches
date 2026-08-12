package app.browzomje.extension.pinterest;

import android.content.Context;
import android.os.LocaleList;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Testi della schermata Morphe, nelle stesse lingue in cui sono tradotte le voci di menu, con
 * l'inglese come ripiego per tutte le altre.
 *
 * <p>Si usa una tabella invece della catena di {@code if} di {@code PinterestUtils.getString}
 * perché qui i testi sono una ventina e comprendono paragrafi interi: la forma "una riga per
 * chiave, una colonna per lingua" rende evidente se a una lingua manca qualcosa.
 */
final class MorpheStrings {

    // ------------------------------------------------------------------ chiavi

    static final String SECTION_CONTENT = "section_content";
    static final String SECTION_DOWNLOAD = "section_download";
    static final String SECTION_NAVBAR = "section_navbar";
    static final String SECTION_BACKUP = "section_backup";
    static final String SECTION_DIAGNOSTICS = "section_diagnostics";

    static final String ADS_TITLE = "ads_title";
    static final String SHOPPING_PINS_TITLE = "shopping_pins_title";
    static final String SEARCH_HISTORY_TITLE = "search_history_title";
    static final String SEARCH_HISTORY_DESCRIPTION = "search_history_description";
    static final String EMAIL_DIALOG_TITLE = "email_dialog_title";

    static final String BOARD_DOWNLOAD_TITLE = "board_download_title";
    static final String BOARD_DOWNLOAD_DESCRIPTION = "board_download_description";

    static final String HIDE_SEARCH_TITLE = "hide_search_title";
    static final String HIDE_CREATE_TITLE = "hide_create_title";
    static final String HIDE_NOTIFICATIONS_TITLE = "hide_notifications_title";
    static final String NAVBAR_NOTE = "navbar_note";

    static final String VERBOSE_TITLE = "verbose_title";
    static final String VERBOSE_DESCRIPTION = "verbose_description";
    static final String DIAGNOSTICS_HINT = "diagnostics_hint";
    static final String DIAGNOSTICS_EMPTY = "diagnostics_empty";

    static final String BACKUP_HINT = "backup_hint";
    static final String BUTTON_EXPORT = "button_export";
    static final String BUTTON_IMPORT = "button_import";
    static final String TOAST_EXPORTED = "toast_exported";
    static final String TOAST_IMPORTED = "toast_imported";
    static final String TOAST_EXPORT_FAILED = "toast_export_failed";
    static final String TOAST_IMPORT_FAILED = "toast_import_failed";
    static final String TOAST_NO_FILE_PICKER = "toast_no_file_picker";

    static final String BUTTON_REFRESH = "button_refresh";
    static final String BUTTON_COPY = "button_copy";
    static final String TOAST_RESTART = "toast_restart";
    static final String TOAST_COPIED = "toast_copied";
    static final String TOAST_COPY_FAILED = "toast_copy_failed";

    // ------------------------------------------------------------------ tabella

    /**
     * Ordine delle colonne in {@link #TRANSLATIONS}. L'inglese non compare: è il primo
     * argomento di {@link #put}, cioè il valore di ripiego.
     */
    private static final String[] LANGUAGES = {
        "it", "es", "fr", "de", "pt", "ru", "ja", "zh", "ko", "pl", "nl", "tr", "ar",
    };

    /** chiave -> {inglese, poi una voce per ciascuna lingua di {@link #LANGUAGES}}. */
    private static final Map<String, String[]> TRANSLATIONS = new HashMap<>();

    static {
        put(SECTION_CONTENT,
            "Content",
            "Contenuti", "Contenido", "Contenu", "Inhalte", "Conteúdo", "Контент",
            "コンテンツ", "内容", "콘텐츠", "Treść", "Inhoud", "İçerik", "المحتوى");

        put(SECTION_DOWNLOAD,
            "Download",
            "Download", "Descargas", "Téléchargement", "Download", "Download", "Загрузки",
            "ダウンロード", "下载", "다운로드", "Pobieranie", "Downloaden", "İndirme", "التنزيل");

        put(SECTION_NAVBAR,
            "Navigation bar",
            "Barra di navigazione", "Barra de navegación", "Barre de navigation",
            "Navigationsleiste", "Barra de navegação", "Панель навигации",
            "ナビゲーションバー", "导航栏", "내비게이션 바", "Pasek nawigacji", "Navigatiebalk",
            "Gezinme çubuğu", "شريط التنقل");

        put(SECTION_BACKUP,
            "Backup",
            "Backup", "Copia de seguridad", "Sauvegarde", "Backup", "Cópia de segurança",
            "Резервная копия", "バックアップ", "备份", "백업", "Kopia zapasowa", "Back-up",
            "Yedekleme", "النسخ الاحتياطي");

        put(SECTION_DIAGNOSTICS,
            "Diagnostics",
            "Diagnostica", "Diagnóstico", "Diagnostic", "Diagnose", "Diagnóstico", "Диагностика",
            "診断", "诊断", "진단", "Diagnostyka", "Diagnostiek", "Tanılama", "التشخيص");

        put(ADS_TITLE,
            "Disable ads",
            "Disabilita pubblicità", "Desactivar anuncios", "Désactiver les publicités",
            "Werbung deaktivieren", "Desativar anúncios", "Отключить рекламу",
            "広告を無効にする", "禁用广告", "광고 비활성화", "Wyłącz reklamy",
            "Advertenties uitschakelen", "Reklamları devre dışı bırak", "تعطيل الإعلانات");

        put(SHOPPING_PINS_TITLE,
            "Hide sponsored products",
            "Nascondi prodotti sponsorizzati", "Ocultar productos patrocinados",
            "Masquer les produits sponsorisés", "Gesponserte Produkte ausblenden",
            "Ocultar produtos patrocinados", "Скрыть спонсируемые товары",
            "スポンサー商品を非表示", "隐藏赞助商品", "스폰서 제품 숨기기",
            "Ukryj produkty sponsorowane", "Gesponsorde producten verbergen",
            "Sponsorlu ürünleri gizle", "إخفاء المنتجات المموّلة");

        put(SEARCH_HISTORY_TITLE,
            "Hide search history",
            "Nascondi cronologia ricerche", "Ocultar historial de búsqueda",
            "Masquer l'historique de recherche", "Suchverlauf ausblenden",
            "Ocultar histórico de pesquisa", "Скрыть историю поиска",
            "検索履歴を非表示", "隐藏搜索历史", "검색 기록 숨기기",
            "Ukryj historię wyszukiwania", "Zoekgeschiedenis verbergen",
            "Arama geçmişini gizle", "إخفاء سجل البحث");

        put(SEARCH_HISTORY_DESCRIPTION,
            "Only hides them: Pinterest keeps saving them on its server.",
            "Le nasconde soltanto: Pinterest continua a salvarle sul suo server.",
            "Solo las oculta: Pinterest las sigue guardando en su servidor.",
            "Les masque seulement : Pinterest continue de les enregistrer sur son serveur.",
            "Blendet sie nur aus: Pinterest speichert sie weiterhin auf seinem Server.",
            "Apenas as oculta: o Pinterest continua a guardá-las no servidor.",
            "Только скрывает: Pinterest продолжает сохранять их на сервере.",
            "隠すだけです。Pinterest はサーバーへの保存を続けます。",
            "仅隐藏：Pinterest 仍会保存在服务器上。",
            "숨기기만 합니다. Pinterest는 서버에 계속 저장합니다.",
            "Tylko je ukrywa: Pinterest nadal zapisuje je na serwerze.",
            "Verbergt ze alleen: Pinterest blijft ze op zijn server opslaan.",
            "Yalnızca gizler: Pinterest bunları sunucusunda saklamaya devam eder.",
            "يخفيها فقط: يواصل Pinterest حفظها على خادمه.");

        put(EMAIL_DIALOG_TITLE,
            "Disable the \"confirm your email\" dialog",
            "Disabilita il dialog \"conferma la tua email\"",
            "Desactivar el diálogo «confirma tu correo»",
            "Désactiver la fenêtre « confirmez votre e-mail »",
            "Dialog „E-Mail bestätigen“ deaktivieren",
            "Desativar a janela \"confirma o teu email\"",
            "Отключить окно «подтвердите email»",
            "「メールアドレスを確認」ダイアログを無効にする",
            "禁用“确认你的邮箱”弹窗",
            "\"이메일 확인\" 대화상자 비활성화",
            "Wyłącz okno „potwierdź e-mail”",
            "Dialoogvenster \"bevestig je e-mail\" uitschakelen",
            "\"E-postanı doğrula\" penceresini devre dışı bırak",
            "تعطيل نافذة «أكِّد بريدك الإلكتروني»");

        put(BOARD_DOWNLOAD_TITLE,
            "Download whole board",
            "Scarica tutta la bacheca", "Descargar todo el tablero", "Télécharger tout le tableau",
            "Ganze Pinnwand herunterladen", "Baixar a pasta inteira", "Скачать всю доску",
            "ボード全体をダウンロード", "下载整个画板", "보드 전체 다운로드", "Pobierz całą tablicę",
            "Hele bord downloaden", "Tüm panoyu indir", "تنزيل اللوحة بالكامل");

        put(BOARD_DOWNLOAD_DESCRIPTION,
            "Saves the pins already loaded: scroll the board to the end first.",
            "Scarica i pin già caricati: scorri prima la bacheca fino in fondo.",
            "Descarga los pines ya cargados: desplázate antes hasta el final del tablero.",
            "Télécharge les pins déjà chargés : faites d'abord défiler le tableau jusqu'au bout.",
            "Lädt die bereits geladenen Pins: scrolle die Pinnwand vorher bis zum Ende.",
            "Baixa os pins já carregados: role a pasta até ao fim primeiro.",
            "Скачивает уже загруженные пины: сначала прокрутите доску до конца.",
            "読み込み済みのピンを保存します。先にボードを最後までスクロールしてください。",
            "只下载已加载的 Pin：请先将画板滚动到底部。",
            "이미 불러온 핀을 저장합니다. 먼저 보드를 끝까지 스크롤하세요.",
            "Pobiera już wczytane piny: najpierw przewiń tablicę do końca.",
            "Downloadt de al geladen pins: scrol het bord eerst helemaal door.",
            "Yüklenmiş pinleri indirir: önce panoyu sonuna kadar kaydırın.",
            "ينزّل الدبابيس المحمّلة بالفعل: مرّر اللوحة حتى نهايتها أولاً.");

        put(HIDE_SEARCH_TITLE,
            "Hide Search button",
            "Nascondi tasto Ricerca", "Ocultar botón Buscar", "Masquer le bouton Recherche",
            "Suchen-Button ausblenden", "Ocultar botão Pesquisar", "Скрыть кнопку «Поиск»",
            "検索ボタンを非表示", "隐藏搜索按钮", "검색 버튼 숨기기", "Ukryj przycisk Szukaj",
            "Zoekknop verbergen", "Arama düğmesini gizle", "إخفاء زر البحث");

        // Senza il "(+)" nel testo: la riga porta già l'icona del tasto a sinistra, e ripeterlo
        // sembrava un doppione.
        put(HIDE_CREATE_TITLE,
            "Hide Create button",
            "Nascondi tasto Crea", "Ocultar botón Crear", "Masquer le bouton Créer",
            "Erstellen-Button ausblenden", "Ocultar botão Criar",
            "Скрыть кнопку «Создать»", "作成ボタンを非表示", "隐藏创建按钮",
            "만들기 버튼 숨기기", "Ukryj przycisk Utwórz", "Knop Maken verbergen",
            "Oluştur düğmesini gizle", "إخفاء زر الإنشاء");

        put(HIDE_NOTIFICATIONS_TITLE,
            "Hide Notifications button",
            "Nascondi tasto Notifiche", "Ocultar botón Notificaciones",
            "Masquer le bouton Notifications", "Benachrichtigungen-Button ausblenden",
            "Ocultar botão Notificações", "Скрыть кнопку «Уведомления»",
            "お知らせボタンを非表示", "隐藏通知按钮", "알림 버튼 숨기기",
            "Ukryj przycisk Powiadomienia", "Meldingenknop verbergen",
            "Bildirimler düğmesini gizle", "إخفاء زر الإشعارات");

        put(NAVBAR_NOTE,
            "Home and Profile cannot be hidden: Profile is the only way back here.",
            "Home e Profilo non si possono nascondere: da Profilo si torna qui.",
            "Inicio y Perfil no se pueden ocultar: por Perfil se vuelve aquí.",
            "Accueil et Profil ne peuvent pas être masqués : on revient ici par Profil.",
            "Start und Profil lassen sich nicht ausblenden: über Profil kommt man hierher zurück.",
            "Início e Perfil não podem ser ocultados: por Perfil volta-se aqui.",
            "«Главная» и «Профиль» скрыть нельзя: через профиль сюда возвращаются.",
            "ホームとプロフィールは非表示にできません。ここに戻れる入口だからです。",
            "首页和个人主页无法隐藏：个人主页是回到本页面的唯一入口。",
            "홈과 프로필은 숨길 수 없습니다. 프로필이 이 화면으로 오는 유일한 경로입니다.",
            "Strony głównej i Profilu nie można ukryć: przez Profil wraca się tutaj.",
            "Home en Profiel kunnen niet worden verborgen: via Profiel kom je hier terug.",
            "Ana sayfa ve Profil gizlenemez: buraya dönüş yolu Profildir.",
            "لا يمكن إخفاء «الرئيسية» و«الملف الشخصي»: من الملف الشخصي تعود إلى هنا.");

        put(VERBOSE_TITLE,
            "Verbose logging in logcat",
            "Log dettagliato in logcat", "Registro detallado en logcat",
            "Journal détaillé dans logcat", "Ausführliches Log in logcat",
            "Registo detalhado no logcat", "Подробный лог в logcat",
            "logcat に詳細ログを出力", "在 logcat 中输出详细日志", "logcat 상세 로그",
            "Szczegółowy log w logcat", "Uitgebreide logging in logcat",
            "logcat'te ayrıntılı günlük", "سجل مفصّل في logcat");

        put(VERBOSE_DESCRIPTION,
            "Writes what each patch does: adb logcat -s Morphe:V",
            "Scrive cosa fa ogni patch: adb logcat -s Morphe:V",
            "Escribe lo que hace cada parche: adb logcat -s Morphe:V",
            "Écrit ce que fait chaque patch : adb logcat -s Morphe:V",
            "Schreibt, was jeder Patch tut: adb logcat -s Morphe:V",
            "Escreve o que cada patch faz: adb logcat -s Morphe:V",
            "Пишет, что делает каждый патч: adb logcat -s Morphe:V",
            "各パッチの動作を出力します: adb logcat -s Morphe:V",
            "输出每个补丁的行为：adb logcat -s Morphe:V",
            "각 패치의 동작을 기록합니다: adb logcat -s Morphe:V",
            "Zapisuje, co robi każda łatka: adb logcat -s Morphe:V",
            "Schrijft wat elke patch doet: adb logcat -s Morphe:V",
            "Her yamanın ne yaptığını yazar: adb logcat -s Morphe:V",
            "يسجّل ما يفعله كل تعديل: adb logcat -s Morphe:V");

        put(DIAGNOSTICS_HINT,
            "Hooks that fired in this session. A missing entry never ran.",
            "Hook scattati in questa sessione. Una voce che manca non è mai partita.",
            "Hooks ejecutados en esta sesión. Una entrada que falta nunca se ejecutó.",
            "Hooks déclenchés dans cette session. Une entrée absente ne s'est jamais exécutée.",
            "In dieser Sitzung ausgelöste Hooks. Ein fehlender Eintrag lief nie.",
            "Hooks disparados nesta sessão. Uma entrada em falta nunca correu.",
            "Хуки, сработавшие в этой сессии. Отсутствующая запись не выполнялась.",
            "このセッションで実行されたフックです。項目がなければ一度も実行されていません。",
            "本次会话中触发的 hook。缺失的项从未执行。",
            "이번 세션에서 실행된 후크입니다. 없는 항목은 실행된 적이 없습니다.",
            "Hooki uruchomione w tej sesji. Brakująca pozycja nigdy się nie wykonała.",
            "Hooks die in deze sessie zijn afgevuurd. Een ontbrekende regel liep nooit.",
            "Bu oturumda tetiklenen hook'lar. Eksik bir satır hiç çalışmamıştır.",
            "الخطافات التي عملت في هذه الجلسة. البند الغائب لم يُنفَّذ قط.");

        put(DIAGNOSTICS_EMPTY,
            "No hook has run yet. Open the feed and come back.",
            "Nessun hook ancora eseguito. Apri il feed e torna qui.",
            "Ningún hook se ha ejecutado aún. Abre el feed y vuelve aquí.",
            "Aucun hook exécuté pour l'instant. Ouvrez le feed puis revenez.",
            "Noch kein Hook ausgeführt. Öffne den Feed und komm zurück.",
            "Nenhum hook foi executado ainda. Abre o feed e volta aqui.",
            "Ни один хук ещё не выполнялся. Откройте ленту и вернитесь.",
            "まだ実行されたフックはありません。フィードを開いてから戻ってください。",
            "还没有 hook 被执行。请打开信息流后再回来。",
            "아직 실행된 후크가 없습니다. 피드를 연 뒤 다시 오세요.",
            "Żaden hook jeszcze się nie wykonał. Otwórz feed i wróć tutaj.",
            "Er is nog geen hook uitgevoerd. Open de feed en kom terug.",
            "Henüz çalışan bir hook yok. Akışı açıp geri dönün.",
            "لم يُنفَّذ أي خطاف بعد. افتح الموجز ثم عُد إلى هنا.");

        put(BACKUP_HINT,
            "Reinstalling the patched app wipes the switches: export before you repatch.",
            "Reinstallare l'app patchata azzera gli interruttori: esporta prima di ripatchare.",
            "Reinstalar la app parcheada borra los interruptores: exporta antes de reparchear.",
            "Réinstaller l'app patchée efface les réglages : exportez avant de re-patcher.",
            "Beim Neuinstallieren der gepatchten App gehen die Schalter verloren: vorher exportieren.",
            "Reinstalar a app com patch apaga os interruptores: exporta antes de repatchar.",
            "Переустановка пропатченного приложения стирает переключатели: экспортируйте заранее.",
            "パッチ済みアプリを入れ直すと設定が消えます。再パッチの前にエクスポートを。",
            "重装打过补丁的应用会清空开关：请在重新打补丁前导出。",
            "패치된 앱을 다시 설치하면 설정이 지워집니다. 다시 패치하기 전에 내보내세요.",
            "Ponowna instalacja załatanej aplikacji kasuje przełączniki: wyeksportuj wcześniej.",
            "Het opnieuw installeren van de gepatchte app wist de schakelaars: exporteer eerst.",
            "Yamalı uygulamayı yeniden kurmak anahtarları siler: önce dışa aktarın.",
            "تثبيت التطبيق المعدَّل من جديد يمسح المفاتيح: صدِّرها قبل إعادة التعديل.");

        put(BUTTON_EXPORT,
            "Export",
            "Esporta", "Exportar", "Exporter", "Exportieren", "Exportar", "Экспорт",
            "エクスポート", "导出", "내보내기", "Eksportuj", "Exporteren", "Dışa aktar", "تصدير");

        put(BUTTON_IMPORT,
            "Import",
            "Importa", "Importar", "Importer", "Importieren", "Importar", "Импорт",
            "インポート", "导入", "가져오기", "Importuj", "Importeren", "İçe aktar", "استيراد");

        put(TOAST_EXPORTED,
            "Settings exported",
            "Impostazioni esportate", "Ajustes exportados", "Réglages exportés",
            "Einstellungen exportiert", "Definições exportadas", "Настройки экспортированы",
            "設定をエクスポートしました", "已导出设置", "설정을 내보냈습니다",
            "Wyeksportowano ustawienia", "Instellingen geëxporteerd", "Ayarlar dışa aktarıldı",
            "تم تصدير الإعدادات");

        put(TOAST_IMPORTED,
            "%d settings imported",
            "%d impostazioni importate", "%d ajustes importados", "%d réglages importés",
            "%d Einstellungen importiert", "%d definições importadas", "Импортировано настроек: %d",
            "%d 件の設定をインポートしました", "已导入 %d 项设置", "설정 %d개를 가져왔습니다",
            "Zaimportowano ustawienia: %d", "%d instellingen geïmporteerd",
            "%d ayar içe aktarıldı", "تم استيراد %d من الإعدادات");

        put(TOAST_EXPORT_FAILED,
            "Export failed: ",
            "Esportazione non riuscita: ", "No se pudo exportar: ", "Échec de l'export : ",
            "Export fehlgeschlagen: ", "Não foi possível exportar: ", "Не удалось экспортировать: ",
            "エクスポートできませんでした: ", "导出失败：", "내보내지 못했습니다: ",
            "Nie udało się wyeksportować: ", "Exporteren mislukt: ", "Dışa aktarılamadı: ",
            "تعذّر التصدير: ");

        put(TOAST_IMPORT_FAILED,
            "Import failed: ",
            "Importazione non riuscita: ", "No se pudo importar: ", "Échec de l'import : ",
            "Import fehlgeschlagen: ", "Não foi possível importar: ", "Не удалось импортировать: ",
            "インポートできませんでした: ", "导入失败：", "가져오지 못했습니다: ",
            "Nie udało się zaimportować: ", "Importeren mislukt: ", "İçe aktarılamadı: ",
            "تعذّر الاستيراد: ");

        put(TOAST_NO_FILE_PICKER,
            "No app on this phone can pick a file",
            "Nessuna app su questo telefono può scegliere un file",
            "Ninguna app de este teléfono puede elegir un archivo",
            "Aucune app de ce téléphone ne permet de choisir un fichier",
            "Keine App auf diesem Telefon kann eine Datei auswählen",
            "Nenhuma app deste telemóvel consegue escolher um ficheiro",
            "На этом телефоне нет приложения для выбора файла",
            "この端末にはファイルを選べるアプリがありません",
            "此手机上没有可以选择文件的应用",
            "이 휴대폰에는 파일을 선택할 수 있는 앱이 없습니다",
            "Żadna aplikacja na tym telefonie nie umie wybrać pliku",
            "Geen app op deze telefoon kan een bestand kiezen",
            "Bu telefonda dosya seçebilecek bir uygulama yok",
            "لا يوجد على هذا الهاتف تطبيق يمكنه اختيار ملف");

        put(BUTTON_REFRESH,
            "Refresh",
            "Aggiorna", "Actualizar", "Actualiser", "Aktualisieren", "Atualizar", "Обновить",
            "更新", "刷新", "새로고침", "Odśwież", "Vernieuwen", "Yenile", "تحديث");

        put(BUTTON_COPY,
            "Copy",
            "Copia", "Copiar", "Copier", "Kopieren", "Copiar", "Копировать",
            "コピー", "复制", "복사", "Kopiuj", "Kopiëren", "Kopyala", "نسخ");

        put(TOAST_RESTART,
            "Restart Pinterest to apply everywhere",
            "Riavvia Pinterest per applicare ovunque",
            "Reinicia Pinterest para aplicarlo en todas partes",
            "Redémarrez Pinterest pour appliquer partout",
            "Pinterest neu starten, damit es überall greift",
            "Reinicia o Pinterest para aplicar em todo o lado",
            "Перезапустите Pinterest, чтобы применить везде",
            "すべてに反映するには Pinterest を再起動してください",
            "重启 Pinterest 以在各处生效",
            "모든 곳에 적용하려면 Pinterest를 다시 시작하세요",
            "Uruchom Pinterest ponownie, aby zastosować wszędzie",
            "Start Pinterest opnieuw om het overal toe te passen",
            "Her yerde geçerli olması için Pinterest'i yeniden başlatın",
            "أعد تشغيل Pinterest لتطبيق التغيير في كل مكان");

        put(TOAST_COPIED,
            "Diagnostics copied",
            "Diagnostica copiata", "Diagnóstico copiado", "Diagnostic copié", "Diagnose kopiert",
            "Diagnóstico copiado", "Диагностика скопирована", "診断情報をコピーしました",
            "已复制诊断信息", "진단 정보를 복사했습니다", "Skopiowano diagnostykę",
            "Diagnostiek gekopieerd", "Tanılama kopyalandı", "تم نسخ معلومات التشخيص");

        put(TOAST_COPY_FAILED,
            "Copy failed: ",
            "Copia non riuscita: ", "No se pudo copiar: ", "Échec de la copie : ",
            "Kopieren fehlgeschlagen: ", "Não foi possível copiar: ", "Не удалось скопировать: ",
            "コピーできませんでした: ", "复制失败：", "복사하지 못했습니다: ",
            "Nie udało się skopiować: ", "Kopiëren mislukt: ", "Kopyalanamadı: ",
            "تعذّر النسخ: ");
    }

    private MorpheStrings() {}

    /**
     * Lingua in cui è l'app, non quella in cui è il telefono.
     *
     * <p>Da Android 13 si può impostare una lingua per singola applicazione, e Pinterest ne ha
     * anche una propria: con {@code Locale.getDefault()} chi ha il telefono in una lingua e
     * Pinterest in un'altra vedrebbe le voci di Morphe in una lingua diversa da quelle accanto.
     * La configurazione delle risorse dell'app è ciò che determina in che lingua Pinterest
     * disegna le proprie schermate.
     *
     * @return il codice a due lettere della lingua, es. {@code "it"}, con ripiego sulla lingua di
     *     sistema se la configurazione non è raggiungibile.
     */
    static String language() {
        try {
            Context context = MorpheSettingsStore.appContext();
            if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                LocaleList locales = context.getResources().getConfiguration().getLocales();
                if (locales != null && !locales.isEmpty()) {
                    return locales.get(0).getLanguage();
                }
            }
        } catch (Throwable ignored) {
            // Configurazione non leggibile: si usa il ripiego qui sotto.
        }
        return Locale.getDefault().getLanguage();
    }

    /**
     * @return il testo nella lingua del telefono, o in inglese se quella lingua non è fra quelle
     *     tradotte. Non restituisce mai null: una chiave sconosciuta torna com'è, così un
     *     eventuale refuso si vede a schermo invece di far sparire l'etichetta.
     */
    static String get(String key) {
        String[] values = TRANSLATIONS.get(key);
        if (values == null) {
            return key;
        }
        String language = language();
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(language)) {
                String translated = values[i + 1];
                return translated == null ? values[0] : translated;
            }
        }
        return values[0];
    }

    /**
     * @param english testo di ripiego, usato per ogni lingua non elencata.
     * @param translations una voce per ciascuna lingua di {@link #LANGUAGES}, nello stesso ordine.
     */
    private static void put(String key, String english, String... translations) {
        if (translations.length != LANGUAGES.length) {
            // Una traduzione mancante sfaserebbe tutte le colonne successive: meglio fallire.
            throw new IllegalStateException("MorpheStrings." + key + ": expected "
                    + LANGUAGES.length + " translations, found " + translations.length);
        }
        String[] values = new String[translations.length + 1];
        values[0] = english;
        System.arraycopy(translations, 0, values, 1, translations.length);
        TRANSLATIONS.put(key, values);
    }
}
