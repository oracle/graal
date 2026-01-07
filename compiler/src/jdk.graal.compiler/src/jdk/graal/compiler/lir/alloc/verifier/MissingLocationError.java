package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.meta.Value;

@SuppressWarnings("serial")
public class MissingLocationError extends RAVError {
    public MissingLocationError(LIRInstruction instruction, BasicBlock<?> block, Value variable) {
        super(MissingLocationError.getMessage(instruction, block, variable));
    }

    static String getMessage(LIRInstruction instruction, BasicBlock<?> block, Value variable) {
        return "Variable " + variable + " is missing a location in " + instruction + " in block " + block;
    }
}
