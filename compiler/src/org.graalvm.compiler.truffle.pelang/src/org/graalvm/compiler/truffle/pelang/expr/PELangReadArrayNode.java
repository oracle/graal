package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ArrayUtil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("arrayNode"), @NodeChild("indicesNode")})
public abstract class PELangReadArrayNode extends PELangExpressionNode {

    @Specialization(guards = "array.getClass().isArray()")
    public Object readSingleArray(Object array, long index) {
        try {
            return ArrayUtil.readValue(array, (int) index);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    @Specialization(guards = "array.getClass().isArray()")
    public Object readMultiArray(Object array, long[] indices) {
        try {
            return ArrayUtil.unwrapArray(array, indices, indices.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    public static PELangReadArrayNode createNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode) {
        return PELangReadArrayNodeGen.create(arrayNode, indicesNode);
    }

}
