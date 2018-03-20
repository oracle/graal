package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangLiteralStringNode extends PELangExpressionNode {

    private final String value;

    public PELangLiteralStringNode(String value) {
        this.value = value;
    }

    @Override
    public String executeGeneric(VirtualFrame frame) {
        return value;
    }

}
