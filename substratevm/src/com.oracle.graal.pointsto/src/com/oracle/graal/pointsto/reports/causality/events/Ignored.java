package com.oracle.graal.pointsto.reports.causality.events;

// Can be used in Rerooting to indicate that registrations simply should be ignored
public final class Ignored extends CausalityEvent {
    Ignored() {
    }

    @Override
    public boolean unused() {
        return true;
    }

    @Override
    public String toString() {
        throw new RuntimeException("[Ignored dummy node that never happens]");
    }
}
