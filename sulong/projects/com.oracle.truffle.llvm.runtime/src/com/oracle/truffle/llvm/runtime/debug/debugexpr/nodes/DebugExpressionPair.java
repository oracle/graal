package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExpressionPair {

    private final LLVMExpressionNode node;
    private final DebugExprType type;

    private DebugExpressionPair(LLVMExpressionNode node, DebugExprType type) {
        this.node = node;
        this.type = type;
    }

    public static DebugExpressionPair create(LLVMExpressionNode node, DebugExprType type) {
        return new DebugExpressionPair(node, type);
    }

    public LLVMExpressionNode getNode() {
        return node;
    }

    public DebugExprType getType() {
        return type;
    }

}