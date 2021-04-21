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
import static org.graalvm.nativebridge.jni.JNIUtil.createString;
import static org.graalvm.nativebridge.jni.JNIUtil.trace;

import org.graalvm.nativebridge.jni.JNI.JClass;
import org.graalvm.nativebridge.jni.JNI.JObject;
import org.graalvm.nativebridge.jni.JNI.JMethodID;
import org.graalvm.nativebridge.jni.JNI.JNIEnv;
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

    private static final ThreadLocal<Boolean> inTrace = ThreadLocal.withInitial(()->false);

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
    public void callStaticVoid(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        env.getFunctions().getCallStaticVoidMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    @HotSpotCall
    public boolean callStaticBoolean(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public long callStaticLong(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public int callStaticInt(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @SuppressWarnings("unchecked")
    @HotSpotCall
    public <R extends JObject> R callStaticJObject(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        JNI.JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callNewObject(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        JNI.JObject res = env.getFunctions().getNewObjectA().call(clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    public void callVoid(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        env.getFunctions().getCallVoidMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    @HotSpotCall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callJObject(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        JNI.JObject res = env.getFunctions().getCallObjectMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    @HotSpotCall
    public boolean callBoolean(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        boolean res = env.getFunctions().getCallBooleanMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public short callShort(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        short res = env.getFunctions().getCallShortMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public int callInt(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        int res = env.getFunctions().getCallIntMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public long callLong(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        long res = env.getFunctions().getCallLongMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public double callDouble(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        double res = env.getFunctions().getCallDoubleMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public float callFloat(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        float res = env.getFunctions().getCallFloatMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public byte callByte(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        byte res = env.getFunctions().getCallByteMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    @HotSpotCall
    public char callChar(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        char res = env.getFunctions().getCallCharMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    private void traceCall(JNIEnv env, JClass clazz, JNIMethod method) {
        // Do not trace tracing.
        if (!inTrace.get()) {
            inTrace.set(true);
            try {
                trace(1, "%s->HS: %s::%s",
                        JNIUtil.getFeatureName(),
                        toSimpleName(createString(env, JNIExceptionWrapper.callGetClassName(env, clazz))),
                        method.getDisplayName());
            } finally {
                inTrace.remove();
            }
        }
    }

    private void traceCall(JNIEnv env, JObject receiver, JNIMethod method) {
        // Intentionally does not use JNIUtil. The tracing JNI usage should not be traced.
        traceCall(env, env.getFunctions().getGetObjectClass().call(env, receiver), method);
    }

    private static String toSimpleName(String fqn) {
        int separatorIndex = fqn.lastIndexOf('.');
        return separatorIndex < 0 || separatorIndex + 1 == fqn.length() ?  fqn : fqn.substring(separatorIndex+1);
    }

    public interface JNIMethod {

        JMethodID getJMethodID();

        String getDisplayName();

        static JNIMethod findMethod(JNIEnv env, JClass clazz, boolean staticMethod, String methodName, String methodSignature) {
            JMethodID methodID = JNIUtil.findMethod(env, clazz, staticMethod, methodName, methodSignature);
            return new JNIMethod() {
                @Override
                public JMethodID getJMethodID() {
                    return methodID;
                }

                @Override
                public String getDisplayName() {
                    return methodName;
                }
            };
        }
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
