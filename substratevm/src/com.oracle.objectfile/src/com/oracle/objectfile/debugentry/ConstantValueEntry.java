package com.oracle.objectfile.debugentry;

import java.util.Objects;

import jdk.vm.ci.meta.JavaConstant;

public class ConstantValueEntry extends LocalValueEntry {
    private final long heapOffset;
    private final JavaConstant constant;

    public ConstantValueEntry(long heapOffset, JavaConstant constant, LocalEntry local) {
        super(local);
        this.heapOffset = heapOffset;
        this.constant = constant;
    }

    @Override
    public String toString() {
        return "CONST:" + (constant != null ? constant.toValueString() : "null") + "[" + Long.toHexString(heapOffset) + "]";
    }

    public JavaConstant getConstant() {
        return constant;
    }

    public long getHeapOffset() {
        return heapOffset;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (ConstantValueEntry) obj;
        return this.heapOffset == that.heapOffset &&
                        Objects.equals(this.constant, that.constant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(heapOffset, constant);
    }
}
