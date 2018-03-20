package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class PELangLiteralLongNode extends PELangExpressionNode {

    private final long value;

    public PELangLiteralLongNode(long value) {
        this.value = value;
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }

}
