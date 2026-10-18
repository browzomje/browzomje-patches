"""Production store + JSON backup: string enum roundtrip, legacy import, atomic validation."""
import ast,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'extensions/extension/src/main/java/app/template/extension/pinterest'
# Guard coverage against newly added UI/runtime settings, excluding the retired profile toggle.
store=(source/'MorpheSettingsStore.java').read_text()
keys=set(re.findall(r'(?:public )?static final String (KEY_\w+) =',store))
array=store.split('public static final String[] KEYS = {',1)[1].split('};',1)[0]
covered=set(re.findall(r'KEY_\w+',array))|{'KEY_STARTUP_TAB','KEY_MENU_FILTER','KEY_HIDE_PROFILE_BUTTON'}
assert keys==covered,(keys-covered,covered-keys)
tree=ast.parse((Path(__file__).with_name('test_settings_dependencies.py')).read_text())
stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='STUBS' for t in n.targets)))
stubs.pop('app/browzomje/extension/pinterest/SettingsTest.java')
stubs['app/browzomje/extension/pinterest/MorpheLog.java']=stubs['app/browzomje/extension/pinterest/MorpheLog.java'].replace('static String TAG=', 'static String SETTINGS_ENTRY="settings";static String TAG=')
stubs['android/content/SharedPreferences.java']='package android.content;public class SharedPreferences {\n public final java.util.Map<String,Object> values=new java.util.HashMap<>();public boolean fail;public int commits;\n public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}\n public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}\n public Editor edit(){return new Editor();}public class Editor {\n final java.util.Map<String,Object> pending=new java.util.HashMap<>();\n public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}\n public Editor putString(String k,String v){pending.put(k,v);return this;}\n public void apply(){values.putAll(pending);}public boolean commit(){commits++;values.putAll(pending);return !fail;}}\n}'
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

 // Every current toggle, both string settings, and dependency combinations roundtrip.
 for(boolean enabled:new boolean[]{false,true}) {
  prefs.values.clear();
  for(String key:MorpheSettingsStore.KEYS) MorpheSettingsStore.set(key,enabled);
  MorpheSettingsStore.setMenuFilter("follow,report,custom_future_action");
  MorpheSettingsStore.setStartupTab("PROFILE");
  String snapshot=MorpheSettingsBackup.export(new android.content.Context());
  org.json.JSONObject expected=new org.json.JSONObject(snapshot).getJSONObject("settings");
  check(expected.length()==MorpheSettingsStore.KEYS.length+2);
  prefs.values.clear();int before=prefs.commits;
  check(MorpheSettingsBackup.apply(snapshot)==26);check(prefs.commits==before+1);
  org.json.JSONObject actual=new org.json.JSONObject(MorpheSettingsBackup.export(new android.content.Context())).getJSONObject("settings");
  for(java.util.Iterator<String> keys=expected.keys();keys.hasNext();) {String key=keys.next();check(expected.get(key).equals(actual.get(key)));}
 }
 for(String key:MorpheSettingsStore.KEYS) {
  MorpheSettingsStore.applyRecommended();
  MorpheSettingsStore.set(key,!MorpheSettingsStore.effective(key));
  String snapshot=MorpheSettingsBackup.export(new android.content.Context());
  prefs.values.clear();MorpheSettingsBackup.apply(snapshot);
  org.json.JSONObject expected=new org.json.JSONObject(snapshot).getJSONObject("settings");
  org.json.JSONObject actual=new org.json.JSONObject(MorpheSettingsBackup.export(new android.content.Context())).getJSONObject("settings");
  for(java.util.Iterator<String> keys=expected.keys();keys.hasNext();) {String k=keys.next();check(expected.get(k).equals(actual.get(k)));}
 }
 java.util.Map<String,Object> previous=new java.util.HashMap<>(prefs.values);
 for(String bad:new String[]{"null","17","[]","{}","\\"true\\""}) {
  try {MorpheSettingsBackup.apply("{\\"settings\\":{\\"disable_ads\\":false,\\"verbose_logging\\":"+bad+"}}");throw new AssertionError();}catch(org.json.JSONException expected){}
  check(previous.equals(prefs.values));
 }
 prefs.fail=true;
 try {MorpheSettingsBackup.apply(MorpheSettingsBackup.export(new android.content.Context()));throw new AssertionError();}catch(java.io.IOException expected){}
 prefs.fail=false;f.set(null,null);
 try {MorpheSettingsBackup.apply("{\\"settings\\":{\\"disable_ads\\":true}}");throw new AssertionError();}catch(java.io.IOException expected){}
 System.out.println("PASS all 26 settings roundtrip, legacy preservation, strict types, one commit and persistence errors");
 }}'''
jars=list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.json/json').glob('**/*.jar'))
assert jars,'org.json runtime JAR needed for backup test'
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,body in stubs.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 cp=str(jars[0]);subprocess.run(['javac','-cp',cp,'-d',tmp,*paths,str(source/'MorpheSettingsStore.java'),str(source/'MorpheSettingsBackup.java')],check=True)
 subprocess.run(['java','-cp',tmp+':'+cp,'app.browzomje.extension.pinterest.BackupTest'],check=True)
