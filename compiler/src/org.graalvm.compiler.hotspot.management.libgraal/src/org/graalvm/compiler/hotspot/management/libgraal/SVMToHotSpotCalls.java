/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.management.libgraal;

import static org.graalvm.word.WordFactory.nullPointer;
import static org.graalvm.libgraal.jni.JNIUtil.GetMethodID;
import static org.graalvm.libgraal.jni.JNIUtil.GetStaticFieldID;
import static org.graalvm.libgraal.jni.JNIUtil.GetStaticMethodID;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.WordFactory;

/**
 * Calls from SVM to HotSpot.
 */
final class SVMToHotSpotCalls {

    private static final String CLASS_SERVICES = "jdk/vm/ci/services/Services";
    static final String CLASS_RUNTIME = "jdk/vm/ci/hotspot/HotSpotJVMCIRuntime";
    static final String CLASS_LIBGRAAL = "org/graalvm/libgraal/LibGraal";

    private static final String[] METHOD_GET_FACTORY = {
                    "getFactory",
                    "()Lorg/graalvm/compiler/hotspot/management/SVMMBean$Factory;"
    };
    private static final String[] METHOD_SIGNAL = {
                    "signal",
                    "(Lorg/graalvm/compiler/hotspot/management/SVMMBean$Factory;J)V"
    };
    private static final String[] METHOD_GET_JVMCI_CLASS_LOADER = {
                    "getJVMCIClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_GET_PLATFORM_CLASS_LOADER = {
                    "getPlatformClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_LOAD_CLASS = {
                    "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;"
    };
    private static final String[] METHOD_GET_MESSAGE = {
                    "getMessage",
                    "()Ljava/lang/String;"
    };
    private static final String[] METHOD_GET_CLASS_NAME = {
                    "getName",
                    "()Ljava/lang/String;"
    };
    private static final String[] METHOD_RUNTIME = {
                    "runtime",
                    "()Ljdk/vm/ci/hotspot/HotSpotJVMCIRuntime;"
    };
    private static final String[] METHOD_REGISTER_NATIVES = {
                    "registerNativeMethods",
                    "(Ljdk/vm/ci/hotspot/HotSpotJVMCIRuntime;Ljava/lang/Class;)V"
    };
    private static final String[] FIELD_NATIVE_REGISTERED = {
                    "nativeRegistered",
                    "Z"
    };

    private SVMToHotSpotCalls() {
    }

