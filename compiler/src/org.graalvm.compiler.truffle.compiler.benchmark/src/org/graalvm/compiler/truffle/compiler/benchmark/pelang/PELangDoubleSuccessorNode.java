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
    public Execution executeBlock(VirtualFrame frame) {
        long conditionResult = bodyNode.evaluateCondition(frame);
        int successor = (conditionResult == 0L) ? firstSuccessor : secondSuccessor;

        return new Execution(conditionResult, successor);
    }

}
