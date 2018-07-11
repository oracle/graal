package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ArrayUtil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("arrayNode"), @NodeChild("indicesNode"), @NodeChild("valueNode")})
public abstract class PELangWriteArrayNode extends PELangExpressionNode {

    @Specialization(guards = "array.getClass().isArray()")
    public Object writeSingleArray(Object array, long index, Object value) {
        try {
            ArrayUtil.writeValue(array, (int) index, value);
            return value;
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    @Specialization(guards = "array.getClass().isArray()")
    public Object writeMultiArray(Object array, long[] indices, Object value) {
        try {
            Object unwrappedArray = ArrayUtil.unwrapArray(array, indices, indices.length - 1);
            int lastIndex = (int) indices[indices.length - 1];
            ArrayUtil.writeValue(unwrappedArray, lastIndex, value);
            return value;
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    public static PELangWriteArrayNode createNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode, PELangExpressionNode valueNode) {
        return PELangWriteArrayNodeGen.create(arrayNode, indicesNode, valueNode);
    }

}
