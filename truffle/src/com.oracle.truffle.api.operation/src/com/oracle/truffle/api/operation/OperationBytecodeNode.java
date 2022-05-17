package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class OperationBytecodeNode extends Node implements BytecodeOSRNode {

    // Thevalues of these must be the same as FrameKind.ordinal && Frame tags
    public static final int FRAME_TYPE_OBJECT = 0;
    public static final int FRAME_TYPE_LONG = 1;
    public static final int FRAME_TYPE_INT = 2;
    public static final int FRAME_TYPE_DOUBLE = 3;
    public static final int FRAME_TYPE_FLOAT = 4;
    public static final int FRAME_TYPE_BOOLEAN = 5;
    public static final int FRAME_TYPE_BYTE = 6;
    public static final int FRAME_TYPE_ILLEGAL = 7;

    static {
        assert FRAME_TYPE_OBJECT == FrameSlotKind.Object.tag;
        assert FRAME_TYPE_LONG == FrameSlotKind.Long.tag;
        assert FRAME_TYPE_INT == FrameSlotKind.Int.tag;
        assert FRAME_TYPE_DOUBLE == FrameSlotKind.Double.tag;
        assert FRAME_TYPE_FLOAT == FrameSlotKind.Float.tag;
        assert FRAME_TYPE_BOOLEAN == FrameSlotKind.Boolean.tag;
        assert FRAME_TYPE_BYTE == FrameSlotKind.Byte.tag;
        assert FRAME_TYPE_ILLEGAL == FrameSlotKind.Illegal.tag;
    }

    private static final ByteArraySupport LE_BYTES = ByteArraySupport.littleEndian();

    protected final int maxStack;
    protected final int maxLocals;

    @CompilationFinal(dimensions = 1) protected final byte[] bc;
    @CompilationFinal(dimensions = 1) protected final Object[] consts;
    @Children protected final Node[] children;
    @CompilationFinal(dimensions = 1) protected final BuilderExceptionHandler[] handlers;
    @CompilationFinal(dimensions = 1) protected final ConditionProfile[] conditionProfiles;

    protected static final int VALUES_OFFSET = 0;

    protected OperationBytecodeNode(int maxStack, int maxLocals, byte[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers, ConditionProfile[] conditionProfiles) {
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.bc = bc;
        this.consts = consts;
        this.children = children;
        this.handlers = handlers;
        this.conditionProfiles = conditionProfiles;
    }

    FrameDescriptor createFrameDescriptor() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlots(maxLocals, FrameSlotKind.Illegal);
        builder.addSlots(maxStack, FrameSlotKind.Illegal);
        return builder.build();
    }

    Object execute(VirtualFrame frame) {
        return continueAt(frame, 0, maxLocals + VALUES_OFFSET);
    }

    protected abstract Object continueAt(VirtualFrame frame, int bci, int sp);

    boolean isInstrumented() {
        return false;
    }

    // OSR

    @CompilationFinal private Object osrMetadata;

    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        return continueAt(osrFrame, target, (int) interpreterState);
    }

    public Object getOSRMetadata() {
        return osrMetadata;
    }

    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    // boxing elim

    protected static void setResultBoxedImpl(byte[] bc, int bci, int targetType, short[] descriptor) {
        int op = LE_BYTES.getShort(bc, bci) & 0xffff;
        short todo = descriptor[op];

        if (todo > 0) {
            // quicken
            LE_BYTES.putShort(bc, bci, todo);
        } else {
            // set bit
            int offset = (todo >> 8) & 0x7f;
            int bit = todo & 0xff;
            if (targetType == FRAME_TYPE_OBJECT) {
                bc[bci + offset] &= ~bit;
            } else {
                bc[bci + offset] |= bit;
            }
        }
    }

    protected static Object expectObject(VirtualFrame frame, int slot) {
        if (frame.isObject(slot)) {
            return frame.getObject(slot);
        } else {
            // this should only happen in edge cases, when we have specialized to a generic case on
            // one thread, but other threads have already executed the child with primitive return
            // type
            return frame.getValue(slot);
        }
    }

    protected static byte expectByte(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.BYTE_TAG:
                return frame.getByte(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Byte) {
                    return (byte) value;
                }
                break;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(frame.getValue(slot));
    }

    protected static boolean expectBoolean(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.BOOLEAN_TAG:
                return frame.getBoolean(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Boolean) {
                    return (boolean) value;
                }
                break;
        }

        throw new UnexpectedResultException(frame.getValue(slot));
    }

    protected static int expectInt(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.INT_TAG:
                return frame.getInt(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Integer) {
                    return (int) value;
                }
                break;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(frame.getValue(slot));
    }

    protected static float expectFloat(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.FLOAT_TAG:
                return frame.getFloat(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Float) {
                    return (float) value;
                }
                break;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(frame.getValue(slot));
    }

    protected static long expectLong(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.LONG_TAG:
                return frame.getLong(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Long) {
                    return (long) value;
                }
                break;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(frame.getValue(slot));
    }

    protected static double expectDouble(VirtualFrame frame, int slot) throws UnexpectedResultException {
        switch (frame.getTag(slot)) {
            case FrameWithoutBoxing.DOUBLE_TAG:
                return frame.getDouble(slot);
            case FrameWithoutBoxing.OBJECT_TAG:
                Object value = frame.getObject(slot);
                if (value instanceof Double) {
                    return (double) value;
                }
                break;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(frame.getValue(slot));
    }
}
