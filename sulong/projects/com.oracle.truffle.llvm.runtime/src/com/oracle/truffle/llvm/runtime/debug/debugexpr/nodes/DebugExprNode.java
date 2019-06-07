package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class DebugExprNode extends LLVMExpressionNode {

    protected LLVMExpressionNode expressionNode;
    protected DebugExprType type;

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeGenericWithType(frame).getLeft();
    }

    public abstract Pair<Object, DebugExprType> executeGenericWithType(VirtualFrame frame);

}
