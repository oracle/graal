package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class RootMethodRegistration extends CausalityEvent {
    public final AnalysisMethod method;

    RootMethodRegistration(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R") + " [Root Registration]";
    }
}
