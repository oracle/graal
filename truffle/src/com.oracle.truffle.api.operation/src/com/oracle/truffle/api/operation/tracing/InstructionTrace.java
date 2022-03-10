package com.oracle.truffle.api.operation.tracing;

public class InstructionTrace {
    private final int id;
    private final int hitCount;
    private final Object[] arguments;

    public InstructionTrace(int id, int hitCount, Object... arguments) {
        this.id = id;
        this.hitCount = hitCount;
        this.arguments = arguments;
    }

    public int getId() {
        return id;
    }

    public int getHitCount() {
        return hitCount;
    }

    public Object[] getArguments() {
        return arguments;
    }
}
