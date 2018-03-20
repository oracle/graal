package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@TypeSystemReference(PELangTypes.class)
public abstract class PELangExpressionNode extends Node {

    public abstract Object executeGeneric(VirtualFrame frame);

    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return PELangTypesGen.expectLong(executeGeneric(frame));
    }

    public String executeString(VirtualFrame frame) throws UnexpectedResultException {
        return PELangTypesGen.expectString(executeGeneric(frame));
    }

    protected long evaluateCondition(VirtualFrame frame) {
        try {
            return executeLong(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type long", this);
        }
    }

}
