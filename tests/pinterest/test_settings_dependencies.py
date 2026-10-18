#!/usr/bin/env python3
"""Exercise the production settings store against in-memory Android preferences."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'extensions/extension/src/main/java/app/template/extension/pinterest/MorpheSettingsStore.java'
STUBS = {
'android/content/Context.java': 'package android.content; public class Context { public static int MODE_PRIVATE=0; }',
'android/app/Application.java': 'package android.app; public class Application extends android.content.Context { public android.content.SharedPreferences getSharedPreferences(String n,int m){return null;} }',
'android/util/Log.java': 'package android.util; public class Log {public static int i(String a,String b){return 0;}public static int e(String a,String b){return 0;}public static int e(String a,String b,Throwable t){return 0;}}',
'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 public final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
 public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
 public Editor edit(){return new Editor();}public class Editor {
 public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
 public Editor putString(String k,String v){values.put(k,v);return this;}
 public void apply(){}public boolean commit(){return true;}}
}''',
'app/browzomje/extension/pinterest/MorpheLog.java': 'package app.browzomje.extension.pinterest;class MorpheLog {static String TAG="test";static void hookFired(String c,String d){}static void ok(String c,String d){}}',
'app/browzomje/extension/pinterest/PinMenuFilter.java': 'package app.browzomje.extension.pinterest;class PinMenuFilter {static java.util.Set<String> recommended(){return java.util.Collections.emptySet();}static String encode(java.util.Set<String> s){return "";}}',
'app/browzomje/extension/pinterest/SettingsTest.java': '''package app.browzomje.extension.pinterest;
public class SettingsTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  android.content.SharedPreferences prefs=new android.content.SharedPreferences();
  java.lang.reflect.Field field=MorpheSettingsStore.class.getDeclaredField("cachedPrefs");field.setAccessible(true);field.set(null,prefs);
  check(!HomeFeedRefresh.shouldKeepFeed(),"fresh install uses original refresh");
  check(!MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS),"videos opt in");
  String info=MorpheSettingsStore.KEY_IMAGE_INFO,replace=MorpheSettingsStore.KEY_INFO_REPLACE;
  String like=MorpheSettingsStore.KEY_HIDE_LIKE,comment=MorpheSettingsStore.KEY_HIDE_COMMENT_BUTTON;
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_HIDE_COMMENTS,false);
  for(boolean i:new boolean[]{false,true})for(boolean r:new boolean[]{false,true}) {
   prefs.edit().putBoolean(info,i).putBoolean(replace,r).apply();
   check(MorpheSettingsStore.effective(replace)==(i&&r),"runtime dependency");
   check(MorpheSettingsStore.isToggleEnabled(replace)==i,"replacement availability");
   check(MorpheSettingsStore.isToggleEnabled(like)==!(i&&r),"like availability");
   check(MorpheSettingsStore.isToggleEnabled(comment)==!(i&&r),"comment availability");
  }
  MorpheSettingsStore.set(like,false);MorpheSettingsStore.set(comment,true);
  MorpheSettingsStore.set(info,true);MorpheSettingsStore.set(replace,true);
  MorpheSettingsStore.set(info,false);
  check(!prefs.getBoolean(replace,true),"disabling info clears replacement");
  MorpheSettingsStore.set(replace,true);check(!MorpheSettingsStore.effective(replace),"cannot enable without info");
  MorpheSettingsStore.set(info,true);check(!MorpheSettingsStore.effective(replace),"replacement stays off after info reenabled");
  check(!MorpheSettingsStore.effective(like)&&MorpheSettingsStore.effective(comment),"individual choices preserved");
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_HIDE_COMMENTS,true);
  check(!MorpheSettingsStore.isToggleEnabled(comment),"all comments supersedes button");
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_HIDE_COMMENTS,false);
  check(MorpheSettingsStore.isToggleEnabled(comment)&&MorpheSettingsStore.effective(comment),"button choice restored");
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_KEEP_HOME_FEED,false);
  check(!HomeFeedRefresh.shouldKeepFeed(),"original refresh selectable");
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_KEEP_HOME_FEED,true);
  check(HomeFeedRefresh.shouldKeepFeed(),"keep feed gate");
  MorpheSettingsStore.applyRecommended();check(MorpheSettingsStore.effective(replace)&&!MorpheSettingsStore.isToggleEnabled(like),"preset dependency");
  check(!HomeFeedRefresh.shouldKeepFeed(),"recommended preset disables keep feed");
  check(!MorpheSettingsStore.effective(MorpheSettingsStore.KEY_HIDE_VIDEO_PINS),"recommended preset leaves videos visible");
  MorpheSettingsStore.set(MorpheSettingsStore.KEY_KEEP_HOME_FEED,true);
  check(HomeFeedRefresh.shouldKeepFeed(),"explicit saved preference still honored");
  System.out.println("PASS settings combinations, writes, legacy/imported values and preset");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='pinterest-settings-test-') as tmp:
    files=[]
    for name, content in STUBS.items():
        path=Path(tmp)/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);files.append(str(path))
    subprocess.run(['javac','-d',tmp,*files,str(SOURCE),str(SOURCE.with_name("HomeFeedRefresh.java"))],check=True)
    subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.SettingsTest'],check=True)
