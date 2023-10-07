package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

public abstract class CausalityEvent {
    /**
     * Indicates whether this Event has never occured and thus can be removed from the CausalityGraph
     */
    public boolean unused() {
        return false;
    }

    /**
     * Indicates whether this Event is always reachable.
     * Such events are still useful for providing more details
     */
    public boolean root() {
        return false;
    }

    /**
     * Used to distinguish nodes that are part of the CausalityGraph-API vs. implementation detail nodes.
     * Non-essential events may be removed from the Graph, if the change doesn't affect the reachability of essential events.
     */
    public boolean essential() {
        return true;
    }

    public String toString(AnalysisMetaAccess metaAccess) {
        return this.toString();
    }
}
