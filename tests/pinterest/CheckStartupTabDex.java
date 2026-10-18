import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
class CheckStartupTabDex {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Map<String,ClassDef> load(String path)throws Exception {
  var result=new HashMap<String,ClassDef>();var dex=DexFileFactory.loadDexContainer(new File(path),Opcodes.getDefault());
  for(String name:dex.getDexEntryNames())for(ClassDef c:dex.getEntry(name).getDexFile().getClasses())result.put(c.getType(),c);return result;
 }
 static List<Instruction> code(Method m){var list=new ArrayList<Instruction>();m.getImplementation().getInstructions().forEach(list::add);return list;}
 static String ref(Instruction i){return i instanceof ReferenceInstruction r?r.getReference().toString():"";}
 public static void main(String[]args)throws Exception {
 var before=load(args[0]);var after=load(args[1]);String type="Lcom/pinterest/activityLibrary/activity/task/activity/MainActivity;";int modified=0;
 for(Method source:before.get(type).getMethods()) {
  if(source.getImplementation()==null)continue;
  Method target=null;for(Method m:after.get(type).getMethods())if(source.toString().equals(m.toString()))target=m;
  check(target!=null,"original method retained");var a=code(source);var b=code(target);int added=source.getName().equals("onCreate")?1:0;
  check(b.size()==a.size()+added,"only onCreate entry changed");
  check(source.getImplementation().getRegisterCount()==target.getImplementation().getRegisterCount(),"frame unchanged");
  for(int i=0;i<a.size();i++)check(a.get(i).getOpcode()==b.get(i+added).getOpcode()&&ref(a.get(i)).equals(ref(b.get(i+added))),"native router/lifecycle retained");
  if(added==1){modified++;check(b.get(0).getOpcode()==Opcode.INVOKE_STATIC_RANGE,"wide entry call");check(ref(b.get(0)).contains("StartupTab;->onMainCreate(Ljava/lang/Object;Ljava/lang/Object;)V"),"startup hook");var range=(RegisterRangeInstruction)b.get(0);check(range.getRegisterCount()==2&&range.getStartRegister()==target.getImplementation().getRegisterCount()-2,"Activity and saved state passed");}
 }
 String launcher="Lcom/pinterest/activity/PinterestActivity;";int launcherHooks=0;
 for(Method source:before.get(launcher).getMethods()) {
  if(source.getImplementation()==null)continue;Method target=null;for(Method m:after.get(launcher).getMethods())if(source.toString().equals(m.toString()))target=m;
  check(target!=null,"launcher method retained");var a=code(source);var b=code(target);
  int prefix=source.getName().equals("onCreate")||source.getName().equals("onNewIntent")?1:0;
  check(b.size()==a.size()+prefix,"launcher hook scope");
  for(int i=0;i<a.size();i++)check(a.get(i).getOpcode()==b.get(i+prefix).getOpcode()&&ref(a.get(i)).equals(ref(b.get(i+prefix))),"launcher native code retained");
  if(prefix==1){launcherHooks++;check(ref(b.get(0)).contains(source.getName().equals("onCreate")?"->onLauncherCreate(":"->cancelLauncher("),"launcher lifecycle gate");}
 }
 check(launcherHooks==2,"launcher create/new-intent tracked");
 Method transitionBefore=null,transitionAfter=null;
 for(Method m:before.get("Lr82/c;").getMethods())if(m.getName().equals("g")&&m.getParameterTypes().size()==2)transitionBefore=m;
 for(Method m:after.get("Lr82/c;").getMethods())if(transitionBefore!=null&&m.toString().equals(transitionBefore.toString()))transitionAfter=m;
 check(transitionAfter!=null,"native authenticated transition retained");var ta=code(transitionBefore);var tb=code(transitionAfter);
 check(tb.size()==ta.size()+1&&ref(tb.get(0)).contains("->prepareAuthenticated("),"authenticated handoff gate");
 for(int i=0;i<ta.size();i++)check(ta.get(i).getOpcode()==tb.get(i+1).getOpcode()&&ref(ta.get(i)).equals(ref(tb.get(i+1))),"native extras forwarding retained");
 check(modified==1,"single onCreate hook");check(after.containsKey("Lapp/browzomje/extension/pinterest/StartupTab;"),"extension merged");
 System.out.println("PASS startup DEX gate: Activity/saved-state registers, original MainActivity lifecycle and native deferred router retained");
 }
}
