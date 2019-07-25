package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.CoverageTracker;

public class RootCoverageNode extends CoverageNode {

    private final SourceSection rootSection;

    public RootCoverageNode(CoverageTracker tracker, SourceSection rootSection) {
        super(tracker);
        this.rootSection = rootSection;
    }

    @Override
    protected void notifyTracker() {
        tracker.addCoveredRoot(rootSection);
    }
}
