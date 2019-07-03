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

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateException;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTrace;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementClassName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementFileName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementMethodName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetThrowableMessage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.UpdateStackTrace;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callCreateException;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetStackTrace;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetStackTraceElementClassName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetStackTraceElementFileName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetStackTraceElementLineNumber;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetStackTraceElementMethodName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callGetThrowableMessage;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIExceptionWrapperGen.callUpdateStackTrace;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.SVMToHotSpotUtil.isHotSpotCall;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.ExceptionCheck;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.ExceptionClear;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.ExceptionDescribe;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.ExceptionOccurred;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.GetArrayLength;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.GetObjectArrayElement;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.NewObjectArray;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.SetObjectArrayElement;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.Throw;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createHSString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createString;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObjectArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JThrowable;
import org.graalvm.word.WordFactory;

/**
 * Wraps an exception thrown by a JNI call into HotSpot. If the exception propagates up to an
 * {@link HotSpotToSVM} entry point, the exception is re-thrown in HotSpot.
 */
final class JNIExceptionWrapper extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final JThrowable throwableHandle;
    private final boolean throwableRequiresStackTraceUpdate;

    private JNIExceptionWrapper(JNIEnv env, JThrowable throwableHandle) {
        super(getMessage(env, throwableHandle));
        this.throwableHandle = throwableHandle;
        this.throwableRequiresStackTraceUpdate = createMergedStackTrace(env);
    }

    /**
     * Re-throws this JNI exception in HotSpot after updating the stack trace to include the SVM
     * frames between the SVM call entry point and the call back to HotSpot that threw the original
     * JNI exception.
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
     * Creates a merged SVM and HotSpot stack trace and updates this {@link JNIExceptionWrapper}
     * stack trace to it.
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
            StackTraceElement[] svmStack = getStackTrace();
            mergedStack = mergeStackTraces(hsStack, svmStack, 0, getIndexOfPropagateJNIExceptionFrame(svmStack), true);
            res = true;
        }
        setStackTrace(mergedStack);
        return res;
    }

    /**
     * If there is a pending JNI exception, this method wraps it in a {@link JNIExceptionWrapper},
     * clears the pending exception and throws the {@link JNIExceptionWrapper} wrapper.
     */
    static void wrapAndThrowPendingJNIException(JNIEnv env) {
        if (ExceptionCheck(env)) {
            JThrowable exception = ExceptionOccurred(env);
            if (HotSpotToSVMEntryPoints.tracingAt(1) && exception.isNonNull()) {
                ExceptionDescribe(env);
            }
            ExceptionClear(env);
            throw new JNIExceptionWrapper(env, exception);
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
    @SVMToHotSpot(CreateException)
    static void throwInHotSpot(JNIEnv env, Throwable original) {
        if (HotSpotToSVMEntryPoints.tracingAt(1)) {
            original.printStackTrace(TTY.out);
        }
        if (original.getClass() == JNIExceptionWrapper.class) {
            ((JNIExceptionWrapper) original).throwInHotSpot(env);
        } else {
            StringBuilder message = new StringBuilder(original.getClass().getName());
            String originalMessage = original.getMessage();
            if (originalMessage != null) {
                message.append(": ").append(originalMessage);
            }
            JString hsMessage = createHSString(env, message.toString());
            JThrowable hsThrowable = callCreateException(env, hsMessage);
            StackTraceElement[] hsStack = getJNIExceptionStackTrace(env, hsThrowable);
            StackTraceElement[] svmStack = original.getStackTrace();
            String[] merged = encode(mergeStackTraces(hsStack, svmStack, 1, 0, false));
            Throw(env, updateStackTrace(env, hsThrowable, merged));
        }
    }

    /**
     * Merges {@code hotSpotStackTrace} with {@code svmStackTrace}.
     *
     * @param hotSpotStackTrace
     * @param svmStackTrace
     * @param hotSpotStackStartIndex
     * @param svmStackStartIndex
     * @param originatedInHotSpot
     */
    private static StackTraceElement[] mergeStackTraces(
                    StackTraceElement[] hotSpotStackTrace,
                    StackTraceElement[] svmStackTrace,
                    int hotSpotStackStartIndex,
                    int svmStackStartIndex,
                    boolean originatedInHotSpot) {
        int targetIndex = 0;
        StackTraceElement[] merged = new StackTraceElement[hotSpotStackTrace.length - hotSpotStackStartIndex + svmStackTrace.length - svmStackStartIndex];
        boolean startingHotSpotFrame = true;
        boolean startingSvmFrame = true;
        boolean useHotSpotStack = originatedInHotSpot;
        int hotSpotStackIndex = hotSpotStackStartIndex;
        int svmStackIndex = svmStackStartIndex;
        while (hotSpotStackIndex < hotSpotStackTrace.length || svmStackIndex < svmStackTrace.length) {
            if (useHotSpotStack) {
                while (hotSpotStackIndex < hotSpotStackTrace.length && (startingHotSpotFrame || !hotSpotStackTrace[hotSpotStackIndex].isNativeMethod())) {
                    startingHotSpotFrame = false;
                    merged[targetIndex++] = hotSpotStackTrace[hotSpotStackIndex++];
                }
                startingHotSpotFrame = true;
            } else {
                useHotSpotStack = true;
            }
            while (svmStackIndex < svmStackTrace.length && (startingSvmFrame || !isHotSpotCall(svmStackTrace[svmStackIndex]))) {
                startingSvmFrame = false;
                merged[targetIndex++] = svmStackTrace[svmStackIndex++];
            }
            startingSvmFrame = true;
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
    @SVMToHotSpot(GetStackTrace)
    @SVMToHotSpot(GetStackTraceElementClassName)
    @SVMToHotSpot(GetStackTraceElementMethodName)
    @SVMToHotSpot(GetStackTraceElementFileName)
    @SVMToHotSpot(GetStackTraceElementLineNumber)
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
            if (SVMToHotSpotUtil.isHotSpotCall(e)) {
                return true;
            }
        }
        return false;
    }

    @SVMToHotSpot(UpdateStackTrace)
    private static JThrowable updateStackTrace(JNIEnv env, JThrowable throwableHandle, String[] encodedStackTrace) {
        SVMToHotSpotUtil.JNIClass string = SVMToHotSpotUtil.getJNIClass(env, String.class);
        JObjectArray stackTraceHandle = NewObjectArray(env, encodedStackTrace.length, string.jclass, WordFactory.nullPointer());
        for (int i = 0; i < encodedStackTrace.length; i++) {
            JString element = createHSString(env, encodedStackTrace[i]);
            SetObjectArrayElement(env, stackTraceHandle, i, element);
        }
        return callUpdateStackTrace(env, throwableHandle, stackTraceHandle);
    }

    @SVMToHotSpot(GetThrowableMessage)
    private static String getMessage(JNIEnv env, JThrowable throwableHandle) {
        JString message = callGetThrowableMessage(env, throwableHandle);
        return createString(env, message);
    }

    /**
     * Gets the index of the first frame denoting the caller of
     * {@link #wrapAndThrowPendingJNIException(JNIEnv)} in {@code stackTrace}.
     *
     * @returns {@code 0} if no caller found
     */
    private static int getIndexOfPropagateJNIExceptionFrame(StackTraceElement[] stackTrace) {
        for (int i = 0; i < stackTrace.length; i++) {
            if (JNIExceptionWrapper.class.getName().equals(stackTrace[i].getClassName()) &&
                            "wrapAndThrowPendingJNIException".equals(stackTrace[i].getMethodName())) {
                return i + 1;
            }
        }
        return 0;
    }
}
