package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.reports.causality.ReachabilityExport;

public abstract class ClassEvent extends CausalityEvent {
    public final Class<?> clazz;

    ClassEvent(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return clazz.getTypeName() + typeDescriptor().suffix;
    }

    private String getTypeName(AnalysisMetaAccess metaAccess) {
        return metaAccess.getWrapped().lookupJavaType(clazz).toJavaName();
    }

    @Override
    public String toString(AnalysisMetaAccess metaAccess) {
        return getTypeName(metaAccess) + typeDescriptor().suffix;
    }

    public ReachabilityExport.HierarchyNode getParent(ReachabilityExport export, AnalysisMetaAccess metaAccess) {
        return export.computeIfAbsent(metaAccess, clazz);
    }
}
