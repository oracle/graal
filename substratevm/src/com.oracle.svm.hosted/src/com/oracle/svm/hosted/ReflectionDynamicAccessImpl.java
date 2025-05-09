package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.ReflectionDynamicAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;

import com.oracle.svm.core.util.UserError;

/**
 * Instance of this class is used to register classes, methods, and fields for reflection,
 * serialization and JNI access at runtime. It can only be created at
 * {@link Feature#afterRegistration} via {@link Feature.AfterRegistrationAccess}.
 */
public final class ReflectionDynamicAccessImpl implements ReflectionDynamicAccess {

    private static boolean afterRegistrationFinished;
    private static InternalReflectionDynamicAccess rdaInstance;

    public ReflectionDynamicAccessImpl() {
        rdaInstance = new InternalReflectionDynamicAccess();
        afterRegistrationFinished = false;
    }

    public static void setAfterRegistrationFinished() {
        afterRegistrationFinished = true;
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        printUserError(Arrays.toString(classes));
        rdaInstance.register(condition, classes);
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        printUserError(className + "for lookup");
        rdaInstance.registerClassLookup(condition, className);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        printUserError(Arrays.toString(methods));
        rdaInstance.register(condition, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        printUserError(Arrays.toString(fields));
        rdaInstance.register(condition, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        printUserError(Arrays.toString(classes));
        rdaInstance.registerForSerialization(condition, classes);
    }

    @Override
    public void registerForJNIAccess(RegistrationCondition condition, Class<?>... classes) {
        printUserError(Arrays.toString(classes));
        rdaInstance.registerForJNIAccess(condition, classes);
    }

    private void printUserError(String registrationEntry) {
        UserError.guarantee(!afterRegistrationFinished, "Registration for runtime access after Feature#afterRegistration is not allowed. You tried to register: %s", registrationEntry);
    }
}
