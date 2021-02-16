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
package org.graalvm.libgraal.jni;

import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.CreateException;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetClassName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTrace;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementClassName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementFileName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementLineNumber;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementMethodName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetThrowableMessage;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.UpdateStackTrace;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callCreateException;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetClassName;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetStackTrace;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetStackTraceElementClassName;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetStackTraceElementFileName;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetStackTraceElementLineNumber;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetStackTraceElementMethodName;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callGetThrowableMessage;
import static org.graalvm.libgraal.jni.JNIExceptionWrapperGen.callUpdateStackTrace;
import static org.graalvm.libgraal.jni.FromLibGraalCalls.isHotSpotCall;
import static org.graalvm.libgraal.jni.JNIUtil.ExceptionCheck;
import static org.graalvm.libgraal.jni.JNIUtil.ExceptionClear;
import static org.graalvm.libgraal.jni.JNIUtil.ExceptionDescribe;
import static org.graalvm.libgraal.jni.JNIUtil.ExceptionOccurred;
import static org.graalvm.libgraal.jni.JNIUtil.GetArrayLength;
import static org.graalvm.libgraal.jni.JNIUtil.GetObjectArrayElement;
import static org.graalvm.libgraal.jni.JNIUtil.GetObjectClass;
import static org.graalvm.libgraal.jni.JNIUtil.NewObjectArray;
import static org.graalvm.libgraal.jni.JNIUtil.SetObjectArrayElement;
import static org.graalvm.libgraal.jni.JNIUtil.Throw;
import static org.graalvm.libgraal.jni.JNIUtil.createHSString;
import static org.graalvm.libgraal.jni.JNIUtil.createString;
import static org.graalvm.libgraal.jni.JNIUtil.getBinaryName;
import static org.graalvm.libgraal.jni.JNIUtil.findClass;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.libgraal.jni.JNI.JClass;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JObjectArray;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNI.JThrowable;
import org.graalvm.libgraal.jni.annotation.FromLibGraalEntryPointsResolver;
import org.graalvm.libgraal.jni.annotation.JNIFromLibGraal;
import org.graalvm.word.WordFactory;

/**
 * Wraps an exception thrown by a JNI call into HotSpot. If the exception propagates up to an
 * libgraal entry point, the exception is re-thrown in HotSpot.
 */
public final class JNIExceptionWrapper extends RuntimeException {

    private static final String HS_ENTRYPOINTS_CLASS = "org.graalvm.libgraal.jni.JNIFromLibGraalEntryPoints";
    private static final long serialVersionUID = 1L;

    private final JThrowable throwableHandle;
    private final boolean throwableRequiresStackTraceUpdate;

    private JNIExceptionWrapper(JNIEnv env, JThrowable throwableHandle) {
        super(formatExceptionMessage(getClassName(env, throwableHandle), getMessage(env, throwableHandle)));
        this.throwableHandle = throwableHandle;
        this.throwableRequiresStackTraceUpdate = createMergedStackTrace(env);
    }

