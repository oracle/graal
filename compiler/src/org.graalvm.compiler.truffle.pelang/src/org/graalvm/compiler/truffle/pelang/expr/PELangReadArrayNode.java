package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("arrayNode"), @NodeChild("indicesNode")})
public abstract class PELangReadArrayNode extends PELangExpressionNode {

    @Specialization
    public long readLongArray(long[] array, long index) {
        return array[(int) index];
    }

    @Specialization
    public String readStringArray(String[] array, long index) {
        return array[(int) index];
    }

    @Specialization
    public Object readArray(Object array, long[] indices) {
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

    public static PELangReadArrayNode createNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode) {
        return PELangReadArrayNodeGen.create(arrayNode, indicesNode);
    }

}
