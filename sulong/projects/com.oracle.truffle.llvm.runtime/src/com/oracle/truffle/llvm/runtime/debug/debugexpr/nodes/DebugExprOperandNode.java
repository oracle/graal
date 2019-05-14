package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;

public class DebugExprOperandNode {
    public Object type;
    public Object value;

    public DebugExprOperandNode(Object type, Object value) {
        this.type = type;
        this.value = value;
    }

}
