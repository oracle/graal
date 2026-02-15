package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.vm.ci.meta.Value;

@SuppressWarnings("serial")
public class ValueNotInRegisterException extends RAVException {
    public LIRInstruction instruction;
    public BasicBlock<?> block;
    public RAValue variable; // Can be a constant or other symbolic value
    public RAValue location; // Can be StackSlot, RegisterValue or memory
    public AllocationState state;

    public ValueNotInRegisterException(LIRInstruction instruction, BasicBlock<?> block, RAValue variable, RAValue location, AllocationState state) {
        super(ValueNotInRegisterException.getErrorMessage(instruction, block, variable, location, state));

        this.instruction = instruction;
        this.block = block;
        this.variable = variable;
        this.location = location;
        this.state = state;
    }

    static String getErrorMessage(LIRInstruction instruction, BasicBlock<?> block, RAValue variable, RAValue location, AllocationState state) {
        var messageBuilder = new StringBuilder();
        // @formatter:off
        messageBuilder
                .append("Value ")
                .append(variable)
                .append(" not found in ")
                .append(location)
                .append(" for instruction ")
                .append(instruction)
                .append(" in ")
                .append(block)
                .append(" actual state is ");
        // @formatter:on

        if (state instanceof ConflictedAllocationState confState) {
            var confStates = confState.getConflictedStates();

            messageBuilder.append("\n");
            for (var conflictedState : confStates) {
                messageBuilder.append(" - ").append(conflictedState.getRAValue()).append(" from ").append(conflictedState.source).append("\n");
            }
        } else {
            messageBuilder.append(state);
        }

        return messageBuilder.toString();
    }
}
