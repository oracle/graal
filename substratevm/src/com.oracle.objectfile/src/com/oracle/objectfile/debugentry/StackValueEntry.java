package com.oracle.objectfile.debugentry;

import java.util.Objects;

public class StackValueEntry extends LocalValueEntry {
    private final int stackSlot;

    public StackValueEntry(int stackSlot, LocalEntry local) {
        super(local);
        this.stackSlot = stackSlot;
    }

    @Override
    public String toString() {
        return "STACK:" + stackSlot;
    }

    public int getStackSlot() {
        return stackSlot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (StackValueEntry) obj;
        return this.stackSlot == that.stackSlot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stackSlot);
    }
}
