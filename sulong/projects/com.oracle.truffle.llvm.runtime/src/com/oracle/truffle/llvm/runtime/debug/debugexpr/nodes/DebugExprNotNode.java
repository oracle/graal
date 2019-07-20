package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(value = "child", type = LLVMExpressionNode.class)
public abstract class DebugExprNotNode extends LLVMExpressionNode {
    public abstract Object executeWithTarget(Object child);

    @Specialization
    protected boolean flip(boolean child) {
        return !child;
    }

    @Specialization
    protected byte flip(byte child) {
        return (byte) (child == 0 ? 1 : 0);
    }

    @Specialization
    protected short flip(short child) {
        return (short) (child == 0 ? 1 : 0);
    }

    @Specialization
    protected char flip(char child) {
        return (char) (child == 0 ? 1 : 0);
    }

    @Specialization
    protected int flip(int child) {
        return (child == 0 ? 1 : 0);
    }

    @Specialization
    protected long flip(long child) {
        return (child == 0 ? 1 : 0);
    }

    @Fallback
    protected Object typeError(Object child) {
        throw DebugExprException.typeError(this, child);
    }

}
