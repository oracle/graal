package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.ReflectionDynamicAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;

/**
 * Instance of this class is used to register classes, methods, and fields for reflection,
 * serialization at runtime. It can only be created at {@link Feature#afterRegistration} via
 * {@link Feature.AfterRegistrationAccess}.
 */
public final class ReflectionDynamicAccessImpl implements ReflectionDynamicAccess {

    private static InternalReflectionDynamicAccess rdaInstance;

    public ReflectionDynamicAccessImpl() {
        rdaInstance = new InternalReflectionDynamicAccess();
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for reflection: " + Arrays.toString(classes));
        rdaInstance.register(condition, classes);
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        DynamicAccessSupport.printUserError("following classes for lookup: " + className);
        rdaInstance.registerClassLookup(condition, className);
    }

    @Override
    public void registerUnsafeAllocated(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for reflection and unsafe allocation: " + Arrays.toString(classes));
        rdaInstance.registerUnsafeAllocated(condition, classes);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        DynamicAccessSupport.printUserError("following methods for reflection: " + Arrays.toString(methods));
        rdaInstance.register(condition, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        DynamicAccessSupport.printUserError("following fields for reflection: " + Arrays.toString(fields));
        rdaInstance.register(condition, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for serialization: " + Arrays.toString(classes));
        rdaInstance.registerForSerialization(condition, classes);
    }

    @Override
    public Class<?> registerProxy(RegistrationCondition condition, Class<?>... interfaces) {
        DynamicAccessSupport.printUserError("following interfaces that define a dynamic proxy class: " + Arrays.toString(interfaces));
        return rdaInstance.registerProxy(condition, interfaces);
    }
}
