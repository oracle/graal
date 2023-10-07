package com.oracle.graal.pointsto.reports.causality.events;

import java.lang.reflect.AnnotatedElement;

public final class JNIRegistration extends ReflectionObjectEvent {
    JNIRegistration(AnnotatedElement element) {
        super(element);
    }

    @Override
    protected String getSuffix() {
        return " [JNI Registration]";
    }
}
