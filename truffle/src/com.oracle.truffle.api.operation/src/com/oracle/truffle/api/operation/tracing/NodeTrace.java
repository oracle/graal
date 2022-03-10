package com.oracle.truffle.api.operation.tracing;

public class NodeTrace {
    private final InstructionTrace[] instructions;

    public NodeTrace(InstructionTrace[] instructions) {
        this.instructions = instructions;
    }

    public InstructionTrace[] getInstructions() {
        return instructions;
    }
}
