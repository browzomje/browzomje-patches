"""Production Intent routing, including unavailable/blocked frontends and fallback."""
from pathlib import Path
import subprocess,tempfile
src=Path(__file__).resolve().parents[2]/'extensions/extension/src/main/java/app/template/extension/pinterest/ExternalVideoDownloader.java'
files={
'android/content/ActivityNotFoundException.java':'package android.content;public class ActivityNotFoundException extends RuntimeException {}',
'android/content/Intent.java':'''package android.content;public class Intent {public static String ACTION_SEND="send",EXTRA_TEXT="text";public static int FLAG_ACTIVITY_NEW_TASK=1;public String pkg,url,type,action;public boolean chooser;public int flags;public Intent(String a){action=a;}public Intent(Intent i){pkg=i.pkg;url=i.url;type=i.type;action=i.action;flags=i.flags;}public Intent setType(String t){type=t;return this;}public Intent putExtra(String k,String v){url=v;return this;}public Intent addFlags(int f){flags|=f;return this;}public Intent setPackage(String p){pkg=p;return this;}public static Intent createChooser(Intent i,String t){Intent n=new Intent(i);n.chooser=true;return n;}}''',
'android/content/Context.java':'''package android.content;public class Context {public String available,blocked;public java.util.List<Intent> opened=new java.util.ArrayList<>();public void startActivity(Intent i){if(!i.chooser&&!java.util.Objects.equals(available,i.pkg))throw new ActivityNotFoundException();if(i.pkg!=null&&i.pkg.equals(blocked))throw new SecurityException();opened.add(i);}}''',
'app/browzomje/extension/pinterest/PinterestUtils.java':'package app.browzomje.extension.pinterest;class PinterestUtils {static String getString(String k){return k;}}',
'app/browzomje/extension/pinterest/DownloaderTest.java':'''package app.browzomje.extension.pinterest;
import android.content.*;
public class DownloaderTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[]args){
 for(String pkg:new String[]{"com.deniscerri.ytdl","com.junkfood.seal",null}) {
 Context c=new Context();c.available=pkg;ExternalVideoDownloader.open(c,"https://www.pinterest.com/pin/123/");
 check(c.opened.size()==1);Intent i=c.opened.get(0);check(i.chooser==(pkg==null));check(java.util.Objects.equals(pkg,i.pkg));check("text/plain".equals(i.type));check(i.url.endsWith("/123/"));check(i.flags!=0);
 }
 Context c=new Context();c.available=c.blocked="com.deniscerri.ytdl";ExternalVideoDownloader.open(c,"url");check(c.opened.size()==1&&c.opened.get(0).chooser);
 System.out.println("PASS direct YTDLnis/Seal routing, unchanged URL/MIME, unavailable or blocked frontend fallback");
 }}'''}
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,body in files.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 subprocess.run(['javac','-d',tmp,*paths,str(src)],check=True)
 subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.DownloaderTest'],check=True)
