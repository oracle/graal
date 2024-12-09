package com.oracle.objectfile.debugentry;

import java.util.Objects;

public class RegisterValueEntry extends LocalValueEntry {

    private final int regIndex;

    public RegisterValueEntry(int regIndex, LocalEntry local) {
        super(local);
        this.regIndex = regIndex;
    }

    @Override
    public String toString() {
        return "REG:" + regIndex;
    }

    public int getRegIndex() {
        return regIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (RegisterValueEntry) obj;
        return this.regIndex == that.regIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(regIndex);
    }
}
