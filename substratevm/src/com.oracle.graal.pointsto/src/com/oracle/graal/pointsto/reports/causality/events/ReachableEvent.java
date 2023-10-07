package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisElement;

abstract class ReachableEvent<T extends AnalysisElement> extends CausalityEvent {
    public final T element;

    ReachableEvent(T element) {
        this.element = element;
    }
}
