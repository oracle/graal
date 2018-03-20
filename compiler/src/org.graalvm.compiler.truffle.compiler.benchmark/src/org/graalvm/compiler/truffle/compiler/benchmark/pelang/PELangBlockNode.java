package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangBlockNode extends PELangExpressionNode {

    @Children private final PELangExpressionNode[] bodyNodes;

    public PELangBlockNode(PELangExpressionNode[] bodyNodes) {
        this.bodyNodes = bodyNodes;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(bodyNodes.length);

        Object lastResult = PELangNull.Instance;

        for (PELangExpressionNode bodyNode : bodyNodes) {
            lastResult = bodyNode.executeGeneric(frame);
        }
        return lastResult;
    }

}
