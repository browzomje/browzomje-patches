import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Verify that the broad education renderer is restored and only the final Save tooltip gate remains. */
class CheckSaveTooltipDex {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Map<String,ClassDef> load(String path)throws Exception {
  var result=new HashMap<String,ClassDef>();var dex=DexFileFactory.loadDexContainer(new File(path),Opcodes.getDefault());
  for(String name:dex.getDexEntryNames())for(ClassDef c:dex.getEntry(name).getDexFile().getClasses())result.put(c.getType(),c);
  return result;
 }
 static List<Instruction> code(Method m){var list=new ArrayList<Instruction>();m.getImplementation().getInstructions().forEach(list::add);return list;}
 static String ref(Instruction i){return i instanceof ReferenceInstruction r?r.getReference().toString():"";}
 public static void main(String[] args)throws Exception {
  var original=load(args[0]);var patched=load(args[1]);String type="Lcom/pinterest/education/view/EducationNewContainerView;";
  for(Method source:original.get(type).getMethods()) {
   if(source.getImplementation()==null)continue;
   Method target=null;for(Method m:patched.get(type).getMethods())if(m.toString().equals(source.toString()))target=m;
   check(target!=null,"method retained");var before=code(source);var after=code(target);
   check(after.size()==before.size(),"original education renderer restored");
   check(source.getImplementation().getRegisterCount()==target.getImplementation().getRegisterCount(),"original renderer frame restored");
   for(int i=0;i<before.size();i++) {
    check(before.get(i).getOpcode()==after.get(i).getOpcode(),"original education opcode restored");
    check(ref(before.get(i)).equals(ref(after.get(i))),"original education reference restored");
   }
  }
  String tooltip="Lcom/pinterest/education/view/EducationToolTipView;";int placements=0;
  for(Method source:original.get(tooltip).getMethods()) {
   if(!source.getReturnType().equals("V")||source.getParameterTypes().size()!=4||!source.getParameterTypes().get(2).equals("Landroid/view/View;"))continue;
   Method target=null;for(Method m:patched.get(tooltip).getMethods())if(m.toString().equals(source.toString()))target=m;
   check(target!=null,"placement method retained");var before=code(source);var after=code(target);
   check(after.size()==before.size()+5,"placement gate added before original show");
   check(after.get(0).getOpcode()==Opcode.INVOKE_STATIC_RANGE,"wide caller-safe placement gate");
   check(ref(after.get(0)).contains("->suppressSaveAnchor(Ljava/lang/Object;Ljava/lang/Object;ZLjava/lang/Object;)Z"),"anchor gate signature");
   var call=(RegisterRangeInstruction)after.get(0);
   check(call.getRegisterCount()==4 && call.getStartRegister()==target.getImplementation().getRegisterCount()-5,"p0..p3 passed without clobber");
   check(after.get(3).getOpcode()==Opcode.RETURN_VOID,"blocked anchor returns before native show");
   for(int i=0;i<before.size();i++)check(before.get(i).getOpcode()==after.get(i+5).getOpcode()&&ref(before.get(i)).equals(ref(after.get(i+5))),"original tooltip code retained");
   placements++;
  }
  check(placements==1,"exactly one final tooltip placement gated");
  ClassDef gate=patched.get("Lapp/browzomje/extension/pinterest/SavePinEducation;");check(gate!=null,"extension merged");
  for(Method m:gate.getMethods())check(!m.getName().equals("shouldSuppress"),"obsolete ID gate removed");
  System.out.println("PASS original education renderer restored; only final Save anchor gate retained; no obsolete ID gate");
 }
}
