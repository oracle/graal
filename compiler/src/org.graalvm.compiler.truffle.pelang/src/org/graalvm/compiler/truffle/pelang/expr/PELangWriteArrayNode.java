package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ClassUtil;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("arrayNode"), @NodeChild("indicesNode"), @NodeChild("valueNode")})
public abstract class PELangWriteArrayNode extends PELangExpressionNode {

    @Specialization
    public long writeLongArray(long[] array, long index, long value) {
        array[(int) index] = value;
        return value;
    }

    @Specialization
    public String writeStringArray(String[] array, long index, String value) {
        array[(int) index] = value;
        return value;
    }

    @Specialization
    public Object writeArray(Object array, long[] indices, Object value) {
        Object unwrappedArray = unwrapArray(array, indices);
        int lastIndex = (int) indices[indices.length - 1];

        Class<?> componentType = unwrappedArray.getClass().getComponentType();
        Class<?> valueType = value.getClass();

        if (!ClassUtil.isAssignableTo(componentType, valueType)) {
            throw new PELangException("value type must be assignable to array component type", this);
        }
        writeValue(unwrappedArray, lastIndex, value);
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

    public static PELangWriteArrayNode create(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode, PELangExpressionNode valueNode) {
        return PELangWriteArrayNodeGen.create(arrayNode, indicesNode, valueNode);
    }

}
