package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.tracing.NodeTrace;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class OperationsNode extends RootNode implements InstrumentableNode {

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
    protected final String nodeName;

    protected final Object parseContext;
    protected int[][] sourceInfo;
    protected Source[] sources;
    protected final int buildOrder;
    protected final boolean isInternal;

    protected OperationsNode(
                    TruffleLanguage<?> language,
                    Object parseContext,
                    String nodeName,
                    boolean isInternal,
                    int[][] sourceInfo,
                    Source[] sources,
                    int buildOrder,
                    int maxStack,
                    int maxLocals) {
        super(language, createFrameDescriptor(maxStack, maxLocals));
        this.buildOrder = buildOrder;
        this.parseContext = parseContext;
        this.nodeName = nodeName;
        this.isInternal = isInternal;
        this.sourceInfo = sourceInfo;
        this.sources = sources;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
    }

    public abstract Object continueAt(VirtualFrame frame, OperationLabel index);

    public abstract String dump();

    @Override
    public String getName() {
        return nodeName;
    }

    @Override
    public boolean isInternal() {
        return isInternal;
    }

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

    @Override
    public boolean isCaptureFramesForTrace() {
        return true;
    }

    @Override
    protected Object translateStackTraceElement(TruffleStackTraceElement element) {
        int bci = element.getFrame().getInt(BCI_SLOT);
        return new OperationsStackTraceElement(element.getTarget().getRootNode(), getSourceSectionAtBci(bci));
    }

    public final Node createFakeLocationNode(final int bci) {
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
        return true;
    }
}
