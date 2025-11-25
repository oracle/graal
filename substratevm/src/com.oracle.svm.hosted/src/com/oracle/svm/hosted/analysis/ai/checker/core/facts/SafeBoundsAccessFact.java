package com.oracle.svm.hosted.analysis.ai.checker.core.facts;

import jdk.graal.compiler.graph.Node;

import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;

public final class SafeBoundsAccessFact implements Fact {
    private final Node access;
    private final boolean inBounds;
    private final IntInterval indexRange;
    private final int arrayLength;

    public SafeBoundsAccessFact(Node arrayAccess, boolean inBounds, IntInterval indexRange, int arrayLength) {
        this.access = arrayAccess;
        this.inBounds = inBounds;
        this.indexRange = indexRange == null ? null : indexRange.copyOf();
        this.arrayLength = arrayLength;
    }

    public boolean isInBounds() {
        return inBounds;
    }

    public IntInterval getIndexRange() {
        return indexRange;
    }

    public int getArrayLength() {
        return arrayLength;
    }

    public Node getArrayAccess() {
        return access;
    }

    @Override
    public FactKind kind() {
        return FactKind.BOUNDS_SAFETY;
    }

    @Override
    public String describe() {
        return access + " indexRange=" + indexRange + " length=" + arrayLength + " safe=" + inBounds;
    }

    @Override
    public Node node() {
        return access;
    }
}

