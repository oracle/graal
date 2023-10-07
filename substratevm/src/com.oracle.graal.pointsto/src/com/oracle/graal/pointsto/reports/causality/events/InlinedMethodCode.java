package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class InlinedMethodCode extends CausalityEvent {
    public final AnalysisMethod[] context;

    InlinedMethodCode(AnalysisMethod[] context) {
        this.context = context;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(context[0].format("%H.%n(%P):%R"));

        for (int i = 1; i < context.length; i++) {
            sb.append(';');
            AnalysisMethod m = context[i];
            sb.append(m.format("%H.%n(%P):%R"));
        }
        sb.append(" [Impl]");

        return sb.toString();
    }
}
