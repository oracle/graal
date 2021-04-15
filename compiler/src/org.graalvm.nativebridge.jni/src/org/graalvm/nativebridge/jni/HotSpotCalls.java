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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class HotSpotCalls {

    private HotSpotCalls() {
    }

    @HotSpotCall
    public static void callStaticVoid(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        env.getFunctions().getCallStaticVoidMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env);
    }

    @HotSpotCall
    public static boolean callStaticBoolean(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    public static long callStaticLong(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @HotSpotCall
    public static int callStaticInt(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env);
        return res;
    }

    @SuppressWarnings("unchecked")
    @HotSpotCall
    public static <R extends JObject> R callStaticJObject(JNI.JNIEnv env, JClass clazz, JMethodID id, JNI.JValue args) {
        JNI.JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, id, args);
        wrapAndThrowPendingJNIException(env);
        return (R) res;
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
