package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangLiteralArrayNode extends PELangExpressionNode {

    private final Object array;

    public PELangLiteralArrayNode(Object array) {
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("argument is not an array");
        }
        this.array = array;
    }

    public Object getArray() {
        return array;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return array;
    }

}
