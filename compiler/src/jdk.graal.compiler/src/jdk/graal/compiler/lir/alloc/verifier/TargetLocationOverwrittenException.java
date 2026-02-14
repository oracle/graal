package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

@SuppressWarnings("serial")
public class TargetLocationOverwrittenException extends RAVException {
    public RAVInstruction.VirtualMove virtualMove;
    public BasicBlock<?> block;

    public TargetLocationOverwrittenException(RAVInstruction.VirtualMove move, BasicBlock<?> block) {
        super(getErrorMessage(move, block));
        this.virtualMove = move;
        this.block = block;
    }

    static String getErrorMessage(RAVInstruction.VirtualMove move, BasicBlock<?> block) {
        return "Target location " + move.location + " was overwritten by " + move.lirInstruction + " in " + block;
    }
}
