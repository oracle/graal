package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class OperationNode extends Node {

    private final OperationNodes nodes;
    @CompilationFinal private SourceInfo sourceInfo;

    @Child private OperationBytecodeNode bcNode;

    OperationNode(OperationNodes nodes, SourceInfo sourceInfo, OperationBytecodeNode bcNode) {
        this.nodes = nodes;
        this.sourceInfo = sourceInfo;
        this.bcNode = bcNode;
    }

    public FrameDescriptor createFrameDescriptor() {
        return bcNode.createFrameDescriptor();
    }

    public OperationsRootNode createRootNode(TruffleLanguage<?> language, String name) {
        return new OperationsRootNode(language, this, name, false);
    }

    public OperationsRootNode createInternalRootNode(TruffleLanguage<?> language, String name) {
        return new OperationsRootNode(language, this, name, true);
    }

    public Object execute(VirtualFrame frame) {
        return bcNode.execute(frame);
    }

    // ------------------------------------ internal accessors ------------------------------------

    boolean isBytecodeInstrumented() {
        return bcNode.isInstrumented();
    }

    void changeBytecode(OperationBytecodeNode node) {
        CompilerAsserts.neverPartOfCompilation();
        bcNode = insert(node);
    }

    boolean hasSourceInfo() {
        return sourceInfo != null;
    }

    void setSourceInfo(SourceInfo sourceInfo) {
        CompilerAsserts.neverPartOfCompilation();
        assert !hasSourceInfo() : "already have source info";
        this.sourceInfo = sourceInfo;
    }

    // ------------------------------------ sources ------------------------------------

    @Override
    public SourceSection getSourceSection() {
        nodes.ensureSources();

        if (sourceInfo == null) {
            return null;
        }

        for (int i = 0; i < sourceInfo.length(); i++) {
            if (sourceInfo.sourceStart[i] >= 0) {
                // return the first defined source section - that one should encompass the entire
                // function
                return nodes.sources[sourceInfo.sourceIndex[i]].createSection(sourceInfo.sourceStart[i], sourceInfo.sourceLength[i]);
            }
        }

        return null;
    }

    @ExplodeLoop
    protected final SourceSection getSourceSectionAtBci(int bci) {
        nodes.ensureSources();

        if (sourceInfo == null) {
            return null;
        }

        int i;
        // find the index of the first greater BCI
        for (i = 0; i < sourceInfo.length(); i++) {
            if (sourceInfo.bci[i] > bci) {
                break;
            }
        }

        if (i == 0) {
            return null;
        } else {
            int sourceIndex = sourceInfo.sourceIndex[i - 1];
            if (sourceIndex < 0) {
                return null;
            }

            int sourceStart = sourceInfo.sourceStart[i - 1];
            int sourceLength = sourceInfo.sourceLength[i - 1];
            if (sourceStart < 0) {
                return null;
            }
            return nodes.sources[sourceIndex].createSection(sourceStart, sourceLength);
        }
    }

    static class SourceInfo {
        @CompilationFinal(dimensions = 1) final int[] bci;
        @CompilationFinal(dimensions = 1) final int[] sourceIndex;
        @CompilationFinal(dimensions = 1) final int[] sourceStart;
        @CompilationFinal(dimensions = 1) final int[] sourceLength;

        SourceInfo(int[] bci, int[] sourceIndex, int[] start, int[] length) {
            assert bci.length == sourceIndex.length;
            assert bci.length == start.length;
            assert bci.length == length.length;

            this.bci = bci;
            this.sourceIndex = sourceIndex;
            this.sourceStart = start;
            this.sourceLength = length;
        }

        int length() {
            return bci.length;
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
}
