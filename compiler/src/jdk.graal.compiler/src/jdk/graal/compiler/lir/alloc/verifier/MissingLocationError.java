package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;

/**
 * No location found in an instruction after allocation for certain variable.
 */
@SuppressWarnings("serial")
public class MissingLocationError extends RAVError {
    /**
     * Construct a MissingLocationError
     *
     * @param instruction Instruction where violation occurred
     * @param block       Block where violation occurred
     * @param variable    Variable before allocation, that has no location afterward
     */
    public MissingLocationError(LIRInstruction instruction, BasicBlock<?> block, RAValue variable) {
        super(MissingLocationError.getMessage(instruction, block, variable));
    }

    static String getMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue variable) {
        return "Variable " + variable + " is missing a location in " + instruction + " in block " + block;
    }
}
