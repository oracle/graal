package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class MethodReachable extends ReachableEvent<AnalysisMethod> {
    MethodReachable(AnalysisMethod method) {
        super(method);
    }

    @Override
    public String toString() {
        return element.format("%H.%n(%P):%R");
    }

    @Override
    public boolean unused() {
        return !element.isReachable();
    }
}
