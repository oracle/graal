package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class VirtualMethodInvoked extends CausalityEvent {
    public final AnalysisMethod method;

    VirtualMethodInvoked(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R") + " [Virtual Invoke]";
    }
}
