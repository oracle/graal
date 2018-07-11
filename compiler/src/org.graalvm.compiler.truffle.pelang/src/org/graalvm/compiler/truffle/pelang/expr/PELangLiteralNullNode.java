package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.PELangNull;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangLiteralNullNode extends PELangExpressionNode {

    @Override
    public PELangNull executeGeneric(VirtualFrame frame) {
        return PELangNull.getInstance();
    }

}
