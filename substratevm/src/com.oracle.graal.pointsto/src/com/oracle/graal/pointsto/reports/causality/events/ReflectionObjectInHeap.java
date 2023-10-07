package com.oracle.graal.pointsto.reports.causality.events;

import java.lang.reflect.AnnotatedElement;

public final class ReflectionObjectInHeap extends ReflectionObjectEvent {
    ReflectionObjectInHeap(AnnotatedElement element) {
        super(element);
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    protected String getSuffix() {
        return " [Reflection Object In Heap]";
    }
}
