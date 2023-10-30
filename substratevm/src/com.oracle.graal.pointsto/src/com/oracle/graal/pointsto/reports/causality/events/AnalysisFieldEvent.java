package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.reports.causality.ReachabilityExport;

public abstract class AnalysisFieldEvent extends CausalityEvent {
    public final AnalysisField field;

    AnalysisFieldEvent(AnalysisField field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return field.format("%H.%n") + this.typeDescriptor().suffix;
    }

    @Override
    public ReachabilityExport.HierarchyNode getParent(ReachabilityExport export, AnalysisMetaAccess metaAccess) {
        return export.computeIfAbsent(field);
    }
}