    /**
     * Re-throws this JNI exception in HotSpot after updating the stack trace to include the
     * libgraal frames between the libgraal call entry point and the call back to HotSpot that threw
     * the original JNI exception.
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
     * Creates a merged libgraal and HotSpot stack trace and updates this
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
     * If there is a pending JNI exception, this method wraps it in a {@link JNIExceptionWrapper},
     * clears the pending exception and throws the {@link JNIExceptionWrapper} wrapper. The
     * {@link JNIExceptionWrapper} message is composed of the JNI exception class name and the JNI
     * exception message
     */
    @SafeVarargs
    public static void wrapAndThrowPendingJNIException(JNIEnv env, Class<? extends Throwable>... allowedExceptions) {
        if (ExceptionCheck(env)) {
            JThrowable exception = ExceptionOccurred(env);
            if (JNIUtil.tracingAt(1) && exception.isNonNull()) {
                ExceptionDescribe(env);
            }
            ExceptionClear(env);
            JNI.JClass exceptionClass = JNIUtil.GetObjectClass(env, exception);
            boolean allowed = false;
            for (Class<? extends Throwable> allowedException : allowedExceptions) {
                JNI.JClass allowedExceptionClass = findClass(env, getBinaryName(allowedException.getName()));
                if (allowedExceptionClass.isNonNull() && JNIUtil.IsSameObject(env, exceptionClass, allowedExceptionClass)) {
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
     * If {@code original} is a {@link JNIExceptionWrapper} the wrapped JNI exception is thrown.
     *
     * Otherwise a new {@link RuntimeException} is thrown. The {@link RuntimeException} message is
     * composed of {@code original.getClass().getName()} and {@code original.getMessage()}. The
     * stack trace is result of merging the {@code original.getStackTrace()} with the current
     * execution stack in HotSpot.
     *
     * @param env the {@link JNIEnv}
     * @param original an exception to be thrown in HotSpot
     */
    @JNIFromLibGraal(CreateException)
    public static void throwInHotSpot(JNIEnv env, Throwable original) {
        try {
            if (JNIUtil.tracingAt(1)) {
                original.printStackTrace(TTY.out);
            }
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
            while (libGraalStackIndex < libGraalStackTrace.length && (startingLibGraalFrame || !isHotSpotCall(libGraalStackTrace[libGraalStackIndex]))) {
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
     * Gets the stack trace from a JNI exception.
     *
     * @param env the {@link JNIEnv}
     * @param throwableHandle the JNI exception to get the stack trace from
     * @return the stack trace
     */
    @JNIFromLibGraal(GetStackTrace)
    @JNIFromLibGraal(GetStackTraceElementClassName)
    @JNIFromLibGraal(GetStackTraceElementMethodName)
    @JNIFromLibGraal(GetStackTraceElementFileName)
    @JNIFromLibGraal(GetStackTraceElementLineNumber)
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
            if (FromLibGraalCalls.isHotSpotCall(e)) {
                return true;
            }
        }
        return false;
    }

    @JNIFromLibGraal(UpdateStackTrace)
    private static JThrowable updateStackTrace(JNIEnv env, JThrowable throwableHandle, String[] encodedStackTrace) {
        JClass string = FromLibGraalCalls.getJNIClass(env, String.class);
        JObjectArray stackTraceHandle = NewObjectArray(env, encodedStackTrace.length, string, WordFactory.nullPointer());
        for (int i = 0; i < encodedStackTrace.length; i++) {
            JString element = createHSString(env, encodedStackTrace[i]);
            SetObjectArrayElement(env, stackTraceHandle, i, element);
        }
        return callUpdateStackTrace(env, throwableHandle, stackTraceHandle);
    }

    @JNIFromLibGraal(GetThrowableMessage)
    private static String getMessage(JNIEnv env, JThrowable throwableHandle) {
        JString message = callGetThrowableMessage(env, throwableHandle);
        return createString(env, message);
    }

    @JNIFromLibGraal(GetClassName)
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
     * {@link #wrapAndThrowPendingJNIException(org.graalvm.libgraal.jni.JNI.JNIEnv, java.lang.Class...)}
     * in {@code stackTrace}.
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

    @FromLibGraalEntryPointsResolver(value = JNIFromLibGraal.Id.class)
    static JNI.JClass getHotSpotEntryPoints(JNIEnv env) {
        if (fromLibGraalEntryPoints.isNull()) {
            String binaryName = JNIUtil.getBinaryName(HS_ENTRYPOINTS_CLASS);
            JNI.JObject classLoader = JNIUtil.getJVMCIClassLoader(env);
            JNI.JClass entryPoints;
            if (classLoader.isNonNull()) {
                entryPoints = JNIUtil.findClass(env, classLoader, binaryName);
            } else {
                entryPoints = JNIUtil.findClass(env, binaryName);
            }
            if (entryPoints.isNull()) {
                // Here we cannot use JNIExceptionWrapper.
                // We failed to load HostSpot entry points for it.
                JNIUtil.ExceptionClear(env);
                throw new InternalError("Failed to load " + HS_ENTRYPOINTS_CLASS);
            }
            fromLibGraalEntryPoints = JNIUtil.NewGlobalRef(env, entryPoints, "Class<" + HS_ENTRYPOINTS_CLASS + ">");
        }
        return fromLibGraalEntryPoints;
    }

    private static JNI.JClass fromLibGraalEntryPoints;
}
