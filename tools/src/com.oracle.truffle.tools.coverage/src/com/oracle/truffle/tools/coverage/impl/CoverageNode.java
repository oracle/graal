package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.tools.coverage.CoverageTracker;

public abstract class CoverageNode extends ExecutionEventNode {
    protected final CoverageTracker tracker;
    @CompilerDirectives.CompilationFinal private boolean covered;

    public CoverageNode(CoverageTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
        if (!covered) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            covered = true;
            notifyTracker();
        }
    }

    protected abstract void notifyTracker();
}
