/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent;

import static com.oracle.svm.agent.Support.check;
import static com.oracle.svm.agent.Support.checkJni;
import static com.oracle.svm.agent.Support.checkNoException;
import static com.oracle.svm.agent.Support.clearException;
import static com.oracle.svm.agent.Support.fromCString;
import static com.oracle.svm.agent.Support.fromJniString;
import static com.oracle.svm.agent.Support.getCallerClass;
import static com.oracle.svm.agent.Support.getCallerMethod;
import static com.oracle.svm.agent.Support.getClassNameOr;
import static com.oracle.svm.agent.Support.getClassNameOrNull;
import static com.oracle.svm.agent.Support.getMethodDeclaringClass;
import static com.oracle.svm.agent.Support.getObjectArgument;
import static com.oracle.svm.agent.Support.handles;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.jvmtiEnv;
import static com.oracle.svm.agent.Support.jvmtiFunctions;
import static com.oracle.svm.agent.Support.testException;
import static com.oracle.svm.agent.Support.toCString;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_BREAKPOINT;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_NATIVE_METHOD_BIND;
import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.agent.Support.WordSupplier;
import com.oracle.svm.agent.jvmti.JvmtiCapabilities;
import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.agent.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.agent.jvmti.JvmtiEventMode;
import com.oracle.svm.agent.restrict.ProxyAccessVerifier;
import com.oracle.svm.agent.restrict.ReflectAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceAccessVerifier;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallBooleanMethod0FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallLongMethod1FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallLongMethod2FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod0FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod1FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod2FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod3FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeMethod;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.MetaUtil;

/*
 * NOTE: With most of our breakpoints, we recursively call the intercepted method ourselves to
 * inspect its return value and determine whether it provides a valid result. This permits us to
 * identify probing.
 *
 * Many of the methods are caller-sensitive, so when we call them from a breakpoint, they
 * observe a different caller class and therefore can behave differently. Short of using
 * bytecode instrumentation to read the return value, there seems to be no strictly better
 * approach (and instrumenting java.lang.Class and friends might be tricky, too). It would be
 * possible to set breakpoints at return bytecodes instead, but then there is no way to retrieve
 * the return value from the operand stack.
 */

final class BreakpointInterceptor {
    private static TraceWriter traceWriter;

    private static ReflectAccessVerifier accessVerifier;
    private static ProxyAccessVerifier proxyVerifier;
    private static ResourceAccessVerifier resourceVerifier;

    private static Map<Long, Breakpoint> installedBreakpoints;

    /**
     * A map from {@link JNIMethodId} to entry point addresses for bound Java {@code native}
     * methods, NOT considering our intercepting functions, i.e., these are the original entry
     * points for a native method from symbol resolution or {@code registerNatives}.
     */
    private static Map<Long, Long> boundNativeMethods = Collections.synchronizedMap(new HashMap<>());

    /**
     * Map from {@link JNIMethodId} to breakpoints in {@code native} methods. Not all may be
     * installed if the native methods haven't been {@linkplain #bindNativeBreakpoint bound}.
     */
    private static Map<Long, NativeBreakpoint> nativeBreakpoints;

    private static final ThreadLocal<Boolean> recursive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static void traceBreakpoint(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, String function, Object result, Object... args) {
        if (traceWriter != null) {
            traceWriter.traceCall("reflect",
                            function,
                            getClassNameOr(env, clazz, null, TraceWriter.UNKNOWN_VALUE),
                            getClassNameOr(env, declaringClass, null, TraceWriter.UNKNOWN_VALUE),
                            getClassNameOr(env, callerClass, null, TraceWriter.UNKNOWN_VALUE),
                            result,
                            args);
            guarantee(!testException(env));
        }
    }

