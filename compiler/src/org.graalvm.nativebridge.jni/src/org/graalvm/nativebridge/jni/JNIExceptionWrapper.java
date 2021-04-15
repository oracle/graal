/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.nativebridge.jni.JNIUtil.ExceptionCheck;
import static org.graalvm.nativebridge.jni.JNIUtil.ExceptionClear;
import static org.graalvm.nativebridge.jni.JNIUtil.ExceptionDescribe;
import static org.graalvm.nativebridge.jni.JNIUtil.ExceptionOccurred;
import static org.graalvm.nativebridge.jni.JNIUtil.GetArrayLength;
import static org.graalvm.nativebridge.jni.JNIUtil.GetObjectArrayElement;
import static org.graalvm.nativebridge.jni.JNIUtil.GetObjectClass;
import static org.graalvm.nativebridge.jni.JNIUtil.GetStaticMethodID;
import static org.graalvm.nativebridge.jni.JNIUtil.IsSameObject;
import static org.graalvm.nativebridge.jni.JNIUtil.NewGlobalRef;
import static org.graalvm.nativebridge.jni.JNIUtil.NewObjectArray;
import static org.graalvm.nativebridge.jni.JNIUtil.SetObjectArrayElement;
import static org.graalvm.nativebridge.jni.JNIUtil.Throw;
import static org.graalvm.nativebridge.jni.JNIUtil.createHSString;
import static org.graalvm.nativebridge.jni.JNIUtil.createString;
import static org.graalvm.nativebridge.jni.JNIUtil.encodeMethodSignature;
import static org.graalvm.nativebridge.jni.JNIUtil.getBinaryName;
import static org.graalvm.nativebridge.jni.JNIUtil.findClass;
import static org.graalvm.nativebridge.jni.JNIUtil.getJVMCIClassLoader;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import org.graalvm.nativebridge.jni.JNI.JClass;
import org.graalvm.nativebridge.jni.JNI.JNIEnv;
import org.graalvm.nativebridge.jni.JNI.JObject;
import org.graalvm.nativebridge.jni.JNI.JObjectArray;
import org.graalvm.nativebridge.jni.JNI.JString;
import org.graalvm.nativebridge.jni.JNI.JThrowable;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

/**
 * Wraps an exception thrown by a org.graalvm.nativebridge.jni.JNI call into HotSpot. If the
 * exception propagates up to an native image entry point, the exception is re-thrown in HotSpot.
 */
public final class JNIExceptionWrapper extends RuntimeException {

    private static final String HS_ENTRYPOINTS_CLASS = "org.graalvm.nativebridge.jni.JNIExceptionWrapperEntryPoints";
    private static final long serialVersionUID = 1L;

    private static final MethodResolver CreateException = MethodResolver.create("createException", Throwable.class, String.class);
    private static final MethodResolver GetClassName = MethodResolver.create("getClassName", String.class, Class.class);
    private static final MethodResolver GetStackTraceElementClassName = MethodResolver.create("getStackTraceElementClassName", String.class, StackTraceElement.class);
    private static final MethodResolver GetStackTraceElementFileName = MethodResolver.create("getStackTraceElementFileName", String.class, StackTraceElement.class);
    private static final MethodResolver GetStackTraceElementLineNumber = MethodResolver.create("getStackTraceElementLineNumber", int.class, StackTraceElement.class);
    private static final MethodResolver GetStackTraceElementMethodName = MethodResolver.create("getStackTraceElementMethodName", String.class, StackTraceElement.class);
    private static final MethodResolver GetStackTrace = MethodResolver.create("getStackTrace", StackTraceElement[].class, Throwable.class);
    private static final MethodResolver GetThrowableMessage = MethodResolver.create("getThrowableMessage", String.class, Throwable.class);
    private static final MethodResolver UpdateStackTrace = MethodResolver.create("updateStackTrace", Throwable.class, Throwable.class, String[].class);

    private static volatile JNI.JClass fromLibGraalEntryPoints;

    private final JThrowable throwableHandle;
    private final boolean throwableRequiresStackTraceUpdate;

    private JNIExceptionWrapper(JNIEnv env, JThrowable throwableHandle) {
        super(formatExceptionMessage(getClassName(env, throwableHandle), getMessage(env, throwableHandle)));
        this.throwableHandle = throwableHandle;
        this.throwableRequiresStackTraceUpdate = createMergedStackTrace(env);
    }

