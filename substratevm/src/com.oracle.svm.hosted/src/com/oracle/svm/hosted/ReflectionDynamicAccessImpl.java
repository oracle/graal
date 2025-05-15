package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.ReflectionDynamicAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;

/**
 * Instance of this class is used to register classes, methods, and fields for reflection,
 * serialization and JNI access at runtime. It can only be created at
 * {@link Feature#afterRegistration} via {@link Feature.AfterRegistrationAccess}.
 */
public final class ReflectionDynamicAccessImpl implements ReflectionDynamicAccess {

    private static InternalReflectionDynamicAccess rdaInstance;

    public ReflectionDynamicAccessImpl() {
        rdaInstance = new InternalReflectionDynamicAccess();
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError(Arrays.toString(classes));
        rdaInstance.register(condition, classes);
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        DynamicAccessSupport.printUserError(className + "for lookup");
        rdaInstance.registerClassLookup(condition, className);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        DynamicAccessSupport.printUserError(Arrays.toString(methods));
        rdaInstance.register(condition, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        DynamicAccessSupport.printUserError(Arrays.toString(fields));
        rdaInstance.register(condition, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError(Arrays.toString(classes));
        rdaInstance.registerForSerialization(condition, classes);
    }
}
