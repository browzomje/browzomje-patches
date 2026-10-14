import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Run with the Morphe CLI JAR on the classpath and original/patched APK arguments. */
class CheckHomeRefreshDex {
 static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
 static Map<String,ClassDef> classes(String file) throws Exception {
  Map<String,ClassDef> result=new HashMap<>();
  var container=DexFileFactory.loadDexContainer(new File(file),Opcodes.getDefault());
  for(String entry:container.getDexEntryNames())
   for(ClassDef type:container.getEntry(entry).getDexFile().getClasses()) {
    check(result.put(type.getType(),type)==null,"duplicate class "+type.getType());
   }
  return result;
 }
 static List<Instruction> code(Method method) {
  List<Instruction> result=new ArrayList<>();
  method.getImplementation().getInstructions().forEach(result::add);return result;
 }
 static MethodReference call(Instruction i) {
  return i instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m ? m : null;
 }
 static String signature(MethodReference m) { return m.toString(); }
 static Method resolve(Map<String,ClassDef> classes,MethodReference ref) {
  for(Method m:classes.get(ref.getDefiningClass()).getMethods())if(signature(m).equals(signature(ref)))return m;
  throw new AssertionError("missing method "+ref);
 }
 public static void main(String[] args) throws Exception {
  var original=classes(args[0]);var patched=classes(args[1]);
  String nav="Lcom/pinterest/navigation/view/FloatingBottomNavBar;";
  Method selection=null;
  for(Method m:original.get(nav).getMethods())if(m.getImplementation()!=null && m.getParameterTypes().size()==2)
   for(Instruction i:code(m))if(i instanceof ReferenceInstruction r && r.getReference() instanceof StringReference s
      && s.getString().equals("android_hf_auto_refresh"))selection=m;
  check(selection!=null,"selection anchor");
  Method helper=null;
  for(Instruction i:code(selection)) {
   MethodReference ref=call(i);
   if(ref!=null && ref.getReturnType().equals("Z") && ref.getParameterTypes().size()==3
     && ref.getParameterTypes().get(1).equals("Z") && ref.getParameterTypes().get(2).equals("Ljava/lang/String;")) {
    check(helper==null,"unique helper"); helper=resolve(original,ref);
   }
  }
  check(helper!=null,"timed helper");String bus=helper.getParameterTypes().get(0).toString();int total=0;
  for(Method source:List.of(selection,helper)) {
   var before=code(source);var after=code(resolve(patched,source));int changes=0;
   check(before.size()==after.size(),"no extra caller instructions");
   check(source.getImplementation().getRegisterCount()==resolve(patched,source).getImplementation().getRegisterCount(),"caller frame retained");
   MethodReference post=null;
   for(int n=0;n<before.size();n++) {
    Instruction a=before.get(n), b=after.get(n);MethodReference ref=call(a);
    if(ref!=null && ref.getDefiningClass().equals(bus) && ref.getReturnType().equals("V")
      && ref.getParameterTypes().toString().equals("[Ljava/lang/Object;]")) {
     post=ref;changes++;
     check(b.getOpcode()==Opcode.INVOKE_STATIC && call(b).getName().equals("morphePostAutomaticHomeRefresh"),"typed bridge call");
     var ar=(FiveRegisterInstruction)a;var br=(FiveRegisterInstruction)b;
     check(ar.getRegisterC()==br.getRegisterC() && ar.getRegisterD()==br.getRegisterD(),"caller arguments preserved");
    } else {
     check(a.getOpcode()==b.getOpcode(),"unrelated instruction opcode retained");
     if(a instanceof ReferenceInstruction ar)check(ar.getReference().equals(((ReferenceInstruction)b).getReference()),"unrelated references retained");
     if(a instanceof OffsetInstruction ar)check(ar.getCodeOffset()==((OffsetInstruction)b).getCodeOffset(),"caller branches retained");
    }
   }
   check(changes==(source==selection?2:1),"expected source emission count");total+=changes;
   Method bridge=null;
   for(Method m:patched.get(source.getDefiningClass()).getMethods())if(m.getName().equals("morphePostAutomaticHomeRefresh"))bridge=m;
   var instructions=code(bridge);
   check(instructions.size()==5,"bridge has only gate and original dispatch");
   check(call(instructions.get(0)).getName().equals("shouldKeepFeed"),"runtime preference gate");
   check(instructions.get(2).getOpcode()==Opcode.IF_NEZ,"gate skips original dispatch only when enabled");
   check(instructions.get(3).getOpcode()==Opcode.INVOKE_VIRTUAL && call(instructions.get(3)).equals(post),"OFF dispatch unchanged");
   check(((OffsetInstruction)instructions.get(2)).getCodeOffset()==5,"ON branches to return");
   check(instructions.get(4).getOpcode()==Opcode.RETURN_VOID,"bridge returns");
  }
  // The shared bus must retain every original method implementation, including manual refresh.
  for(Method m:original.get(bus).getMethods())if(m.getImplementation()!=null) {
   var a=code(m);var b=code(resolve(patched,m));check(a.size()==b.size(),"shared bus untouched");
   for(int n=0;n<a.size();n++) {
    check(a.get(n).getOpcode()==b.get(n).getOpcode(),"shared bus opcode unchanged");
    if(a.get(n) instanceof ReferenceInstruction r)check(r.getReference().equals(((ReferenceInstruction)b.get(n)).getReference()),"shared bus references unchanged");
   }
  }
  String fragment="Lcom/pinterest/feature/home/mainfeed/HomeFeedFragment;";
  Method callback=null;
  for(Method m:original.get(fragment).getMethods())if(m.getImplementation()!=null && m.getParameterTypes().toString().equals("[Landroid/os/Bundle;]"))
   for(Instruction i:code(m))if(i instanceof ReferenceInstruction r && r.getReference() instanceof StringReference str
      && str.getString().equals("com.pinterest.BOTTOM_NAV_GOING_TO_HOME"))callback=m;
  check(callback!=null,"reselection callback anchor");
  var beforeReselect=code(callback);var afterReselect=code(resolve(patched,callback));
  check(beforeReselect.size()==afterReselect.size(),"reselection caller size preserved");int redirected=0;MethodReference presenter=null;
  for(int n=0;n<beforeReselect.size();n++) {
   MethodReference a=call(beforeReselect.get(n)),b=call(afterReselect.get(n));
   if(b!=null && b.getName().equals("morpheHomeReselection")) {
    redirected++;presenter=a;check(afterReselect.get(n).getOpcode()==Opcode.INVOKE_STATIC,"reselection typed bridge");
   }else {
    check(beforeReselect.get(n).getOpcode()==afterReselect.get(n).getOpcode(),"reselection native state retained");
    if(beforeReselect.get(n) instanceof ReferenceInstruction r)check(r.getReference().equals(((ReferenceInstruction)afterReselect.get(n)).getReference()),"reselection native references retained");
   }
  }
  check(redirected==1,"one presenter refresh redirected");
  Method reselectBridge=null;
  for(Method m:patched.get(fragment).getMethods())if(m.getName().equals("morpheHomeReselection"))reselectBridge=m;
  var bridgeCode=code(reselectBridge);
  check(call(bridgeCode.get(0)).getName().equals("shouldKeepFeed"),"reselection preference gate");
  check(bridgeCode.get(2).getOpcode()==Opcode.IF_NEZ,"ON uses scroll branch");
  check(call(bridgeCode.get(3)).equals(presenter) && bridgeCode.get(4).getOpcode()==Opcode.RETURN_VOID,"OFF keeps original presenter refresh");
  check(call(bridgeCode.get(5)).getReturnType().equals("Landroidx/recyclerview/widget/RecyclerView;"),"ON gets existing recycler");
  check(((NarrowLiteralInstruction)bridgeCode.get(8)).getNarrowLiteral()==0,"ON scrolls to position zero");
  check(call(bridgeCode.get(9)).getDefiningClass().equals("Landroidx/recyclerview/widget/RecyclerView;"),"scroll stays in native recycler");
  // F4 itself remains shared with manual refresh and must not be globally neutralized.
  var presenterBefore=code(resolve(original,presenter));var presenterAfter=code(resolve(patched,presenter));
  check(presenterBefore.size()==presenterAfter.size(),"manual presenter refresh retained");
  for(int n=0;n<presenterBefore.size();n++) {
   check(presenterBefore.get(n).getOpcode()==presenterAfter.get(n).getOpcode(),"manual presenter opcodes retained");
   if(presenterBefore.get(n) instanceof ReferenceInstruction r)check(r.getReference().equals(((ReferenceInstruction)presenterAfter.get(n)).getReference()),"manual presenter references retained");
  }
  var extension=patched.get("Lapp/browzomje/extension/pinterest/HomeFeedRefresh;");
  check(extension!=null,"extension merged");
  boolean capability=false,visible=false;
  for(Method m:extension.getMethods())if(m.getName().equals("isAvailable")) {
   capability=true;check(((NarrowLiteralInstruction)code(m).get(0)).getNarrowLiteral()==1,"feature marked available");
  }
  for(Method m:patched.get("Lapp/browzomje/extension/pinterest/MorpheSettingsActivity;").getMethods())
   if(m.getImplementation()!=null)for(Instruction i:code(m)) {
    MethodReference ref=call(i);
    if(ref!=null && ref.getDefiningClass().equals(extension.getType()) && ref.getName().equals("isAvailable"))visible=true;
   }
  check(capability && visible,"capability gate retained in the settings UI");
  System.out.println("PASS "+total+" automatic emissions plus reselection gated; caller frames/branches, shared bus and manual presenter retained; OFF dispatch preserved");
 }
}
