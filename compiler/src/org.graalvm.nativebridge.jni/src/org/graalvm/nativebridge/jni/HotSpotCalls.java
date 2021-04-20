/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.jni;

import static org.graalvm.nativebridge.jni.JNIExceptionWrapper.wrapAndThrowPendingJNIException;

import org.graalvm.nativebridge.jni.JNI.JClass;
import org.graalvm.nativebridge.jni.JNI.JObject;
import org.graalvm.nativebridge.jni.JNI.JMethodID;
import org.graalvm.nativebridge.jni.JNIExceptionWrapper.ExceptionHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HotSpotCalls {

    private static final HotSpotCalls INSTANCE = new HotSpotCalls(ExceptionHandler.DEFAULT);

    private final ExceptionHandler exceptionHandler;

    private HotSpotCalls(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public static HotSpotCalls getDefault() {
        return INSTANCE;
    }

    public static HotSpotCalls createWithExceptionHandler(ExceptionHandler handler) {
        Objects.requireNonNull(handler, "Handler must be non null.");
        return new HotSpotCalls(handler);
    }

    @HotSpotCall
    public void callStaticVoid(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        env.getFunctions().getCallStaticVoidMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    @HotSpotCall
    public boolean callStaticBoolean(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public long callStaticLong(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public int callStaticInt(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @SuppressWarnings("unchecked")
    @HotSpotCall
    public <R extends JObject> R callStaticJObject(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        JNI.JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callNewObject(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        JNI.JObject res = env.getFunctions().getNewObjectA().call(clazz, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    public void callVoid(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        env.getFunctions().getCallVoidMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    @HotSpotCall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callJObject(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        JNI.JObject res = env.getFunctions().getCallObjectMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    public boolean callBoolean(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        boolean res = env.getFunctions().getCallBooleanMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public short callShort(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        short res = env.getFunctions().getCallShortMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public int callInt(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        int res = env.getFunctions().getCallIntMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public long callLong(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        long res = env.getFunctions().getCallLongMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public double callDouble(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        double res = env.getFunctions().getCallDoubleMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public float callFloat(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        float res = env.getFunctions().getCallFloatMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public byte callByte(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        byte res = env.getFunctions().getCallByteMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public char callChar(JNI.JNIEnv env, JObject object, JMethodID id, JNI.JValue args) {
        char res = env.getFunctions().getCallCharMethodA().call(env, object, id, args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Determines if {@code frame} is for a method denoting a call into HotSpot.
     */
    static boolean isHotSpotCall(StackTraceElement frame) {
        if (!HotSpotCalls.class.getName().equals(frame.getClassName())) {
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

        for (Method m : HotSpotCalls.class.getDeclaredMethods()) {
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