    /**
     * Re-throws this org.graalvm.nativebridge.jni.JNI exception in HotSpot after updating the stack
     * trace to include the native image frames between the native image entry point and the call
     * back to HotSpot that threw the original org.graalvm.nativebridge.jni.JNI exception.
     */
    private void throwInHotSpot(JNIEnv env) {
        JThrowable toThrow;
        if (throwableRequiresStackTraceUpdate) {
            toThrow = updateStackTrace(env, throwableHandle, encode(getStackTrace()));
        } else {
            toThrow = throwableHandle;
        }
        Throw(env, toThrow);
    }

    /**
     * Creates a merged native image and HotSpot stack trace and updates this
     * {@link JNIExceptionWrapper} stack trace to it.
     *
     * @return true if the stack trace needed a merge
     */
    private boolean createMergedStackTrace(JNIEnv env) {
        StackTraceElement[] hsStack = getJNIExceptionStackTrace(env, throwableHandle);
        StackTraceElement[] mergedStack;
        boolean res;
        if (containsHotSpotCall(hsStack)) {
            mergedStack = hsStack;
            res = false;
        } else {
            StackTraceElement[] libGraalStack = getStackTrace();
            mergedStack = mergeStackTraces(hsStack, libGraalStack, 0, getIndexOfPropagateJNIExceptionFrame(libGraalStack), true);
            res = true;
        }
        setStackTrace(mergedStack);
        return res;
    }

