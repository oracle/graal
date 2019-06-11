/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapper.wrapAndThrowPendingJNIException;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.FindClass;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.GetStaticMethodID;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.NewGlobalRef;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.getBinaryName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HotSpotToSVMEntryPoints.trace;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JClass;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JMethodID;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JValue;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

/**
 * Helpers for calling methods in {@value #HOTSPOT_ENTRY_POINTS_CLASS_NAME} via JNI.
 */
final class SVMToHotSpotUtil {

    /**
     * Name of the class in the HotSpot heap to which the calls are made via JNI.
     */
    private static final String HOTSPOT_ENTRY_POINTS_CLASS_NAME = "org.graalvm.compiler.truffle.runtime.hotspot.libgraal.SVMToHotSpotEntryPoints";

    /**
     * Describes a class and holds a reference to its {@linkplain #jclass JNI value}.
     */
    static final class JNIClass {
        final String className;
        final JClass jclass;

        JNIClass(String className, JClass clazz) {
            this.className = className;
            this.jclass = clazz;
        }
    }

    /**
     * Describes a method in {@link SVMToHotSpotUtil#HOTSPOT_ENTRY_POINTS_CLASS_NAME}.
     */
    static final class JNIMethod {
        final SVMToHotSpot.Id hcId;
        final JMethodID jniId;

        JNIMethod(SVMToHotSpot.Id hcId, JMethodID jniId) {
            this.hcId = hcId;
            this.jniId = jniId;
        }

        @Override
        public String toString() {
            return hcId + "[0x" + Long.toHexString(jniId.rawValue()) + ']';
        }
    }

    @HotSpotCall
    static void callVoid(JNIEnv env, SVMToHotSpot.Id id, JValue args) {
        JNIMethod method = getJNIMethod(env, id, void.class);
        traceCall(id);
        env.getFunctions().getCallStaticVoidMethodA().call(env, peer(env).jclass, method.jniId, args);
        wrapAndThrowPendingJNIException(env);
    }

