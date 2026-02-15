package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;

@SuppressWarnings("serial")
public class MissingLocationError extends RAVError {
    public MissingLocationError(LIRInstruction instruction, BasicBlock<?> block, RAValue variable) {
        super(MissingLocationError.getMessage(instruction, block, variable));
    }

    static String getMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue variable) {
        return "Variable " + variable + " is missing a location in " + instruction + " in block " + block;
    }
}
