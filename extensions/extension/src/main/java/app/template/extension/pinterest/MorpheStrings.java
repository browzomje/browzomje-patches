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
    static final String SEARCH_MODULES_TITLE = "search_modules_title";
    static final String SEARCH_MODULES_DESCRIPTION = "search_modules_description";
    static final String SCREENSHOT_TITLE = "screenshot_title";
    static final String SCREENSHOT_DESCRIPTION = "screenshot_description";
    static final String SANITIZE_LINKS_TITLE = "sanitize_links_title";
    static final String SANITIZE_LINKS_DESCRIPTION = "sanitize_links_description";
    static final String SHARE_LINK_ONLY_TITLE = "share_link_only_title";
    static final String SHARE_LINK_ONLY_DESCRIPTION = "share_link_only_description";
    static final String SYSTEM_SHARE_SHEET_TITLE = "system_share_sheet_title";
    static final String SYSTEM_SHARE_SHEET_DESCRIPTION = "system_share_sheet_description";
    static final String EXTERNAL_BROWSER_TITLE = "external_browser_title";
    static final String EXTERNAL_BROWSER_DESCRIPTION = "external_browser_description";
    static final String HIDE_COMMENTS_TITLE = "hide_comments_title";
    static final String HIDE_COMMENTS_DESCRIPTION = "hide_comments_description";

    static final String BOARD_DOWNLOAD_TITLE = "board_download_title";
    static final String BOARD_DOWNLOAD_DESCRIPTION = "board_download_description";
    static final String LONG_PRESS_DOWNLOAD_TITLE = "long_press_download_title";
    static final String LONG_PRESS_DOWNLOAD_DESCRIPTION = "long_press_download_description";

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

    static final String IMAGE_INFO_TITLE = "image_info_title";
    static final String IMAGE_INFO_DESCRIPTION = "image_info_description";
    static final String HIGH_QUALITY_TITLE = "high_quality_title";
    static final String HIGH_QUALITY_DESCRIPTION = "high_quality_description";

    static final String MENU_FILTER_TITLE = "menu_filter_title";
    static final String MENU_FILTER_HINT = "menu_filter_hint";
    static final String MENU_FILTER_DESCRIPTION = "menu_filter_description";

    static final String SECTION_PIN_MENU = "section_pin_menu";
    static final String BUTTON_SAVE = "button_save";
    static final String BUTTON_CANCEL = "button_cancel";

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
        put(IMAGE_INFO_TITLE,
            "Image information",
            "Informazioni immagine",
            "Información de la imagen",
            "Informations sur l’image",
            "Bildinformationen",
            "Informações da imagem",
            "Информация об изображении",
            "画像情報",
            "图片信息",
            "이미지 정보",
            "Informacje o obrazie",
            "Afbeeldingsinformatie",
            "Görsel bilgileri",
            "معلومات الصورة");

        put(IMAGE_INFO_DESCRIPTION,
            "Adds image dimensions and format to the pin menu, using the largest available variant.",
            "Mostra dimensioni e formato nel menu del Pin, usando la variante più grande disponibile.",
            "Muestra dimensiones y formato en el menú del Pin usando la variante más grande disponible.",
            "Affiche les dimensions et le format dans le menu de l’épingle, pour la plus grande version disponible.",
            "Zeigt Größe und Format der größten verfügbaren Bildvariante im Pin-Menü.",
            "Mostra dimensões e formato no menu do Pin, usando a maior versão disponível.",
            "Показывает размеры и формат самой большой доступной версии в меню пина.",
            "ピンのメニューに、利用可能な最大サイズの画像の寸法と形式を表示します。",
            "在 Pin 菜单中显示最大可用图片的尺寸和格式。",
            "핀 메뉴에 가장 큰 이미지의 크기와 형식을 표시합니다.",
            "Pokazuje w menu pina wymiary i format największej dostępnej wersji obrazu.",
            "Toont in het pinmenu de afmetingen en het formaat van de grootste beschikbare afbeelding.",
            "Pin menüsünde mevcut en büyük görselin boyutlarını ve biçimini gösterir.",
            "يعرض أبعاد أكبر نسخة متاحة من الصورة وتنسيقها في قائمة الدبوس.");

        put(HIGH_QUALITY_TITLE,
            "Download images in highest quality",
            "Scarica immagini alla massima qualità",
            "Descargar imágenes con la máxima calidad",
            "Télécharger les images en qualité maximale",
            "Bilder in höchster Qualität herunterladen",
            "Transferir imagens na qualidade máxima",
            "Скачивать изображения в максимальном качестве",
            "最高画質で画像をダウンロード",
            "以最高画质下载图片",
            "최고 화질로 이미지 다운로드",
            "Pobieraj obrazy w najwyższej jakości",
            "Afbeeldingen in de hoogste kwaliteit downloaden",
            "Görselleri en yüksek kalitede indir",
            "تنزيل الصور بأعلى جودة");

        put(HIGH_QUALITY_DESCRIPTION,
            "The Download image menu action uses the largest variant provided by Pinterest.",
            "La voce Scarica immagine usa la variante più grande fornita da Pinterest.",
            "La opción Descargar imagen usa la variante más grande proporcionada por Pinterest.",
            "L’action Télécharger l’image utilise la plus grande version fournie par Pinterest.",
            "Die Aktion Bild herunterladen verwendet die größte von Pinterest bereitgestellte Variante.",
            "A opção de transferência da imagem usa a maior versão fornecida pelo Pinterest.",
            "Пункт скачивания изображения использует самую большую версию, предоставленную Pinterest.",
            "画像のダウンロードでは Pinterest が提供する最大サイズの画像を使用します。",
            "下载图片时使用 Pinterest 提供的最大尺寸版本。",
            "이미지 다운로드 메뉴에서 Pinterest가 제공하는 가장 큰 이미지를 사용합니다.",
            "Opcja pobierania obrazu używa największej wersji udostępnionej przez Pinterest.",
            "De optie voor het downloaden van afbeeldingen gebruikt de grootste versie die Pinterest aanbiedt.",
            "Görsel indirme seçeneği Pinterest’in sağladığı en büyük sürümü kullanır.",
            "يستخدم خيار تنزيل الصورة أكبر نسخة يوفّرها Pinterest.");

        put(MENU_FILTER_TITLE,
            "Hide pin menu actions",
            "Nascondi voci del menu Pin",
            "Ocultar opciones del menú del Pin",
            "Masquer des actions du menu de l’épingle",
            "Aktionen im Pin-Menü ausblenden",
            "Ocultar opções do menu do Pin",
            "Скрыть пункты меню пина",
            "ピンのメニュー項目を非表示",
            "隐藏 Pin 菜单选项",
            "핀 메뉴 항목 숨기기",
            "Ukryj opcje menu pina",
            "Acties in het pinmenu verbergen",
            "Pin menüsü seçeneklerini gizle",
            "إخفاء خيارات قائمة الدبوس");

        put(MENU_FILTER_HINT,
            "Choose which actions to hide",
            "Scegli quali voci nascondere",
            "Elige qué opciones ocultar",
            "Choisissez les actions à masquer",
            "Wähle die auszublendenden Aktionen",
            "Escolhe as opções a ocultar",
            "Выберите пункты для скрытия",
            "非表示にする項目を選択",
            "选择要隐藏的菜单选项",
            "숨길 항목 선택",
            "Wybierz opcje do ukrycia",
            "Kies welke acties je wilt verbergen",
            "Gizlenecek seçenekleri seçin",
            "اختر الخيارات التي تريد إخفاءها");

        put(MENU_FILTER_DESCRIPTION,
            "Select the Pinterest actions you want to hide. Clear the selections to show them again. Morphe actions are managed separately.",
            "Seleziona le voci di Pinterest che vuoi nascondere. Deselezionale per mostrarle di nuovo. Le voci Morphe si gestiscono nelle altre impostazioni.",
            "Selecciona las opciones de Pinterest que quieres ocultar. Desmárcalas para volver a mostrarlas. Las opciones de Morphe se gestionan por separado.",
            "Sélectionnez les actions Pinterest à masquer. Décochez-les pour les réafficher. Les actions Morphe se gèrent séparément.",
            "Wähle die Pinterest-Aktionen, die du ausblenden möchtest. Entferne die Auswahl, um sie wieder anzuzeigen. Morphe-Aktionen werden separat verwaltet.",
            "Seleciona as opções do Pinterest que queres ocultar. Desmarca-as para as mostrar novamente. As opções Morphe são geridas separadamente.",
            "Выберите пункты Pinterest, которые хотите скрыть. Снимите отметки, чтобы вернуть их. Пункты Morphe настраиваются отдельно.",
            "非表示にする Pinterest の項目を選択してください。選択を解除すると再表示されます。Morphe の項目は別の設定で管理します。",
            "勾选要隐藏的 Pinterest 选项。取消勾选即可重新显示。Morphe 选项在其他设置中管理。",
            "숨길 Pinterest 항목을 선택하세요. 선택을 해제하면 다시 표시됩니다. Morphe 항목은 별도로 설정합니다.",
            "Zaznacz opcje Pinterest, które chcesz ukryć. Odznacz je, aby je przywrócić. Opcje Morphe są zarządzane osobno.",
            "Selecteer de Pinterest-acties die je wilt verbergen. Vink ze uit om ze weer te tonen. Morphe-acties beheer je apart.",
            "Gizlemek istediğiniz Pinterest seçeneklerini işaretleyin. Yeniden göstermek için işaretlerini kaldırın. Morphe seçenekleri ayrı olarak yönetilir.",
            "حدد خيارات Pinterest التي تريد إخفاءها. أزل التحديد لإظهارها مجددًا. تُدار خيارات Morphe بشكل منفصل.");

        put(SECTION_PIN_MENU,
            "Pin menu",
            "Menu dei Pin",
            "Menú del Pin",
            "Menu de l’épingle",
            "Pin-Menü",
            "Menu do Pin",
            "Меню пина",
            "ピンのメニュー",
            "Pin 菜单",
            "핀 메뉴",
            "Menu pina",
            "Pinmenu",
            "Pin menüsü",
            "قائمة الدبوس");

        put(BUTTON_SAVE,
            "Save",
            "Salva",
            "Guardar",
            "Enregistrer",
            "Speichern",
            "Guardar",
            "Сохранить",
            "保存",
            "保存",
            "저장",
            "Zapisz",
            "Opslaan",
            "Kaydet",
            "حفظ");

        put(BUTTON_CANCEL,
            "Cancel",
            "Annulla",
            "Cancelar",
            "Annuler",
            "Abbrechen",
            "Cancelar",
            "Отмена",
            "キャンセル",
            "取消",
            "취소",
            "Anuluj",
            "Annuleren",
            "İptal",
            "إلغاء");

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
            "Disabilita la pubblicità", "Desactivar anuncios", "Désactiver les publicités",
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

        put(SEARCH_MODULES_TITLE,
            "Hide board modules in search",
            "Nascondi moduli a bacheche nella ricerca", "Ocultar módulos de tableros en la búsqueda",
            "Masquer les modules de tableaux dans la recherche", "Pinnwand-Module in der Suche ausblenden",
            "Ocultar módulos de pastas na pesquisa", "Скрыть блоки досок в поиске",
            "検索のボードモジュールを非表示", "隐藏搜索中的图板模块", "검색의 보드 모듈 숨기기",
            "Ukryj moduły tablic w wyszukiwaniu", "Bordmodules in zoeken verbergen",
            "Aramada pano modüllerini gizle", "إخفاء وحدات اللوحات في البحث");

        put(SEARCH_MODULES_DESCRIPTION,
            "Sponsored carousel and featured boards. \"Ideas for you\" stays.",
            "Carosello sponsorizzato e bacheche in evidenza. \"Idee per te\" resta.",
            "Carrusel patrocinado y tableros destacados. \"Ideas para ti\" se mantiene.",
            "Carrousel sponsorisé et tableaux à la une. « Idées pour vous » reste.",
            "Gesponsertes Karussell und vorgestellte Pinnwände. \"Ideen für dich\" bleibt.",
            "Carrossel patrocinado e pastas em destaque. \"Ideias para si\" fica.",
            "Спонсируемая карусель и рекомендованные доски. \"Идеи для вас\" остаются.",
            "スポンサーカルーセルと注目のボード。「あなたへのアイデア」は残ります。",
            "赞助轮播和精选图板。“为你推荐”会保留。",
            "스폰서 캐러셀과 추천 보드. \"회원님을 위한 아이디어\"는 유지됩니다.",
            "Sponsorowana karuzela i polecane tablice. \"Pomysły dla Ciebie\" zostają.",
            "Gesponsorde carrousel en uitgelichte borden. \"Ideeën voor jou\" blijft.",
            "Sponsorlu karusel ve öne çıkan panolar. \"Senin için fikirler\" kalır.",
            "الدوّار المموّل واللوحات المميزة. تبقى \"أفكار لك\".");

        put(SCREENSHOT_TITLE,
            "Hide screenshot share menu",
            "Nascondi menu condivisione screenshot", "Ocultar menú de compartir captura",
            "Masquer le menu de partage de capture", "Screenshot-Teilen-Menü ausblenden",
            "Ocultar menu de partilha de captura", "Скрыть меню отправки скриншота",
            "スクリーンショット共有メニューを非表示", "隐藏截图分享菜单",
            "스크린샷 공유 메뉴 숨기기", "Ukryj menu udostępniania zrzutu",
            "Deelmenu voor schermafbeeldingen verbergen", "Ekran görüntüsü paylaşım menüsünü gizle",
            "إخفاء قائمة مشاركة لقطة الشاشة");

        put(SCREENSHOT_DESCRIPTION,
            "Also stops Pinterest from watching for screenshots, where possible.",
            "Impedisce anche a Pinterest di sorvegliare gli screenshot, dove possibile.",
            "También impide que Pinterest vigile las capturas, cuando es posible.",
            "Empêche aussi Pinterest de surveiller les captures, quand c'est possible.",
            "Hindert Pinterest zudem daran, Screenshots zu überwachen, wo möglich.",
            "Também impede o Pinterest de vigiar as capturas, quando possível.",
            "Также мешает Pinterest следить за скриншотами, где это возможно.",
            "可能な場合、Pinterest によるスクリーンショットの監視も止めます。",
            "在可能的情况下，也会阻止 Pinterest 监视截图。",
            "가능한 경우 Pinterest의 스크린샷 감시도 중단합니다.",
            "Uniemożliwia też Pinterestowi śledzenie zrzutów ekranu, gdy to możliwe.",
            "Voorkomt ook dat Pinterest schermafbeeldingen bewaakt, waar mogelijk.",
            "Mümkün olduğunda Pinterest'in ekran görüntülerini izlemesini de engeller.",
            "يمنع أيضًا Pinterest من مراقبة لقطات الشاشة، حيثما أمكن.");

        put(SANITIZE_LINKS_TITLE,
            "Clean shared links",
            "Ripulisci i link condivisi", "Limpiar los enlaces compartidos",
            "Nettoyer les liens partagés", "Geteilte Links bereinigen",
            "Limpar as ligações partilhadas", "Очищать отправляемые ссылки",
            "共有リンクをクリーンにする", "清理分享链接", "공유 링크 정리",
            "Czyść udostępniane linki", "Gedeelde links opschonen",
            "Paylaşılan bağlantıları temizle", "تنظيف الروابط المشاركة");

        put(SANITIZE_LINKS_DESCRIPTION,
            "Removes tracking parameters and turns pin.it short links into the plain pin link. "
                + "Resolving a short link costs one network request.",
            "Toglie i parametri di tracciamento e trasforma gli short link pin.it nel link "
                + "normale del pin. Risolvere uno short link costa una richiesta di rete.",
            "Quita los parámetros de seguimiento y convierte los enlaces cortos pin.it en el "
                + "enlace normal del pin. Resolverlos cuesta una petición de red.",
            "Supprime les paramètres de suivi et transforme les liens courts pin.it en lien "
                + "normal du pin. Résoudre un lien court coûte une requête réseau.",
            "Entfernt Tracking-Parameter und wandelt pin.it-Kurzlinks in den normalen Pin-Link "
                + "um. Das Auflösen kostet eine Netzwerkanfrage.",
            "Remove os parâmetros de rastreio e converte as ligações curtas pin.it na ligação "
                + "normal do pin. Resolver uma ligação curta custa um pedido de rede.",
            "Убирает параметры отслеживания и превращает короткие ссылки pin.it в обычную "
                + "ссылку на пин. Разрешение короткой ссылки требует сетевого запроса.",
            "トラッキングパラメータを削除し、pin.it の短縮リンクを通常のピンのリンクに変換します。"
                + "短縮リンクの解決には通信が 1 回必要です。",
            "移除跟踪参数，并把 pin.it 短链接还原为普通的 Pin 链接。解析短链接需要一次网络请求。",
            "추적 매개변수를 제거하고 pin.it 단축 링크를 일반 핀 링크로 바꿉니다. "
                + "단축 링크 해석에는 네트워크 요청이 한 번 필요합니다.",
            "Usuwa parametry śledzenia i zamienia krótkie linki pin.it na zwykły link do pinu. "
                + "Rozwinięcie krótkiego linku kosztuje jedno zapytanie sieciowe.",
            "Verwijdert trackingparameters en zet pin.it-verkorte links om in de gewone pinlink. "
                + "Het oplossen kost één netwerkverzoek.",
            "Takip parametrelerini kaldırır ve pin.it kısa bağlantılarını normal pin "
                + "bağlantısına çevirir. Çözümlemek bir ağ isteğine mal olur.",
            "يزيل معاملات التتبع ويحوّل روابط pin.it المختصرة إلى رابط الدبوس العادي. "
                + "يتطلب حل الرابط المختصر طلب شبكة واحدًا.");

        put(SHARE_LINK_ONLY_TITLE,
            "Share the link only",
            "Condividi solo il link", "Compartir solo el enlace",
            "Partager seulement le lien", "Nur den Link teilen",
            "Partilhar apenas a ligação", "Отправлять только ссылку",
            "リンクだけを共有", "只分享链接", "링크만 공유",
            "Udostępniaj tylko link", "Alleen de link delen",
            "Yalnızca bağlantıyı paylaş", "مشاركة الرابط فقط");

        put(SHARE_LINK_ONLY_DESCRIPTION,
            "Drops the promotional sentence Pinterest puts in front of the link (\"Take a look at "
                + "this Pin! →\").",
            "Toglie la frase promozionale che Pinterest antepone al link (\"Dai un'occhiata a "
                + "questa idea →\").",
            "Quita la frase promocional que Pinterest antepone al enlace.",
            "Supprime la phrase promotionnelle que Pinterest ajoute devant le lien.",
            "Entfernt den Werbesatz, den Pinterest vor den Link setzt.",
            "Remove a frase promocional que o Pinterest coloca antes da ligação.",
            "Убирает рекламную фразу, которую Pinterest ставит перед ссылкой.",
            "Pinterest がリンクの前に付ける宣伝文を削除します。",
            "移除 Pinterest 加在链接前面的推广语句。",
            "Pinterest가 링크 앞에 붙이는 홍보 문구를 제거합니다.",
            "Usuwa zdanie promocyjne, które Pinterest dodaje przed linkiem.",
            "Verwijdert de promotiezin die Pinterest voor de link zet.",
            "Pinterest'in bağlantının önüne koyduğu tanıtım cümlesini kaldırır.",
            "يزيل العبارة الترويجية التي يضعها Pinterest قبل الرابط.");

        put(SYSTEM_SHARE_SHEET_TITLE,
            "Use the system share sheet",
            "Usa il pannello di condivisione di sistema",
            "Usar el panel de compartir del sistema",
            "Utiliser le panneau de partage du système",
            "System-Teilen-Dialog verwenden",
            "Usar o painel de partilha do sistema",
            "Использовать системное меню «Поделиться»",
            "システムの共有シートを使う",
            "使用系统分享面板",
            "시스템 공유 시트 사용",
            "Używaj systemowego panelu udostępniania",
            "Systeemdeelvenster gebruiken",
            "Sistem paylaşım panelini kullan",
            "استخدام لوحة المشاركة في النظام");

        put(SYSTEM_SHARE_SHEET_DESCRIPTION,
            "Shares pins through Android's own sheet, which reaches every installed app. Boards, "
                + "profiles and conversations keep Pinterest's sheet.",
            "Condivide i pin con il pannello di Android, che raggiunge tutte le app installate. "
                + "Bacheche, profili e conversazioni restano con il pannello di Pinterest.",
            "Comparte los pines con el panel de Android, que llega a todas las apps instaladas. "
                + "Tableros, perfiles y conversaciones mantienen el panel de Pinterest.",
            "Partage les épingles via le panneau d'Android, qui atteint toutes les applications "
                + "installées. Tableaux, profils et conversations gardent celui de Pinterest.",
            "Teilt Pins über den Android-Dialog, der alle installierten Apps erreicht. Pinnwände, "
                + "Profile und Unterhaltungen behalten den Dialog von Pinterest.",
            "Partilha os pins pelo painel do Android, que chega a todas as apps instaladas. "
                + "Pastas, perfis e conversas mantêm o painel do Pinterest.",
            "Отправляет пины через системное меню Android, доступное всем установленным "
                + "приложениям. Доски, профили и переписки используют меню Pinterest.",
            "Android の共有シートでピンを共有します。インストール済みのすべてのアプリに届きます。"
                + "ボード・プロフィール・会話は Pinterest のシートのままです。",
            "通过 Android 自带的分享面板分享 Pin，可发送到所有已安装的应用。图板、个人主页和对话仍使用 Pinterest 的面板。",
            "안드로이드 기본 공유 시트로 핀을 공유해 설치된 모든 앱에 보낼 수 있습니다. 보드·프로필·대화는 "
                + "핀터레스트 시트를 그대로 씁니다.",
            "Udostępnia piny panelem Androida, który obejmuje wszystkie zainstalowane aplikacje. "
                + "Tablice, profile i rozmowy zostają przy panelu Pinteresta.",
            "Deelt pins via het venster van Android, dat elke geïnstalleerde app bereikt. Borden, "
                + "profielen en gesprekken houden het venster van Pinterest.",
            "Pinleri, kurulu tüm uygulamalara ulaşan Android panelinden paylaşır. Panolar, "
                + "profiller ve sohbetler Pinterest'in panelinde kalır.",
            "يشارك التثبيتات عبر لوحة أندرويد التي تصل إلى كل التطبيقات المثبّتة. أما اللوحات "
                + "والملفات الشخصية والمحادثات فتبقى على لوحة Pinterest.");

        put(EXTERNAL_BROWSER_TITLE,
            "Open links in the default browser",
            "Apri i link nel browser predefinito",
            "Abrir los enlaces en el navegador predeterminado",
            "Ouvrir les liens dans le navigateur par défaut",
            "Links im Standardbrowser öffnen",
            "Abrir as ligações no navegador predefinido",
            "Открывать ссылки в браузере по умолчанию",
            "リンクを既定のブラウザーで開く",
            "在默认浏览器中打开链接",
            "링크를 기본 브라우저로 열기",
            "Otwieraj linki w domyślnej przeglądarce",
            "Links in de standaardbrowser openen",
            "Bağlantıları varsayılan tarayıcıda aç",
            "فتح الروابط في المتصفح الافتراضي");

        put(EXTERNAL_BROWSER_DESCRIPTION,
            "\"Visit site\" opens your browser instead of Pinterest's built-in one, which runs "
                + "inside the app and can watch the page. Pinterest's own pages stay in-app.",
            "\"Visita il sito\" apre il tuo browser invece di quello interno di Pinterest, che "
                + "gira dentro l'app e può osservare la pagina. Le pagine di Pinterest restano "
                + "dentro l'app.",
            "\"Visitar el sitio\" abre tu navegador en vez del interno de Pinterest, que se "
                + "ejecuta dentro de la app. Las páginas de Pinterest siguen dentro.",
            "« Visiter le site » ouvre votre navigateur au lieu de celui intégré à Pinterest, qui "
                + "s'exécute dans l'app. Les pages de Pinterest restent dans l'app.",
            "„Website besuchen“ öffnet deinen Browser statt des in Pinterest eingebauten, der "
                + "innerhalb der App läuft. Pinterest-eigene Seiten bleiben in der App.",
            "\"Visitar site\" abre o teu navegador em vez do interno do Pinterest, que corre "
                + "dentro da app. As páginas do próprio Pinterest ficam na app.",
            "«Перейти на сайт» открывает ваш браузер вместо встроенного в Pinterest, который "
                + "работает внутри приложения. Страницы самого Pinterest остаются внутри.",
            "「サイトにアクセス」で、アプリ内で動く Pinterest の内蔵ブラウザーではなく既定の"
                + "ブラウザーが開きます。Pinterest 自身のページはアプリ内のままです。",
            "点按“访问网站”时打开你的浏览器，而不是运行在应用内部的 Pinterest 内置浏览器。Pinterest 自己的页面仍在应用内打开。",
            "'사이트 방문'을 누르면 앱 안에서 실행되는 핀터레스트 내장 브라우저 대신 기본 브라우저가 열립니다. "
                + "핀터레스트 자체 페이지는 앱 안에 남습니다.",
            "„Odwiedź stronę” otwiera Twoją przeglądarkę zamiast wbudowanej w Pinterest, która "
                + "działa wewnątrz aplikacji. Strony samego Pinteresta zostają w aplikacji.",
            "\"Site bezoeken\" opent je eigen browser in plaats van die van Pinterest, die binnen "
                + "de app draait. Pinterest-eigen pagina's blijven in de app.",
            "\"Siteyi ziyaret et\" uygulamanın içinde çalışan Pinterest tarayıcısı yerine kendi "
                + "tarayıcını açar. Pinterest'in kendi sayfaları uygulamada kalır.",
            "يفتح زر \"زيارة الموقع\" متصفحك بدل متصفح Pinterest الداخلي الذي يعمل داخل التطبيق. "
                + "أما صفحات Pinterest نفسها فتبقى داخل التطبيق.");

        put(HIDE_COMMENTS_TITLE,
            "Hide comments",
            "Nascondi i commenti", "Ocultar los comentarios",
            "Masquer les commentaires", "Kommentare ausblenden",
            "Ocultar os comentários", "Скрыть комментарии",
            "コメントを非表示にする", "隐藏评论", "댓글 숨기기",
            "Ukryj komentarze", "Reacties verbergen",
            "Yorumları gizle", "إخفاء التعليقات");

        put(HIDE_COMMENTS_DESCRIPTION,
            "Removes the comments section, the preview with \"view all\" and the comment button, "
                + "so the section cannot be reached.",
            "Toglie la sezione commenti, l'anteprima con \"visualizza tutti\" e il tasto dei "
                + "commenti, così la sezione non è raggiungibile.",
            "Quita la sección de comentarios, la vista previa con \"ver todos\" y el botón de "
                + "comentarios, de modo que la sección no se pueda abrir.",
            "Supprime la section commentaires, l'aperçu avec « tout voir » et le bouton de "
                + "commentaire, de sorte que la section devienne inaccessible.",
            "Entfernt den Kommentarbereich, die Vorschau mit „Alle ansehen“ und die "
                + "Kommentarschaltfläche, sodass der Bereich nicht erreichbar ist.",
            "Remove a secção de comentários, a pré-visualização com \"ver tudo\" e o botão de "
                + "comentários, de modo que a secção não seja acessível.",
            "Убирает раздел комментариев, предпросмотр с «смотреть все» и кнопку комментариев, "
                + "так что раздел становится недоступен.",
            "コメント欄、「すべて見る」付きのプレビュー、コメントボタンを取り除き、"
                + "コメント欄に到達できないようにします。",
            "移除评论区、带“查看全部”的预览以及评论按钮，使评论区无法进入。",
            "댓글 섹션과 '모두 보기'가 있는 미리보기, 댓글 버튼을 없애 댓글 섹션에 들어갈 수 없게 합니다.",
            "Usuwa sekcję komentarzy, podgląd z „zobacz wszystkie” oraz przycisk komentarzy, więc "
                + "sekcja staje się niedostępna.",
            "Verwijdert de reactiesectie, het voorbeeld met \"alles bekijken\" en de "
                + "reactieknop, zodat de sectie onbereikbaar wordt.",
            "Yorumlar bölümünü, \"tümünü gör\" içeren önizlemeyi ve yorum düğmesini kaldırır; "
                + "böylece bölüme ulaşılamaz.",
            "يزيل قسم التعليقات والمعاينة التي تحتوي على \"عرض الكل\" وزر التعليقات، فلا يعود "
                + "بالإمكان الوصول إلى القسم.");

        put(SEARCH_HISTORY_TITLE,
            "Hide search history",
            "Nascondi la cronologia delle ricerche", "Ocultar historial de búsqueda",
            "Masquer l'historique de recherche", "Suchverlauf ausblenden",
            "Ocultar histórico de pesquisa", "Скрыть историю поиска",
            "検索履歴を非表示", "隐藏搜索历史", "검색 기록 숨기기",
            "Ukryj historię wyszukiwania", "Zoekgeschiedenis verbergen",
            "Arama geçmişini gizle", "إخفاء سجل البحث");

        put(SEARCH_HISTORY_DESCRIPTION,
            "Hides recent searches without deleting them from your Pinterest account.",
            "Nasconde le ricerche recenti senza eliminarle dal tuo account Pinterest.",
            "Oculta las búsquedas recientes sin eliminarlas de tu cuenta de Pinterest.",
            "Masque les recherches récentes sans les supprimer de votre compte Pinterest.",
            "Blendet letzte Suchanfragen aus, ohne sie aus deinem Pinterest-Konto zu löschen.",
            "Oculta as pesquisas recentes sem as eliminar da tua conta Pinterest.",
            "Скрывает недавние запросы, не удаляя их из вашего аккаунта Pinterest.",
            "最近の検索を非表示にします。Pinterest アカウントからは削除されません。",
            "隐藏最近的搜索，但不会从你的 Pinterest 账号中删除这些记录。",
            "최근 검색을 숨깁니다. Pinterest 계정에서 검색 기록을 삭제하지는 않습니다.",
            "Ukrywa ostatnie wyszukiwania bez usuwania ich z konta Pinterest.",
            "Verbergt recente zoekopdrachten zonder ze uit je Pinterest-account te verwijderen.",
            "Son aramaları Pinterest hesabınızdan silmeden gizler.",
            "يخفي عمليات البحث الأخيرة دون حذفها من حسابك على Pinterest.");

        put(EMAIL_DIALOG_TITLE,
            "Disable the \"confirm your email\" dialog",
            "Nascondi la finestra \"Conferma la tua email\"",
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

        put(LONG_PRESS_DOWNLOAD_TITLE,
            "Download button on long press",
            "Download con pressione prolungata", "Botón de descarga al mantener pulsado",
            "Bouton de téléchargement en appui long", "Download-Taste beim langen Drücken",
            "Botão de transferência ao premir longamente", "Кнопка загрузки при долгом нажатии",
            "長押しでダウンロードボタン", "长按显示下载按钮", "길게 눌러 다운로드 버튼",
            "Przycisk pobierania przy długim naciśnięciu", "Downloadknop bij lang indrukken",
            "Uzun basmada indirme düğmesi", "زر التنزيل عند الضغط المطوّل");

        put(LONG_PRESS_DOWNLOAD_DESCRIPTION,
            "Adds a download button to the long-press menu to save a pin image on your device without opening the pin.",
            "Aggiunge un pulsante al menu della pressione prolungata per scaricare l’immagine sul dispositivo senza aprire il Pin.",
            "Añade un botón al menú de pulsación prolongada para descargar la imagen en tu dispositivo sin abrir el Pin.",
            "Ajoute un bouton au menu d’appui long pour télécharger l’image sur votre appareil sans ouvrir l’épingle.",
            "Fügt dem Menü beim langen Drücken eine Schaltfläche hinzu, um das Bild auf dein Gerät herunterzuladen, ohne den Pin zu öffnen.",
            "Adiciona um botão ao menu de pressão prolongada para transferir a imagem para o dispositivo sem abrir o Pin.",
            "Добавляет кнопку в меню долгого нажатия, чтобы скачать изображение на устройство, не открывая пин.",
            "長押しメニューにダウンロードボタンを追加し、ピンを開かずに画像を端末に保存できます。",
            "在长按菜单中添加下载按钮，无需打开 Pin 即可将图片保存到设备。",
            "길게 누르기 메뉴에 다운로드 버튼을 추가해 핀을 열지 않고 이미지를 기기에 저장합니다.",
            "Dodaje przycisk do menu po przytrzymaniu, aby pobrać obraz na urządzenie bez otwierania pina.",
            "Voegt een downloadknop toe aan het menu bij lang indrukken om de afbeelding op je apparaat op te slaan zonder de pin te openen.",
            "Pini açmadan görseli cihazınıza indirmek için uzun basma menüsüne bir indirme düğmesi ekler.",
            "يضيف زر تنزيل إلى قائمة الضغط المطوّل لحفظ الصورة على جهازك دون فتح الدبوس.");

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
            "Patch activity recorded in this session. Open the relevant screen to check a patch.",
            "Attività delle patch registrata in questa sessione. Apri la schermata interessata per controllare una patch.",
            "Actividad de los parches registrada en esta sesión. Abre la pantalla correspondiente para comprobar un parche.",
            "Activité des correctifs enregistrée pendant cette session. Ouvrez l’écran concerné pour vérifier un correctif.",
            "In dieser Sitzung erfasste Patch-Aktivität. Öffne den entsprechenden Bildschirm, um einen Patch zu prüfen.",
            "Atividade dos patches registada nesta sessão. Abre o ecrã correspondente para verificar um patch.",
            "Действия патчей, зарегистрированные в этом сеансе. Откройте соответствующий экран, чтобы проверить патч.",
            "このセッションで記録されたパッチの動作です。確認したいパッチに対応する画面を開いてください。",
            "本次会话记录的补丁活动。请打开相关页面以检查补丁。",
            "이번 세션에 기록된 패치 동작입니다. 패치를 확인하려면 관련 화면을 여세요.",
            "Aktywność poprawek zarejestrowana w tej sesji. Otwórz odpowiedni ekran, aby sprawdzić poprawkę.",
            "Patchactiviteit die tijdens deze sessie is vastgelegd. Open het bijbehorende scherm om een patch te controleren.",
            "Bu oturumda kaydedilen yama etkinliği. Bir yamayı kontrol etmek için ilgili ekranı açın.",
            "نشاط التعديلات المسجّل في هذه الجلسة. افتح الشاشة المعنية للتحقق من التعديل.");

        put(DIAGNOSTICS_EMPTY,
            "No patch activity recorded yet. Open the Pinterest feed, then return here.",
            "Nessuna attività delle patch ancora registrata. Apri il feed di Pinterest, poi torna qui.",
            "Aún no se ha registrado actividad de los parches. Abre el inicio de Pinterest y vuelve aquí.",
            "Aucune activité des correctifs enregistrée. Ouvrez le fil Pinterest, puis revenez ici.",
            "Noch keine Patch-Aktivität erfasst. Öffne den Pinterest-Feed und kehre dann hierher zurück.",
            "Ainda não foi registada atividade dos patches. Abre o feed do Pinterest e volta aqui.",
            "Действия патчей ещё не зарегистрированы. Откройте ленту Pinterest и вернитесь сюда.",
            "パッチの動作はまだ記録されていません。Pinterest のフィードを開いてから、ここに戻ってください。",
            "尚未记录补丁活动。请打开 Pinterest 信息流后返回此处。",
            "아직 기록된 패치 동작이 없습니다. Pinterest 피드를 연 뒤 여기로 돌아오세요.",
            "Nie zarejestrowano jeszcze aktywności poprawek. Otwórz stronę główną Pinterest i wróć tutaj.",
            "Nog geen patchactiviteit vastgelegd. Open de Pinterest-feed en kom daarna hier terug.",
            "Henüz yama etkinliği kaydedilmedi. Pinterest akışını açıp buraya geri dönün.",
            "لم يُسجَّل أي نشاط للتعديلات بعد. افتح موجز Pinterest ثم عُد إلى هنا.");

        put(BACKUP_HINT,
            "Uninstalling the app or clearing its data resets Morphe settings. Export them first to restore them later.",
            "Disinstallare l'app o cancellarne i dati ripristina le impostazioni Morphe. Esportale prima per poterle recuperare.",
            "Desinstalar la app o borrar sus datos restablece los ajustes de Morphe. Expórtalos antes para poder recuperarlos.",
            "Désinstaller l'application ou effacer ses données réinitialise les paramètres Morphe. Exportez-les d'abord pour pouvoir les restaurer.",
            "Durch Deinstallieren der App oder Löschen ihrer Daten werden die Morphe-Einstellungen zurückgesetzt. Exportiere sie vorher, um sie später wiederherzustellen.",
            "Desinstalar a aplicação ou limpar os seus dados repõe as definições Morphe. Exporta-as primeiro para as poderes restaurar.",
            "Удаление приложения или очистка его данных сбрасывает настройки Morphe. Сначала экспортируйте их, чтобы затем восстановить.",
            "アプリをアンインストールするかデータを消去すると、Morphe の設定がリセットされます。復元できるよう、先にエクスポートしてください。",
            "卸载应用或清除应用数据会重置 Morphe 设置。请先导出设置，以便之后恢复。",
            "앱을 삭제하거나 앱 데이터를 지우면 Morphe 설정이 초기화됩니다. 나중에 복원할 수 있도록 먼저 내보내세요.",
            "Odinstalowanie aplikacji lub wyczyszczenie jej danych resetuje ustawienia Morphe. Najpierw je wyeksportuj, aby móc je przywrócić.",
            "Als je de app verwijdert of de appgegevens wist, worden de Morphe-instellingen gereset. Exporteer ze eerst om ze later te kunnen herstellen.",
            "Uygulamayı kaldırmak veya verilerini temizlemek Morphe ayarlarını sıfırlar. Daha sonra geri yükleyebilmek için önce ayarları dışa aktarın.",
            "تؤدي إزالة التطبيق أو مسح بياناته إلى إعادة ضبط إعدادات Morphe. صدّر الإعدادات أولًا لتتمكن من استعادتها لاحقًا.");

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
