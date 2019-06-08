package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExpressionNode {

    private final LLVMExpressionNode node;
    private final DebugExprType type;

    public DebugExpressionNode(LLVMExpressionNode node, DebugExprType type) {
        this.node = node;
        this.type = type;
    }

    public LLVMExpressionNode getNode() {
        return node;
    }

    public DebugExprType getType() {
        return type;
    }

}