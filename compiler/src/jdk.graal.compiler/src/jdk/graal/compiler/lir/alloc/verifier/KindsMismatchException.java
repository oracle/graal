package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.meta.Value;

@SuppressWarnings("serial")
public class KindsMismatchException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;
    public RAValue value1;
    public RAValue value2;
    public boolean origVsCurr;

    public KindsMismatchException(LIRInstruction instruction, BasicBlock<?> block, RAValue value1, RAValue value2, boolean origVsCurr) {
        super(KindsMismatchException.getErrorMessage(instruction, block, value1, value2, origVsCurr));

        this.instruction = instruction;
        this.block = block;
        this.value1 = value1;
        this.value2 = value2;
        this.origVsCurr = origVsCurr;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue value1, RAValue value2, boolean origVsCurr) {
        if (origVsCurr) {
            return value1.getValue().getValueKind() + " has different kind after allocation: " + value2.getValue().getValueKind() + " in " + instruction + " in block " + block;
        }

        return "Value in location has different kind: " + value1.getValue().getValueKind() + " vs. " + value2.getValue().getValueKind() + " in " + instruction + " in block " + block;
    }
}
