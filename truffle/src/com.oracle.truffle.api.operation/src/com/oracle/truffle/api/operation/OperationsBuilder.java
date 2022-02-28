package com.oracle.truffle.api.operation;

import java.io.OutputStream;

public abstract class OperationsBuilder {

    private OperationsStatistics statistics;

    protected final void onBuild(OperationsNode executable) {
        if (statistics == null) {
            return;
        }
        // executable.createPointer();
    }

    public void setCollectStatistics(boolean statistics) {
        this.statistics = new OperationsStatistics();
    }

    private static final class OperationsStatistics {
    }

    public void dumpStatistics(OutputStream stream) {
    }

}
