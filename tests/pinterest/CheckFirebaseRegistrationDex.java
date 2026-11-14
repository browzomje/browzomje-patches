import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.*;

class CheckFirebaseRegistrationDex {
    private static final String CERT_HEADER = "X-Android-Cert";
    private static final String CERT_SHA1 = "B6A74DBCB894B0F73D8C485C72EB1247A8F027CA";

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static String reference(Instruction instruction) {
        return instruction instanceof ReferenceInstruction reference ? reference.getReference().toString() : "";
    }

    private static List<Instruction> headers(String apk, String owner) throws Exception {
        var dex = DexFileFactory.loadDexContainer(new File(apk), Opcodes.getDefault());
        for (String entry : dex.getDexEntryNames()) {
            for (var type : dex.getEntry(entry).getDexFile().getClasses()) {
                if (!type.getType().equals(owner)) continue;
                for (var method : type.getMethods()) {
                    if (!method.getReturnType().equals("Ljava/net/HttpURLConnection;")) continue;
                    var instructions = new ArrayList<Instruction>();
                    method.getImplementation().getInstructions().forEach(instructions::add);
                    if (instructions.stream().anyMatch(i -> CERT_HEADER.equals(reference(i)))) return instructions;
                }
            }
        }
        throw new AssertionError("Firebase header builder missing");
    }

    private static Map<Integer, Integer> jumpTargets(List<Instruction> code) {
        var addresses = new HashMap<Integer, Integer>();
        var targets = new HashMap<Integer, Integer>();
        int address = 0;
        for (int i = 0; i < code.size(); i++) {
            var instruction = code.get(i);
            addresses.put(address, i);
            if (instruction instanceof OffsetInstruction jump) targets.put(i, address + jump.getCodeOffset());
            address += instruction.getCodeUnits();
        }
        targets.replaceAll((index, target) -> addresses.get(target));
        return targets;
    }

    public static void main(String[] args) throws Exception {
        String owner = switch (args[2]) {
            case "14.39.0" -> "Lap/c;";
            case "14.43.0" -> "Ldp/c;";
            case "14.44.0" -> "Lnp/c;";
            default -> throw new IllegalArgumentException("Unsupported fixture");
        };
        var original = headers(args[0], owner);
        var patched = headers(args[1], owner);
        check(patched.size() == original.size() + 1, "exactly one value load added");
        int insertion = 0;
        while (!CERT_HEADER.equals(reference(original.get(insertion)))) insertion++;
        var load = patched.get(insertion);
        check(load.getOpcode() == Opcode.CONST_STRING || load.getOpcode() == Opcode.CONST_STRING_JUMBO,
                "replacement is a string constant");
        check(CERT_SHA1.equals(reference(load)), "expected original signer fingerprint");
        var write = (FiveRegisterInstruction) patched.get(insertion + 2);
        check(((OneRegisterInstruction) load).getRegisterA() == write.getRegisterE(),
                "certificate replaces only the header value");
        check(((OneRegisterInstruction) patched.get(insertion + 1)).getRegisterA() == write.getRegisterD(),
                "header name register retained");
        for (int i = 0; i < original.size(); i++) {
            var before = original.get(i);
            var after = patched.get(i < insertion ? i : i + 1);
            check(before.getOpcode() == after.getOpcode()
                            || before.getOpcode() == Opcode.CONST_STRING && after.getOpcode() == Opcode.CONST_STRING_JUMBO,
                    "native opcode retained at " + i);
            check(reference(before).equals(reference(after)), "native reference retained at " + i);
        }
        var patchedJumps = jumpTargets(patched);
        for (var jump : jumpTargets(original).entrySet()) {
            int from = jump.getKey() < insertion ? jump.getKey() : jump.getKey() + 1;
            int to = jump.getValue() <= insertion ? jump.getValue() : jump.getValue() + 1;
            check(patchedJumps.get(from) == to, "native control flow retained at " + jump.getKey());
        }
        System.out.println("PASS Firebase certificate header DEX on " + args[2]);
    }
}
