package org.graalvm.compiler.truffle.pelang.ncf;

import org.graalvm.compiler.truffle.pelang.expr.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.stmt.PELangStatementNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;

public final class PELangWhileRepeatingNode extends Node implements RepeatingNode {

    @Child private PELangExpressionNode conditionNode;
    @Child private PELangStatementNode bodyNode;

    PELangWhileRepeatingNode(PELangExpressionNode conditionNode, PELangStatementNode bodyNode) {
        this.conditionNode = conditionNode;
        this.bodyNode = bodyNode;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (conditionNode.evaluateCondition(frame) == 0L) {
            bodyNode.executeVoid(frame);
            return true;
        } else {
            return false;
        }
    }

}
