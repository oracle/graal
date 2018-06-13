package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ClassUtil;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class PELangWriteArrayNode extends PELangExpressionNode {

    @Child private PELangExpressionNode arrayNode;
    @Child private PELangExpressionNode indicesNode;
    @Child private PELangExpressionNode valueNode;

    public PELangWriteArrayNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode, PELangExpressionNode valueNode) {
        this.arrayNode = arrayNode;
        this.indicesNode = indicesNode;
        this.valueNode = valueNode;
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
        array = unwrapArray(array, indices);
        int lastIndex = (int) indices[indices.length - 1];

        Object value = valueNode.executeGeneric(frame);
        Class<?> componentType = array.getClass().getComponentType();
        Class<?> valueType = value.getClass();

        if (!ClassUtil.isAssignableTo(componentType, valueType)) {
            throw new PELangException("value type must be assignable to array component type", this);
        }
        writeValue(array, lastIndex, value);
        return value;
    }

    private static Object unwrapArray(Object array, long[] indices) {
        Object value = array;

        // stop unwrapping at last array
        for (int i = 0; i < indices.length - 1; i++) {
            int index = (int) indices[i];
            value = readValue(value, index);
        }
        return value;
    }

    @TruffleBoundary
    private static Object readValue(Object array, int index) {
        return Array.get(array, index);
    }

    @TruffleBoundary
    private static void writeValue(Object array, int index, Object value) {
        Array.set(array, index, value);
    }

}