    static JNI.JObject getJVMCIClassLoader(JNI.JNIEnv env) {
        JNI.JClass clazz;
        try (CCharPointerHolder className = CTypeConversion.toCString(CLASS_SERVICES)) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + CLASS_SERVICES);
        }
        JNI.JMethodID getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_JVMCI_CLASS_LOADER);
        if (getClassLoaderId.isNonNull()) {
            return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, nullPointer());
        }
        try (CCharPointerHolder className = CTypeConversion.toCString(JNIUtil.getBinaryName(ClassLoader.class.getName()))) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + ClassLoader.class.getName());
        }
        getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_PLATFORM_CLASS_LOADER);
        if (getClassLoaderId.isNonNull()) {
            return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, nullPointer());
        }
        return WordFactory.nullPointer();
    }

    static JNI.JObject getFactory(JNI.JNIEnv env, JNI.JClass svmToHotSpotEntryPointsClass) {
        JNI.JMethodID createId = findMethod(env, svmToHotSpotEntryPointsClass, true, false, METHOD_GET_FACTORY);
        return env.getFunctions().getCallStaticObjectMethodA().call(env, svmToHotSpotEntryPointsClass, createId, nullPointer());
    }

    static void signal(JNI.JNIEnv env, JNI.JClass svmToHotSpotEntryPointsClass, JNI.JObject factory) {
        JNI.JMethodID signalId = findMethod(env, svmToHotSpotEntryPointsClass, true, false, METHOD_SIGNAL);
        JNI.JValue params = StackValue.get(2, JNI.JValue.class);
        params.addressOf(0).setJObject(factory);
        params.addressOf(1).setLong(CurrentIsolate.getIsolate().rawValue());
        env.getFunctions().getCallStaticVoidMethodA().call(env, svmToHotSpotEntryPointsClass, signalId, params);
        if (JNIUtil.ExceptionCheck(env)) {
            JNIUtil.ExceptionDescribe(env);
        }
    }

    /**
     * Calls {@link Class#getName()} on a class in HotSpot heap.
     */
    static JNI.JString getClassName(JNI.JNIEnv env, JNI.JClass exception) {
        JNI.JMethodID getClassNameId = findMethod(env, JNIUtil.GetObjectClass(env, exception), false, false, METHOD_GET_CLASS_NAME);
        return (JNI.JString) env.getFunctions().getCallObjectMethodA().call(env, exception, getClassNameId, WordFactory.nullPointer());
    }

    /**
     * Calls {@link Throwable#getMessage()} on an exception in HotSpot heap.
     */
    static JNI.JString getExceptionMessage(JNI.JNIEnv env, JNI.JThrowable exception) {
        JNI.JMethodID getMessageId = findMethod(env, JNIUtil.GetObjectClass(env, exception), false, false, METHOD_GET_MESSAGE);
        return (JNI.JString) env.getFunctions().getCallObjectMethodA().call(env, exception, getMessageId, WordFactory.nullPointer());
    }

    /**
     * Finds a class in HotSpot heap using a given {@code ClassLoader}.
     *
     * @param env the {@code JNIEnv}
     * @param className the class name
     */
    static JNI.JClass findClass(JNI.JNIEnv env, JNI.JObject classLoader, String className) {
        if (classLoader.isNull()) {
            throw new IllegalArgumentException("ClassLoader must be non null.");
        }
        JNI.JMethodID findClassId = findMethod(env, JNIUtil.GetObjectClass(env, classLoader), false, false, METHOD_LOAD_CLASS);
        JNI.JValue params = StackValue.get(1, JNI.JValue.class);
        params.addressOf(0).setJObject(JNIUtil.createHSString(env, className.replace('/', '.')));
        return (JNI.JClass) env.getFunctions().getCallObjectMethodA().call(env, classLoader, findClassId, params);
    }

    static JNI.JObject getRuntime(JNI.JNIEnv env, JNI.JClass runtimeClass) {
        JNI.JMethodID runtimeId = findMethod(env, runtimeClass, true, false, METHOD_RUNTIME);
        return env.getFunctions().getCallStaticObjectMethodA().call(env, runtimeClass, runtimeId, nullPointer());
    }

    static void registerNatives(JNI.JNIEnv env, JNI.JClass libgraal, JNI.JObject runtime, JNI.JClass target) {
        JNI.JMethodID registerId = findMethod(env, libgraal, true, false, METHOD_REGISTER_NATIVES);
        JNI.JValue params = StackValue.get(2, JNI.JValue.class);
        params.addressOf(0).setJObject(runtime);
        params.addressOf(1).setJObject(target);
        env.getFunctions().getCallStaticObjectMethodA().call(env, libgraal, registerId, params);
    }

    static void nativeRegistered(JNI.JNIEnv env, JNI.JClass hsToSvmCalls) {
        JNI.JFieldID nativeRegisteredId = findStaticField(env, hsToSvmCalls, FIELD_NATIVE_REGISTERED);
        env.getFunctions().getSetStaticBooleanField().call(env, hsToSvmCalls, nativeRegisteredId, true);
    }

    static boolean waitForRegisterNatives(JNI.JNIEnv env, JNI.JClass hsToSvmCalls) {
        JNI.JFieldID nativeRegisteredId = findStaticField(env, hsToSvmCalls, FIELD_NATIVE_REGISTERED);
        JNI.GetStaticBooleanField access = env.getFunctions().getGetStaticBooleanField();
        boolean res;
        do {
            res = access.call(env, hsToSvmCalls, nativeRegisteredId);
            if (JNIUtil.ExceptionCheck(env)) {
                return false;
            }
        } while (!res);
        return true;
    }

    private static JNI.JMethodID findMethod(JNI.JNIEnv env, JNI.JClass clazz, boolean staticMethod, boolean optional, String[] descriptor) {
        assert descriptor.length == 2;
        JNI.JMethodID result;
        try (CCharPointerHolder name = toCString(descriptor[0]); CCharPointerHolder sig = toCString(descriptor[1])) {
            result = staticMethod ? GetStaticMethodID(env, clazz, name.get(), sig.get()) : GetMethodID(env, clazz, name.get(), sig.get());
            if (optional) {
                MBeanProxy.checkException(env, "Cannot find method " + descriptor[0], NoSuchMethodError.class);
            } else {
                MBeanProxy.checkException(env, "Cannot find method " + descriptor[0]);
            }
            return result;
        }
    }

    private static JNI.JFieldID findStaticField(JNI.JNIEnv env, JNI.JClass clazz, String[] descriptor) {
        assert descriptor.length == 2;
        JNI.JFieldID result;
        try (CCharPointerHolder name = toCString(descriptor[0]); CCharPointerHolder sig = toCString(descriptor[1])) {
            result = GetStaticFieldID(env, clazz, name.get(), sig.get());
            MBeanProxy.checkException(env, "Cannot find method " + descriptor[0]);
            return result;
        }
    }
}
