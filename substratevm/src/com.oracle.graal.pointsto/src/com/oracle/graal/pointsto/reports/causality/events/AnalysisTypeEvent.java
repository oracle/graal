package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.ReachabilityExport;

public abstract class AnalysisTypeEvent extends CausalityEvent {
    public final AnalysisType type;

    AnalysisTypeEvent(AnalysisType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toJavaName() + this.typeDescriptor().suffix;
    }

    @Override
    public ReachabilityExport.HierarchyNode getParent(ReachabilityExport export, AnalysisMetaAccess metaAccess) {
        return export.computeIfAbsent(type);
    }
}