    @HotSpotCall
    static boolean callBoolean(JNIEnv env, SVMToHotSpot.Id id, JValue args) {
        JNIMethod method = getJNIMethod(env, id, boolean.class);
        traceCall(id);
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, peer(env).jclass, method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    static long callLong(JNIEnv env, SVMToHotSpot.Id id, JValue args) {
        JNIMethod method = getJNIMethod(env, id, long.class);
        traceCall(id);
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, peer(env).jclass, method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    static int callInt(JNIEnv env, SVMToHotSpot.Id id, JValue args) {
        JNIMethod method = getJNIMethod(env, id, int.class);
        traceCall(id);
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, peer(env).jclass, method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @SuppressWarnings("unchecked")
    @HotSpotCall
    static <T extends JObject> T callJObject(JNIEnv env, SVMToHotSpot.Id id, JValue args) {
        JNIMethod method = getJNIMethod(env, id, Object.class);
        traceCall(id);
        JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, peer(env).jclass, method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return (T) res;
    }

    private static final EnumMap<SVMToHotSpot.Id, JNIMethod> methods = new EnumMap<>(SVMToHotSpot.Id.class);
    private static final Map<String, JNIClass> classes = new ConcurrentHashMap<>();
    /**
     * Prevents recursion when an exception happens in {@link #getJNIClass} or {@link #getJNIMethod}
     * called from {@link JNIExceptionWrapper#wrapAndThrowPendingJNIException}.
     */
    private static final ThreadLocal<Boolean> inExceptionHandler = new ThreadLocal<>();

    private static void traceCall(SVMToHotSpot.Id id) {
        trace(1, "SVM->HS: %s", id);
    }

    static JNIClass getJNIClass(JNIEnv env, Class<?> clazz) {
        if (clazz.isArray()) {
            throw new UnsupportedOperationException("Array classes are not supported");
        }
        return getJNIClass(env, clazz.getName());
    }

    private static JNIClass getJNIClass(JNIEnv env, String className) {
        try {
            return classes.computeIfAbsent(className, new Function<String, JNIClass>() {
                @Override
                public JNIClass apply(String name) {
                    try (CCharPointerHolder cName = toCString(getBinaryName(name))) {
                        JClass clazz = FindClass(env, cName.get());
                        if (clazz.isNull()) {
                            throw new InternalError("Cannot load class: " + name);
                        }
                        return new JNIClass(name, NewGlobalRef(env, clazz, "Class<" + name + ">"));
                    }
                }
            });
        } catch (InternalError ie) {
            if (inExceptionHandler.get() != Boolean.TRUE) {
                inExceptionHandler.set(true);
                try {
                    wrapAndThrowPendingJNIException(env);
                } finally {
                    inExceptionHandler.remove();
                }
            }
            throw ie;
        }
    }

    private static JNIClass hsvmPeer;

    private static JNIClass peer(JNIEnv env) {
        if (hsvmPeer == null) {
            hsvmPeer = getJNIClass(env, HOTSPOT_ENTRY_POINTS_CLASS_NAME);
        }
        return hsvmPeer;
    }

    private static JNIMethod getJNIMethod(JNIEnv env, SVMToHotSpot.Id hcId, Class<?> expectedReturnType) {
        assert hcId.getReturnType() == expectedReturnType || expectedReturnType.isAssignableFrom(hcId.getReturnType());
        try {
            return methods.computeIfAbsent(hcId, new Function<SVMToHotSpot.Id, JNIMethod>() {
                @Override
                public JNIMethod apply(Id id) {
                    JNIClass c = peer(env);
                    String methodName = id.getMethodName();
                    try (CCharPointerHolder name = toCString(methodName); CCharPointerHolder sig = toCString(id.getSignature())) {
                        JMethodID jniId = GetStaticMethodID(env, c.jclass, name.get(), sig.get());
                        if (jniId.isNull()) {
                            throw new InternalError("No such method: " + methodName);
                        }
                        return new JNIMethod(id, jniId);
                    }
                }
            });
        } catch (InternalError ie) {
            if (inExceptionHandler.get() != Boolean.TRUE) {
                inExceptionHandler.set(true);
                try {
                    wrapAndThrowPendingJNIException(env);
                } finally {
                    inExceptionHandler.remove();
                }
            }
            throw ie;
        }
    }

    /**
     * Determines if {@code frame} is for a method denoting a call into HotSpot.
     */
    static boolean isHotSpotCall(StackTraceElement frame) {
        if (!SVMToHotSpotUtil.class.getName().equals(frame.getClassName())) {
            return false;
        }
        return HotSpotCallNames.contains(frame.getMethodName());
    }

    /**
     * Marker annotation for the helper methods for calling a method in HotSpot.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private static @interface HotSpotCall {
    }

    /**
     * Names of the methods in this class annotated by {@link HotSpotCall}.
     */
    private static final Set<String> HotSpotCallNames;
    static {
        Map<String, Method> entryPoints = new HashMap<>();
        Map<String, Method> others = new HashMap<>();

        for (Method m : SVMToHotSpotUtil.class.getDeclaredMethods()) {
            if (m.getAnnotation(HotSpotCall.class) != null) {
                Method existing = entryPoints.put(m.getName(), m);
                if (existing != null) {
                    throw new InternalError("Method annotated by " + HotSpotCall.class.getSimpleName() +
                                    " must have unique name: " + m + " and " + existing);
                }
            } else {
                others.put(m.getName(), m);
            }
        }
        for (Map.Entry<String, Method> e : entryPoints.entrySet()) {
            Method existing = others.get(e.getKey());
            if (existing != null) {
                throw new InternalError("Method annotated by " + HotSpotCall.class.getSimpleName() +
                                " must have unique name: " + e.getValue() + " and " + existing);
            }
        }
        HotSpotCallNames = Collections.unmodifiableSet(entryPoints.keySet());
    }
}
