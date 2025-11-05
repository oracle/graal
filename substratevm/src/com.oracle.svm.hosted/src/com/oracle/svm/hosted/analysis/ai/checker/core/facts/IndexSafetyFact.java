package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;

public final class IndexSafetyFact implements Fact {
    private final Node access;
    private final boolean inBounds;
    private final IntInterval indexRange;
    private final int arrayLength;

    public IndexSafetyFact(Node arrayAccess, boolean inBounds, IntInterval indexRange, int arrayLength) {
        this.access = arrayAccess;
        this.inBounds = inBounds;
        this.indexRange = indexRange == null ? null : indexRange.copyOf();
        this.arrayLength = arrayLength;
    }

    public boolean isInBounds() { return inBounds; }
    public IntInterval getIndexRange() { return indexRange; }
    public int getArrayLength() { return arrayLength; }
    public Node getArrayAccess() { return access; }

    @Override
    public String kind() { return "IndexSafety"; }

    @Override
    public String describe() {
        String kind = (access instanceof LoadIndexedNode) ? "LoadIndexed" : (access instanceof StoreIndexedNode ? "StoreIndexed" : access.getClass().getSimpleName());
        return kind + " indexRange=" + indexRange + " length=" + arrayLength + " safe=" + inBounds;
    }

    @Override
    public Node node() { return access; }
}

