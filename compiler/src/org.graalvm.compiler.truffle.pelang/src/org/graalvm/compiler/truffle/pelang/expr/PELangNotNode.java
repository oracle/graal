package org.graalvm.compiler.truffle.pelang.expr;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangNotNode extends PELangExpressionNode {

    @Child private PELangExpressionNode bodyNode;

    public PELangNotNode(PELangExpressionNode bodyNode) {
        this.bodyNode = bodyNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        long result = bodyNode.evaluateCondition(frame);

        if (result == 0L) {
            return 1L;
        } else {
            return 0L;
        }
    }

}
