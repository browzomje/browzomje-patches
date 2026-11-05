from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
src = root / 'extensions/extension/src/main/java/app/template/extension/pinterest/MorpheStrings.java'
stubs = {
    'android/content/Context.java': 'package android.content; public class Context { public Resources getResources() { return new Resources(); } }',
    'android/content/Resources.java': 'package android.content; public class Resources { public Configuration getConfiguration() { return new Configuration(); } }',
    'android/content/Configuration.java': 'package android.content; public class Configuration { public android.os.LocaleList getLocales() { return new android.os.LocaleList(); } }',
    'android/os/LocaleList.java': 'package android.os; public class LocaleList { public boolean isEmpty() { return false; } public java.util.Locale get(int i) { return java.util.Locale.forLanguageTag("zh-TW"); } }',
    'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=34; } public static class VERSION_CODES { public static final int N=24; } }',
    'MorpheSettingsStore.java': 'package app.browzomje.extension.pinterest; class MorpheSettingsStore { static android.content.Context context; static android.content.Context appContext() { return context; } }',
}
with tempfile.TemporaryDirectory() as tmp:
 p = Path(tmp)
 paths = []
 for name, contents in stubs.items():
  stub = p / name
  stub.parent.mkdir(parents=True, exist_ok=True)
  stub.write_text(contents)
  paths.append(str(stub))
 subprocess.run(
  ['python3', str(root/'scripts/pinterest_l10n.py'), '--output', tmp], check=True,
 )
 generated=p/'app/browzomje/extension/pinterest/PinterestStringCatalog.java'
 test=p/'SearchTest.java'
 test.write_text('''package app.browzomje.extension.pinterest;
import java.util.Arrays;
import java.util.Locale;
public class SearchTest {
 static void check(boolean condition, String message) {
  if (!condition) throw new AssertionError(message);
 }
 public static void main(String[] args) {
  for (String[] locale : new String[][]{
    {"zh-CN", "zh-CN"}, {"zh-TW", "zh-TW"}, {"zh-Hant", "zh-TW"},
    {"zh-Hans-TW", "zh-CN"}, {"pt-PT", "pt-PT"}, {"pt-BR", "pt-BR"},
    {"pt", "pt-BR"}, {"in", "id"}, {"iw", "he"}, {"tl", "fil"},
    {"uk-UA", "uk"}, {"en-AU", "en"}, {"es-ES", "es"}, {"en-ZA", "en"},
  }) {
   check(MorpheStrings.catalogLanguage(Locale.forLanguageTag(locale[0])).equals(locale[1]), locale[0]);
  }
  var keys = PinterestStringCatalog.KEYS;
  var en = PinterestStringCatalog.forLanguage("en");
  for (String language : PinterestStringCatalog.LANGUAGES) {
   Locale.setDefault(Locale.forLanguageTag(language));
   var values = PinterestStringCatalog.forLanguage(language);
   check(values.length == keys.length, language);
   for (int i = 0; i < keys.length; i++) {
    String key = keys[i], visible = values[i];
    check(MorpheStrings.get(key).equals(visible), language + "/" + key);
    if (key.endsWith("_title") || key.endsWith("_description")) {
     String indexed = MorpheStrings.normalizeSearch(MorpheStrings.searchAliases(visible));
     check(indexed.contains(MorpheStrings.normalizeSearch(en[i])), language + "/" + key + " alias");
    }
   }
  }
  int search = Arrays.binarySearch(keys, "settings_search");
  check(!PinterestStringCatalog.forLanguage("zh-CN")[search].equals(
          PinterestStringCatalog.forLanguage("zh-TW")[search]), "Chinese variants");
  Locale.setDefault(Locale.US);
  MorpheSettingsStore.context = new android.content.Context();
  check(MorpheStrings.get("settings_search").equals(PinterestStringCatalog.forLanguage("zh-TW")[search]),
        "app locale takes precedence over device locale");
  MorpheSettingsStore.context = null;
  check(MorpheStrings.get("settings_search").equals(en[search]), "device locale fallback");
  check(MorpheStrings.get("unknown_key").equals("unknown_key"), "fallback");
  check(MorpheStrings.normalizeSearch("ÉCRAN İ").equals("ecran i"), "normalization");
  System.out.println("PASS runtime locale selection, all catalog entries and multilingual search aliases");
 }
}''')
 subprocess.run(['javac','-d',tmp,*paths,str(test),str(src),str(generated)],check=True)
 subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.SearchTest'],check=True)
