#!/usr/bin/env python3
"""Exercise real menu filtering and the exact-Pin native download bridge with Android stubs."""
from pathlib import Path
import tempfile, subprocess
BASE=Path(__file__).resolve().parents[2]/'extensions/extension/src/main/java/app/template/extension/pinterest'
SOURCES={
'android/content/res/Configuration.java': 'package android.content.res; public class Configuration {public Configuration(){}public Configuration(Configuration c){}public void setLocale(java.util.Locale l){}}',
'android/content/res/Resources.java': '''package android.content.res;public class Resources {
 public final java.util.Map<String,String> labels=new java.util.LinkedHashMap<>();private final java.util.List<String> keys=new java.util.ArrayList<>();
 public int getIdentifier(String n,String t,String p){if(!labels.containsKey(n))return 0;if(!keys.contains(n))keys.add(n);return keys.indexOf(n)+1;}
 public String getString(int n){return labels.get(keys.get(n-1));}public Configuration getConfiguration(){return new Configuration();}
}''',
'android/content/Context.java': 'package android.content;public class Context {final android.content.res.Resources r=new android.content.res.Resources();public android.content.res.Resources getResources(){return r;}public String getString(int id){return r.getString(id);}public String getPackageName(){return "test";}public Context createConfigurationContext(android.content.res.Configuration c){return this;}}',
'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=34;}}',
'android/view/View.java': 'package android.view;public class View {public static final int GONE=8;public int visibility;public android.content.Context context;public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}public void setVisibility(int v){visibility=v;}public boolean isClickable(){return true;}public void post(Runnable r){r.run();}}',
'android/view/ViewGroup.java': 'package android.view;public class ViewGroup extends View {public final java.util.List<View> children=new java.util.ArrayList<>();public ViewGroup(android.content.Context c){super(c);}public void addView(View v){children.add(v);}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}',
'android/widget/TextView.java': 'package android.widget;public class TextView extends android.view.View {String text;public TextView(android.content.Context c,String t){super(c);text=t;}public CharSequence getText(){return text;}}',
'app/browzomje/extension/pinterest/PinterestUtils.java': 'package app.browzomje.extension.pinterest;class PinterestUtils {static String getString(String s){return s;}static android.widget.TextView findTextView(android.view.View v){if(v instanceof android.widget.TextView)return (android.widget.TextView)v;if(v instanceof android.view.ViewGroup)for(android.view.View child:((android.view.ViewGroup)v).children){android.widget.TextView t=findTextView(child);if(t!=null)return t;}return null;}}',
'app/browzomje/extension/pinterest/MorpheSettingsStore.java': 'package app.browzomje.extension.pinterest;class MorpheSettingsStore {static String selection="";static String menuFilter(){return selection;}}',
'app/browzomje/extension/pinterest/MorpheLog.java': 'package app.browzomje.extension.pinterest;class MorpheLog {static String REFLECTION="reflection",BOARD="board";static void i(String a,String b){}static void d(String a,String b){}}',
'app/browzomje/extension/pinterest/AdDetector.java': 'package app.browzomje.extension.pinterest;class AdDetector {static Object fieldValueBySerializedName(Object o,String n){return o instanceof java.util.Map?((java.util.Map<?,?>)o).get(n):null;}}',
'app/browzomje/extension/pinterest/PinImageMenu.java': 'package app.browzomje.extension.pinterest;class PinImageMenu {static Object bestImage(Object p){return AdDetector.fieldValueBySerializedName(p,"image");}}',
'app/browzomje/extension/pinterest/PinImageDownloader.java': 'package app.browzomje.extension.pinterest;class PinImageDownloader {static int count;static String id,url;static void download(android.content.Context c,String i,String u,int w,int h,Runnable r){count++;id=i;url=u;r.run();}}',
'app/browzomje/extension/pinterest/ActionsTest.java': '''package app.browzomje.extension.pinterest;
public class ActionsTest {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static class Completion {public int calls;public void invoke(){calls++;}}
 public static void main(String[] args) {
  android.content.Context c=new android.content.Context();c.getResources().labels.put("follow","Segui");c.getResources().labels.put("unfollow","Smetti di seguire");c.getResources().labels.put("pin_overflow_follow_user","Segui %1$s");c.getResources().labels.put("pin_overflow_unfollow_user","Smetti di seguire %1$s");c.getResources().labels.put("download_image","Scarica immagine");
  MorpheSettingsStore.selection=PinMenuFilter.encode(PinMenuFilter.recommended());
  android.view.ViewGroup menu=new android.view.ViewGroup(c);android.widget.TextView follow=new android.widget.TextView(c,"  Segui   Ada Lovelace "),unfollow=new android.widget.TextView(c,"Smetti di seguire Ada Lovelace"),download=new android.widget.TextView(c,"Scarica immagine"),unrelated=new android.widget.TextView(c,"Segui");
  menu.addView(follow);menu.addView(unfollow);menu.addView(download);menu.addView(unrelated);PinMenuFilter.apply(menu);
  check(follow.visibility==8 && unfollow.visibility==8,"dynamic follow labels filtered");check(download.visibility==0,"recommended preserves download");
  MorpheSettingsStore.selection="";android.widget.TextView shown=new android.widget.TextView(c,"Segui Ada");android.view.ViewGroup cleared=new android.view.ViewGroup(c);cleared.addView(shown);PinMenuFilter.apply(cleared);check(shown.visibility==0,"show all preserves follow");System.out.println("PASS production dynamic Follow/Unfollow filtering");
  java.util.Map<String,Object> image=new java.util.HashMap<>();image.put("url","https://i.pinimg.com/736x/test.jpg");image.put("width",736);image.put("height",805);
  java.util.Map<String,Object> pin=new java.util.HashMap<>();pin.put("id","123");pin.put("image",image);NativeImageDownload.remember(pin);
  Object handler=new Object();NativeImageDownload.capture(handler,"123","unused");Completion completion=new Completion();check(NativeImageDownload.intercept(handler,c,null,completion),"native image intercepted");check(PinImageDownloader.id.equals("123") && completion.calls==1,"exact Pin and completion");
  Object other=new Object();NativeImageDownload.capture(other,"456","unused");check(!NativeImageDownload.intercept(other,c,null,completion),"no last-seen Pin fallback");pin.put("is_video",true);check(!NativeImageDownload.intercept(handler,c,null,completion),"videos preserved");android.os.Build.VERSION.SDK_INT=28;pin.put("is_video",false);check(!NativeImageDownload.intercept(handler,c,null,completion),"legacy native path preserved");System.out.println("PASS production native download bridge");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='pinterest-actions-test-') as tmp:
 root=Path(tmp);files=[]
 for relative,source in SOURCES.items():
  path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);files.append(str(path))
 subprocess.run(['javac','-d',tmp,*files,str(BASE/'PinMenuFilter.java'),str(BASE/'NativeImageDownload.java')],check=True)
 subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.ActionsTest'],check=True,timeout=10)
