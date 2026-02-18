package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp;

/**
 * Unknown instruction was found after the allocation,
 * this usually means that it's a different instruction
 * from a Move, and we do not know how to handle it.
 */
@SuppressWarnings("serial")
public class UnknownInstructionError extends RAVError {
    public LIRInstruction instruction;
    public BasicBlock<?> block;

    public UnknownInstructionError(LIRInstruction instruction, BasicBlock<?> block) {
        super(UnknownInstructionError.getErrorMessage(instruction, block));

        this.instruction = instruction;
        this.block = block;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block) {
        if (instruction.isMoveOp()) {
            return "Unknown MOVE " + instruction + " in " + block;
        }

        if (instruction instanceof StandardOp.LabelOp) {
            return "Unknown LABEL " + instruction + " in " + block;
        }

        return "Unknown instruction for RAV " + instruction + " in " + block;
    }
}
