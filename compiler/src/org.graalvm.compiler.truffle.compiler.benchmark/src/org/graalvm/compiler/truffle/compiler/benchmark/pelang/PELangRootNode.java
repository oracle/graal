package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public final class PELangRootNode extends RootNode {

    @Child private PELangExpressionNode bodyNode;

    protected PELangRootNode(TruffleLanguage<?> language, PELangExpressionNode bodyNode) {
        super(language);
        this.bodyNode = bodyNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return bodyNode.executeGeneric(frame);
    }

}
