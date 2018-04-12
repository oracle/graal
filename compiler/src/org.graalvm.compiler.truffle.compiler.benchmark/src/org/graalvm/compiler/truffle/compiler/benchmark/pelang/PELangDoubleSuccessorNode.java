package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public class PELangDoubleSuccessorNode extends PELangBasicBlockNode {

    private final int firstSuccessor;
    private final int secondSuccessor;

    public PELangDoubleSuccessorNode(PELangExpressionNode bodyNode, int firstSuccessor, int secondSuccessor) {
        super(bodyNode);
        this.firstSuccessor = firstSuccessor;
        this.secondSuccessor = secondSuccessor;
    }

    @Override
    public int executeBlock(VirtualFrame frame) {
        long conditionResult = bodyNode.evaluateCondition(frame);
        return (conditionResult == 0L) ? firstSuccessor : secondSuccessor;
    }

}
