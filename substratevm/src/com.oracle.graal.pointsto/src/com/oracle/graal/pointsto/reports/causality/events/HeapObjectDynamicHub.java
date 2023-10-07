package com.oracle.graal.pointsto.reports.causality.events;

public final class HeapObjectDynamicHub extends CausalityEvent {
    public final Class<?> forClass;


    HeapObjectDynamicHub(Class<?> forClass) {
        this.forClass = forClass;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return forClass.getTypeName() + " [DynamicHub-Object in Heap]";
    }
}
