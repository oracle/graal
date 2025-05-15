package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.ReflectionDynamicAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

public class InternalReflectionDynamicAccess implements ReflectionDynamicAccess {

    private static RuntimeReflectionSupport rrsInstance;

    InternalReflectionDynamicAccess() {
        rrsInstance = ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        rrsInstance.register(condition, classes);
        for (Class<?> clazz : classes) {
            rrsInstance.registerAllClassesQuery(condition, clazz);
            rrsInstance.registerAllDeclaredClassesQuery(condition, clazz);
            rrsInstance.registerAllDeclaredMethodsQuery(condition, true, clazz);
            rrsInstance.registerAllMethodsQuery(condition, true, clazz);
            rrsInstance.registerAllDeclaredConstructorsQuery(condition, true, clazz);
            rrsInstance.registerAllConstructorsQuery(condition, true, clazz);
            rrsInstance.registerAllFieldsQuery(condition, true, clazz);
            rrsInstance.registerAllDeclaredFieldsQuery(condition, true, clazz);
            rrsInstance.registerAllNestMembersQuery(condition, clazz);
            rrsInstance.registerAllPermittedSubclassesQuery(condition, clazz);
            rrsInstance.registerAllRecordComponentsQuery(condition, clazz);
            rrsInstance.registerAllSignersQuery(condition, clazz);
        }
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        rrsInstance.registerClassLookup(condition, className);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(methods)
                        .map(Executable::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);

        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(fields)
                        .map(Field::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);
        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        register(condition, classes);
        RuntimeSerializationSupport.singleton().register(condition, classes);
    }
}
