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
package org.graalvm.libgraal.jni;

import static org.graalvm.libgraal.jni.JNIExceptionWrapper.wrapAndThrowPendingJNIException;

import org.graalvm.libgraal.jni.annotation.FromLibGraalId;
import static org.graalvm.libgraal.jni.JNIUtil.GetStaticMethodID;
import static org.graalvm.libgraal.jni.JNIUtil.NewGlobalRef;
import static org.graalvm.libgraal.jni.JNIUtil.getBinaryName;
import static org.graalvm.libgraal.jni.JNIUtil.trace;
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

import org.graalvm.libgraal.jni.JNI.JClass;
import org.graalvm.libgraal.jni.JNI.JMethodID;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JValue;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

/**
 * Helpers for calling methods in HotSpot heap via JNI.
 */
public abstract class FromLibGraalCalls<T extends Enum<T> & FromLibGraalId> {

    private static final Map<String, JNIClass> classes = new ConcurrentHashMap<>();
    /**
     * Prevents recursion when an exception happens in {@link #getJNIClass} or {@link #getJNIMethod}
     * called from
     * {@link JNIExceptionWrapper#wrapAndThrowPendingJNIException(org.graalvm.libgraal.jni.JNI.JNIEnv, java.lang.Class...)}.
     */
    private static final ThreadLocal<Boolean> inExceptionHandler = new ThreadLocal<>();

    private final EnumMap<T, JNIMethod<T>> methods;
    private volatile JClass peer;

    protected FromLibGraalCalls(Class<T> idType) {
        methods = new EnumMap<>(idType);
    }

    /**
     * Returns a {@link JClass} for the from LibGraal entry points.
     */
    protected abstract JClass resolvePeer(JNIEnv env);

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
     * Describes a method in {@link #peer(org.graalvm.libgraal.jni.JNI.JNIEnv) HotSpot peer class}.
     */
    static final class JNIMethod<T extends Enum<T> & FromLibGraalId> {
        final T hcId;
        final JMethodID jniId;

        JNIMethod(T hcId, JMethodID jniId) {
            this.hcId = hcId;
            this.jniId = jniId;
        }

        @Override
        public String toString() {
            return hcId + "[0x" + Long.toHexString(jniId.rawValue()) + ']';
        }
    }

    @HotSpotCall
    public final void callVoid(JNIEnv env, T id, JValue args) {
        JNIMethod<T> method = getJNIMethod(env, id, void.class);
        traceCall(id);
        env.getFunctions().getCallStaticVoidMethodA().call(env, peer(env), method.jniId, args);
        wrapAndThrowPendingJNIException(env);
    }

    @HotSpotCall
    public final boolean callBoolean(JNIEnv env, T id, JValue args) {
        JNIMethod<T> method = getJNIMethod(env, id, boolean.class);
        traceCall(id);
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, peer(env), method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    public final long callLong(JNIEnv env, T id, JValue args) {
        JNIMethod<T> method = getJNIMethod(env, id, long.class);
        traceCall(id);
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, peer(env), method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    public final int callInt(JNIEnv env, T id, JValue args) {
        JNIMethod<T> method = getJNIMethod(env, id, int.class);
        traceCall(id);
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, peer(env), method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @SuppressWarnings("unchecked")
    @HotSpotCall
    public final <R extends JObject> R callJObject(JNIEnv env, T id, JValue args) {
        JNIMethod<T> method = getJNIMethod(env, id, Object.class);
        traceCall(id);
        JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, peer(env), method.jniId, args);
        wrapAndThrowPendingJNIException(env);
        return (R) res;
    }

    public static JClass getJNIClass(JNIEnv env, Class<?> clazz) {
        if (clazz.isArray()) {
            throw new UnsupportedOperationException("Array classes are not supported");
        }
        return getJNIClassImpl(env, clazz.getName()).jclass;
    }

    public static JClass getJNIClass(JNIEnv env, String className) {
        return getJNIClassImpl(env, className).jclass;
    }

    private void traceCall(T id) {
        trace(1, "LIBGRAAL->HS: %s", id);
    }

    private static JNIClass getJNIClassImpl(JNIEnv env, String className) {
        try {
            return classes.computeIfAbsent(className, new Function<String, JNIClass>() {
                @Override
                public JNIClass apply(String name) {
                    JClass clazz = JNIUtil.findClass(env, getBinaryName(name));
                    if (clazz.isNull()) {
                        JNIUtil.ExceptionClear(env);
                        throw new InternalError("Cannot load class: " + name);
                    }
                    return new JNIClass(name, NewGlobalRef(env, clazz, "Class<" + name + ">"));
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

    private JNIMethod<T> getJNIMethod(JNIEnv env, T hcId, Class<?> expectedReturnType) {
        assert hcId.getReturnType() == expectedReturnType || expectedReturnType.isAssignableFrom(hcId.getReturnType());
        try {
            return methods.computeIfAbsent(hcId, new Function<T, JNIMethod<T>>() {
                @Override
                public JNIMethod<T> apply(T id) {
                    JClass c = peer(env);
                    String methodName = id.getMethodName();
                    try (CCharPointerHolder name = toCString(methodName); CCharPointerHolder sig = toCString(id.getSignature())) {
                        JMethodID jniId = GetStaticMethodID(env, c, name.get(), sig.get());
                        if (jniId.isNull()) {
                            throw new InternalError("No such method: " + methodName);
                        }
                        return new JNIMethod<>(id, jniId);
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

    private JClass peer(JNIEnv env) {
        if (peer.isNull()) {
            peer = resolvePeer(env);
        }
        return peer;
    }

    /**
     * Determines if {@code frame} is for a method denoting a call into HotSpot.
     */
    public static boolean isHotSpotCall(StackTraceElement frame) {
        boolean res = isHotSpotCallImpl(frame);
        return res;
    }

    private static boolean isHotSpotCallImpl(StackTraceElement frame) {
        if (!FromLibGraalCalls.class.getName().equals(frame.getClassName())) {
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

        for (Method m : FromLibGraalCalls.class.getDeclaredMethods()) {
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
