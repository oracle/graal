package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisType;

public final class TypeInstantiated extends CausalityEvent {
    public final AnalysisType type;

    TypeInstantiated(AnalysisType type) {
        this.type = type;
    }

    @Override
    public boolean unused() {
        return !type.isInstantiated();
    }

    @Override
    public String toString() {
        return type.toJavaName() + " [Instantiated]";
    }
}
