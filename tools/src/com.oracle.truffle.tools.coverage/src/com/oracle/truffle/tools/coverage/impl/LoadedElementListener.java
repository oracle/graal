package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.tools.coverage.CoverageTracker;

public final class LoadedElementListener implements LoadSourceSectionListener {

    private final CoverageTracker tracker;

    public LoadedElementListener(CoverageTracker coverageTracker) {
        tracker = coverageTracker;
    }

    @Override
    public void onLoad(LoadSourceSectionEvent event) {
        tracker.addLoaded(event.getSourceSection());
    }
}
