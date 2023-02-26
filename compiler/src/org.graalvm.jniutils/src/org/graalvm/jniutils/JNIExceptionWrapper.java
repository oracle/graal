/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.jniutils;

import static org.graalvm.jniutils.JNIUtil.ExceptionCheck;
import static org.graalvm.jniutils.JNIUtil.ExceptionClear;
import static org.graalvm.jniutils.JNIUtil.ExceptionDescribe;
import static org.graalvm.jniutils.JNIUtil.ExceptionOccurred;
import static org.graalvm.jniutils.JNIUtil.GetObjectClass;
import static org.graalvm.jniutils.JNIUtil.GetStaticMethodID;
import static org.graalvm.jniutils.JNIUtil.IsSameObject;
import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;
import static org.graalvm.jniutils.JNIUtil.Throw;
import static org.graalvm.jniutils.JNIUtil.createHSString;
import static org.graalvm.jniutils.JNIUtil.createString;
import static org.graalvm.jniutils.JNIUtil.encodeMethodSignature;
import static org.graalvm.jniutils.JNIUtil.getBinaryName;
import static org.graalvm.jniutils.JNIUtil.findClass;
import static org.graalvm.jniutils.JNIUtil.getJVMCIClassLoader;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import org.graalvm.jniutils.JNICalls.JNICall;
import org.graalvm.jniutils.JNICalls.JNIMethod;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNI.JThrowable;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps an exception thrown by a JNI call into HotSpot. If the exception propagates up to an native
 * image entry point, the exception is re-thrown in HotSpot.
 */
@SuppressWarnings("serial")
public final class JNIExceptionWrapper extends RuntimeException {

    private static final String HS_ENTRYPOINTS_CLASS = "org.graalvm.jniutils.JNIExceptionWrapperEntryPoints";
    private static final long serialVersionUID = 1L;

    private static final JNIMethodResolver CreateException = JNIMethodResolver.create("createException", Throwable.class, String.class);
    private static final JNIMethodResolver GetClassName = JNIMethodResolver.create("getClassName", String.class, Class.class);
    private static final JNIMethodResolver GetStackTrace = JNIMethodResolver.create("getStackTrace", byte[].class, Throwable.class);
    private static final JNIMethodResolver GetThrowableMessage = JNIMethodResolver.create("getThrowableMessage", String.class, Throwable.class);
    private static final JNIMethodResolver UpdateStackTrace = JNIMethodResolver.create("updateStackTrace", Throwable.class, Throwable.class, byte[].class);

    private static volatile JNI.JClass entryPointsClass;

    private final JThrowable throwableHandle;
    private final boolean throwableRequiresStackTraceUpdate;

    private JNIExceptionWrapper(JNIEnv env, JThrowable throwableHandle) {
        super(formatExceptionMessage(getClassName(env, throwableHandle), getMessage(env, throwableHandle)));
        this.throwableHandle = throwableHandle;
        this.throwableRequiresStackTraceUpdate = createMergedStackTrace(env);
    }

