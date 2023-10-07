package com.oracle.graal.pointsto.reports.causality.events;

import java.lang.reflect.AnnotatedElement;

public final class ReflectionRegistration extends ReflectionObjectEvent {
    ReflectionRegistration(AnnotatedElement element) {
        super(element);
    }

    @Override
    protected String getSuffix() {
        return " [Reflection Registration]";
    }
}
