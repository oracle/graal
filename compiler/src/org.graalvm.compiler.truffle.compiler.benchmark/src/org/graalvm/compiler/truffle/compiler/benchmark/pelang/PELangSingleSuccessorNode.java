package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public class PELangSingleSuccessorNode extends PELangBasicBlockNode {

    private final int successor;

    public PELangSingleSuccessorNode(PELangExpressionNode bodyNode, int successor) {
        super(bodyNode);
        this.successor = successor;
    }

    @Override
    public int executeBlock(VirtualFrame frame) {
        bodyNode.executeGeneric(frame);
        return successor;
    }

}
