package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

/**
 * Target location was overwritten when looking for
 * label variable locations by their usage.
 */
@SuppressWarnings("serial")
public class TargetLocationOverwrittenException extends RAVException {
    public RAVInstruction.ValueMove valueMove;
    public BasicBlock<?> block;

    public TargetLocationOverwrittenException(RAVInstruction.ValueMove move, BasicBlock<?> block) {
        super(getErrorMessage(move, block));
        this.valueMove = move;
        this.block = block;
    }

    static String getErrorMessage(RAVInstruction.ValueMove move, BasicBlock<?> block) {
        return "Target location " + move.location + " was overwritten by " + move.lirInstruction + " in " + block;
    }
}
