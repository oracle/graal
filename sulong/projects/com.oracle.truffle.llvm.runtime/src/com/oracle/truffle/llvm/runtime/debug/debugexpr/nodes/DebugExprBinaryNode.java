package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

public abstract class DebugExprBinaryNode extends DebugExprOperandNode {

    protected DebugExprOperandNode left, right;

    public DebugExprBinaryNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        super(null, null);
        this.left = left;
        this.right = right;
    }

    public abstract DebugExprOperandNode execute();

}
