#!/usr/bin/env python3
"""Compile the production downloader against tiny Android stubs; exercise partial metadata, HTTP failures, format selection and shared cache."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / 'extensions/extension/src/main/java/app/template/extension/pinterest/PinImageDownloader.java'
SOURCES = {
'android/annotation/TargetApi.java': 'package android.annotation; public @interface TargetApi { int value(); }',
'android/net/Uri.java': 'package android.net; public class Uri {}',
'android/content/ContentValues.java': 'package android.content; public class ContentValues extends java.util.HashMap<String,Object> {}',
'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver {
 public volatile byte[] saved; public volatile String name, mime; private java.io.ByteArrayOutputStream stream;
 public android.net.Uri insert(android.net.Uri uri, ContentValues values) { name=(String)values.get("name"); mime=(String)values.get("mime");stream=new java.io.ByteArrayOutputStream();return uri; }
 public java.io.OutputStream openOutputStream(android.net.Uri uri) { return stream; }
 public int update(android.net.Uri uri,ContentValues v,String a,String[] b) { saved=stream.toByteArray();return 1; }
 public int delete(android.net.Uri uri,String a,String[] b) { saved=null;return 1; }
}''',
'android/content/Context.java': '''package android.content; public class Context {
 private final java.io.File cache; private final ContentResolver resolver=new ContentResolver();
 public Context(java.io.File cache) {this.cache=cache;} public Context getApplicationContext(){return this;}
 public java.io.File getCacheDir(){return cache;} public ContentResolver getContentResolver(){return resolver;}
}''',
'android/graphics/BitmapFactory.java': '''package android.graphics; public class BitmapFactory {
 public static class Options { public boolean inJustDecodeBounds; public int outWidth=-1,outHeight=-1; }
 public static Object decodeFile(String file,Options opts) {try {java.awt.image.BufferedImage im=javax.imageio.ImageIO.read(new java.io.File(file));if(im!=null){opts.outWidth=im.getWidth();opts.outHeight=im.getHeight();}}catch(Exception e){}return null;}
}''',
'android/os/Environment.java': 'package android.os; public class Environment {public static final String DIRECTORY_DOWNLOADS="Download";}',
'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': 'package android.os; public class Handler {public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;}}',
'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}',
'android/provider/MediaStore.java': '''package android.provider; public class MediaStore {
 public static class MediaColumns {public static final String DISPLAY_NAME="name", MIME_TYPE="mime",RELATIVE_PATH="path", IS_PENDING="pending";}
 public static class Downloads {public static final android.net.Uri EXTERNAL_CONTENT_URI=new android.net.Uri();}
}''',
'app/browzomje/extension/pinterest/MorpheStrings.java': '''package app.browzomje.extension.pinterest; class MorpheStrings {
 static String IMAGE_DOWNLOAD_CHECKING="checking",IMAGE_DOWNLOAD_FALLBACK="fallback",IMAGE_DOWNLOAD_SAVED="saved";static String get(String s){return s;}
}''',
'app/browzomje/extension/pinterest/MorpheLog.java': '''package app.browzomje.extension.pinterest; class MorpheLog {
 static String BOARD="board";static void d(String a,String b){}static void i(String a,String b){}static void w(String a,String b,Exception e){}
}''',
'app/browzomje/extension/pinterest/PinterestUtils.java': '''package app.browzomje.extension.pinterest; class PinterestUtils {
 static void showNativeToast(android.content.Context c,String s){}static String getString(String s){return s;}
}''',

}
SOURCES.update({
'app/browzomje/extension/pinterest/PinImageDetails.java': 'package app.browzomje.extension.pinterest; class PinImageDetails {static void refreshDownloadInfo(String s,PinImageResolver.Result r){}}',
'android/content/SharedPreferences.java': """package android.content; public class SharedPreferences {
 private final java.util.Map<String,String> values=new java.util.HashMap<>();
 public String getString(String k,String d){return values.getOrDefault(k,d);} public java.util.Map<String,?> getAll(){return values;}
 public boolean contains(String k){return values.containsKey(k);} public Editor edit(){return new Editor();}
 public class Editor {public Editor putString(String k,String v){values.put(k,v);return this;}public Editor remove(String k){values.remove(k);return this;}public Editor clear(){values.clear();return this;}public void apply(){}}
}""",
'android/content/Context.java': """package android.content; public class Context {
 private final SharedPreferences prefs=new SharedPreferences();public Context(java.io.File f){}public SharedPreferences getSharedPreferences(String n,int m){return prefs;}public Context getApplicationContext(){return this;}
}""",
'android/graphics/BitmapFactory.java': """package android.graphics; public class BitmapFactory {
 public static class Options {public boolean inJustDecodeBounds;public int outWidth=-1,outHeight=-1;}
 public static Object decodeByteArray(byte[] b,int a,int n,Options o) {
  if(n>=30&&b[0]=='R'&&b[8]=='W'&&b[12]=='V'&&b[15]=='X'){o.outWidth=1+(b[24]&255)+((b[25]&255)<<8)+((b[26]&255)<<16);o.outHeight=1+(b[27]&255)+((b[28]&255)<<8)+((b[29]&255)<<16);return null;}
  try(javax.imageio.stream.ImageInputStream in=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(b, a,n))) {
   java.util.Iterator<javax.imageio.ImageReader> readers=javax.imageio.ImageIO.getImageReaders(in);
   if(readers.hasNext()){javax.imageio.ImageReader r=readers.next();try{r.setInput(in);o.outWidth=r.getWidth(0);o.outHeight=r.getHeight(0);}finally{r.dispose();}}
  }catch(Exception e){}return null;
 }
}""",
'org/json/JSONObject.java': """package org.json;public class JSONObject {
 private static final java.util.Map<String,JSONObject> objects=new java.util.HashMap<>();private final java.util.Map<String,Object> values;
 public JSONObject(){values=new java.util.HashMap<>();}public JSONObject(String s){values=objects.get(s).values;}
 public JSONObject put(String k,Object v){values.put(k,v);return this;}public String getString(String k){return (String)values.get(k);}public int getInt(String k){return ((Number)values.get(k)).intValue();}public long optLong(String k,long d){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():d;}public long getLong(String k){return ((Number)values.get(k)).longValue();}public boolean getBoolean(String k){return (Boolean)values.get(k);}
 public String toString(){String id=java.util.UUID.randomUUID().toString();objects.put(id,this);return id;}
}""",
'app/browzomje/extension/pinterest/PinDownloadQueue.java': """package app.browzomje.extension.pinterest;class PinDownloadQueue {static void add(android.content.Context c,String id,String source,int w,int h,String folder,boolean hq,Runnable completion){}}""",
'app/browzomje/extension/pinterest/MorpheSettingsStore.java': """package app.browzomje.extension.pinterest;class MorpheSettingsStore {static String KEY_HIGH_QUALITY_IMAGES="hq";static boolean effective(String k){return true;}}""",
'app/browzomje/extension/pinterest/DownloaderTest.java': r'''package app.browzomje.extension.pinterest;
import java.io.*;import java.net.*;import java.util.*;
public class DownloaderTest {
 static int mode,requests;static byte[] original,fallback;static final String URL="https://i.pinimg.com/736x/aa/bb/cc/0123456789abcdef0123456789abcdef.jpg";
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Connection extends HttpURLConnection {
  boolean orig;byte[] bytes;Connection(URL u){super(u);requests++;orig=u.getPath().startsWith("/originals/");bytes=orig?original:fallback;if(orig&&mode==2)bytes="<html>not an image</html>".getBytes();if(orig&&mode==3)bytes=fallback;}
  public int getResponseCode(){return orig&&mode==1?403:206;}public String getHeaderField(String k){return "Content-Range".equals(k)?(orig&&mode==7?"bytes 10-100/1000":"bytes 0-1000/1001"):null;}
  public InputStream getInputStream(){check(getRequestProperty("Range").startsWith("bytes=0-"),"prefix request");return new ByteArrayInputStream(bytes);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
 }
 public static void main(String[] args)throws Exception {
  java.net.URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
  check(PinImageDownloader.originals(URL).size()==3,"candidates");check(PinImageDownloader.originals(URL+"?signed=1").isEmpty(),"signed URLs untouched");check(PinImageDownloader.originals(URL.replace("i.pinimg.com","example.com")).isEmpty(),"other hosts untouched");check(PinImageDownloader.originals(URL.replace(".jpg",".gif")).size()==1,"GIF preserves format");
  for(mode=0;mode<=7;mode++) {
   original=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0],mode==5?"original.gif":mode==6?"original.webp":"original.png"));fallback=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0],"fallback.jpg"));
   android.content.Context context=new android.content.Context(new File(args[0]));requests=0;
   String source=mode==5?URL.replace(".jpg",".gif"):URL;
   PinImageResolver.Result result=PinImageResolver.resolve(context,source,150,150,mode!=4);
   boolean expectedOriginal=mode==0||mode==5||mode==6;
   check(result.fileSize==1001,"total size from range, not prefix");check(result.describe().contains("kB"),"size label");check(result.original==expectedOriginal,"original/fallback case "+mode);check(result.width==(expectedOriginal?200:100),"verified width");check(result.extension.equals(mode==5?"gif":mode==6?"webp":expectedOriginal?"png":"jpg"),"actual format independent of URL");
   int before=requests;PinImageResolver.Result cached=PinImageResolver.resolve(context,source,150,150,mode!=4);check(requests==before,"same download choice from cache");check(cached.fileSize==result.fileSize,"size cached");check(cached.url.equals(result.url),"same selected URL");
   java.lang.reflect.Field cache=PinImageResolver.class.getDeclaredField("CACHE");cache.setAccessible(true);((Map<?,?>)cache.get(null)).clear();
   PinImageResolver.Result persisted=PinImageResolver.resolve(context,source,150,150,mode!=4);check(persisted.fileSize==1001&&requests==before,"persisted size without network");
   PinImageResolver.invalidate(context,source,150,150,mode!=4);System.out.println("PASS metadata/cache case "+mode);
  }
  String[][] headers={{"206","bytes 0-1023/98010","1024","","98010"},{"206","bytes 0-1023/*","1024","","-1"},{"206","bytes 0-1023/100","1024","","-1"},{"206","bytes 0-1023/999999999999999999999999","1024","","-1"},{"200","","98010","","98010"},{"200","","","","-1"},{"200","","-5","","-1"},{"200","","98010","gzip","-1"},{"206","bad","1024","","-1"}};
  for(String[] h:headers){Connection c=new Connection(new URL(URL)){public String getHeaderField(String k){return k.equals("Content-Range")?h[1]:k.equals("Content-Length")?h[2]:k.equals("Content-Encoding")&&!h[3].isEmpty()?h[3]:null;}};check(PinImageResolver.responseSize(c,Integer.parseInt(h[0]))==Long.parseLong(h[4]),"size headers "+Arrays.toString(h));}
  check(!new PinImageResolver.Result(URL,"jpg",100,100,false).describe().contains("kB"),"unknown size omitted");
  System.out.println("PASS total size: partial/full/missing/invalid/encoded headers");
  System.exit(0);
 }
}'''
})

with tempfile.TemporaryDirectory(prefix='pinterest-download-test-') as temp:
    directory = Path(temp)
    sources = []
    for relative, content in SOURCES.items():
        file = directory / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        sources.append(str(file))
    from PIL import Image
    Image.new('RGB',(200,200)).save(directory/'original.png')
    Image.new('RGB',(200,200)).save(directory/'original.gif')
    Image.new('RGBA',(200,200),(255,0,0,128)).save(directory/'original.webp')
    Image.new('RGB',(100,100)).save(directory/'fallback.jpg')
    subprocess.run(['javac' , '-d', str(directory), *sources, str(PRODUCTION), str(PRODUCTION.with_name('PinImageResolver.java'))], check=True)
    subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(directory), 'app.browzomje.extension.pinterest.DownloaderTest', str(directory)], check=True, timeout=20)
