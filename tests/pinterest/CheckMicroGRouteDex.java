import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;

class CheckMicroGRouteDex {
    private static final List<String> ROUTES = List.of(
            "com.google.android.gms", "com.google",
            "com.google.android.gms.auth.api.identity.service.signin.START",
            "com.google.android.gms.identitycredentials.service.START",
            "com.google.android.c2dm.intent.RECEIVE",
            "com.google.android.c2dm.intent.REGISTER", "com.google.iid.TOKEN_REQUEST");

    private record Result(Map<String, Integer> routes, int frameworkProviders, int disabledProviders, int pickerBridges, int clickGuards) {}

    private static Result read(String apk) throws Exception {
        var dex = DexFileFactory.loadDexContainer(new File(apk), Opcodes.getDefault());
        var counts = new HashMap<String, Integer>();
        int providers = 0;
        int disabled = 0;
        int pickerBridges = 0;
        int clickGuards = 0;
        for (String entry : dex.getDexEntryNames()) {
            for (var type : dex.getEntry(entry).getDexFile().getClasses()) {
                if (type.getType().startsWith("Lapp/browzomje/extension/")) continue;
                for (var field : type.getStaticFields()) {
                    if (field.getInitialValue() instanceof StringEncodedValue text) record(counts, text.getValue());
                }
                boolean framework = false;
                for (var field : type.getFields()) {
                    framework |= field.getType().equals("Landroid/credentials/CredentialManager;");
                }
                for (var method : type.getMethods()) {
                    if (method.getImplementation() == null) continue;
                    var instructions = method.getImplementation().getInstructions();
                    if (framework && method.getName().equals("isAvailableOnDevice")
                            && method.getReturnType().equals("Z") && method.getParameterTypes().isEmpty()) {
                        providers++;
                        var iterator = instructions.iterator();
                        Instruction first = iterator.next();
                        if (first instanceof NarrowLiteralInstruction literal && literal.getNarrowLiteral() == 0
                                && iterator.next().getOpcode() == Opcode.RETURN) disabled++;
                    }
                    var first = instructions.iterator().next();
                    if (first.getOpcode() == Opcode.INVOKE_STATIC
                            && first instanceof ReferenceInstruction reference
                            && reference.getReference().toString().equals(
                                    "Lapp/browzomje/extension/pinterest/MicroGSignIn;->canStart()Z")) {
                        clickGuards++;
                    }
                    for (Instruction instruction : instructions) {
                        if (type.getType().startsWith("Landroidx/credentials/playservices/HiddenActivity$")
                                && instruction.getOpcode() == Opcode.INVOKE_STATIC_RANGE
                                && instruction instanceof RegisterRangeInstruction range
                                && range.getRegisterCount() == 8
                                && ((ReferenceInstruction) instruction).getReference().toString().startsWith(
                                        "Lapp/browzomje/extension/pinterest/MicroGSignIn;->launch(")) pickerBridges++;
                        if (instruction instanceof ReferenceInstruction reference
                                && reference.getReference() instanceof StringReference text) {
                            record(counts, text.getString());
                        }
                    }
                }
            }
        }
        return new Result(counts, providers, disabled, pickerBridges, clickGuards);
    }

    private static void record(Map<String, Integer> counts, String value) {
        if (ROUTES.contains(value) || value.startsWith("app.revanced")) counts.merge(value, 1, Integer::sum);
    }

    private static void check(boolean passed, String detail) {
        if (!passed) throw new AssertionError(detail);
    }

    public static void main(String[] args) throws Exception {
        Result before = read(args[0]);
        Result after = read(args[1]);
        for (String route : ROUTES) {
            String replacement = "app.revanced" + route.substring("com.google".length());
            check(before.routes().getOrDefault(route, 0) > 0, "original route missing " + route);
            check(after.routes().getOrDefault(route, 0) == 0, "stock route remains " + route);
            check(after.routes().getOrDefault(replacement, 0) > 0, "replacement missing " + replacement);
        }
        check(before.frameworkProviders() == 1 && before.disabledProviders() == 0
                && after.frameworkProviders() == 1 && after.disabledProviders() == 1,
                "only the selected build disables Android's framework provider");
        check(before.pickerBridges() == 0 && after.pickerBridges() == 1, "one credential picker launch bridge");
        check(before.clickGuards() == 0 && after.clickGuards() == 1, "one microG click guard");
        System.out.println("PASS microG DEX routes for Google identity, account type and Firebase Messaging");
    }
}
