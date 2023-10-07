package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

abstract class ReflectionObjectEvent extends CausalityEvent {
    public final AnnotatedElement element;

    ReflectionObjectEvent(AnnotatedElement element) {
        this.element = element;
    }

    protected abstract String getSuffix();

    @Override
    public String toString() {
        return reflectionObjectToString(element) + getSuffix();
    }

    @Override
    public String toString(AnalysisMetaAccess metaAccess) {
        return reflectionObjectToGraalLikeString(metaAccess, element) + getSuffix();
    }

    private static String reflectionObjectToString(AnnotatedElement reflectionObject) {
        if(reflectionObject instanceof Class<?> clazz) {
            return clazz.getTypeName();
        } else if(reflectionObject instanceof Constructor<?> c) {
            return c.getDeclaringClass().getTypeName() + ".<init>(" + Arrays.stream(c.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else if(reflectionObject instanceof Method m) {
            return m.getDeclaringClass().getTypeName() + '.' + m.getName() + '(' + Arrays.stream(m.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else {
            Field f = ((Field) reflectionObject);
            return f.getDeclaringClass().getTypeName() + '.' + f.getName();
        }
    }

    private static String reflectionObjectToGraalLikeString(AnalysisMetaAccess metaAccess, AnnotatedElement reflectionObject) {
        if(reflectionObject instanceof Class<?> c) {
            return metaAccess.lookupJavaType(c).toJavaName();
        } else if(reflectionObject instanceof Executable e) {
            return metaAccess.lookupJavaMethod(e).format("%H.%n(%P):%R");
        } else {
            return metaAccess.lookupJavaField((Field) reflectionObject).format("%H.%n");
        }
    }
}
