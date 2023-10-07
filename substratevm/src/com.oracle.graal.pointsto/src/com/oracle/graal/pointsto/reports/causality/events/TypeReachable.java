package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisType;

public final class TypeReachable extends ReachableEvent<AnalysisType> {
    TypeReachable(AnalysisType type) {
        super(type);
    }

    @Override
    public String toString() {
        return element.toJavaName();
    }

    @Override
    public boolean unused() {
        return !element.isReachable();
    }
}
