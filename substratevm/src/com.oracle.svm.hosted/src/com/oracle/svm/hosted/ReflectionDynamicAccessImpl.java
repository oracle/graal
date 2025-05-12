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
    private static InternalReflectionDynamicAccessImpl rdaInstance;

    public ReflectionDynamicAccessImpl() {
        rdaInstance = new InternalReflectionDynamicAccessImpl();
        afterRegistrationFinished = false;
    }

    public static void setAfterRegistrationFinished() {
        afterRegistrationFinished = true;
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        Arrays.toString(classes));
        rdaInstance.register(condition, classes);
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        className);
        rdaInstance.registerClassLookup(condition, className);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        Arrays.toString(methods));
        rdaInstance.register(condition, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        Arrays.toString(fields));
        rdaInstance.register(condition, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        Arrays.toString(classes));
        rdaInstance.registerForSerialization(condition, classes);
    }

    @Override
    public void registerForJNIAccess(RegistrationCondition condition, Class<?>... classes) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        Arrays.toString(classes));
        rdaInstance.registerForJNIAccess(condition, classes);
    }
}
