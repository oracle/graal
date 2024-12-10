package com.oracle.objectfile.debugentry;

public record RegisterValueEntry(int regIndex) implements LocalValueEntry {

    @Override
    public String toString() {
        return "REG:" + regIndex;
    }
}
