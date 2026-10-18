"""Production store + JSON backup: string enum roundtrip, legacy import, atomic validation."""
import ast,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'extensions/extension/src/main/java/app/template/extension/pinterest'
tree=ast.parse((Path(__file__).with_name('test_settings_dependencies.py')).read_text())
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='STUBS' for t in n.targets)))
stubs.pop('app/browzomje/extension/pinterest/SettingsTest.java')
stubs['app/browzomje/extension/pinterest/MorpheLog.java']=stubs['app/browzomje/extension/pinterest/MorpheLog.java'].replace('static String TAG=', 'static String SETTINGS_ENTRY="settings";static String TAG=')
stubs['android/content/Context.java']='''package android.content;public class Context {public static int MODE_PRIVATE=0;public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public String getPackageName(){return "com.pinterest";}}'''
stubs['android/content/pm/PackageManager.java']='''package android.content.pm;public class PackageManager {public PackageInfo getPackageInfo(String p,int f){return new PackageInfo();}}'''
stubs['android/content/pm/PackageInfo.java']='package android.content.pm;public class PackageInfo {public String versionName="14.39.0";}'
stubs['app/browzomje/extension/pinterest/BackupTest.java']='''package app.browzomje.extension.pinterest;
public class BackupTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[]args)throws Exception {
 android.content.SharedPreferences prefs=new android.content.SharedPreferences();java.lang.reflect.Field f=MorpheSettingsStore.class.getDeclaredField("cachedPrefs");f.setAccessible(true);f.set(null,prefs);
 MorpheSettingsStore.setStartupTab("PROFILE");String exported=MorpheSettingsBackup.export(new android.content.Context());
 check("PROFILE".equals(new org.json.JSONObject(exported).getJSONObject("settings").getString("startup_tab")));
 MorpheSettingsStore.setStartupTab("HOME");MorpheSettingsBackup.apply(exported);check("PROFILE".equals(MorpheSettingsStore.startupTab()));
 MorpheSettingsBackup.apply("{\\"settings\\":{\\"hide_video_pins\\":false}}");check("PROFILE".equals(MorpheSettingsStore.startupTab()));
 for(String bad:new String[]{"\\"INVALID\\"","true","17"}) {
 try {MorpheSettingsBackup.apply("{\\"settings\\":{\\"hide_video_pins\\":true,\\"startup_tab\\":"+bad+"}}");throw new AssertionError();}catch(org.json.JSONException expected){}
 check(!MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS));check("PROFILE".equals(MorpheSettingsStore.startupTab()));
 }
 check(MorpheSettingsBackup.apply("{\\"settings\\":{\\"startup_tab\\":\\"SEARCH\\"}}") == 1);check("SEARCH".equals(MorpheSettingsStore.startupTab()));
 System.out.println("PASS startup tab JSON roundtrip, legacy backup preservation, string type/enum validation before any writes");
 }}'''
jars=list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.json/json').glob('**/*.jar'))
assert jars,'org.json runtime JAR needed for backup test'
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,body in stubs.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 cp=str(jars[0]);subprocess.run(['javac','-cp',cp,'-d',tmp,*paths,str(source/'MorpheSettingsStore.java'),str(source/'MorpheSettingsBackup.java')],check=True)
 subprocess.run(['java','-cp',tmp+':'+cp,'app.browzomje.extension.pinterest.BackupTest'],check=True)
