"""Exercise the production launcher gate without a device or app navigation mocks."""
from pathlib import Path
import subprocess,tempfile
src=Path(__file__).resolve().parents[2]/'extensions/extension/src/main/java/app/template/extension/pinterest/StartupTab.java'
files={
'android/os/Bundle.java':'''package android.os; public class Bundle {public java.util.Map<String,Object> values=new java.util.HashMap<>();public boolean isEmpty(){return values.isEmpty();}}''',
'android/content/Intent.java':'''package android.content;public class Intent {
 public static String ACTION_MAIN="main",CATEGORY_LAUNCHER="launcher";public static int FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY=0x100000;
 public String action=ACTION_MAIN,data;public boolean launcher=true;public int flags;public android.os.Bundle extras;
 public String getAction(){return action;}public boolean hasCategory(String c){return launcher;}public String getData(){return data;}public int getFlags(){return flags;}
 public android.os.Bundle getExtras(){return extras;}public boolean getBooleanExtra(String k,boolean d){return extras!=null&&extras.values.get(k) instanceof Boolean?(Boolean)extras.values.get(k):d;}public Intent removeExtra(String k){if(extras!=null)extras.values.remove(k);return this;}public Intent putExtra(String k,boolean v){if(extras==null)extras=new android.os.Bundle();extras.values.put(k,v);return this;}public Intent putExtra(String k,String v){if(extras==null)extras=new android.os.Bundle();extras.values.put(k,v);return this;}}
''',
'android/app/Activity.java':'''package android.app;public class Activity {public android.content.Intent intent=new android.content.Intent();public boolean destroyed,finishing;public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}public android.content.Intent getIntent(){return intent;}}''',
'app/browzomje/extension/pinterest/MorpheSettingsStore.java':'''package app.browzomje.extension.pinterest;class MorpheSettingsStore {static String tab="HOME";static boolean hidden;static String startupTab(){return tab;}static boolean isSearchButtonHidden(){return hidden;}}''',
'app/browzomje/extension/pinterest/MorpheLog.java':'''package app.browzomje.extension.pinterest;class MorpheLog{static void i(String a,String b){}}''',
'app/browzomje/extension/pinterest/StartupTest.java':'''package app.browzomje.extension.pinterest;
import android.app.Activity;import android.os.Bundle;
public class StartupTest {
 static String KEY="com.pinterest.EXTRA_PENDING_TAB";
 static void prepare(Activity a,Object state){StartupTab.onLauncherCreate(a,state);StartupTab.prepareAuthenticated(a);}
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void untouched(Activity a,Object state,String why){prepare(a,state);check(a.intent.extras==null||!a.intent.extras.values.containsKey(KEY),why);}
 public static void main(String[]args){
 untouched(new Activity(),null,"default HOME does not change intent");
 MorpheSettingsStore.tab="PROFILE";Activity a=new Activity();prepare(a,null);check("PROFILE".equals(a.intent.extras.values.get(KEY)),"native Profile route");
 prepare(a,null);check(a.intent.extras.values.size()==2,"no duplicate state");
 MorpheSettingsStore.tab="SEARCH";a=new Activity();prepare(a,null);check("SEARCH".equals(a.intent.extras.values.get(KEY)),"Search route");
 MorpheSettingsStore.hidden=true;untouched(new Activity(),null,"hidden Search uses Home");MorpheSettingsStore.hidden=false;
 untouched(new Activity(),new Bundle(),"saved/restored state");
 a=new Activity();a.intent.action="view";a.intent.data="https://www.pinterest.com/pin/123/";untouched(a,null,"Pin deep link");
 a=new Activity();a.intent.action="send";untouched(a,null,"share launch");
 a=new Activity();a.intent.launcher=false;untouched(a,null,"internal routing/onboarding");
 a=new Activity();a.intent.flags=android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;untouched(a,null,"recent tasks");
 a=new Activity();a.intent.extras=new Bundle();a.intent.extras.values.put("notification",true);untouched(a,null,"notification wins");
 a=new Activity();a.intent.extras=new Bundle();a.intent.extras.values.put(KEY,"HOME");prepare(a,null);check("HOME".equals(a.intent.extras.values.get(KEY)),"explicit destination preserved");
 Activity launcher=new Activity();StartupTab.onLauncherCreate(launcher,null);StartupTab.cancelLauncher(launcher);StartupTab.prepareAuthenticated(launcher);check(launcher.intent.extras==null,"new intent cancels pending launcher");
 a=new Activity();prepare(a,null);StartupTab.onMainCreate(a,new Bundle());check(!a.intent.extras.values.containsKey(KEY),"native restoration wins");a.destroyed=true;
 a=new Activity();a.intent.putExtra(KEY,"HOME");StartupTab.onMainCreate(a,new Bundle());check("HOME".equals(a.intent.extras.values.get(KEY)),"external native destination not removed");
 untouched(new Activity(),null,"live main prevents warm launch override");a.destroyed=true;
 prepare(null,null);
 System.out.println("PASS production startup gate: Home/Profile/Search, hidden Search, restored state, history, deep link, share, notification and native destination");
 }}'''}
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,body in files.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 subprocess.run(['javac','-d',tmp,*paths,str(src)],check=True)
 subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.StartupTest'],check=True)
