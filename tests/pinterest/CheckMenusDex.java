import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;

class CheckMenusDex {
    private static final String EXTENSION = "Lapp/browzomje/extension/pinterest/";

    private static List<Instruction> method(String apk, String owner, String name) throws Exception {
        var dex = DexFileFactory.loadDexContainer(new File(apk), Opcodes.getDefault());
        for (String entry : dex.getDexEntryNames()) {
            if (!entry.matches("classes[0-9]*\\.dex")) continue;
            for (var type : dex.getEntry(entry).getDexFile().getClasses()) {
                if (!type.getType().equals(owner)) continue;
                for (var method : type.getMethods()) {
                    if (!method.getName().equals(name)) continue;
                    var instructions = new ArrayList<Instruction>();
                    method.getImplementation().getInstructions().forEach(instructions::add);
                    return instructions;
                }
            }
        }
        throw new AssertionError("Method missing: " + owner + name);
    }

    private static int find(List<Instruction> code, String target) {
        int found = -1;
        for (int index = 0; index < code.size(); index++) {
            if (!(code.get(index) instanceof ReferenceInstruction ref)
                    || !ref.getReference().toString().startsWith(target)) continue;
            check(found < 0, "duplicate call " + target);
            found = index;
        }
        return found;
    }

    private static void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        String before = args[0], after = args[1];
        var board = method(after, "Ldy0/b;", "a");
        int boardIcon = find(board, EXTENSION + "BoardMenuRowFactory;->boardOptionIcon");
        check(boardIcon >= 0 && find(method(before, "Ldy0/b;", "a"), EXTENSION) < 0, "board builder hook");
        var rowCtor = (RegisterRangeInstruction) board.get(find(board, "Lt93/y;-><init>"));
        check(rowCtor.getStartRegister() == 9 && rowCtor.getRegisterCount() == 12, "native board row arguments");

        var saved = method(after, "Lia3/s0;", "<init>");
        int saveGate = find(saved, EXTENSION + "SavedPinMenu;->hideSave");
        check(saveGate >= 0 && saved.get(saveGate + 2).getOpcode() == Opcode.IF_EQZ
                && saved.get(saveGate + 4).getOpcode() == Opcode.IPUT_BOOLEAN, "conditional repin capability");

        var list = method(after, "Lja3/u0;", "l");
        var append = (RegisterRangeInstruction) list.get(find(list, EXTENSION + "ComposePinDownload;->append"));
        check(append.getStartRegister() == 38 && append.getRegisterCount() == 1, "native Compose action list");

        var effect = method(after, "Lja3/g;", "invokeSuspend");
        int download = find(effect, EXTENSION + "ComposePinDownload;->download");
        var call = (FiveRegisterInstruction) effect.get(download);
        check(call.getRegisterCount() == 2 && call.getRegisterC() == 1 && call.getRegisterD() == 5,
                "selected option and Pin model registers");
        check(effect.get(download + 2).getOpcode() == Opcode.IF_EQZ
                && effect.get(download + 4).getOpcode() == Opcode.RETURN_OBJECT
                && effect.get(download + 6).getOpcode() == Opcode.SGET_OBJECT, "native action branch retained");
        System.out.println("PASS native board row, saved-pin capability, and Compose selection DEX");
    }
}
