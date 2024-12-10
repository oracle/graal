package com.oracle.objectfile.debugentry;

import jdk.vm.ci.meta.JavaConstant;

public record ConstantValueEntry(long heapOffset, JavaConstant constant) implements LocalValueEntry {

    @Override
    public String toString() {
        return "CONST:" + (constant != null ? constant.toValueString() : "null") + "[" + Long.toHexString(heapOffset) + "]";
    }
}
