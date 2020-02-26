package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.lang.reflect.Constructor;

@TargetClass(classNameProvider = Package_jdk_internal_reflect.class, className = "ReflectionFactory")
public final class Target_jdk_internal_reflect_ReflectionFactory {

    @Substitute
    private Constructor<?> generateConstructor(Class<?> cl, Constructor<?> constructorToCall) {
        return null;
    }

}
