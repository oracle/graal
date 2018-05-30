package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ClassUtil;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

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
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame) {
        Object array = arrayNode.evaluateArray(frame);
        long[] indices = indicesNode.evaluateLongArray(frame);
        Object value = valueNode.executeGeneric(frame);

        if (indices.length == 0) {
            throw new PELangException("length of indices must not be zero", this);
        }

        for (int i = 0; i < indices.length - 1; i++) {
            int index = (int) indices[i];
            array = Array.get(array, index);
        }

        Class<?> componentType = array.getClass().getComponentType();
        Class<?> valueType = value.getClass();

        if (!ClassUtil.isAssignableTo(componentType, valueType)) {
            throw new PELangException("value type must be assignable to array component type", this);
        }

        int lastIndex = (int) indices[indices.length - 1];
        Array.set(array, lastIndex, value);
        return value;
    }

}
