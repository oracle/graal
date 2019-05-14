package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;

public class DebugExprAddNode extends DebugExprBinaryNode {

    public DebugExprAddNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        super(left, right);
    }

    @Override
    public DebugExprOperandNode execute() {
        if (left.type.toString().contentEquals("int") && right.type.toString().contentEquals("int")) {
            type = left.type;
            value = left.value;
            System.out.print("VALUE: " + left.value + "|" + right.value + "=" + value);
        } else {
            System.out.print("TYPE: " + left.type + "|" + right.type);
        }
        return this;
    }

}
