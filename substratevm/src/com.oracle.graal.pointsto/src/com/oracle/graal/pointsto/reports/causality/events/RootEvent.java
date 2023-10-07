package com.oracle.graal.pointsto.reports.causality.events;

public final class RootEvent extends CausalityEvent {
    public final String label;

    RootEvent(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean root() {
        return true;
    }
}
