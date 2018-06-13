package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

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
    public Object executeGeneric(VirtualFrame frame) {
        Object array = arrayNode.evaluateArray(frame);
        long[] indices = indicesNode.evaluateLongArray(frame);

        if (indices.length == 0) {
            throw new PELangException("length of indices must not be zero", this);
        }
        return unwrapArray(array, indices);
    }

    private static Object unwrapArray(Object array, long[] indices) {
        Object value = array;

        // fully unwrap
        for (int i = 0; i < indices.length; i++) {
            int index = (int) indices[i];
            value = readValue(value, index);
        }
        return value;
    }

    @TruffleBoundary
    private static Object readValue(Object array, int index) {
        return Array.get(array, index);
    }

}
