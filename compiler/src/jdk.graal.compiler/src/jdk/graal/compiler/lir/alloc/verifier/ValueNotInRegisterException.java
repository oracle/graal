package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.meta.Value;

@SuppressWarnings("serial")
public class ValueNotInRegisterException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;
    public Value variable; // Can be a constant or other symbolic value
    public Value location; // Can be StackSlot, RegisterValue or memory
    public AllocationState state;

    public ValueNotInRegisterException(LIRInstruction instruction, BasicBlock<?> block, Value variable, Value location, AllocationState state) {
        super(ValueNotInRegisterException.getErrorMessage(instruction, block, variable, location, state));

        this.instruction = instruction;
        this.block = block;
        this.variable = variable;
        this.location = location;
        this.state = state;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, Value variable, Value location, AllocationState state) {
        return "Value " +
                variable +
                " not found in " +
                location +
                " for instruction " +
                instruction +
                " in block " +
                block +
                " actual state is " +
                state;
    }
}
