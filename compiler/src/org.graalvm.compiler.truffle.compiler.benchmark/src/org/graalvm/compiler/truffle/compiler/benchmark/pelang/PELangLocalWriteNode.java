package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("valueNode")
@NodeField(name = "slot", type = FrameSlot.class)
public abstract class PELangLocalWriteNode extends PELangExpressionNode {

    protected abstract FrameSlot getSlot();

    @Specialization(guards = "isLongOrIllegal(frame)")
    protected long writeLong(VirtualFrame frame, long value) {
        getSlot().setKind(FrameSlotKind.Long);
        frame.setLong(getSlot(), value);
        return value;
    }

    @Specialization(replaces = {"writeLong"})
    protected Object write(VirtualFrame frame, Object value) {
        getSlot().setKind(FrameSlotKind.Object);
        frame.setObject(getSlot(), value);
        return value;
    }

    /**
     * Guard function that the local variable has the type {@code long}.
     *
     * @param frame The parameter seems unnecessary, but it is required: Without the parameter, the
     *            Truffle DSL would not check the guard on every execution of the specialization. Guards
     *            without parameters are assumed to be pure, but our guard depends on the slot kind
     *            which can change.
     */
    protected boolean isLongOrIllegal(VirtualFrame frame) {
        return getSlot().getKind() == FrameSlotKind.Long || getSlot().getKind() == FrameSlotKind.Illegal;
    }

}