    /**
     * Re-throws this JNI exception in HotSpot after updating the stack trace to include the native
     * image frames between the native image entry point and the call back to HotSpot that threw the
     * original JNI exception.
     */
    private void throwInHotSpot(JNIEnv env) {
        JThrowable toThrow;
        if (throwableRequiresStackTraceUpdate) {
            toThrow = updateStackTrace(env, throwableHandle, getStackTrace());
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
        if (hsStack.length == 0 || containsJNIHostCall(hsStack)) {
            mergedStack = hsStack;
            res = false;
        } else {
            StackTraceElement[] nativeStack = getStackTrace();
            boolean originatedInHotSpot = !hsStack[0].isNativeMethod();
            mergedStack = mergeStackTraces(hsStack, nativeStack, 0, getIndexOfPropagateJNIExceptionFrame(nativeStack), originatedInHotSpot);
            res = true;
        }
        setStackTrace(mergedStack);
        return res;
    }

    /**
     * If there is a pending JNI exception, this method wraps it in a {@link JNIExceptionWrapper},
     * clears the pending exception and throws the {@link JNIExceptionWrapper} wrapper. The
     * {@link JNIExceptionWrapper} message is composed of the JNI exception class name and the JNI
     * exception message. For exception filtering or custom handling of JNI exceptions see
     * {@link #wrapAndThrowPendingJNIException(JNIEnv, ExceptionHandler)}.
     */
    public static void wrapAndThrowPendingJNIException(JNIEnv env) {
        wrapAndThrowPendingJNIException(env, ExceptionHandler.DEFAULT);
    }

    /**
     * If there is a pending JNI exception, this method wraps it in a {@link JNIExceptionWrapper},
     * clears the pending exception and throws the {@link JNIExceptionWrapper} wrapper. The
     * {@link JNIExceptionWrapper} message is composed of the JNI exception class name and the JNI
     * exception message.
     *
     * @see ExceptionHandler
     *
     */
    public static void wrapAndThrowPendingJNIException(JNIEnv env, ExceptionHandler exceptionHandler) {
        Objects.requireNonNull(exceptionHandler, "ExceptionHandler must be non null.");
        if (ExceptionCheck(env)) {
            JThrowable exception = ExceptionOccurred(env);
            if (JNIUtil.tracingAt(2) && exception.isNonNull()) {
                ExceptionDescribe(env);
            }
            ExceptionClear(env);
            exceptionHandler.handleException(new ExceptionHandlerContext(env, exception));
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
    public static void throwInHotSpot(JNIEnv env, Throwable original) {
        try {
            JNIUtil.trace(2, original);
            if (original.getClass() == JNIExceptionWrapper.class) {
                ((JNIExceptionWrapper) original).throwInHotSpot(env);
            } else {
                Throw(env, createHSException(env, original));
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
     * Crates an exception in HotSpot representing the given {@code original} exception.
     *
     * @param env the {@link JNIEnv}
     * @param original an exception to be created in HotSpot
     */
    public static JThrowable createHSException(JNIEnv env, Throwable original) {
        JThrowable hsThrowable;
        if (original instanceof JNIExceptionWrapper) {
            JNIExceptionWrapper jniExceptionWrapper = (JNIExceptionWrapper) original;
            hsThrowable = jniExceptionWrapper.throwableHandle;
            if (jniExceptionWrapper.throwableRequiresStackTraceUpdate) {
                hsThrowable = updateStackTrace(env, hsThrowable, jniExceptionWrapper.getStackTrace());
            }
        } else {
            String message = formatExceptionMessage(original.getClass().getName(), original.getMessage());
            JString hsMessage = createHSString(env, message);
            hsThrowable = callCreateException(env, hsMessage);
            StackTraceElement[] nativeStack = original.getStackTrace();
            if (nativeStack.length != 0) {
                // Update stack trace only for exceptions which have stack trace.
                // For exceptions which override fillInStackTrace merging stack traces only adds
                // useless JNI calls.
                StackTraceElement[] hsStack = getJNIExceptionStackTrace(env, hsThrowable);
                StackTraceElement[] mergedStack = mergeStackTraces(hsStack, nativeStack, 1,
                                getIndexOfPropagateJNIExceptionFrame(nativeStack), false);
                hsThrowable = updateStackTrace(env, hsThrowable, mergedStack);
            }
        }
        return hsThrowable;
    }

    /**
     * Creates a new {@link JNIExceptionWrapper} for given HotSpot exception.
     */
    public static JNIExceptionWrapper forHSException(JNIEnv env, JThrowable jThrowable) {
        if (jThrowable.isNull()) {
            throw new NullPointerException("jThrowable must be non null");
        }
        return new JNIExceptionWrapper(env, jThrowable);
    }

    /**
     * Context for {@link ExceptionHandler}.
     */
    public static final class ExceptionHandlerContext {

        private final JNIEnv env;
        private final JThrowable throwable;

        ExceptionHandlerContext(JNIEnv env, JThrowable throwable) {
            this.env = env;
            this.throwable = throwable;
        }

        /**
         * Returns current thread JNIEnv.
         */
        public JNIEnv getEnv() {
            return env;
        }

        /**
         * Returns pending JNI exception.
         */
        public JThrowable getThrowable() {
            return throwable;
        }

        /**
         * Returns pending JNI exception message.
         */
        public String getThrowableMessage() {
            return getMessage(env, throwable);
        }

        /**
         * Returns pending JNI exception class name.
         */
        public String getThrowableClassName() {
            return getClassName(env, throwable);
        }

        /**
         * Returns merged JNI exception and native stack trace.
         */
        public StackTraceElement[] getMergedStackTrace() {
            StackTraceElement[] hsStack = getJNIExceptionStackTrace(env, throwable);
            if (hsStack.length == 0 || containsJNIHostCall(hsStack)) {
                return hsStack;
            } else {
                StackTraceElement[] nativeStack = Thread.currentThread().getStackTrace();
                boolean originatedInHotSpot = !hsStack[0].isNativeMethod();
                return mergeStackTraces(hsStack, nativeStack, 0, getIndexOfPropagateJNIExceptionFrame(nativeStack), originatedInHotSpot);
            }
        }

        /**
         * Throws {@link JNIExceptionWrapper} for the pending JNI exception.
         */
        public void throwJNIExceptionWrapper() {
            throw new JNIExceptionWrapper(env, throwable);
        }
    }

    public interface ExceptionHandler {

        /**
         * Default handler throwing {@link JNIExceptionWrapper} for the pending JNI exception.
         */
        ExceptionHandler DEFAULT = new ExceptionHandler() {
            @Override
            public void handleException(ExceptionHandlerContext context) {
                context.throwJNIExceptionWrapper();
            }
        };

        /**
         * Creates an exception handler suppressing {@code allowedExceptions}. Other JNI exceptions
         * are rethrown as {@link JNIExceptionWrapper}.
         */
        @SafeVarargs
        static ExceptionHandler allowExceptions(Class<? extends Throwable>... allowedExceptions) {
            return new ExceptionHandler() {
                @Override
                public void handleException(ExceptionHandlerContext context) {
                    JThrowable throwable = context.getThrowable();
                    JNIEnv env = context.getEnv();
                    JClass throwableClass = GetObjectClass(env, throwable);
                    boolean allowed = false;
                    for (Class<? extends Throwable> allowedException : allowedExceptions) {
                        JClass allowedExceptionClass = findClass(env, getBinaryName(allowedException.getName()));
                        if (allowedExceptionClass.isNonNull() && IsSameObject(env, throwableClass, allowedExceptionClass)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        context.throwJNIExceptionWrapper();
                    }
                }
            };
        }

        /**
         * Handles the JNI pending exception.
         */
        void handleException(ExceptionHandlerContext context);
    }

    public static StackTraceElement[] mergeStackTraces(
                    StackTraceElement[] hotSpotStackTrace,
                    StackTraceElement[] nativeStackTrace,
                    boolean originatedInHotSpot) {
        if (originatedInHotSpot) {
            if (containsJNIHostCall(hotSpotStackTrace)) {
                // Already merged
                return hotSpotStackTrace;
            }
        } else {
            if (containsJNIHostCall(nativeStackTrace)) {
                // Already merged
                return nativeStackTrace;
            }
        }
        return mergeStackTraces(hotSpotStackTrace, nativeStackTrace, originatedInHotSpot ? 0 : getIndexOfTransitionToNativeFrame(hotSpotStackTrace),
                        getIndexOfPropagateJNIExceptionFrame(nativeStackTrace), originatedInHotSpot);
    }

    /**
     * Merges {@code hotSpotStackTrace} with {@code nativeStackTrace}.
     *
     * @param hotSpotStackTrace
     * @param nativeStackTrace
     * @param hotSpotStackStartIndex
     * @param nativeStackStartIndex
     * @param originatedInHotSpot
     */
    private static StackTraceElement[] mergeStackTraces(
                    StackTraceElement[] hotSpotStackTrace,
                    StackTraceElement[] nativeStackTrace,
                    int hotSpotStackStartIndex,
                    int nativeStackStartIndex,
                    boolean originatedInHotSpot) {
        int targetIndex = 0;
        StackTraceElement[] merged = new StackTraceElement[hotSpotStackTrace.length - hotSpotStackStartIndex + nativeStackTrace.length - nativeStackStartIndex];
        boolean startingHotSpotFrame = true;
        boolean startingnativeFrame = true;
        boolean useHotSpotStack = originatedInHotSpot;
        int hotSpotStackIndex = hotSpotStackStartIndex;
        int nativeStackIndex = nativeStackStartIndex;
        while (hotSpotStackIndex < hotSpotStackTrace.length || nativeStackIndex < nativeStackTrace.length) {
            if (useHotSpotStack) {
                while (hotSpotStackIndex < hotSpotStackTrace.length && (startingHotSpotFrame || !hotSpotStackTrace[hotSpotStackIndex].isNativeMethod())) {
                    startingHotSpotFrame = false;
                    merged[targetIndex++] = hotSpotStackTrace[hotSpotStackIndex++];
                }
                startingHotSpotFrame = true;
            } else {
                useHotSpotStack = true;
            }
            while (nativeStackIndex < nativeStackTrace.length && (startingnativeFrame || !isJNIHostCall(nativeStackTrace[nativeStackIndex]))) {
                startingnativeFrame = false;
                merged[targetIndex++] = nativeStackTrace[nativeStackIndex++];
            }
            startingnativeFrame = true;
        }
        return merged;
    }

    /**
     * Gets the stack trace from a JNI exception.
     *
     * @param env the {@link JNIEnv}
     * @param throwableHandle the JNI exception to get the stack trace from.
     * @return the stack trace
     */
    private static StackTraceElement[] getJNIExceptionStackTrace(JNIEnv env, JObject throwableHandle) {
        byte[] serializedStackTrace = JNIUtil.createArray(env, (JByteArray) callGetStackTrace(env, throwableHandle));
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(serializedStackTrace))) {
            int len = in.readInt();
            StackTraceElement[] res = new StackTraceElement[len];
            for (int i = 0; i < len; i++) {
                String className = in.readUTF();
                String methodName = in.readUTF();
                String fileName = in.readUTF();
                fileName = fileName.isEmpty() ? null : fileName;
                int lineNumber = in.readInt();
                res[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
            }
            return res;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Determines if {@code stackTrace} contains a frame denoting a call into HotSpot.
     */
    private static boolean containsJNIHostCall(StackTraceElement[] stackTrace) {
        for (StackTraceElement e : stackTrace) {
            if (isJNIHostCall(e)) {
                return true;
            }
        }
        return false;
    }

    private static JThrowable updateStackTrace(JNIEnv env, JThrowable throwableHandle, StackTraceElement[] mergedStackTrace) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bout)) {
            out.writeInt(mergedStackTrace.length);
            for (int i = 0; i < mergedStackTrace.length; i++) {
                StackTraceElement stackTraceElement = mergedStackTrace[i];
                out.writeUTF(stackTraceElement.getClassName());
                out.writeUTF(stackTraceElement.getMethodName());
                String fileName = stackTraceElement.getFileName();
                out.writeUTF(fileName == null ? "" : fileName);
                out.writeInt(stackTraceElement.getLineNumber());
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return callUpdateStackTrace(env, throwableHandle, JNIUtil.createHSArray(env, bout.toByteArray()));
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
     * {@link #wrapAndThrowPendingJNIException(JNI.JNIEnv)} or
     * {@link #wrapAndThrowPendingJNIException(JNI.JNIEnv, ExceptionHandler)} in {@code stackTrace}.
     *
     * @returns {@code 0} if no caller found
     */
    private static int getIndexOfPropagateJNIExceptionFrame(StackTraceElement[] stackTrace) {
        int state = 0;
        for (int i = 0; i < stackTrace.length; i++) {
            if (isStackFrame(stackTrace[i], JNIExceptionWrapper.class, "wrapAndThrowPendingJNIException")) {
                state = 1;
            } else if (state == 1) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Gets the index of the first frame denoting the native method call.
     *
     * @returns {@code 0} if no caller found
     */
    private static int getIndexOfTransitionToNativeFrame(StackTraceElement[] stackTrace) {
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i].isNativeMethod()) {
                return i;
            }
        }
        return 0;
    }

    private static boolean isStackFrame(StackTraceElement stackTraceElement, Class<?> clazz, String methodName) {
        return clazz.getName().equals(stackTraceElement.getClassName()) && methodName.equals(stackTraceElement.getMethodName());
    }

    // JNI calls
    private static JThrowable callCreateException(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return JNICalls.getDefault().callStaticJObject(env, getEntryPoints(env), CreateException.resolve(env), args);
    }

    private static <T extends JObject> T callUpdateStackTrace(JNIEnv env, JObject p0, JByteArray p1) {
        JNI.JValue args = StackValue.get(2, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        args.addressOf(1).setJObject(p1);
        return JNICalls.getDefault().callStaticJObject(env, getEntryPoints(env), UpdateStackTrace.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    private static <T extends JObject> T callGetThrowableMessage(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return JNICalls.getDefault().callStaticJObject(env, getEntryPoints(env), GetThrowableMessage.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    static <T extends JObject> T callGetClassName(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return JNICalls.getDefault().callStaticJObject(env, getEntryPoints(env), GetClassName.resolve(env), args);
    }

    @SuppressWarnings("unchecked")
    private static <T extends JObject> T callGetStackTrace(JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return JNICalls.getDefault().callStaticJObject(env, getEntryPoints(env), GetStackTrace.resolve(env), args);
    }

    private static final class JNIMethodResolver implements JNIMethod {

        private final String methodName;
        private final String methodSignature;
        private volatile JNI.JMethodID methodId;

        private JNIMethodResolver(String methodName, String methodSignature) {
            this.methodName = methodName;
            this.methodSignature = methodSignature;
        }

        JNIMethodResolver resolve(JNIEnv jniEnv) {
            JNI.JMethodID res = methodId;
            if (res.isNull()) {
                JNI.JClass entryPointClass = getEntryPoints(jniEnv);
                try (CTypeConversion.CCharPointerHolder name = toCString(methodName); CTypeConversion.CCharPointerHolder sig = toCString(methodSignature)) {
                    res = GetStaticMethodID(jniEnv, entryPointClass, name.get(), sig.get());
                    if (res.isNull()) {
                        throw new InternalError("No such method: " + methodName);
                    }
                    methodId = res;
                }
            }
            return this;
        }

        @Override
        public JNI.JMethodID getJMethodID() {
            return methodId;
        }

        @Override
        public String getDisplayName() {
            return methodName;
        }

        static JNIMethodResolver create(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
            return new JNIMethodResolver(methodName, encodeMethodSignature(returnType, parameterTypes));
        }
    }

    private static JNI.JClass getEntryPoints(JNIEnv env) {
        JNI.JClass res = entryPointsClass;
        if (res.isNull()) {
            String binaryName = getBinaryName(HS_ENTRYPOINTS_CLASS);
            JNI.JClass entryPoints = findClass(env, binaryName);
            if (entryPoints.isNull()) {
                // Clear the exception and try to load the entry points class using JVMCI
                // classloader.
                ExceptionClear(env);
                JObject classLoader = getJVMCIClassLoader(env);
                if (classLoader.isNonNull()) {
                    entryPoints = findClass(env, classLoader, binaryName);
                }
            }
            if (entryPoints.isNull()) {
                // Here we cannot use JNIExceptionWrapper.
                // We failed to load HostSpot entry points for it.
                ExceptionClear(env);
                throw new InternalError("Failed to load " + HS_ENTRYPOINTS_CLASS);
            }
            synchronized (JNIExceptionWrapper.class) {
                res = entryPointsClass;
                if (res.isNull()) {
                    res = NewGlobalRef(env, entryPoints, "Class<" + HS_ENTRYPOINTS_CLASS + ">");
                    entryPointsClass = res;
                }
            }
        }
        return res;
    }

    /**
     * Determines if {@code frame} is for a method denoting a call into HotSpot.
     */
    private static boolean isJNIHostCall(StackTraceElement frame) {
        return JNI_TRANSITION_CLASS.equals(frame.getClassName()) && JNI_TRANSITION_METHODS.contains(frame.getMethodName());
    }

    /**
     * Names of the methods in the {@link JNICalls} class annotated by the {@link JNICall}.
     */
    private static final Set<String> JNI_TRANSITION_METHODS;
    private static final String JNI_TRANSITION_CLASS;
    static {
        Map<String, Method> entryPoints = new HashMap<>();
        Map<String, Method> others = new HashMap<>();
        for (Method m : JNICalls.class.getDeclaredMethods()) {
            if (m.getAnnotation(JNICall.class) != null) {
                Method existing = entryPoints.put(m.getName(), m);
                if (existing != null) {
                    throw new InternalError("Method annotated by " + JNICall.class.getSimpleName() +
                                    " must have unique name: " + m + " and " + existing);
                }
            } else {
                others.put(m.getName(), m);
            }
        }
        for (Map.Entry<String, Method> e : entryPoints.entrySet()) {
            Method existing = others.get(e.getKey());
            if (existing != null) {
                throw new InternalError("Method annotated by " + JNICall.class.getSimpleName() +
                                " must have unique name: " + e.getValue() + " and " + existing);
            }
        }
        JNI_TRANSITION_CLASS = JNICalls.class.getName();
        JNI_TRANSITION_METHODS = Set.copyOf(entryPoints.keySet());
    }
}
