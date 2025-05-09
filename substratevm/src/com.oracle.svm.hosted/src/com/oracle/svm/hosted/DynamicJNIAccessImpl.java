package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.DynamicJNIAccess;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;

public class DynamicJNIAccessImpl implements DynamicJNIAccess {

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for JNI access: " + Arrays.toString(classes));
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(condition, classes);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        DynamicAccessSupport.printUserError("following methods for JNI access: " + Arrays.toString(methods));
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(condition, false, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        DynamicAccessSupport.printUserError("following fields for JNI access: " + Arrays.toString(fields));
        ImageSingletons.lookup(RuntimeJNIAccessSupport.class).register(condition, false, fields);
    }
}
