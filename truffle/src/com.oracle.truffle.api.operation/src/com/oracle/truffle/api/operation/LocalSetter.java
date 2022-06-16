package com.oracle.truffle.api.operation;

import java.util.HashMap;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class LocalSetter {

    private final int index;

    private static final HashMap<Integer, LocalSetter> LOCAL_SETTERS = new HashMap<>();

    public static LocalSetter create(int index) {
        return LOCAL_SETTERS.computeIfAbsent(index, LocalSetter::new);
    }

    public static LocalSetter[] createArray(int[] index) {
        LocalSetter[] result = new LocalSetter[index.length];
        for (int i = 0; i < index.length; i++) {
            result[i] = create(index[i]);
        }
        return result;
    }

    private LocalSetter(int index) {
        this.index = index;
    }

    public void setObject(VirtualFrame frame, Object value) {
        frame.setObject(index, value);
    }

    public void setLong(VirtualFrame frame, long value) {
        FrameSlotKind slotKind = frame.getFrameDescriptor().getSlotKind(index);
        if (slotKind == FrameSlotKind.Long) {
            frame.setLong(index, value);
        } else {
            // TODO this should be compatible with local boxing elimination
            frame.setObject(index, value);
        }
    }

    public void setInt(VirtualFrame frame, int value) {
        FrameSlotKind slotKind = frame.getFrameDescriptor().getSlotKind(index);
        if (slotKind == FrameSlotKind.Int) {
            frame.setInt(index, value);
        } else {
            // TODO this should be compatible with local boxing elimination
            frame.setObject(index, value);
        }
    }

    public void setDouble(VirtualFrame frame, double value) {
        FrameSlotKind slotKind = frame.getFrameDescriptor().getSlotKind(index);
        if (slotKind == FrameSlotKind.Double) {
            frame.setDouble(index, value);
        } else {
            // TODO this should be compatible with local boxing elimination
            frame.setObject(index, value);
        }
    }

    // TODO other primitives
}
