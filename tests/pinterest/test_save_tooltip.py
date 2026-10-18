"""Run the production Save-tooltip gate against anchor/recycled-view cases."""
from pathlib import Path
import subprocess,tempfile
src=Path(__file__).resolve().parents[2]/'extensions/extension/src/main/java/app/template/extension/pinterest/SavePinEducation.java'
files={
'android/content/res/Resources.java':'''package android.content.res;public class Resources {public static class NotFoundException extends RuntimeException {}public String name;public String getResourceEntryName(int id){if(name==null)throw new NotFoundException();return name;}}''',
'android/view/View.java':'''package android.view;public class View {public static int GONE=8;public int visibility=0;public android.content.res.Resources res=new android.content.res.Resources();public void setVisibility(int v){visibility=v;}public int getId(){return 1;}public android.content.res.Resources getResources(){return res;}}''',
'app/browzomje/extension/pinterest/MorpheLog.java':'''package app.browzomje.extension.pinterest;class MorpheLog{static void i(String a,String b){}}''',
'app/browzomje/extension/pinterest/SaveTest.java':'''package app.browzomje.extension.pinterest;
import android.view.View;
public class SaveTest {
 enum Anchor {PIN_IT_BUTTON,IAB_PINIT_BUTTON,CLOSEUP_SEND_BUTTON}
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[]args){
 for(Anchor anchor:new Anchor[]{Anchor.PIN_IT_BUTTON,Anchor.IAB_PINIT_BUTTON}){
  View bubble=new View();check(SavePinEducation.suppressSaveAnchor(bubble,anchor,false,null),"save anchor always blocked");
  check(bubble.visibility==8,"visible/reused bubble hidden");}
 View save=new View();save.res.name="save_pinit_bt";View bubble=new View();
 check(SavePinEducation.suppressSaveAnchor(bubble,null,true,save),"null enum with native Save view blocked");
 check(save.visibility==0,"Save action remains visible");
 View other=new View();other.res.name="send_btn";bubble=new View();
 check(!SavePinEducation.suppressSaveAnchor(bubble,Anchor.CLOSEUP_SEND_BUTTON,false,other),"other tooltip retained");
 check(bubble.visibility==0&&other.visibility==0,"other views unchanged");
 check(!SavePinEducation.suppressSaveAnchor(bubble,null,false,new View()),"unknown/no resource retained");
 check(!SavePinEducation.suppressSaveAnchor(bubble,"PIN_IT_BUTTON",false,null),"arbitrary string not enum");
 System.out.println("PASS production Save anchor gate: both enums, missing enum/resource fallback, recycled bubble hidden, Save and other tooltips retained");
 }}'''}
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,body in files.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 subprocess.run(['javac','-d',tmp,*paths,str(src)],check=True)
 subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.SaveTest'],check=True)
