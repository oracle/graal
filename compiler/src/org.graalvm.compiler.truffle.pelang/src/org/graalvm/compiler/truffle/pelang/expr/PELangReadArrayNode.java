package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class PELangReadArrayNode extends PELangExpressionNode {

    @Child private PELangExpressionNode arrayNode;
    @Child private PELangExpressionNode indicesNode;

    public PELangReadArrayNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode) {
        this.arrayNode = arrayNode;
        this.indicesNode = indicesNode;
    }

    public PELangExpressionNode getArrayNode() {
        return arrayNode;
    }

    public PELangExpressionNode getIndicesNode() {
        return indicesNode;
    }

    @Override
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame) {
        Object array = arrayNode.evaluateArray(frame);
        long[] indices = indicesNode.evaluateLongArray(frame);

        if (indices.length == 0) {
            throw new PELangException("length of indices must not be zero", this);
        }

        Object value = array;
        for (int i = 0; i < indices.length; i++) {
            int index = (int) indices[i];
            value = Array.get(value, index);
        }
        return value;
    }

}
