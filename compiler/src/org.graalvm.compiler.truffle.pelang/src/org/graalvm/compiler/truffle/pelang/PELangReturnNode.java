package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangReturnNode extends PELangStatementNode {

    @Child private PELangExpressionNode bodyNode;

    public PELangReturnNode(PELangExpressionNode bodyNode) {
        this.bodyNode = bodyNode;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object result = bodyNode.executeGeneric(frame);
        throw new PELangReturnException(result);
    }

}
