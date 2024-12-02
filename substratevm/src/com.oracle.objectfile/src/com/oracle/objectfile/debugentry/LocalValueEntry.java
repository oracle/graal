package com.oracle.objectfile.debugentry;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.LocalValueKind;
import jdk.vm.ci.meta.JavaConstant;

public record LocalValueEntry(int regIndex, int stackSlot, long heapOffset, JavaConstant constant, LocalValueKind localKind, LocalEntry local) {

    @Override
    public String toString() {
        return switch (localKind) {
            case REGISTER -> "reg[" + regIndex + "]";
            case STACK -> "stack[" + stackSlot + "]";
            case CONSTANT -> "constant[" + (constant != null ? constant.toValueString() : "null") + "]";
            default -> "-";
        };
    }

}
