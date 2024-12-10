package com.oracle.objectfile.debugentry;

public record StackValueEntry(int stackSlot) implements LocalValueEntry {

    @Override
    public String toString() {
        return "STACK:" + stackSlot;
    }
}
