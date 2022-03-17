package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.tracing.NodeTrace;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class OperationsNode extends RootNode {

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

    protected OperationsNode(TruffleLanguage<?> language, Object parseContext, int[][] sourceInfo, Source[] sources, int buildOrder, int maxStack, int maxLocals) {
        super(language, createFrameDescriptor(maxStack, maxLocals));
        // System.out.printf(" new operations node %d %d\n", maxStack, maxLocals);
        this.buildOrder = buildOrder;
        this.parseContext = parseContext;
        this.sourceInfo = sourceInfo;
        this.sources = sources;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
    }

    public abstract Object continueAt(VirtualFrame frame, OperationLabel index);

// public abstract OperationsNode copyUninitialized();

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
}
