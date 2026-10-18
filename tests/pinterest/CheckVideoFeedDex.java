import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
class CheckVideoFeedDex {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static ClassDef load(String path,String type)throws Exception {var dex=DexFileFactory.loadDexContainer(new File(path),Opcodes.getDefault());for(String name:dex.getDexEntryNames())for(ClassDef c:dex.getEntry(name).getDexFile().getClasses())if(c.getType().equals(type))return c;throw new AssertionError(type);}
 static List<Instruction> code(Method m){var result=new ArrayList<Instruction>();m.getImplementation().getInstructions().forEach(result::add);return result;}
 static String ref(Instruction i){return i instanceof ReferenceInstruction r?r.getReference().toString():"";}
 public static void main(String[]args)throws Exception {
 String type="Lcom/pinterest/legacy/feeds/Feed;";var original=load(args[0],type);var patched=load(args[1],type);int hooks=0;
 for(Method source:original.getMethods()) {
  if(source.getImplementation()==null)continue;Method target=null;for(Method m:patched.getMethods())if(source.toString().equals(m.toString()))target=m;
  check(target!=null,"legacy method retained");var a=code(source);var b=code(target);
  boolean setter=source.getReturnType().equals("V")&&source.getParameterTypes().toString().equals("[Ljava/util/List;]");int added=setter?2:0;
  check(b.size()==a.size()+added,"only list setter changed");
  for(int i=0;i<a.size();i++)check(a.get(i).getOpcode()==b.get(i+added).getOpcode()&&ref(a.get(i)).equals(ref(b.get(i+added))),"original legacy feed/index code retained");
  if(setter){hooks++;check(b.get(0).getOpcode()==Opcode.INVOKE_STATIC_RANGE&&ref(b.get(0)).contains("VideoFeedFilter;->filter("),"copy filter before install");
  var call=(RegisterRangeInstruction)b.get(0);check(call.getRegisterCount()==1&&call.getStartRegister()==target.getImplementation().getRegisterCount()-1,"list argument");
  check(b.get(1).getOpcode()==Opcode.MOVE_RESULT_OBJECT&&((OneRegisterInstruction)b.get(1)).getRegisterA()==call.getStartRegister(),"filtered list becomes setter input");}
 }
 for(String grid : List.of("Ltu1/g0;", "Ltu1/k;", "Ltu1/p;")) {
  var before=load(args[0],grid);var after=load(args[1],grid);int changed=0;
  for(Method source:before.getMethods()) {
   if(source.getImplementation()==null)continue;
   Method target=null;for(Method m:after.getMethods())if(m.toString().equals(source.toString()))target=m;
   check(target!=null,"grid method retained");var a=code(source);var b=code(target);
   boolean hook=grid.equals("Ltu1/k;")?source.getName().equals("<init>"):
       grid.equals("Ltu1/p;")?source.getName().equals("b"):
       List.of("A","j0","Y").contains(source.getName());
   int added=hook?2:0;check(b.size()==a.size()+added,"only grid input changed: "+source);
   for(int i=0;i<a.size();i++)check(a.get(i).getOpcode()==b.get(i+added).getOpcode()&&ref(a.get(i)).equals(ref(b.get(i+added))),"native diff/events/paging retained: "+source);
   if(hook){changed++;check(ref(b.get(0)).contains("VideoFeedFilter;->filter("),"filter before capture");
    int param=(grid.equals("Ltu1/k;")||source.getName().equals("Y"))?2:1;
    int start=target.getImplementation().getRegisterCount()-source.getParameterTypes().size()-1+param;
    check(((RegisterRangeInstruction)b.get(0)).getStartRegister()==start&&((OneRegisterInstruction)b.get(1)).getRegisterA()==start,"correct input parameter: "+source);
   }
  }
  check(changed==(grid.equals("Ltu1/g0;")?3:1),"expected grid hooks");
 }
 check(hooks==1,"one legacy list install hook");System.out.println("PASS legacy/GraphQL/dynamic grid video DEX: copy-filter before list/index update; getters, paging metadata and other native methods retained");
 }
}
