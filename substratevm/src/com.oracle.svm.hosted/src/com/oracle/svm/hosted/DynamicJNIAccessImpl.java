package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.DynamicJNIAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;

public class DynamicJNIAccessImpl implements DynamicJNIAccess {

    private InternalReflectionDynamicAccess rdaInstance;
    private RuntimeJNIAccessSupport rjaInstance;

    DynamicJNIAccessImpl() {
        rdaInstance = new InternalReflectionDynamicAccess();
        rjaInstance = ImageSingletons.lookup(RuntimeJNIAccessSupport.class);
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError(Arrays.toString(classes));
        rdaInstance.register(condition, classes);
        rjaInstance.register(condition, classes);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        DynamicAccessSupport.printUserError(Arrays.toString(methods));
        rdaInstance.register(condition, methods);
        rjaInstance.register(condition, false, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        DynamicAccessSupport.printUserError(Arrays.toString(fields));
        rdaInstance.register(condition, fields);
        rjaInstance.register(condition, false, fields);
    }
}
