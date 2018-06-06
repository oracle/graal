package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.PELangState;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangLiteralNullNode extends PELangExpressionNode {

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return PELangState.getNullObject();
    }

}
