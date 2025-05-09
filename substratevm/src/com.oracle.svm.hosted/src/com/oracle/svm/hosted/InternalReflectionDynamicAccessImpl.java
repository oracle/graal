package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.ReflectionDynamicAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

public class InternalReflectionDynamicAccessImpl implements ReflectionDynamicAccess {

    private static RuntimeReflectionSupport rrsInstance;

    InternalReflectionDynamicAccessImpl() {
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
        rrsInstance.register(condition, false, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        rrsInstance.register(condition, false, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        register(condition, classes);
        RuntimeSerializationSupport.singleton().register(condition, classes);
    }

    @Override
    public void registerForJNIAccess(RegistrationCondition condition, Class<?>... classes) {
        register(condition, classes);
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(condition, classes);
    }
}