    private static boolean forName(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle name = getObjectArgument(0);
        String className = fromJniString(jni, name);
        boolean allowed = (accessVerifier == null || accessVerifier.verifyForName(jni, callerClass, className));
        Object result = false;
        if (allowed) {
            boolean initializeValid = true;
            boolean classLoaderValid = true;
            CIntPointer initializePtr = StackValue.get(CIntPointer.class);
            WordPointer classLoaderPtr = StackValue.get(WordPointer.class);
            if (bp.method == handles().javaLangClassForName3) {
                initializeValid = (jvmtiFunctions().GetLocalInt().invoke(jvmtiEnv(), nullHandle(), 0, 1, initializePtr) == JvmtiError.JVMTI_ERROR_NONE);
                classLoaderValid = (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, 2, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
            } else {
                initializePtr.write(1);
                classLoaderPtr.write(nullHandle());
                if (callerClass.notEqual(nullHandle())) {
                    /*
                     * NOTE: we use our direct caller class, but this class might be skipped over by
                     * Class.forName(nameOnly) in its security stackwalk for @CallerSensitive,
                     * leading to different behavior of our call and the original call.
                     */
                    classLoaderValid = (jvmtiFunctions().GetClassLoader().invoke(jvmtiEnv(), callerClass, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
                }
            }
            result = TraceWriter.UNKNOWN_VALUE;
            if (initializeValid && classLoaderValid) {
                result = nullHandle().notEqual(jniFunctions().<CallObjectMethod3FunctionPointer> getCallStaticObjectMethod().invoke(
                                jni, bp.clazz, handles().javaLangClassForName3, name, WordFactory.signed(initializePtr.read()), classLoaderPtr.read()));
                if (clearException(jni)) {
                    result = false;
                }
            }
        }
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result, className);
        if (!allowed) {
            try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to class: " + className)) {
                jniFunctions().getThrowNew().invoke(jni, handles().javaLangClassNotFoundException, message.get());
            }
        }
        return allowed;
    }

    private static boolean getFields(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetFields(jni, callerClass, bp, false);
    }

    private static boolean getDeclaredFields(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetFields(jni, callerClass, bp, true);
    }

    private static boolean handleGetFields(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle returnResult = nullHandle();
        if (accessVerifier != null) {
            returnResult = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method);
            if (clearException(jni)) {
                returnResult = nullHandle();
            }
            if (returnResult.notEqual(nullHandle())) {
                returnResult = accessVerifier.filterGetFields(jni, self, returnResult, declaredOnly, callerClass);
            }
        }
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, null);
        if (accessVerifier != null && returnResult.notEqual(nullHandle())) {
            jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), returnResult);
        }
        return true;
    }

    private static boolean getMethods(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethods(jni, callerClass, bp, false, () -> handles().getJavaLangReflectMethod(jni));
    }

    private static boolean getDeclaredMethods(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethods(jni, callerClass, bp, true, () -> handles().getJavaLangReflectMethod(jni));
    }

    private static boolean getConstructors(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethods(jni, callerClass, bp, true, () -> handles().getJavaLangReflectConstructor(jni));
    }

    private static boolean getDeclaredConstructors(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethods(jni, callerClass, bp, true, () -> handles().getJavaLangReflectConstructor(jni));
    }

    private static boolean handleGetMethods(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean declaredOnly, WordSupplier<JNIObjectHandle> elementClass) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle returnResult = nullHandle();
        if (accessVerifier != null) {
            returnResult = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method);
            if (clearException(jni)) {
                returnResult = nullHandle();
            }
            if (returnResult.notEqual(nullHandle())) {
                returnResult = accessVerifier.filterGetMethods(jni, self, returnResult, elementClass, declaredOnly, callerClass);
            }
        }
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, null);
        if (accessVerifier != null && returnResult.notEqual(nullHandle())) {
            jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), returnResult);
        }
        return true;
    }

    private static boolean getField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetField(jni, callerClass, bp, false);
    }

    private static boolean getDeclaredField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetField(jni, callerClass, bp, true);
    }

    private static boolean handleGetField(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        JNIObjectHandle result = jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, name);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (!declaredOnly && result.notEqual(nullHandle())) {
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, result, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyGetField(jni, self, name, result, (declaredOnly ? self : declaring), callerClass);
        traceBreakpoint(jni, self, declaring, callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()), fromJniString(jni, name));
        if (!allowed) {
            try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                            getClassNameOr(jni, self, "(null)", "(?)") + "." + fromJniString(jni, name))) {
                jniFunctions().getThrowNew().invoke(jni, handles().javaLangNoSuchFieldException, message.get());
            }
        }
        return allowed;
    }

    private static final CEntryPointLiteral<ObjectFieldOffsetFunctionPointer> nativeObjectFieldOffsetLiteral = CEntryPointLiteral.create(
                    BreakpointInterceptor.class, "nativeObjectFieldOffset", JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class);

    private interface ObjectFieldOffsetFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        long invoke(JNIEnvironment jni, JNIObjectHandle self, JNIObjectHandle field);
    }

    /** Native breakpoint for the JDK 8 {@code sun.misc.Unsafe.objectFieldOffset} native method. */
    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    static long nativeObjectFieldOffset(JNIEnvironment jni, JNIObjectHandle self, JNIObjectHandle field) {
        JNIMethodId currentMethod = getCallerMethod(0);
        JNIObjectHandle callerClass = getCallerClass(1);
        ObjectFieldOffsetFunctionPointer original = WordFactory.pointer(boundNativeMethods.get(currentMethod.rawValue()));
        long result = original.invoke(jni, self, field);
        boolean validResult = !clearException(jni);
        JNIObjectHandle name = nullHandle();
        JNIObjectHandle declaring = nullHandle();
        if (field.notEqual(nullHandle())) {
            name = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, field, handles().javaLangReflectMemberGetName);
            if (clearException(jni)) {
                name = nullHandle();
            }
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, field, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        if (!verifyAndTraceObjectFieldOffset(jni, validResult, name, declaring, currentMethod, callerClass)) {
            return Long.MIN_VALUE; // (pending exception)
        }
        if (!validResult) { // invoke again for exception
            return original.invoke(jni, self, field);
        }
        return result;
    }

    private static boolean objectFieldOffset(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle field = getObjectArgument(1);
        jniFunctions().<CallLongMethod1FunctionPointer> getCallLongMethod().invoke(jni, self, bp.method, field);
        boolean validResult = !clearException(jni);
        JNIObjectHandle name = nullHandle();
        JNIObjectHandle declaring = nullHandle();
        if (field.notEqual(nullHandle())) {
            name = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, field, handles().javaLangReflectMemberGetName);
            if (clearException(jni)) {
                name = nullHandle();
            }
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, field, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        return verifyAndTraceObjectFieldOffset(jni, validResult, name, declaring, bp.method, callerClass);
    }

    private static boolean verifyAndTraceObjectFieldOffset(JNIEnvironment jni, boolean validResult, JNIObjectHandle name,
                    JNIObjectHandle declaring, JNIMethodId currentMethod, JNIObjectHandle callerClass) {

        JNIObjectHandle clazz = getMethodDeclaringClass(currentMethod);
        boolean allowed = !validResult || accessVerifier == null || accessVerifier.verifyObjectFieldOffset(jni, name, declaring, callerClass);
        traceBreakpoint(jni, clazz, declaring, callerClass, "objectFieldOffset", allowed && validResult, fromJniString(jni, name));
        if (!allowed) {
            try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit unsafe access to field: " +
                            getClassNameOr(jni, declaring, "(null)", "(?)") + "." + fromJniString(jni, name))) {
                jniFunctions().getThrowNew().invoke(jni, handles().javaLangRuntimeException, message.get());
            }
            return false;
        }
        return true;
    }

    private static boolean objectFieldOffsetByName(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle declaring = getObjectArgument(1);
        JNIObjectHandle name = getObjectArgument(2);
        jniFunctions().<CallLongMethod2FunctionPointer> getCallLongMethod().invoke(jni, self, bp.method, declaring, name);
        boolean validResult = !clearException(jni);
        return verifyAndTraceObjectFieldOffset(jni, validResult, name, declaring, bp.method, callerClass);
    }

    private static boolean getConstructor(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle paramTypesHandle = getObjectArgument(1);
        JNIObjectHandle result = jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, paramTypesHandle);
        if (clearException(jni)) {
            result = nullHandle();
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        Supplier<String> signature = () -> asInternalSignature(paramTypes);
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyGetConstructor(jni, self, signature, result, callerClass);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, allowed && nullHandle().notEqual(result), paramTypes);
        if (!allowed) {
            throwNoSuchMethodException(jni, self, ConfigurationMethod.CONSTRUCTOR_NAME, signature.get());
        }
        return allowed;
    }

    private static boolean getMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethod(jni, callerClass, bp, false);
    }

    private static boolean getDeclaredMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetMethod(jni, callerClass, bp, true);
    }

    private static boolean handleGetMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle nameHandle = getObjectArgument(1);
        JNIObjectHandle paramTypesHandle = getObjectArgument(2);
        JNIObjectHandle result = jniFunctions().<CallObjectMethod2FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, nameHandle, paramTypesHandle);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (!declaredOnly && result.notEqual(nullHandle())) {
            declaring = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, result, handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        String name = fromJniString(jni, nameHandle);
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        Supplier<String> signature = () -> asInternalSignature(paramTypes);
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyGetMethod(jni, self, name, signature, result, (declaredOnly ? self : declaring), callerClass);
        traceBreakpoint(jni, self, declaring, callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()), name, paramTypes);
        if (!allowed) {
            throwNoSuchMethodException(jni, self, name, signature.get());
        }
        return allowed;
    }

    private static boolean getEnclosingMethod(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        Object result = TraceWriter.EXPLICIT_NULL;
        JNIObjectHandle enclosing = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method);
        JNIObjectHandle holder = nullHandle();
        String name = null;
        String signature = null;
        if (!clearException(jni) && enclosing.notEqual(nullHandle())) {
            result = TraceWriter.UNKNOWN_VALUE;
            JNIMethodId enclosingID = jniFunctions().getFromReflectedMethod().invoke(jni, enclosing);
            if (!clearException(jni) && enclosingID.isNonNull()) {
                WordPointer holderPtr = StackValue.get(WordPointer.class);
                if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), enclosingID, holderPtr) == JvmtiError.JVMTI_ERROR_NONE) {
                    holder = holderPtr.read();
                    String holderName = getClassNameOrNull(jni, holderPtr.read());
                    if (holderName != null) {
                        CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
                        CCharPointerPointer signaturePtr = StackValue.get(CCharPointerPointer.class);
                        if (jvmtiFunctions().GetMethodName().invoke(jvmtiEnv(), enclosingID, namePtr, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
                            name = fromCString(namePtr.read());
                            signature = fromCString(signaturePtr.read());
                            result = holderName + "." + name + signature;
                            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
                            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
                        }
                    }
                }
            }
        }
        boolean allowed = enclosing.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyGetEnclosingMethod(jni, holder, name, signature, enclosing, callerClass);
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, allowed ? result : false);
        if (!allowed) {
            jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), nullHandle());
        }
        return allowed;
    }

    private static boolean newInstance(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIMethodId result;
        String name = "<init>";
        String signature = "()V";
        try (CCharPointerHolder ctorName = toCString(name); CCharPointerHolder ctorSignature = toCString(signature)) {
            result = jniFunctions().getGetMethodID().invoke(jni, self, ctorName.get(), ctorSignature.get());
        }
        if (clearException(jni)) {
            result = nullHandle();
        }
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyNewInstance(jni, self, name, signature, result, callerClass);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()));
        if (!allowed) {
            throwNoSuchMethodException(jni, self, name, signature);
        }
        return allowed;
    }

    private static void throwNoSuchMethodException(JNIEnvironment jni, JNIObjectHandle clazz, String name, String signature) {
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(jni, clazz, "(null)", "(?)") + "." + name + signature)) {

            jniFunctions().getThrowNew().invoke(jni, handles().javaLangNoSuchMethodException, message.get());
        }
    }

    private static boolean getResource(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetResources(jni, callerClass, bp, false);
    }

    private static boolean getResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetResources(jni, callerClass, bp, true);
    }

    private static boolean handleGetResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        boolean result = false;
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetResources(jni, name, callerClass));
        if (allowed) {
            JNIObjectHandle returnValue = jniFunctions().<CallObjectMethod1FunctionPointer> getCallObjectMethod().invoke(jni, self, bp.method, name);
            result = returnValue.notEqual(nullHandle());
            if (clearException(jni)) {
                result = false;
            }
            if (result && returnsEnumeration) {
                result = hasEnumerationElements(jni, returnValue);
            }
        }
        JNIObjectHandle selfClazz = nullHandle(); // self is java.lang.ClassLoader, get its class
        if (self.notEqual(nullHandle())) {
            selfClazz = jniFunctions().getGetObjectClass().invoke(jni, self);
            if (clearException(jni)) {
                selfClazz = nullHandle();
            }
        }
        traceBreakpoint(jni, selfClazz, nullHandle(), callerClass, bp.specification.methodName, result, fromJniString(jni, name));
        if (!allowed) {
            forceGetResourceReturn(jni, returnsEnumeration);
        }
        return allowed;
    }

    private static boolean hasEnumerationElements(JNIEnvironment jni, JNIObjectHandle obj) {
        boolean hasElements = jniFunctions().<CallBooleanMethod0FunctionPointer> getCallBooleanMethod().invoke(jni, obj, handles().javaUtilEnumerationHasMoreElements);
        if (clearException(jni)) {
            hasElements = false;
        }
        return hasElements;
    }

    private static void forceGetResourceReturn(JNIEnvironment env, boolean returnsEnumeration) {
        JNIObjectHandle newResult = nullHandle();
        if (returnsEnumeration) {
            JNIObjectHandle javaUtilCollections = handles().getJavaUtilCollections(env);
            JNIMethodId emptyEnumeration = handles().getJavaUtilCollectionsEmptyEnumeration(env);
            if (javaUtilCollections.notEqual(nullHandle()) && emptyEnumeration.isNonNull()) {
                newResult = jniFunctions().<CallObjectMethod0FunctionPointer> getCallObjectMethod().invoke(env, javaUtilCollections, emptyEnumeration);
                if (clearException(env)) {
                    newResult = nullHandle();
                }
            }
        }
        jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), newResult);
    }

    private static boolean getSystemResource(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetSystemResources(jni, callerClass, bp, false);
    }

    private static boolean getSystemResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        return handleGetSystemResources(jni, callerClass, bp, true);
    }

    private static boolean handleGetSystemResources(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle name = getObjectArgument(0);
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetSystemResources(jni, name, callerClass));
        boolean result = false;
        if (allowed) {
            JNIObjectHandle returnValue = jniFunctions().<CallObjectMethod1FunctionPointer> getCallStaticObjectMethod().invoke(jni, bp.clazz, bp.method, name);
            result = returnValue.notEqual(nullHandle());
            if (clearException(jni)) {
                result = false;
            }
            if (result && returnsEnumeration) {
                result = hasEnumerationElements(jni, returnValue);
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, fromJniString(jni, name));
        if (!allowed) {
            forceGetResourceReturn(jni, returnsEnumeration);
        }
        return allowed;
    }

    private static boolean newProxyInstance(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean allowed = proxyVerifier == null || proxyVerifier.verifyNewProxyInstance(jni, ifaceNames, callerClass);
        boolean result = false;
        if (allowed) {
            JNIObjectHandle invokeHandler = getObjectArgument(2);
            result = nullHandle().notEqual(jniFunctions().<CallObjectMethod3FunctionPointer> getCallStaticObjectMethod()
                            .invoke(jni, bp.clazz, bp.method, classLoader, ifaces, invokeHandler));
            if (clearException(jni)) {
                result = false;
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, TraceWriter.UNKNOWN_VALUE, ifaceNames, TraceWriter.UNKNOWN_VALUE);
        if (!allowed) {
            throwDeniedProxyException(jni, ifaceNames);
        }
        return allowed;
    }

    private static boolean getProxyClass(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp) {
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean allowed = proxyVerifier == null || proxyVerifier.verifyGetProxyClass(jni, ifaceNames, callerClass);
        boolean result = false;
        if (allowed) {
            result = nullHandle().notEqual(jniFunctions().<CallObjectMethod2FunctionPointer> getCallStaticObjectMethod()
                            .invoke(jni, bp.clazz, bp.method, classLoader, ifaces));
            if (clearException(jni)) {
                result = false;
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, TraceWriter.UNKNOWN_VALUE, ifaceNames);
        if (!allowed) {
            throwDeniedProxyException(jni, ifaceNames);
        }
        return allowed;
    }

    private static void throwDeniedProxyException(JNIEnvironment jni, Object ifaceNames) {
        String interfaceString = "(unknown)";
        if (ifaceNames instanceof Object[]) {
            interfaceString = Arrays.toString((Object[]) ifaceNames);
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit proxy class for interfaces: " + interfaceString)) {
            jniFunctions().getThrowNew().invoke(jni, handles().javaLangSecurityException, message.get());
        }
    }

    private static Object getClassArrayNames(JNIEnvironment jni, JNIObjectHandle classArray) {
        Object classNames = TraceWriter.EXPLICIT_NULL;
        if (classArray.notEqual(nullHandle())) {
            classNames = TraceWriter.UNKNOWN_VALUE;
            int length = jniFunctions().getGetArrayLength().invoke(jni, classArray);
            if (!clearException(jni) && length >= 0) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    JNIObjectHandle clazz = jniFunctions().getGetObjectArrayElement().invoke(jni, classArray, i);
                    if (!clearException(jni)) {
                        list.add(getClassNameOr(jni, clazz, TraceWriter.EXPLICIT_NULL, TraceWriter.UNKNOWN_VALUE));
                    } else {
                        list.add(TraceWriter.UNKNOWN_VALUE);
                    }
                }
                classNames = list.toArray(new String[0]);
            }
        }
        return classNames;
    }

    private static String asInternalSignature(Object paramTypesArray) {
        if (paramTypesArray instanceof Object[]) {
            StringBuilder sb = new StringBuilder("(");
            for (Object paramType : (Object[]) paramTypesArray) {
                sb.append(MetaUtil.toInternalName(paramType.toString()));
            }
            return sb.append(')').toString();
        }
        return null;
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static void onBreakpoint(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIMethodId method, @SuppressWarnings("unused") long location) {

        if (recursive.get()) {
            return;
        }
        recursive.set(true);
        try {
            JNIObjectHandle callerClass = getCallerClass(1);
            Breakpoint bp = installedBreakpoints.get(method.rawValue());
            if (bp.specification.handler.dispatch(jni, callerClass, bp)) {
                guarantee(!testException(jni));
            }
        } catch (Throwable t) {
            VMError.shouldNotReachHere(t);
        } finally {
            recursive.set(false);
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static void onNativeMethodBind(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIMethodId method, CodePointer address, WordPointer newAddressPtr) {

        if (recursive.get()) {
            return;
        }
        if (nativeBreakpoints != null) {
            NativeBreakpoint bp = nativeBreakpoints.get(method.rawValue());
            if (bp != null) {
                bindNativeBreakpoint(jni, bp, newAddressPtr);
                boundNativeMethods.put(method.rawValue(), address.rawValue());
            }
        } else { // breakpoints are not yet initialized, remember and install breakpoint later
            boundNativeMethods.put(method.rawValue(), address.rawValue());
        }
    }

    private static final CEntryPointLiteral<CFunctionPointer> onBreakpointLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onBreakpoint",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, long.class);

    private static final CEntryPointLiteral<CFunctionPointer> onNativeMethodBindLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onNativeMethodBind",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, CodePointer.class, WordPointer.class);

    public static void onLoad(JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, TraceWriter writer, ReflectAccessVerifier verifier,
                    ProxyAccessVerifier prverifier, ResourceAccessVerifier resverifier) {

        JvmtiCapabilities capabilities = UnmanagedMemory.calloc(SizeOf.get(JvmtiCapabilities.class));
        check(jvmti.getFunctions().GetCapabilities().invoke(jvmti, capabilities));
        capabilities.setCanGenerateBreakpointEvents(1);
        capabilities.setCanAccessLocalVariables(1);
        capabilities.setCanForceEarlyReturn(1);
        capabilities.setCanGenerateNativeMethodBindEvents(1);
        check(jvmti.getFunctions().AddCapabilities().invoke(jvmti, capabilities));
        UnmanagedMemory.free(capabilities);

        callbacks.setBreakpoint(onBreakpointLiteral.getFunctionPointer());
        callbacks.setNativeMethodBind(onNativeMethodBindLiteral.getFunctionPointer());

        BreakpointInterceptor.traceWriter = writer;
        BreakpointInterceptor.accessVerifier = verifier;
        BreakpointInterceptor.proxyVerifier = prverifier;
        BreakpointInterceptor.resourceVerifier = resverifier;

        Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_NATIVE_METHOD_BIND, nullHandle()));
    }

    public static void onVMInit(JvmtiEnv jvmti, JNIEnvironment jni) {
        Map<Long, Breakpoint> breakpoints = new HashMap<>(BREAKPOINT_SPECIFICATIONS.length);
        Map<Long, NativeBreakpoint> nativeBrkpts = new HashMap<>(NATIVE_BREAKPOINT_SPECIFICATIONS.length);

        JNIObjectHandle lastClass = nullHandle();
        String lastClassName = null;
        for (BreakpointSpecification br : BREAKPOINT_SPECIFICATIONS) {
            JNIObjectHandle clazz;
            if (lastClassName != null && lastClassName.equals(br.className)) {
                clazz = lastClass;
            } else {
                clazz = resolveBreakpointClass(jni, br.className, br.optional);
                lastClass = clazz;
                lastClassName = br.className;
            }
            JNIMethodId method = resolveBreakpointMethod(jni, clazz, br.methodName, br.signature, br.optional);
            JvmtiError result = jvmtiFunctions().SetBreakpoint().invoke(jvmti, method, 0L);
            if (result == JvmtiError.JVMTI_ERROR_NONE) {
                breakpoints.put(method.rawValue(), new Breakpoint(br, clazz, method));
            } else {
                guarantee(br.optional, "Setting breakpoint failed");
            }
        }
        for (NativeBreakpointSpecification br : NATIVE_BREAKPOINT_SPECIFICATIONS) {
            JNIObjectHandle clazz;
            if (lastClassName != null && lastClassName.equals(br.className)) {
                clazz = lastClass;
            } else {
                clazz = resolveBreakpointClass(jni, br.className, br.optional);
                lastClass = clazz;
                lastClassName = br.className;
            }
            JNIMethodId method = resolveBreakpointMethod(jni, clazz, br.methodName, br.signature, br.optional);
            if (method.isNonNull()) {
                NativeBreakpoint bp = new NativeBreakpoint(br, clazz, method);
                nativeBrkpts.put(method.rawValue(), bp);
                if (boundNativeMethods.containsKey(method.rawValue())) {
                    bindNativeBreakpoint(jni, bp, nullPointer());
                }
            }
        }
        boundNativeMethods.keySet().retainAll(nativeBrkpts.keySet()); // others no longer needed

        installedBreakpoints = breakpoints;
        nativeBreakpoints = nativeBrkpts;
        Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullHandle()));
    }

    private static JNIObjectHandle resolveBreakpointClass(JNIEnvironment jni, String className, boolean optional) {
        JNIObjectHandle clazz;
        try (CCharPointerHolder cname = toCString(className)) {
            clazz = jniFunctions().getFindClass().invoke(jni, cname.get());
            if (optional && (clearException(jni) || clazz.equal(nullHandle()))) {
                return nullHandle();
            }
            checkNoException(jni);
        }
        clazz = jniFunctions().getNewGlobalRef().invoke(jni, clazz);
        checkNoException(jni);
        return clazz;
    }

    private static JNIMethodId resolveBreakpointMethod(JNIEnvironment jni, JNIObjectHandle clazz, String methodName, String signature, boolean optional) {
        if (optional && clazz.equal(nullHandle())) {
            return nullPointer();
        }
        guarantee(clazz.notEqual(nullHandle()));
        JNIMethodId method;
        try (CCharPointerHolder cname = toCString(methodName); CCharPointerHolder csignature = toCString(signature)) {
            method = jniFunctions().getGetMethodID().invoke(jni, clazz, cname.get(), csignature.get());
            if (method.isNull()) {
                clearException(jni);
                method = jniFunctions().getGetStaticMethodID().invoke(jni, clazz, cname.get(), csignature.get());
            }
        }
        if (optional && (clearException(jni) || method.isNull())) {
            return nullPointer();
        }
        guarantee(!testException(jni) && method.isNonNull());
        return method;
    }

    private static void bindNativeBreakpoint(JNIEnvironment jni, NativeBreakpoint bp, WordPointer newAddressPtr) {
        assert !recursive.get();
        CFunctionPointer breakpointMethod = bp.specification.handlerLiteral.getFunctionPointer();
        if (newAddressPtr.isNonNull()) {
            newAddressPtr.write(breakpointMethod);
        } else {
            recursive.set(true);
            try (CCharPointerHolder cname = toCString(bp.specification.methodName);
                            CCharPointerHolder csignature = toCString(bp.specification.signature)) {

                JNINativeMethod nativeMethod = StackValue.get(JNINativeMethod.class);
                nativeMethod.setName(cname.get());
                nativeMethod.setSignature(csignature.get());
                nativeMethod.setFnPtr(breakpointMethod);
                checkJni(jni.getFunctions().getRegisterNatives().invoke(jni, bp.clazz, nativeMethod, 1));
            } finally {
                recursive.set(false);
            }
        }
    }

    public static void onUnload(JNIEnvironment env) {
        installedBreakpoints.values().stream().map(bp -> bp.clazz.rawValue()).distinct().forEach(
                        ref -> jniFunctions().getDeleteGlobalRef().invoke(env, WordFactory.pointer(ref)));
        installedBreakpoints = null;
        accessVerifier = null;
        proxyVerifier = null;
        resourceVerifier = null;
        traceWriter = null;
    }

    private interface BreakpointHandler {
        boolean dispatch(JNIEnvironment jni, JNIObjectHandle callerClass, Breakpoint bp);
    }

    private static final BreakpointSpecification[] BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::forName),
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", BreakpointInterceptor::forName),

                    brk("java/lang/Class", "getFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getFields),
                    brk("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethods),
                    brk("java/lang/Class", "getConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructors),
                    brk("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getDeclaredFields),
                    brk("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethods),
                    brk("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getDeclaredConstructors),

                    brk("java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getField),
                    brk("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getDeclaredField),
                    brk("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethod),
                    brk("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),
                    brk("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethod),
                    brk("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),

                    brk("java/lang/Class", "getEnclosingMethod", "()Ljava/lang/reflect/Method;", BreakpointInterceptor::getEnclosingMethod),
                    brk("java/lang/Class", "getEnclosingConstructor", "()Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getEnclosingMethod),

                    brk("java/lang/Class", "newInstance", "()Ljava/lang/Object;", BreakpointInterceptor::newInstance),

                    brk("java/lang/ClassLoader", "getResource", "(Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::getResource),
                    brk("java/lang/ClassLoader", "getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", BreakpointInterceptor::getResources),
                    brk("java/lang/ClassLoader", "getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::getSystemResource),
                    brk("java/lang/ClassLoader", "getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", BreakpointInterceptor::getSystemResources),
                    /*
                     * NOTE: get(System)ResourceAsStream() generallys call get(System)Resource(), no
                     * additional breakpoints necessary
                     */

                    brk("java/lang/reflect/Proxy", "getProxyClass", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;)Ljava/lang/Class;", BreakpointInterceptor::getProxyClass),
                    brk("java/lang/reflect/Proxy", "newProxyInstance",
                                    "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;", BreakpointInterceptor::newProxyInstance),

                    // In Java 9+, these are Java methods that call private methods
                    optionalBrk("sun/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", BreakpointInterceptor::objectFieldOffset),
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", BreakpointInterceptor::objectFieldOffset),
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/Class;Ljava/lang/String;)J", BreakpointInterceptor::objectFieldOffsetByName),
    };

    private static final NativeBreakpointSpecification[] NATIVE_BREAKPOINT_SPECIFICATIONS = {
                    optionalNativeBrk("sun/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", nativeObjectFieldOffsetLiteral),
    };

    private static BreakpointSpecification brk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, false);
    }

    private static BreakpointSpecification optionalBrk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, true);
    }

    private static NativeBreakpointSpecification optionalNativeBrk(String className, String methodName, String signature, CEntryPointLiteral<?> handlerLiteral) {
        return new NativeBreakpointSpecification(className, methodName, signature, handlerLiteral, true);
    }

    private abstract static class AbstractBreakpointSpecification {
        final String className;
        final String methodName;
        final String signature;
        final boolean optional;

        AbstractBreakpointSpecification(String className, String methodName, String signature, boolean optional) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.optional = optional;
        }
    }

    private static class BreakpointSpecification extends AbstractBreakpointSpecification {
        final BreakpointHandler handler;

        BreakpointSpecification(String className, String methodName, String signature, BreakpointHandler handler, boolean optional) {
            super(className, methodName, signature, optional);
            this.handler = handler;
        }
    }

    private static class NativeBreakpointSpecification extends AbstractBreakpointSpecification {
        final CEntryPointLiteral<?> handlerLiteral;

        NativeBreakpointSpecification(String className, String methodName, String signature, CEntryPointLiteral<?> handlerLiteral, boolean optional) {
            super(className, methodName, signature, optional);
            this.handlerLiteral = handlerLiteral;
        }
    }

    private abstract static class AbstractBreakpoint<T extends AbstractBreakpointSpecification> {
        final T specification;
        final JNIObjectHandle clazz;
        final JNIMethodId method;

        AbstractBreakpoint(T specification, JNIObjectHandle clazz, JNIMethodId method) {
            this.specification = specification;
            this.clazz = clazz;
            this.method = method;
        }
    }

    private static final class Breakpoint extends AbstractBreakpoint<BreakpointSpecification> {
        Breakpoint(BreakpointSpecification specification, JNIObjectHandle clazz, JNIMethodId method) {
            super(specification, clazz, method);
        }
    }

    private static final class NativeBreakpoint extends AbstractBreakpoint<NativeBreakpointSpecification> {
        NativeBreakpoint(NativeBreakpointSpecification specification, JNIObjectHandle clazz, JNIMethodId method) {
            super(specification, clazz, method);
        }
    }

    private BreakpointInterceptor() {
    }
}
