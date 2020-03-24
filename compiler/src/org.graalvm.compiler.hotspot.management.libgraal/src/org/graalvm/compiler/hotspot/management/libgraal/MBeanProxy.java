/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.libgraal.jni.JNIUtil.createString;
import static org.graalvm.libgraal.jni.JNIUtil.getBinaryName;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import javax.management.DynamicMBean;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

class MBeanProxy<T extends DynamicMBean> {

    // Classes defined in HotSpot heap by JNI, the values are filled by LibGraalFeature.
    private static final String HS_BEAN_CLASS_NAME = null;
    private static final byte[] HS_BEAN_CLASS = null;
    private static final String HS_BEAN_FACTORY_CLASS_NAME = null;
    private static final byte[] HS_BEAN_FACTORY_CLASS = null;
    private static final String HS_SVM_CALLS_CLASS_NAME = null;
    private static final byte[] HS_SVM_CALLS_CLASS = null;
    private static final String HS_PUSHBACK_ITER_CLASS_NAME = null;
    private static final byte[] HS_PUSHBACK_ITER_CLASS = null;
    private static final String HS_ISOLATE_THREAD_SCOPE_CLASS_NAME = null;
    private static final byte[] HS_ISOLATE_THREAD_SCOPE_CLASS = null;
    private static final String SVM_HS_ENTRYPOINTS_CLASS_NAME = null;
    private static final byte[] SVM_HS_ENTRYPOINTS_CLASS = null;

    /**
     * Pending MBeans registrations on HotSpot side.
     */
    private static Queue<MBeanProxy<?>> registrations = new ArrayDeque<>();

    // JNI Globals
    private static JNI.JClass svmToHotSpotEntryPoints;

    /**
     * Offset of the {@code _jni_environment} field in {@code JavaThread}.
     */
    private static volatile long jniEnvOffset;

    private static LibGraalMemoryPoolMBean memPoolBean;

    /**
     * The MBean instance.
     */
    private T bean;

    /**
     * The name of the MBean.
     */
    private String name;

    /**
     * JMX Object name.
     */
    private ObjectName objName;

    /**
     * Flag for pending registration.
     */
    private volatile boolean needsRegistration = true;

    /**
     * Creates a new uninitialized {@link MBeanProxy}. The
     * {@link MBeanProxy#initialize(javax.management.DynamicMBean, java.lang.String, javax.management.ObjectName)}
     * must be called before the instance is used.
     */
    MBeanProxy() {
    }

    /**
     * Creates a new {@link MBeanProxy} initialized by given {@code mbean}.
     */
    MBeanProxy(T mbean, String strName) throws MalformedObjectNameException {
        initialize(mbean, strName, new ObjectName(strName));
    }

    void initialize(T mbean, String strName, ObjectName objectName) {
        Objects.requireNonNull(mbean);
        Objects.requireNonNull(strName);
        Objects.requireNonNull(objectName);
        if (this.bean != null) {
            throw new IllegalStateException("Already initialized.");
        }
        assert this.name == null;
        assert this.objName == null;
        this.bean = mbean;
        this.name = strName;
        this.objName = objectName;
    }

    /**
     * Returns the MBean used for delegation from HotSpot heap.
     */
    T getBean() {
        return bean;
    }

    /**
     * Notification about finished registration in HotSpot heap.
     */
    void finishRegistration() {
        needsRegistration = false;
    }

    /**
     * Returns the name which should be used to register this MBean.
     */
    String getName() {
        return name;
    }

    ObjectName poll() {
        LibGraalMemoryPoolMBean memPool = memPoolBean;
        if (memPool != null) {
            memPool.update();
        }
        if (bean == null || needsRegistration) {
            return null;
        }
        return objName;
    }

    static void initializeJNI(GraalHotSpotVMConfig config) {
        if (jniEnvOffset == 0) {
            synchronized (MBeanProxy.class) {
                if (jniEnvOffset == 0) {
                    if (config.jniEnvironmentOffset == Integer.MIN_VALUE) {
                        // Old unsupported JVMCI version.
                        return;
                    }
                    memPoolBean = new LibGraalMemoryPoolMBean();
                    jniEnvOffset = config.jniEnvironmentOffset;
                    defineClassesInHotSpot(getCurrentJNIEnv());
                    try {
                        MBeanProxy<?> memPoolMBean = new MBeanProxy<>(memPoolBean, LibGraalMemoryPoolMBean.NAME);
                        registrations.add(memPoolMBean);
                    } catch (MalformedObjectNameException mon) {
                        throw new AssertionError("Invlid object name.", mon);
                    }
                }
            }
        }
    }

