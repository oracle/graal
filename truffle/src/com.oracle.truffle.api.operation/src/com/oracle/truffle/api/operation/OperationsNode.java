package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.tracing.NodeTrace;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class OperationsNode extends Node implements InstrumentableNode {

    protected static final int BCI_SLOT = 0;
    protected static final int VALUES_OFFSET = 1;

    private static FrameDescriptor createFrameDescriptor(int maxStack, int numLocals) {
        FrameDescriptor.Builder b = FrameDescriptor.newBuilder(VALUES_OFFSET + maxStack + numLocals);

        int bciSlot = b.addSlot(FrameSlotKind.Int, null, null);
        assert bciSlot == BCI_SLOT;

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
        return continueAt(frame, null);
    }

    public abstract Object continueAt(VirtualFrame frame, OperationLabel index);

    public abstract String dump();

    public NodeTrace getNodeTrace() {
        throw new UnsupportedOperationException("Operations not built with tracing");
    }

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
}
