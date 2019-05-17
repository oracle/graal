package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprSCENode extends LLVMExpressionNode {

    @Child protected LLVMExpressionNode left, right;
    protected SCEKind kind;

    public DebugExprSCENode(LLVMExpressionNode left, LLVMExpressionNode right, SCEKind kind) {
        this.left = left;
        this.right = right;
        this.kind = kind;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            boolean leftBoolean = left.executeI1(frame);
            if (!leftBoolean && kind == SCEKind.AND) {
                return leftBoolean;
            } else if (leftBoolean && kind == SCEKind.OR) {
                return leftBoolean;
            } else {
                return right.executeI1(frame);
            }
        } catch (UnexpectedResultException e) {
            return DebugExprVarNode.noObj;
        }

    }

    public enum SCEKind {
        AND,
        OR;
    }

}
