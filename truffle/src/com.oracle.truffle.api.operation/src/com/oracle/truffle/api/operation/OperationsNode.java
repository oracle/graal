package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class OperationsNode extends Node implements InstrumentableNode, BytecodeOSRNode {

    // The order of these must be the same as FrameKind in processor
    public static final int FRAME_TYPE_OBJECT = 0;
    public static final int FRAME_TYPE_BYTE = 1;
    public static final int FRAME_TYPE_BOOLEAN = 2;
    public static final int FRAME_TYPE_INT = 3;
    public static final int FRAME_TYPE_FLOAT = 4;
    public static final int FRAME_TYPE_LONG = 5;
    public static final int FRAME_TYPE_DOUBLE = 6;

    protected static final int VALUES_OFFSET = 0;

    private static final OperationsBytesSupport LE_BYTES = OperationsBytesSupport.littleEndian();

    private static FrameDescriptor createFrameDescriptor(int maxStack, int numLocals) {
        FrameDescriptor.Builder b = FrameDescriptor.newBuilder(VALUES_OFFSET + maxStack + numLocals);

        b.addSlots(numLocals + maxStack, FrameSlotKind.Illegal);

        return b.build();
    }

    protected final int maxStack;
    protected final int maxLocals;

    protected final Object parseContext;
    protected int[][] sourceInfo;
    protected Source[] sources;
    protected final int buildOrder;

    protected OperationsNode(
                    Object parseContext,
                    int[][] sourceInfo,
                    Source[] sources,
                    int buildOrder,
                    int maxStack,
                    int maxLocals) {
        this.buildOrder = buildOrder;
        this.parseContext = parseContext;
        this.sourceInfo = sourceInfo;
        this.sources = sources;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
    }

    public FrameDescriptor createFrameDescriptor() {
        return createFrameDescriptor(maxStack, maxLocals);
    }

    public OperationsRootNode createRootNode(TruffleLanguage<?> language, String name) {
        return new OperationsRootNode(language, this, name, false);
    }

    public OperationsRootNode createInternalRootNode(TruffleLanguage<?> language, String name) {
        return new OperationsRootNode(language, this, name, true);
    }

    public final Object execute(VirtualFrame frame) {
        return continueAt(frame, 0, maxLocals + VALUES_OFFSET);
    }

    protected abstract Object continueAt(VirtualFrame frame, int index, int startSp);

    public abstract String dump();

    protected final void copyReparsedInfo(OperationsNode other) {
        this.sourceInfo = other.sourceInfo;
        this.sources = other.sources;
    }

    protected final SourceSection getSourceSectionImpl() {
        if (sourceInfo[0].length == 0) {
            return null;
        }

        for (int i = 0; i < sourceInfo.length; i++) {
            if (sourceInfo[1][i] >= 0) {
                return sources[sourceInfo[3][i]].createSection(sourceInfo[1][i], sourceInfo[2][i]);
            }
        }

        return null;
    }

    protected abstract SourceSection getSourceSectionAtBci(int bci);

    protected final SourceSection getSourceSectionAtBciImpl(int bci) {
        if (sourceInfo[0].length == 0) {
            return null;
        }

        int i;
        for (i = 0; i < sourceInfo[0].length; i++) {
            if (sourceInfo[0][i] > bci) {
                break;
            }
        }

        if (i == 0) {
            return null;
        } else {
            return sources[sourceInfo[3][i - 1]].createSection(sourceInfo[1][i - 1], sourceInfo[2][i - 1]);
        }
    }

    public final Node createLocationNode(final int bci) {
        return new Node() {
            @Override
            public SourceSection getSourceSection() {
                return getSourceSectionAtBci(bci);
            }

            @Override
            public SourceSection getEncapsulatingSourceSection() {
                return getSourceSectionAtBci(bci);
            }
        };
    }

    @Override
    public boolean isInstrumentable() {
        return false;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        // TODO Auto-generated method stub
        return null;
    }

    protected static <T> T interlog(T arg, String reason) {
        if (CompilerDirectives.inInterpreter()) {
            System.out.printf("  >> %s %s%n", reason, arg);
        }

        return arg;
    }

    // OSR

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        // we'll need a container object if we ever need to pass more than just the sp
        // TODO needs workaround for arguments getting null on OSR
        return continueAt(osrFrame, target, (int) interpreterState);
    }

    @CompilationFinal private Object osrMetadata;

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

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

    // --------------- boxing elim -----------------------

    protected static Object expectObject(VirtualFrame frame, int slot) {
        return frame.getObject(slot);
    }

    protected static byte expectByte(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isByte(slot)) {
            return frame.getByte(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Byte) {
            return (byte) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }

    protected static boolean expectBoolean(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isBoolean(slot)) {
            return frame.getBoolean(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Boolean) {
            return (boolean) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }

    protected static int expectInt(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isInt(slot)) {
            return frame.getInt(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Integer) {
            return (int) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }

    protected static float expectFloat(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isFloat(slot)) {
            return frame.getFloat(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Float) {
            return (float) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }

    protected static long expectLong(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isLong(slot)) {
            return frame.getLong(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Long) {
            return (long) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }

    protected static double expectDouble(VirtualFrame frame, int slot) throws UnexpectedResultException {
        Object value;
        if (frame.isDouble(slot)) {
            return frame.getDouble(slot);
        } else if (frame.isObject(slot) && (value = frame.getObject(slot)) instanceof Double) {
            return (double) value;
        } else {
            throw new UnexpectedResultException(frame.getValue(slot));
        }
    }
}
