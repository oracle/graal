package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class MethodInlined extends CausalityEvent {
    public final AnalysisMethod method;

    MethodInlined(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R") + " [Inlined]";
    }
}
