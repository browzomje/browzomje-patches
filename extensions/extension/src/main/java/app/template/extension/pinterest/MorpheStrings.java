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
    static final String SECTION_DIAGNOSTICS = "section_diagnostics";

    static final String ADS_TITLE = "ads_title";
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

        put(SEARCH_HISTORY_TITLE,
            "Hide search history",
            "Nascondi cronologia ricerche", "Ocultar historial de búsqueda",
            "Masquer l'historique de recherche", "Suchverlauf ausblenden",
            "Ocultar histórico de pesquisa", "Скрыть историю поиска",
            "検索履歴を非表示", "隐藏搜索历史", "검색 기록 숨기기",
            "Ukryj historię wyszukiwania", "Zoekgeschiedenis verbergen",
            "Arama geçmişini gizle", "إخفاء سجل البحث");

        put(SEARCH_HISTORY_DESCRIPTION,
            "Removes the \"Recent searches\" section from the search screen and from the carousel "
                + "under the search bar. Pinterest keeps storing them on its server: there is no "
                + "way, from inside the app, to prevent that.",
            "Toglie la sezione \"Ricerche recenti\" dalla schermata di ricerca e dal carosello "
                + "sotto la barra. Pinterest continua a salvarle sul suo server: non esiste un "
                + "modo, dall'app, per impedirlo.",
            "Quita la sección «Búsquedas recientes» de la pantalla de búsqueda y del carrusel bajo "
                + "la barra. Pinterest las sigue guardando en su servidor: desde la app no hay "
                + "forma de impedirlo.",
            "Retire la section « Recherches récentes » de l'écran de recherche et du carrousel "
                + "sous la barre. Pinterest continue de les enregistrer sur son serveur : l'app ne "
                + "permet pas de l'empêcher.",
            "Entfernt den Bereich „Letzte Suchanfragen“ aus dem Suchbildschirm und aus dem "
                + "Karussell unter der Leiste. Pinterest speichert sie weiterhin auf seinem "
                + "Server: aus der App lässt sich das nicht verhindern.",
            "Remove a secção \"Pesquisas recentes\" do ecrã de pesquisa e do carrossel abaixo da "
                + "barra. O Pinterest continua a guardá-las no servidor: pela app não há como "
                + "impedir.",
            "Убирает раздел «Недавние запросы» с экрана поиска и из карусели под строкой. "
                + "Pinterest продолжает сохранять их на сервере: из приложения это не отключить.",
            "検索画面とバー下のカルーセルから「最近の検索」を非表示にします。Pinterest はサーバーへの保存を続けます。"
                + "アプリからそれを止める方法はありません。",
            "从搜索页面和搜索栏下方的轮播中移除“最近搜索”。Pinterest 仍会将其保存在服务器上：应用内无法阻止。",
            "검색 화면과 검색창 아래 캐러셀에서 \"최근 검색\"을 숨깁니다. Pinterest는 서버에 계속 "
                + "저장합니다. 앱에서 이를 막을 방법은 없습니다.",
            "Usuwa sekcję „Ostatnie wyszukiwania” z ekranu wyszukiwania i z karuzeli pod paskiem. "
                + "Pinterest nadal zapisuje je na serwerze: z poziomu aplikacji nie da się temu "
                + "zapobiec.",
            "Verwijdert het gedeelte \"Recente zoekopdrachten\" van het zoekscherm en uit de "
                + "carrousel onder de balk. Pinterest blijft ze op zijn server opslaan: vanuit de "
                + "app is dat niet te voorkomen.",
            "Arama ekranından ve çubuğun altındaki karuselden \"Son aramalar\" bölümünü kaldırır. "
                + "Pinterest bunları sunucusunda saklamaya devam eder: uygulama içinden bunu "
                + "engellemenin bir yolu yok.",
            "يزيل قسم «عمليات البحث الأخيرة» من شاشة البحث ومن الشريط الدوّار أسفل شريط البحث. "
                + "يواصل Pinterest حفظها على خادمه، ولا سبيل لمنع ذلك من داخل التطبيق.");

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
            "Adds a download entry to the board's \"…\" menu, top right. It saves the pins already "
                + "loaded, so scroll the board to the end before using it.",
            "Aggiunge una voce di download nel menu \"…\" della bacheca, in alto a destra. Scarica "
                + "i pin già caricati: scorri la bacheca fino in fondo prima di usarla.",
            "Añade una opción de descarga al menú «…» del tablero, arriba a la derecha. Descarga "
                + "los pines ya cargados: desplázate hasta el final del tablero antes de usarla.",
            "Ajoute une entrée de téléchargement au menu « … » du tableau, en haut à droite. "
                + "Télécharge les pins déjà chargés : faites défiler le tableau jusqu'au bout "
                + "avant de l'utiliser.",
            "Fügt dem „…“-Menü der Pinnwand oben rechts einen Download-Eintrag hinzu. Lädt die "
                + "bereits geladenen Pins herunter: scrolle die Pinnwand vorher bis zum Ende.",
            "Adiciona uma opção de download ao menu \"…\" da pasta, no canto superior direito. "
                + "Baixa os pins já carregados: role a pasta até ao fim antes de usar.",
            "Добавляет пункт загрузки в меню «…» доски, вверху справа. Скачивает уже загруженные "
                + "пины: прокрутите доску до конца перед использованием.",
            "ボード右上の「…」メニューにダウンロード項目を追加します。読み込み済みのピンを保存するので、"
                + "先にボードを最後までスクロールしてください。",
            "在画板右上角的“…”菜单中添加下载项。只下载已加载的 Pin：请先将画板滚动到底部。",
            "보드 오른쪽 위 \"…\" 메뉴에 다운로드 항목을 추가합니다. 이미 불러온 핀을 저장하므로 "
                + "먼저 보드를 끝까지 스크롤하세요.",
            "Dodaje pozycję pobierania do menu „…” tablicy w prawym górnym rogu. Pobiera już "
                + "wczytane piny: najpierw przewiń tablicę do końca.",
            "Voegt een downloadoptie toe aan het \"…\"-menu van het bord, rechtsboven. Downloadt "
                + "de al geladen pins: scrol het bord eerst helemaal door.",
            "Panonun sağ üstündeki \"…\" menüsüne bir indirme seçeneği ekler. Yüklenmiş pinleri "
                + "indirir: önce panoyu sonuna kadar kaydırın.",
            "يضيف خيار تنزيل إلى قائمة «…» الخاصة باللوحة أعلى اليمين. ينزّل الدبابيس المحمّلة "
                + "بالفعل: مرّر اللوحة حتى نهايتها أولاً.");

        put(HIDE_SEARCH_TITLE,
            "Hide Search button",
            "Nascondi tasto Ricerca", "Ocultar botón Buscar", "Masquer le bouton Recherche",
            "Suchen-Button ausblenden", "Ocultar botão Pesquisar", "Скрыть кнопку «Поиск»",
            "検索ボタンを非表示", "隐藏搜索按钮", "검색 버튼 숨기기", "Ukryj przycisk Szukaj",
            "Zoekknop verbergen", "Arama düğmesini gizle", "إخفاء زر البحث");

        put(HIDE_CREATE_TITLE,
            "Hide Create (+) button",
            "Nascondi tasto Crea (+)", "Ocultar botón Crear (+)", "Masquer le bouton Créer (+)",
            "Erstellen-Button (+) ausblenden", "Ocultar botão Criar (+)",
            "Скрыть кнопку «Создать» (+)", "作成 (+) ボタンを非表示", "隐藏创建 (+) 按钮",
            "만들기 (+) 버튼 숨기기", "Ukryj przycisk Utwórz (+)", "Knop Maken (+) verbergen",
            "Oluştur (+) düğmesini gizle", "إخفاء زر الإنشاء (+)");

        put(HIDE_NOTIFICATIONS_TITLE,
            "Hide Notifications button",
            "Nascondi tasto Notifiche", "Ocultar botón Notificaciones",
            "Masquer le bouton Notifications", "Benachrichtigungen-Button ausblenden",
            "Ocultar botão Notificações", "Скрыть кнопку «Уведомления»",
            "お知らせボタンを非表示", "隐藏通知按钮", "알림 버튼 숨기기",
            "Ukryj przycisk Powiadomienia", "Meldingenknop verbergen",
            "Bildirimler düğmesini gizle", "إخفاء زر الإشعارات");

        put(NAVBAR_NOTE,
            "Home and Profile cannot be hidden: Profile is the only way back into Settings, and "
                + "therefore into this screen.",
            "Home e Profilo non si possono nascondere: Profilo è l'unica strada per rientrare "
                + "nelle Impostazioni, e quindi in questa schermata.",
            "Inicio y Perfil no se pueden ocultar: Perfil es el único camino de vuelta a Ajustes "
                + "y, por tanto, a esta pantalla.",
            "Accueil et Profil ne peuvent pas être masqués : Profil est le seul chemin vers les "
                + "Réglages, et donc vers cet écran.",
            "Start und Profil lassen sich nicht ausblenden: Profil ist der einzige Weg zurück in "
                + "die Einstellungen und damit zu diesem Bildschirm.",
            "Início e Perfil não podem ser ocultados: Perfil é o único caminho de volta às "
                + "Definições e, portanto, a este ecrã.",
            "«Главная» и «Профиль» скрыть нельзя: профиль — единственный путь обратно в "
                + "настройки, а значит и на этот экран.",
            "ホームとプロフィールは非表示にできません。設定 — つまりこの画面 — に戻れる唯一の入口が"
                + "プロフィールだからです。",
            "首页和个人主页无法隐藏：个人主页是回到设置、也就是回到本页面的唯一入口。",
            "홈과 프로필은 숨길 수 없습니다. 프로필이 설정으로, 따라서 이 화면으로 돌아오는 유일한 "
                + "경로이기 때문입니다.",
            "Strony głównej i Profilu nie można ukryć: Profil to jedyna droga powrotu do Ustawień, "
                + "a więc i do tego ekranu.",
            "Home en Profiel kunnen niet worden verborgen: Profiel is de enige weg terug naar "
                + "Instellingen, en dus naar dit scherm.",
            "Ana sayfa ve Profil gizlenemez: Ayarlara, dolayısıyla bu ekrana dönmenin tek yolu "
                + "Profildir.",
            "لا يمكن إخفاء «الرئيسية» و«الملف الشخصي»: الملف الشخصي هو الطريق الوحيد للعودة إلى "
                + "الإعدادات، ومن ثَمّ إلى هذه الشاشة.");

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
            "Hooks that fired in this session. If an entry is missing, that hook never ran: the "
                + "problem is in the patch, not in the app.",
            "Hook scattati in questa sessione. Se una voce manca, quell'aggancio non è mai stato "
                + "eseguito: il problema è nella patch, non nell'app.",
            "Hooks ejecutados en esta sesión. Si falta una entrada, ese enganche nunca se "
                + "ejecutó: el problema está en el parche, no en la app.",
            "Hooks déclenchés pendant cette session. Si une entrée manque, ce point d'accroche "
                + "n'a jamais été exécuté : le problème vient du patch, pas de l'app.",
            "In dieser Sitzung ausgelöste Hooks. Fehlt ein Eintrag, wurde dieser Hook nie "
                + "ausgeführt: das Problem liegt im Patch, nicht in der App.",
            "Hooks disparados nesta sessão. Se faltar uma entrada, esse gancho nunca correu: o "
                + "problema está no patch, não na app.",
            "Хуки, сработавшие в этой сессии. Если записи нет, этот хук ни разу не выполнялся: "
                + "проблема в патче, а не в приложении.",
            "このセッションで実行されたフックです。項目がなければ、そのフックは一度も実行されていません。"
                + "問題はアプリではなくパッチ側にあります。",
            "本次会话中触发的 hook。若某项缺失，说明该 hook 从未执行：问题出在补丁，而非应用。",
            "이번 세션에서 실행된 후크입니다. 항목이 없다면 그 후크는 한 번도 실행되지 않은 것입니다. "
                + "문제는 앱이 아니라 패치에 있습니다.",
            "Hooki uruchomione w tej sesji. Jeśli pozycji brakuje, ten hook nigdy się nie "
                + "wykonał: problem jest w łatce, nie w aplikacji.",
            "Hooks die in deze sessie zijn afgevuurd. Ontbreekt een regel, dan is die hook nooit "
                + "uitgevoerd: het probleem zit in de patch, niet in de app.",
            "Bu oturumda tetiklenen hook'lar. Bir satır eksikse o hook hiç çalışmamıştır: sorun "
                + "uygulamada değil, yamadadır.",
            "الخطافات التي عملت في هذه الجلسة. إذا غاب أحد البنود فذلك الخطاف لم يُنفَّذ قط: "
                + "المشكلة في التعديل لا في التطبيق.");

        put(DIAGNOSTICS_EMPTY,
            "No hook has run yet.\nOpen the feed or a pin menu, then come back.",
            "Nessun hook ancora eseguito.\nApri il feed o il menu di un pin e torna qui.",
            "Ningún hook se ha ejecutado aún.\nAbre el feed o el menú de un pin y vuelve aquí.",
            "Aucun hook n'a encore été exécuté.\nOuvrez le feed ou le menu d'un pin, puis revenez.",
            "Noch kein Hook ausgeführt.\nÖffne den Feed oder das Menü eines Pins und komm zurück.",
            "Nenhum hook foi executado ainda.\nAbre o feed ou o menu de um pin e volta aqui.",
            "Ни один хук ещё не выполнялся.\nОткройте ленту или меню пина и вернитесь сюда.",
            "まだ実行されたフックはありません。\nフィードかピンのメニューを開いてから戻ってください。",
            "还没有 hook 被执行。\n请打开信息流或某个 Pin 的菜单后再回到这里。",
            "아직 실행된 후크가 없습니다.\n피드나 핀 메뉴를 연 뒤 다시 돌아오세요.",
            "Żaden hook jeszcze się nie wykonał.\nOtwórz feed lub menu pina i wróć tutaj.",
            "Er is nog geen hook uitgevoerd.\nOpen de feed of het menu van een pin en kom terug.",
            "Henüz çalışan bir hook yok.\nAkışı veya bir pinin menüsünü açıp geri dönün.",
            "لم يُنفَّذ أي خطاف بعد.\nافتح الموجز أو قائمة أحد الدبابيس ثم عُد إلى هنا.");

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
            throw new IllegalStateException("MorpheStrings." + key + ": attese "
                    + LANGUAGES.length + " traduzioni, trovate " + translations.length);
        }
        String[] values = new String[translations.length + 1];
        values[0] = english;
        System.arraycopy(translations, 0, values, 1, translations.length);
        TRANSLATIONS.put(key, values);
    }
}
