package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeField(name = "array", type = Object.class)
public abstract class PELangLiteralArrayNode extends PELangExpressionNode {

    public abstract Object getArray();

    @Override
    @Specialization(guards = "isLongArray()")
    public long[] executeLongArray(VirtualFrame frame) {
        return (long[]) getArray();
    }

    @Override
    @Specialization(guards = "isStringArray()")
    public String[] executeStringArray(VirtualFrame frame) {
        return (String[]) getArray();
    }

    @Override
    @Specialization(guards = "isArray()")
    public Object executeArray(VirtualFrame frame) {
        return getArray();
    }

    protected boolean isLongArray() {
        return getArray() instanceof long[];
    }

    protected boolean isStringArray() {
        return getArray() instanceof String[];
    }

    protected boolean isArray() {
        return getArray().getClass().isArray();
    }

    public static PELangLiteralArrayNode create(Object array) {
        return PELangLiteralArrayNodeGen.create(array);
    }

}