    /**
     * If there is a pending org.graalvm.nativebridge.jni.JNI exception, this method wraps it in a
     * {@link JNIExceptionWrapper}, clears the pending exception and throws the
     * {@link JNIExceptionWrapper} wrapper. The {@link JNIExceptionWrapper} message is composed of
     * the org.graalvm.nativebridge.jni.JNI exception class name and the
     * org.graalvm.nativebridge.jni.JNI exception message
     */
    @SafeVarargs
    public static void wrapAndThrowPendingJNIException(JNIEnv env, Class<? extends Throwable>... allowedExceptions) {
        if (ExceptionCheck(env)) {
            JThrowable exception = ExceptionOccurred(env);
            if (JNIUtil.tracingAt(1) && exception.isNonNull()) {
                ExceptionDescribe(env);
            }
            ExceptionClear(env);
            JNI.JClass exceptionClass = GetObjectClass(env, exception);
            boolean allowed = false;
            for (Class<? extends Throwable> allowedException : allowedExceptions) {
                JNI.JClass allowedExceptionClass = findClass(env, getBinaryName(allowedException.getName()));
                if (allowedExceptionClass.isNonNull() && IsSameObject(env, exceptionClass, allowedExceptionClass)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new JNIExceptionWrapper(env, exception);
            }
        }
    }

    /**
     * Throws an exception into HotSpot.
     *
     * If {@code original} is a {@link JNIExceptionWrapper} the wrapped
     * org.graalvm.nativebridge.jni.JNI exception is thrown.
     *
     * Otherwise a new {@link RuntimeException} is thrown. The {@link RuntimeException} message is
     * composed of {@code original.getClass().getName()} and {@code original.getMessage()}. The
     * stack trace is result of merging the {@code original.getStackTrace()} with the current
     * execution stack in HotSpot.
     *
     * @param env the {@link JNIEnv}
     * @param original an exception to be thrown in HotSpot
     */
    public static void throwInHotSpot(JNIEnv env, Throwable original) {
        try {
            JNIUtil.trace(1, original);
            if (original.getClass() == JNIExceptionWrapper.class) {
                ((JNIExceptionWrapper) original).throwInHotSpot(env);
            } else {
                String message = formatExceptionMessage(original.getClass().getName(), original.getMessage());
                JString hsMessage = createHSString(env, message);
                JThrowable hsThrowable = callCreateException(env, hsMessage);
                StackTraceElement[] hsStack = getJNIExceptionStackTrace(env, hsThrowable);
                StackTraceElement[] libGraalStack = original.getStackTrace();
                String[] merged = encode(mergeStackTraces(hsStack, libGraalStack, 1, 0, false));
                Throw(env, updateStackTrace(env, hsThrowable, merged));
            }
        } catch (Throwable t) {
            // If something goes wrong when re-throwing the exception into HotSpot
            // print the exception stack trace.
            if (t instanceof ThreadDeath) {
                throw t;
            } else {
                original.addSuppressed(t);
                original.printStackTrace();
            }
        }
    }

    /**
     * Merges {@code hotSpotStackTrace} with {@code libGraalStackTrace}.
     *
     * @param hotSpotStackTrace
     * @param libGraalStackTrace
     * @param hotSpotStackStartIndex
     * @param libGraalStackStartIndex
     * @param originatedInHotSpot
     */
    private static StackTraceElement[] mergeStackTraces(
                    StackTraceElement[] hotSpotStackTrace,
                    StackTraceElement[] libGraalStackTrace,
                    int hotSpotStackStartIndex,
                    int libGraalStackStartIndex,
                    boolean originatedInHotSpot) {
        int targetIndex = 0;
        StackTraceElement[] merged = new StackTraceElement[hotSpotStackTrace.length - hotSpotStackStartIndex + libGraalStackTrace.length - libGraalStackStartIndex];
        boolean startingHotSpotFrame = true;
        boolean startingLibGraalFrame = true;
        boolean useHotSpotStack = originatedInHotSpot;
        int hotSpotStackIndex = hotSpotStackStartIndex;
        int libGraalStackIndex = libGraalStackStartIndex;
        while (hotSpotStackIndex < hotSpotStackTrace.length || libGraalStackIndex < libGraalStackTrace.length) {
            if (useHotSpotStack) {
                while (hotSpotStackIndex < hotSpotStackTrace.length && (startingHotSpotFrame || !hotSpotStackTrace[hotSpotStackIndex].isNativeMethod())) {
                    startingHotSpotFrame = false;
                    merged[targetIndex++] = hotSpotStackTrace[hotSpotStackIndex++];
                }
                startingHotSpotFrame = true;
            } else {
                useHotSpotStack = true;
            }
            while (libGraalStackIndex < libGraalStackTrace.length && (startingLibGraalFrame || !HotSpotCalls.isHotSpotCall(libGraalStackTrace[libGraalStackIndex]))) {
                startingLibGraalFrame = false;
                merged[targetIndex++] = libGraalStackTrace[libGraalStackIndex++];
            }
            startingLibGraalFrame = true;
        }
        return merged;
    }

    /**
     * Encodes {@code stackTrace} into a string representation. Each stack trace element has the
     * form {@code className|methodName|fileName|lineNumber}. A missing {@code fileName} is encoded
     * as an empty string. A {@code '|'} in {@code className}, {@code methodName} or
     * {@code fileName} is replaced with a {@code '!'}. Given how rare this is, a complicated
     * escaping mechanism is not warranted.
     */
    private static String[] encode(StackTraceElement[] stackTrace) {
        String[] res = new String[stackTrace.length];
        for (int i = 0; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            String methodName = stackTrace[i].getMethodName();
            String fileName = stackTrace[i].getFileName();
            int lineNumber = stackTrace[i].getLineNumber();
            res[i] = String.format("%s|%s|%s|%d",
                            className == null ? "" : className.replace('|', '!'),
                            methodName == null ? "" : methodName.replace('|', '!'),
                            fileName == null ? "" : fileName.replace('|', '!'),
                            lineNumber);
        }
        return res;
    }

    /**
     * Gets the stack trace from a org.graalvm.nativebridge.jni.JNI exception.
     *
     * @param env the {@link JNIEnv}
     * @param throwableHandle the org.graalvm.nativebridge.jni.JNI exception to get the stack trace
     *            from
     * @return the stack trace
     */
    private static StackTraceElement[] getJNIExceptionStackTrace(JNIEnv env, JObject throwableHandle) {
        JObjectArray elements = callGetStackTrace(env, throwableHandle);
        int len = GetArrayLength(env, elements);
        StackTraceElement[] res = new StackTraceElement[len];
        for (int i = 0; i < len; i++) {
            JObject element = GetObjectArrayElement(env, elements, i);
            String className = createString(env, callGetStackTraceElementClassName(env, element));
            String methodName = createString(env, callGetStackTraceElementMethodName(env, element));
            String fileName = createString(env, callGetStackTraceElementFileName(env, element));
            int lineNumber = callGetStackTraceElementLineNumber(env, element);
            res[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
        }
        return res;
    }

    /**
     * Determines if {@code stackTrace} contains a frame denoting a call into HotSpot.
     */
    private static boolean containsHotSpotCall(StackTraceElement[] stackTrace) {
        for (StackTraceElement e : stackTrace) {
            if (HotSpotCalls.isHotSpotCall(e)) {
                return true;
            }
        }
        return false;
    }

    private static JThrowable updateStackTrace(JNIEnv env, JThrowable throwableHandle, String[] encodedStackTrace) {
        JClass string = findClass(env, getBinaryName(String.class.getName()));
        JObjectArray stackTraceHandle = NewObjectArray(env, encodedStackTrace.length, string, WordFactory.nullPointer());
        for (int i = 0; i < encodedStackTrace.length; i++) {
            JString element = createHSString(env, encodedStackTrace[i]);
            SetObjectArrayElement(env, stackTraceHandle, i, element);
        }
        return callUpdateStackTrace(env, throwableHandle, stackTraceHandle);
    }

    private static String getMessage(JNIEnv env, JThrowable throwableHandle) {
        JString message = callGetThrowableMessage(env, throwableHandle);
        return createString(env, message);
    }

    private static String getClassName(JNIEnv env, JThrowable throwableHandle) {
        JClass classHandle = GetObjectClass(env, throwableHandle);
        JString className = callGetClassName(env, classHandle);
        return createString(env, className);
    }

    private static String formatExceptionMessage(String className, String message) {
        StringBuilder builder = new StringBuilder(className);
        if (message != null) {
            builder.append(": ").append(message);
        }
        return builder.toString();
    }

    /**
     * Gets the index of the first frame denoting the caller of
     * {@link #wrapAndThrowPendingJNIException(JNI.JNIEnv, java.lang.Class...)} in
     * {@code stackTrace}.
     *
     * @returns {@code 0} if no caller found
     */
    private static int getIndexOfPropagateJNIExceptionFrame(StackTraceElement[] stackTrace) {
        for (int i = 0; i < stackTrace.length; i++) {
            if (isStackFrame(stackTrace[i], JNIExceptionWrapper.class, "wrapAndThrowPendingJNIException")) {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean isStackFrame(StackTraceElement stackTraceElement, Class<?> clazz, String methodName) {
        return clazz.getName().equals(stackTraceElement.getClassName()) && methodName.equals(stackTraceElement.getMethodName());
    }

    // JNI calls
    static <T extends JObject> T callCreateException(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), CreateException.resolve(env), args);
    }

    static <T extends JObject> T callUpdateStackTrace(JNIEnv env, JObject p0, JObject p1) {
        JNI.JValue args = StackValue.get(2, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        args.addressOf(1).setJObject(p1);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), UpdateStackTrace.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetThrowableMessage(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetThrowableMessage.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetClassName(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetClassName.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetStackTrace(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetStackTrace.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetStackTraceElementClassName(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetStackTraceElementClassName.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetStackTraceElementMethodName(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetStackTraceElementMethodName.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetStackTraceElementFileName(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticJObject(env, getHotSpotEntryPoints(env), GetStackTraceElementFileName.resolve(env), args);
    }

    static int callGetStackTraceElementLineNumber(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return HotSpotCalls.callStaticInt(env, getHotSpotEntryPoints(env), GetStackTraceElementLineNumber.resolve(env), args);
    }

    private static final class MethodResolver {

        private final String methodName;
        private final String methodSignature;
        private volatile JNI.JMethodID methodId;

        private MethodResolver(String methodName, String methodSignature) {
            this.methodName = methodName;
            this.methodSignature = methodSignature;
        }

        public JNI.JMethodID resolve(JNIEnv jniEnv) {
            JNI.JMethodID res = methodId;
            if (res.isNull()) {
                JNI.JClass entryPointClass = getHotSpotEntryPoints(jniEnv);
                try (CTypeConversion.CCharPointerHolder name = toCString(methodName); CTypeConversion.CCharPointerHolder sig = toCString(methodSignature)) {
                    res = GetStaticMethodID(jniEnv, entryPointClass, name.get(), sig.get());
                    if (res.isNull()) {
                        throw new InternalError("No such method: " + methodName);
                    }
                    methodId = res;
                }
            }
            return res;
        }

        static MethodResolver create(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
            return new MethodResolver(methodName, encodeMethodSignature(returnType, parameterTypes));
        }
    }

    private static JNI.JClass getHotSpotEntryPoints(JNIEnv env) {
        if (fromLibGraalEntryPoints.isNull()) {
            String binaryName = getBinaryName(HS_ENTRYPOINTS_CLASS);
            JNI.JObject classLoader = getJVMCIClassLoader(env);
            JNI.JClass entryPoints;
            if (classLoader.isNonNull()) {
                entryPoints = findClass(env, classLoader, binaryName);
            } else {
                entryPoints = findClass(env, binaryName);
            }
            if (entryPoints.isNull()) {
                // Here we cannot use JNIExceptionWrapper.
                // We failed to load HostSpot entry points for it.
                ExceptionClear(env);
                throw new InternalError("Failed to load " + HS_ENTRYPOINTS_CLASS);
            }
            fromLibGraalEntryPoints = NewGlobalRef(env, entryPoints, "Class<" + HS_ENTRYPOINTS_CLASS + ">");
        }
        return fromLibGraalEntryPoints;
    }
}
