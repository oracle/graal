package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class MethodSnippet extends CausalityEvent {
    public final AnalysisMethod method;

    MethodSnippet(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R") + " [Snippet]";
    }
}
