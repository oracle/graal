/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.jniutils;

import static org.graalvm.jniutils.JNIExceptionWrapper.wrapAndThrowPendingJNIException;
import static org.graalvm.jniutils.JNIUtil.createString;
import static org.graalvm.jniutils.JNIUtil.trace;
import static org.graalvm.jniutils.JNIUtil.tracingAt;

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper.ExceptionHandler;
import org.graalvm.word.WordFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/**
 * Support for calling into HotSpot using JNI. In addition to calling a method using JNI, the
 * {@code JNICalls} also perform JNI call tracing and exception handling. All JNI calls into HotSpot
 * must use this support to correctly merge HotSpot and native image stack traces.
 */
public final class JNICalls {

    private static final JNICalls INSTANCE = new JNICalls(ExceptionHandler.DEFAULT);

    private static final ThreadLocal<Boolean> inTrace = ThreadLocal.withInitial(() -> false);

    private final ExceptionHandler exceptionHandler;

    private JNICalls(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Returns a {@link JNICalls} instance with a default exception handler. The default exception
     * handler rethrows any pending JNI exception as a {@link JNIExceptionWrapper}.
     */
    public static JNICalls getDefault() {
        return INSTANCE;
    }

    /**
     * Creates a new {@link JNICalls} instance with a custom exception handler. The given exception
     * handler is used to handle pending JNI exceptions.
     */
    public static JNICalls createWithExceptionHandler(ExceptionHandler handler) {
        Objects.requireNonNull(handler, "Handler must be non null.");
        return new JNICalls(handler);
    }

    /**
     * Performs a JNI call of a static void method.
     */
    @JNICall
    public void callStaticVoid(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        env.getFunctions().getCallStaticVoidMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    /**
     * Performs a JNI call of a static method returning {@code boolean}.
     */
    @JNICall
    public boolean callStaticBoolean(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        boolean res = env.getFunctions().getCallStaticBooleanMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a static method returning {@code long}.
     */
    @JNICall
    public long callStaticLong(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        long res = env.getFunctions().getCallStaticLongMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a static method returning {@code int}.
     */
    @JNICall
    public int callStaticInt(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        int res = env.getFunctions().getCallStaticIntMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a static method returning {@link Object}.
     */
    @SuppressWarnings("unchecked")
    @JNICall
    public <R extends JObject> R callStaticJObject(JNIEnv env, JClass clazz, JNIMethod method, JNI.JValue args) {
        traceCall(env, clazz, method);
        JNI.JObject res = env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    /**
     * Creates a new object instance using a given constructor.
     */
    @JNICall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callNewObject(JNIEnv env, JClass clazz, JNIMethod constructor, JNI.JValue args) {
        traceCall(env, clazz, constructor);
        JNI.JObject res = env.getFunctions().getNewObjectA().call(env, clazz, constructor.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    /**
     * Performs a JNI call of a void method.
     */
    @JNICall
    public void callVoid(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        env.getFunctions().getCallVoidMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
    }

    /**
     * Performs a JNI call of a method returning {@link Object}.
     */
    @JNICall
    @SuppressWarnings("unchecked")
    public <R extends JObject> R callJObject(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        JNI.JObject res = env.getFunctions().getCallObjectMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return (R) res;
    }

    /**
     * Performs a JNI call of a method returning {@code boolean}.
     */
    @JNICall
    public boolean callBoolean(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        boolean res = env.getFunctions().getCallBooleanMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code short}.
     */
    @JNICall
    public short callShort(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        short res = env.getFunctions().getCallShortMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code int}.
     */
    @JNICall
    public int callInt(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        int res = env.getFunctions().getCallIntMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code long}.
     */
    @JNICall
    public long callLong(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        long res = env.getFunctions().getCallLongMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code double}.
     */
    @JNICall
    public double callDouble(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        double res = env.getFunctions().getCallDoubleMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code float}.
     */
    @JNICall
    public float callFloat(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        float res = env.getFunctions().getCallFloatMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code byte}.
     */
    @JNICall
    public byte callByte(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        byte res = env.getFunctions().getCallByteMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    /**
     * Performs a JNI call of a method returning {@code char}.
     */
    @JNICall
    public char callChar(JNIEnv env, JObject object, JNIMethod method, JNI.JValue args) {
        traceCall(env, object, method);
        char res = env.getFunctions().getCallCharMethodA().call(env, object, method.getJMethodID(), args);
        wrapAndThrowPendingJNIException(env, exceptionHandler);
        return res;
    }

    private static void traceCall(JNIEnv env, JClass clazz, JNIMethod method) {
        if (tracingAt(1)) {
            traceCallImpl(env, clazz, method);
        }
    }

    private static void traceCall(JNIEnv env, JObject receiver, JNIMethod method) {
        if (tracingAt(1)) {
            // Intentionally does not use JNIUtil. The tracing JNI usage should not be traced.
            traceCallImpl(env, env.getFunctions().getGetObjectClass().call(env, receiver), method);
        }
    }

    private static void traceCallImpl(JNIEnv env, JClass clazz, JNIMethod method) {
        // The tracing performs JNI calls to obtain name of the HotSpot entry point class.
        // This call must not be traced to prevent endless recursion.
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

    private static String toSimpleName(String fqn) {
        int separatorIndex = fqn.lastIndexOf('.');
        return separatorIndex < 0 || separatorIndex + 1 == fqn.length() ? fqn : fqn.substring(separatorIndex + 1);
    }

    /**
     * Represents a JNI method.
     */
    public interface JNIMethod {

        /**
         * Returns a method JNI {@link JMethodID}.
         */
        JMethodID getJMethodID();

        /**
         * Returns a method display name used for logging.
         */
        String getDisplayName();

        /**
         * Finds a {@link JNIMethod} in the given {@link JClass clazz} with the given name and
         * signature. If such a method does not exist throws {@link JNIExceptionWrapper} wrapping a
         * {@link NoSuchMethodError}.
         */
        static JNIMethod findMethod(JNIEnv env, JClass clazz, boolean staticMethod, String methodName, String methodSignature) {
            return findMethod(env, clazz, staticMethod, true, methodName, methodSignature);
        }

        /**
         * Finds a {@link JNIMethod} in given {@link JClass clazz} with given name and signature. If
         * such a method does not exist and {@code required} is {@code true}, it throws
         * {@link JNIExceptionWrapper} wrapping a {@link NoSuchMethodError}. If {@code required} is
         * {@code false} it clears the pending JNI exception and returns a
         * {@link WordFactory#nullPointer() C NULL pointer}.
         */
        static JNIMethod findMethod(JNIEnv env, JClass clazz, boolean staticMethod, boolean required, String methodName, String methodSignature) {
            JMethodID methodID = JNIUtil.findMethod(env, clazz, staticMethod, required, methodName, methodSignature);
            return methodID.isNull() ? null : new JNIMethod() {
                @Override
                public JMethodID getJMethodID() {
                    return methodID;
                }

                @Override
                public String getDisplayName() {
                    return methodName;
                }

                @Override
                public String toString() {
                    return methodName + methodSignature + "[0x" + Long.toHexString(methodID.rawValue()) + ']';
                }
            };
        }
    }

    /**
     * Marker annotation for the helper methods for calling a method in HotSpot.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface JNICall {
    }
}
