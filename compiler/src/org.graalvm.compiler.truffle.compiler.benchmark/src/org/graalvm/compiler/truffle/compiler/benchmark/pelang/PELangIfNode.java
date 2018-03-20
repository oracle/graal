package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangIfNode extends PELangExpressionNode {

    @Child private PELangExpressionNode conditionNode;
    @Child private PELangExpressionNode thenNode;
    @Child private PELangExpressionNode elseNode;

    public PELangIfNode(PELangExpressionNode conditionNode, PELangExpressionNode thenNode, PELangExpressionNode elseNode) {
        this.conditionNode = conditionNode;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        long conditionResult = conditionNode.evaluateCondition(frame);

        if (conditionResult == 0L) {
            return thenNode.executeGeneric(frame);
        } else {
            return elseNode.executeGeneric(frame);
        }
    }

}
