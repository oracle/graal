package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.CoverageTracker;

public class StatementCoverageNode extends CoverageNode {
    private final SourceSection sourceSection;

    public StatementCoverageNode(CoverageTracker tracker, SourceSection sourceSection) {
        super(tracker);
        this.sourceSection = sourceSection;
    }

    @Override
    protected void notifyTracker() {
        tracker.addCovered(sourceSection);
    }
}
