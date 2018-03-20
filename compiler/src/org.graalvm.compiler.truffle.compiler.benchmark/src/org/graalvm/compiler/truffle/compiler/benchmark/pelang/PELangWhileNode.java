package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;

public final class PELangWhileNode extends PELangExpressionNode {

    @Child private LoopNode loopNode;

    public PELangWhileNode(PELangExpressionNode conditionNode, PELangExpressionNode bodyNode) {
        loopNode = Truffle.getRuntime().createLoopNode(new PELangWhileRepeatingNode(conditionNode, bodyNode));
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object lastResult = PELangNull.Instance;

        try {
            loopNode.executeLoop(frame);
        } catch (PELangResultException e) {
            lastResult = e.getLastResult();
        }
        return lastResult;
    }

    private static final class PELangWhileRepeatingNode implements RepeatingNode {

        @Child private PELangExpressionNode conditionNode;
        @Child private PELangExpressionNode bodyNode;

        private Object lastResult = PELangNull.Instance;

        public PELangWhileRepeatingNode(PELangExpressionNode conditionNode, PELangExpressionNode bodyNode) {
            this.conditionNode = conditionNode;
            this.bodyNode = bodyNode;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            long conditionResult = conditionNode.evaluateCondition(frame);

            if (conditionResult == 0L) {
                lastResult = bodyNode.executeGeneric(frame);
                return true;
            } else {
                throw new PELangResultException(lastResult);
            }
        }
    }

    private static final class PELangResultException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final Object lastResult;

        public PELangResultException(Object lastResult) {
            this.lastResult = lastResult;
        }

        public Object getLastResult() {
            return lastResult;
        }

    }

}
