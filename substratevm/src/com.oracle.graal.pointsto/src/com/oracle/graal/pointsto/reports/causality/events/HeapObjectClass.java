package com.oracle.graal.pointsto.reports.causality.events;

public final class HeapObjectClass extends CausalityEvent {
    public final Class<?> clazz;

    HeapObjectClass(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return clazz.getTypeName() + " [Class-Object in Heap]";
    }
}
