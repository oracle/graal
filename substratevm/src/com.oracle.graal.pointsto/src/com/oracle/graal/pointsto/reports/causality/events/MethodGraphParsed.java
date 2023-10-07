package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class MethodGraphParsed extends CausalityEvent {
    public final AnalysisMethod method;

    MethodGraphParsed(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R [Method Graph Parsed]");
    }

    @Override
    public boolean essential() {
        return false;
    }
}
