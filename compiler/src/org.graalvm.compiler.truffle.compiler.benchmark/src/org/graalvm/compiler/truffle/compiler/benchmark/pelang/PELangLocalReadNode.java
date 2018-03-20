package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeField(name = "slot", type = FrameSlot.class)
public abstract class PELangLocalReadNode extends PELangExpressionNode {

    protected abstract FrameSlot getSlot();

    @Specialization(guards = "isLong(frame)")
    protected long readLong(VirtualFrame frame) {
        return FrameUtil.getLongSafe(frame, getSlot());
    }

    @Specialization(replaces = {"readLong"})
    protected Object readObject(VirtualFrame frame) {
        if (!frame.isObject(getSlot())) {
            /*
             * The FrameSlotKind has been set to Object, so from now on all writes to the local variable will be
             * Object writes. However, now we are in a frame that still has an old non-Object value. This is a
             * slow-path operation: we read the non-Object value, and write it immediately as an Object value so
             * that we do not hit this path again multiple times for the same variable of the same frame.
             */
            CompilerDirectives.transferToInterpreter();
            Object result = frame.getValue(getSlot());
            frame.setObject(getSlot(), result);
            return result;
        }
        return FrameUtil.getObjectSafe(frame, getSlot());
    }

    /**
     * Guard function that the local variable has the type {@code long}.
     *
     * @param frame The parameter seems unnecessary, but it is required: Without the parameter, the
     *            Truffle DSL would not check the guard on every execution of the specialization. Guards
     *            without parameters are assumed to be pure, but our guard depends on the slot kind
     *            which can change.
     */
    protected boolean isLong(VirtualFrame frame) {
        return getSlot().getKind() == FrameSlotKind.Long;
    }

}
