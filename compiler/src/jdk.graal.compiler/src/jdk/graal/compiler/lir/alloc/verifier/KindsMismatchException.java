package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.meta.Value;

@SuppressWarnings("serial")
public class KindsMismatchException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;
    public Value value1;
    public Value value2;
    public boolean origVsCurr;

    public KindsMismatchException(LIRInstruction instruction, BasicBlock<?> block, Value value1, Value value2, boolean origVsCurr) {
        super(KindsMismatchException.getErrorMessage(instruction, block, value1, value2, origVsCurr));

        this.instruction = instruction;
        this.block = block;
        this.value1 = value1;
        this.value2 = value2;
        this.origVsCurr = origVsCurr;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, Value value1, Value value2, boolean origVsCurr) {
        if (origVsCurr) {
            return value1.getValueKind() + " has different kind after allocation: " + value2.getValueKind() + " in " + instruction + " in block " + block;
        }

        return "Value in location has different kind: " + value1.getValueKind() + " vs. " + value2.getValueKind() + " in " + instruction + " in block " + block;
    }
}
