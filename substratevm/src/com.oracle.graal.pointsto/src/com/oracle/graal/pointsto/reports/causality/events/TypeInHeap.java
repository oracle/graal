package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisType;

public final class TypeInHeap extends CausalityEvent {
    public final AnalysisType type;

    TypeInHeap(AnalysisType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toJavaName() + " [Type In Heap]";
    }
}
