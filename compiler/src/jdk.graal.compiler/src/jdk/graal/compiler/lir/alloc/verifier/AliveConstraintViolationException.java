package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;

@SuppressWarnings("serial")
public class AliveConstraintViolationException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;

    public AliveConstraintViolationException(LIRInstruction instruction, BasicBlock<?> block, RAValue location, boolean asDest) {
        super(AliveConstraintViolationException.getErrorMessage(instruction, block, location, asDest));
        this.instruction = instruction;
        this.block = block;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue location, boolean asDest) {
        if (asDest) {
            return "Location " + location + " used as both alive and output in " + instruction + " in block" + block;
        }

        return "Location " + location + " used as both alive and temp in " + instruction + "in block" + block;
    }
}
