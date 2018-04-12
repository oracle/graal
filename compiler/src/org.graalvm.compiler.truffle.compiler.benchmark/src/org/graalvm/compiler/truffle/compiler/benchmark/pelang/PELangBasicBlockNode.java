package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PELangBasicBlockNode extends PELangExpressionNode {

    public static final int NO_SUCCESSOR = -1;

    @Child protected PELangExpressionNode bodyNode;

    public PELangBasicBlockNode(PELangExpressionNode bodyNode) {
        this.bodyNode = bodyNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeBlock(frame);
    }

    public abstract int executeBlock(VirtualFrame frame);

}
