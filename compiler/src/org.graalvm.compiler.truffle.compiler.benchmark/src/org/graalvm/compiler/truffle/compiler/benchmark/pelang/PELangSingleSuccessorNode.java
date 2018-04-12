package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public class PELangSingleSuccessorNode extends PELangBasicBlockNode {

    private final int successor;

    public PELangSingleSuccessorNode(PELangExpressionNode bodyNode, int successor) {
        super(bodyNode);
        this.successor = successor;
    }

    @Override
    public Execution executeBlock(VirtualFrame frame) {
        Object result = bodyNode.executeGeneric(frame);
        return new Execution(result, successor);
    }

}
