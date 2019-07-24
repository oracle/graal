package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tools.coverage.CoverageTracker;

public class RootCoverageNode extends CoverageNode {

    private final RootNode rootNode;

    public RootCoverageNode(CoverageTracker tracker, RootNode rootNode) {
        super(tracker);
        this.rootNode = rootNode;
    }


    @Override
    protected void notifyTracker() {
        tracker.addCovered(rootNode);
    }
}