    static JNI.JClass getHotSpotEntryPoints() {
        return svmToHotSpotEntryPoints;
    }

    /**
     * Computes {@code JNIEnv} for a current {@code JavaThread}.
     */
    static JNI.JNIEnv getCurrentJNIEnv() {
        if (jniEnvOffset == 0) {
            throw new IllegalStateException("JniEnvOffset is not yet initialized.");
        }
        long currentJavaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
        return WordFactory.pointer(currentJavaThreadAddr + jniEnvOffset);
    }

    /**
     * Removes the pending registrations.
     *
     * @return the pending registrations
     */
    static synchronized List<MBeanProxy<?>> drain() {
        if (registrations.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<MBeanProxy<?>> res = new ArrayList<>(registrations);
            registrations.clear();
            return res;
        }
    }

    /**
     * Registers a given {@link HotSpotGraalManagement} instance into pending registrations.
     */
    static synchronized <T extends MBeanProxy<?>> T enqueueForRegistration(T instance) {
        registrations.add(instance);
        signal(getCurrentJNIEnv(), getHotSpotEntryPoints(), getFactory(getCurrentJNIEnv(), getHotSpotEntryPoints()));
        return instance;
    }

    /**
     * Uses JNI to define the classes in HotSpot heap.
     */
    @SuppressWarnings("try")
    private static void defineClassesInHotSpot(JNI.JNIEnv env) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.DefineClasses, env)) {
            JNI.JObject classLoader = SVMToHotSpotCalls.getJVMCIClassLoader(env);
            JNI.JClass svmHsEntryPoints = findClassInHotSpot(env, classLoader, SVM_HS_ENTRYPOINTS_CLASS_NAME);
            if (svmHsEntryPoints.isNull()) {
                if (defineClassInHotSpot(env, classLoader, HS_BEAN_CLASS_NAME, HS_BEAN_CLASS).isNull()) {
                    checkDefineClassException(env, HS_BEAN_CLASS_NAME);
                }

                if (defineClassInHotSpot(env, classLoader, HS_BEAN_FACTORY_CLASS_NAME, HS_BEAN_FACTORY_CLASS).isNull()) {
                    checkDefineClassException(env, HS_BEAN_FACTORY_CLASS_NAME);
                }

                if (defineClassInHotSpot(env, classLoader, HS_ISOLATE_THREAD_SCOPE_CLASS_NAME, HS_ISOLATE_THREAD_SCOPE_CLASS).isNull()) {
                    checkDefineClassException(env, HS_PUSHBACK_ITER_CLASS_NAME);
                }

                if (defineClassInHotSpot(env, classLoader, HS_PUSHBACK_ITER_CLASS_NAME, HS_PUSHBACK_ITER_CLASS).isNull()) {
                    checkDefineClassException(env, HS_PUSHBACK_ITER_CLASS_NAME);
                }
                JNI.JClass hsToSvmCalls = defineClassInHotSpot(env, classLoader, HS_SVM_CALLS_CLASS_NAME, HS_SVM_CALLS_CLASS);
                if (hsToSvmCalls.isNull()) {
                    checkDefineClassException(env, HS_SVM_CALLS_CLASS_NAME);
                }

                svmHsEntryPoints = defineClassInHotSpot(env, classLoader, SVM_HS_ENTRYPOINTS_CLASS_NAME, SVM_HS_ENTRYPOINTS_CLASS);
                if (svmHsEntryPoints.isNull()) {
                    checkDefineClassException(env, SVM_HS_ENTRYPOINTS_CLASS_NAME);
                }
                registerNatives(env, classLoader, hsToSvmCalls);
                checkException(env, "Failed to register natives");
            }
            svmToHotSpotEntryPoints = JNIUtil.NewGlobalRef(env, svmHsEntryPoints, "Class<" + SVM_HS_ENTRYPOINTS_CLASS_NAME + ">");
        }
    }

    /**
     * Finds a class in HotSpot heap using JNI.
     *
     * @param env the {@code JNIEnv}
     * @param classLoader the class loader to define class in.
     * @param className the class name
     */
    private static JNI.JClass findClassInHotSpot(JNI.JNIEnv env, JNI.JObject classLoader, String className) {
        if (classLoader.isNonNull()) {
            try {
                return SVMToHotSpotCalls.findClass(env, classLoader, className);
            } finally {
                checkFindClassException(env, className, ClassNotFoundException.class);
            }
        } else {
            try {
                return findClassImpl(env, className);
            } finally {
                checkFindClassException(env, className, NoClassDefFoundError.class);
            }
        }
    }

    private static void checkDefineClassException(JNI.JNIEnv env, String className) {
        checkException(env, "Failed to define" + className);
    }

    @SafeVarargs
    private static void checkFindClassException(JNI.JNIEnv env, String className, Class<? extends Throwable>... allowedExceptions) {
        checkException(env, "Failed to load" + className, allowedExceptions);
    }

    /**
     * Checks and clears JNI pending exception. If the pending exception type is not allowed by
     * {@code allowedExceptions} it throws an {@link InternalError}.
     */
    @SafeVarargs
    static void checkException(JNI.JNIEnv env, String message, Class<? extends Throwable>... allowedExceptions) {
        if (JNIUtil.ExceptionCheck(env)) {
            try {
                JNI.JThrowable exception = JNIUtil.ExceptionOccurred(env);
                JNIUtil.ExceptionClear(env);
                JNI.JClass exceptionClass = JNIUtil.GetObjectClass(env, exception);
                boolean allowed = false;
                for (Class<? extends Throwable> allowedException : allowedExceptions) {
                    JNI.JClass allowedExceptionClass = findClassImpl(env, getBinaryName(allowedException.getName()));
                    if (allowedExceptionClass.isNonNull() && JNIUtil.IsSameObject(env, exceptionClass, allowedExceptionClass)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new InternalError(String.format("%s due to %s:%s.",
                                    message,
                                    createString(env, SVMToHotSpotCalls.getClassName(env, exceptionClass)),
                                    createString(env, SVMToHotSpotCalls.getExceptionMessage(env, exception))));
                }
            } finally {
                JNIUtil.ExceptionClear(env);
            }
        }
    }

    /**
     * Finds a class in HotSpot using a system class loader.
     */
    private static JNI.JClass findClassImpl(JNI.JNIEnv env, String className) {
        try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString(className)) {
            return JNIUtil.FindClass(env, name.get());
        }
    }

    /**
     * Defines a class in HotSpot heap using JNI.
     *
     * @param env the {@code JNIEnv}
     * @param classLoader the class loader to define class in.
     * @param clazzName the class name
     * @param clazz the class byte code
     * @return the defined class
     */
    private static JNI.JClass defineClassInHotSpot(JNI.JNIEnv env, JNI.JObject classLoader, String clazzName, byte[] clazz) {
        CCharPointer classData = UnmanagedMemory.malloc(clazz.length);
        ByteBuffer buffer = CTypeConversion.asByteBuffer(classData, clazz.length);
        buffer.put(clazz);
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(clazzName)) {
            return JNIUtil.DefineClass(
                            env,
                            className.get(),
                            classLoader,
                            classData,
                            clazz.length);
        } finally {
            UnmanagedMemory.free(classData);
        }
    }

    @SuppressWarnings("try")
    private static void registerNatives(JNI.JNIEnv env, JNI.JObject classLoader, JNI.JClass target) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.RegisterNatives, env)) {
            JNI.JClass runtimeClass = findClassInHotSpot(env, classLoader, SVMToHotSpotCalls.CLASS_RUNTIME);
            if (runtimeClass.isNull()) {
                throw new InternalError("Cannot load " + SVMToHotSpotCalls.CLASS_RUNTIME);
            }
            JNI.JClass libgraalClass = findClassInHotSpot(env, classLoader, SVMToHotSpotCalls.CLASS_LIBGRAAL);
            if (libgraalClass.isNull()) {
                throw new InternalError("Cannot load " + SVMToHotSpotCalls.CLASS_LIBGRAAL);
            }
            JNI.JObject runtime = SVMToHotSpotCalls.getRuntime(env, runtimeClass);
            if (runtime.isNonNull()) {
                SVMToHotSpotCalls.registerNatives(env, libgraalClass, runtime, target);
            }
        }
    }

    /**
     * Gets a reference to factory thread running in HotSpot heap.
     */
    @SuppressWarnings("try")
    private static JNI.JObject getFactory(JNI.JNIEnv env, JNI.JClass svmHsEntryPoints) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.GetFactory, env)) {
            JNI.JObject factory = SVMToHotSpotCalls.getFactory(env, svmHsEntryPoints);
            checkException(env, "Failed to instantiate MBean factory on HotSpot side");
            assert factory.isNonNull() : "Factory cannot be null.";
            return factory;
        }
    }

    /**
     * Notifies the factory thread in HotSpot heap about new management bean instances to register.
     */
    @SuppressWarnings("try")
    private static void signal(JNI.JNIEnv env, JNI.JClass svmHsEntryPoints, JNI.JObject factory) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.NewMBean, env)) {
            SVMToHotSpotCalls.signal(env, svmHsEntryPoints, factory);
            checkException(env, "Failed to register MBean");
        }
    }
}
