package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

public final class BuildTimeClassInitialization extends CausalityEvent {
    public final Class<?> clazz;

    BuildTimeClassInitialization(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return clazz.getTypeName() + ".<clinit>() [Build-Time]";
    }

    private String getTypeName(AnalysisMetaAccess metaAccess) {
        return metaAccess.getWrapped().lookupJavaType(clazz).toJavaName();
    }

    @Override
    public String toString(AnalysisMetaAccess metaAccess) {
        return getTypeName(metaAccess) + ".<clinit>() [Build-Time]";
    }
}
