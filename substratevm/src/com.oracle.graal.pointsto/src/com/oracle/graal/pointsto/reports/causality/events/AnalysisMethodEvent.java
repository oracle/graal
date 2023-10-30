package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.causality.ReachabilityExport;

public abstract class AnalysisMethodEvent extends CausalityEvent {
    public final AnalysisMethod method;

    AnalysisMethodEvent(AnalysisMethod method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return method.format("%H.%n(%P):%R") + this.typeDescriptor().suffix;
    }

    @Override
    public ReachabilityExport.HierarchyNode getParent(ReachabilityExport export, AnalysisMetaAccess metaAccess) {
        return export.computeIfAbsent(method);
    }
}
