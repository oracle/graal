package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

public final class UnknownHeapObject extends CausalityEvent {
    public final Class<?> heapObjectType;

    UnknownHeapObject(Class<?> heapObjectType) {
        this.heapObjectType = heapObjectType;
    }

    @Override
    public boolean root() {
        return true;
    }

    @Override
    public String toString() {
        return heapObjectType.getTypeName() + " [Unknown Heap Object]";
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString(AnalysisMetaAccess metaAccess) {
        return metaAccess.lookupJavaType(heapObjectType).toJavaName() + " [Unknown Heap Object]";
    }
}
