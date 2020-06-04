/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.checkJni;
import static com.oracle.svm.jvmtiagentbase.Support.checkNoException;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;
import static com.oracle.svm.jvmtiagentbase.Support.getCallerClass;
import static com.oracle.svm.jvmtiagentbase.Support.getCallerMethod;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOr;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;
import static com.oracle.svm.jvmtiagentbase.Support.getDirectCallerClass;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.getObjectArgument;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.testException;
import static com.oracle.svm.jvmtiagentbase.Support.toCString;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_BREAKPOINT;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_CLASS_PREPARE;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_NATIVE_METHOD_BIND;
import static org.graalvm.word.WordFactory.nullPointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.agent.restrict.ProxyAccessVerifier;
import com.oracle.svm.agent.restrict.ReflectAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceAccessVerifier;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeMethod;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;
import com.oracle.svm.jvmtiagentbase.AgentIsolate;
import com.oracle.svm.jvmtiagentbase.ConstantPoolTool;
import com.oracle.svm.jvmtiagentbase.ConstantPoolTool.MethodReference;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.Support.WordSupplier;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiCapabilities;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventMode;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiLocationFormat;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Intercepts events of interest via breakpoints in Java code.
 * <p>
 * With most of our breakpoints, we recursively call the intercepted method ourselves to inspect its
 * return value and determine whether it provides a valid result. This permits us to identify
 * probing.
 * <p>
 * Some of the methods are caller-sensitive, so when we call them from a breakpoint, they observe a
 * different caller class and therefore can behave differently. Short of using bytecode
 * instrumentation to read the return value, there seems to be no strictly better approach (and
 * instrumenting java.lang.Class and friends might be tricky, too). It would be possible to set
 * breakpoints at return bytecodes instead, but then there is no way to retrieve the return value
 * from the operand stack.
 * <p>
 * When other tools or code use bytecode reinstrumentation or code hotswapping on a method in which
 * we already have a breakpoint, our breakpoint is cleared. It seems that we cannot get a useful
 * event in that case: according to the JVMTI specification, only ClassFileLoadHook triggers, but at
 * a time when the changes are not yet applied and it is too early to set another breakpoint.
 * Therefore, we do not support this case for now.
 */
final class BreakpointInterceptor {
    private static TraceWriter traceWriter;
    private static ReflectAccessVerifier accessVerifier;
    private static ProxyAccessVerifier proxyVerifier;
    private static ResourceAccessVerifier resourceVerifier;
    private static NativeImageAgent agent;

    private static Map<Long, Breakpoint> installedBreakpoints;

    /**
     * A map from {@link JNIMethodId} to entry point addresses for bound Java {@code native}
     * methods, NOT considering our intercepting functions, i.e., these are the original entry
     * points for a native method from symbol resolution or {@code registerNatives}.
     */
    private static Map<Long, Long> boundNativeMethods;

    /**
     * Map from {@link JNIMethodId} to breakpoints in {@code native} methods. Not all of them may be
     * installed if the native methods haven't been {@linkplain #bindNativeBreakpoint bound}.
     */
    private static Map<Long, NativeBreakpoint> nativeBreakpoints;

    /** Enables experimental support for instrumenting class lookups via {@code ClassLoader}. */
    private static boolean experimentalClassLoaderSupport = false;

    /**
     * Locations in methods where explicit calls to {@code ClassLoader.loadClass} have been found.
     */
    private static ConcurrentMap<MethodLocation, Boolean> observedExplicitLoadClassCallSites;

    /**
     * Guards access to {@link #boundNativeMethods} and {@link #nativeBreakpoints} to avoid races
     * that cause breakpoints to not be installed.
     */
    private static final ReentrantLock nativeBreakpointsInitLock = new ReentrantLock();

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

    private static boolean forName(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(0);
        String className = fromJniString(jni, name);
        boolean allowed = (accessVerifier == null || accessVerifier.verifyForName(jni, callerClass, className));
        Object result = false;
        if (allowed) {
            boolean classLoaderValid = true;
            WordPointer classLoaderPtr = StackValue.get(WordPointer.class);
            if (bp.method == agent.handles().javaLangClassForName3) {
                classLoaderValid = (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, 2, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
            } else {
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
            if (classLoaderValid) {
                /*
                 * Even if the original call requested class initialization, disable it because
                 * recursion checks keep us from seeing events of interest during initialization.
                 */
                int initialize = 0;
                result = nullHandle().notEqual(Support.callStaticObjectMethodLIL(jni, bp.clazz, agent.handles().javaLangClassForName3, name, initialize, classLoaderPtr.read()));
                if (clearException(jni)) {
                    result = false;
                }
            }
        }
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result, className);
        if (!allowed) {
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to class: " + className)) {
                jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangClassNotFoundException, message.get());
            }
        }
        return allowed;
    }

    private static boolean getFields(JNIEnvironment jni, Breakpoint bp) {
        return handleGetFields(jni, bp, false);
    }

    private static boolean getDeclaredFields(JNIEnvironment jni, Breakpoint bp) {
        return handleGetFields(jni, bp, true);
    }

    private static boolean handleGetFields(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle returnResult = nullHandle();
        if (accessVerifier != null) {
            returnResult = Support.callObjectMethod(jni, self, bp.method);
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

    private static boolean getMethods(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethods(jni, bp, false, () -> agent.handles().getJavaLangReflectMethod(jni));
    }

    private static boolean getDeclaredMethods(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethods(jni, bp, true, () -> agent.handles().getJavaLangReflectMethod(jni));
    }

    private static boolean getConstructors(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethods(jni, bp, true, () -> agent.handles().getJavaLangReflectConstructor(jni));
    }

    private static boolean getDeclaredConstructors(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethods(jni, bp, true, () -> agent.handles().getJavaLangReflectConstructor(jni));
    }

    private static boolean handleGetMethods(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly, WordSupplier<JNIObjectHandle> elementClass) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle returnResult = nullHandle();
        if (accessVerifier != null) {
            returnResult = Support.callObjectMethod(jni, self, bp.method);
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

    private static boolean getField(JNIEnvironment jni, Breakpoint bp) {
        return handleGetField(jni, bp, false);
    }

    private static boolean getDeclaredField(JNIEnvironment jni, Breakpoint bp) {
        return handleGetField(jni, bp, true);
    }

    private static boolean handleGetField(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        JNIObjectHandle result = Support.callObjectMethodL(jni, self, bp.method, name);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (!declaredOnly && result.notEqual(nullHandle())) {
            declaring = Support.callObjectMethod(jni, result, agent.handles().javaLangReflectMemberGetDeclaringClass);
            if (clearException(jni)) {
                declaring = nullHandle();
            }
        }
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyGetField(jni, self, name, result, (declaredOnly ? self : declaring), callerClass);
        traceBreakpoint(jni, self, declaring, callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()), fromJniString(jni, name));
        if (!allowed) {
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                            getClassNameOr(jni, self, "(null)", "(?)") + "." + fromJniString(jni, name))) {
                jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangNoSuchFieldException, message.get());
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

    private static final NativeBreakpointSpecification NATIVE_OBJECTFIELDOFFSET_BREAKPOINT_SPEC = new NativeBreakpointSpecification(
                    "sun/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", nativeObjectFieldOffsetLiteral);

    /** Native breakpoint for the JDK 8 {@code sun.misc.Unsafe.objectFieldOffset} native method. */
    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    static long nativeObjectFieldOffset(JNIEnvironment jni, JNIObjectHandle self, JNIObjectHandle field) {
        VMError.guarantee(NATIVE_OBJECTFIELDOFFSET_BREAKPOINT_SPEC.installed != null &&
                        NATIVE_OBJECTFIELDOFFSET_BREAKPOINT_SPEC.installed.replacedFunction.isNonNull(), "incompletely installed");

        ObjectFieldOffsetFunctionPointer original = (ObjectFieldOffsetFunctionPointer) NATIVE_OBJECTFIELDOFFSET_BREAKPOINT_SPEC.installed.replacedFunction;
        long result = original.invoke(jni, self, field);
        if (!Support.isInitialized()) { // in case of a (very) late call
            return result;
        }
        boolean validResult = !clearException(jni);
        JNIMethodId currentMethod = getCallerMethod(0);
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle name = nullHandle();
        JNIObjectHandle declaring = nullHandle();
        if (field.notEqual(nullHandle())) {
            name = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetName);
            if (clearException(jni)) {
                name = nullHandle();
            }
            declaring = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
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

    private static boolean objectFieldOffset(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle field = getObjectArgument(1);
        Support.callLongMethodL(jni, self, bp.method, field);
        boolean validResult = !clearException(jni);
        JNIObjectHandle name = nullHandle();
        JNIObjectHandle declaring = nullHandle();
        if (field.notEqual(nullHandle())) {
            name = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetName);
            if (clearException(jni)) {
                name = nullHandle();
            }
            declaring = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
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
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit unsafe access to field: " +
                            getClassNameOr(jni, declaring, "(null)", "(?)") + "." + fromJniString(jni, name))) {
                jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangRuntimeException, message.get());
            }
            return false;
        }
        return true;
    }

    private static boolean objectFieldOffsetByName(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle declaring = getObjectArgument(1);
        JNIObjectHandle name = getObjectArgument(2);
        Support.callLongMethodLL(jni, self, bp.method, declaring, name);
        boolean validResult = !clearException(jni);
        return verifyAndTraceObjectFieldOffset(jni, validResult, name, declaring, bp.method, callerClass);
    }

    private static boolean getConstructor(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle paramTypesHandle = getObjectArgument(1);
        JNIObjectHandle result = Support.callObjectMethodL(jni, self, bp.method, paramTypesHandle);
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

    private static boolean getMethod(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethod(jni, bp, false);
    }

    private static boolean getDeclaredMethod(JNIEnvironment jni, Breakpoint bp) {
        return handleGetMethod(jni, bp, true);
    }

    private static boolean handleGetMethod(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle nameHandle = getObjectArgument(1);
        JNIObjectHandle paramTypesHandle = getObjectArgument(2);
        JNIObjectHandle result = Support.callObjectMethodLL(jni, self, bp.method, nameHandle, paramTypesHandle);
        if (clearException(jni)) {
            result = nullHandle();
        }
        JNIObjectHandle declaring = nullHandle();
        if (!declaredOnly && result.notEqual(nullHandle())) {
            declaring = Support.callObjectMethod(jni, result, agent.handles().javaLangReflectMemberGetDeclaringClass);
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

    private static boolean getEnclosingMethod(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        Object result = TraceWriter.EXPLICIT_NULL;
        JNIObjectHandle enclosing = Support.callObjectMethod(jni, self, bp.method);
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
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, (allowed && enclosing.notEqual(nullHandle())) ? result : false);
        if (!allowed) {
            jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), nullHandle());
        }
        return allowed;
    }

    private static boolean newInstance(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIMethodId result = nullPointer();
        String name = "<init>";
        String signature = "()V";
        JNIObjectHandle self = getObjectArgument(0);
        if (self.notEqual(nullHandle())) {
            try (CCharPointerHolder ctorName = toCString(name); CCharPointerHolder ctorSignature = toCString(signature)) {
                result = jniFunctions().getGetMethodID().invoke(jni, self, ctorName.get(), ctorSignature.get());
            }
            if (clearException(jni)) {
                result = nullPointer();
            }
        }
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyNewInstance(jni, self, name, signature, result, callerClass);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()));
        if (!allowed) {
            throwNoSuchMethodException(jni, self, name, signature);
        }
        return allowed;
    }

    private static void throwNoSuchMethodException(JNIEnvironment jni, JNIObjectHandle clazz, String name, String signature) {
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(jni, clazz, "(null)", "(?)") + "." + name + signature)) {

            jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangNoSuchMethodException, message.get());
        }
    }

    private static boolean newArrayInstance(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle componentClass = getObjectArgument(0);
        CIntPointer lengthPtr = StackValue.get(CIntPointer.class);
        boolean lengthValid = (jvmtiFunctions().GetLocalInt().invoke(jvmtiEnv(), nullHandle(), 0, 1, lengthPtr) == JvmtiError.JVMTI_ERROR_NONE);

        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(componentClass);
        args.addressOf(1).setInt(lengthPtr.read());

        return newArrayInstance0(jni, bp, args, lengthValid);
    }

    private static boolean newArrayInstanceMulti(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle componentClass = getObjectArgument(0);
        JNIObjectHandle dimensionsArray = getObjectArgument(1);

        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(componentClass);
        args.addressOf(1).setObject(dimensionsArray);

        return newArrayInstance0(jni, bp, args, dimensionsArray.notEqual(nullHandle()));
    }

    private static boolean newArrayInstance0(JNIEnvironment jni, Breakpoint bp, JNIValue args, boolean argsValid) {

        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle result = nullHandle();
        JNIObjectHandle resultClass = nullHandle();
        JNIObjectHandle componentClass = args.addressOf(0).getObject();
        if (componentClass.notEqual(nullHandle()) && argsValid) {
            result = jniFunctions().getCallStaticObjectMethodA().invoke(jni, bp.clazz, bp.method, args);
            if (clearException(jni)) {
                result = nullHandle();
            } else {
                resultClass = jniFunctions().getGetObjectClass().invoke(jni, result);
                if (clearException(jni)) {
                    resultClass = nullHandle();
                }
            }
        }
        boolean allowed = result.equal(nullHandle()) || accessVerifier == null || accessVerifier.verifyNewArray(jni, resultClass, callerClass);
        String resultClassName = getClassNameOr(jni, resultClass, null, TraceWriter.UNKNOWN_VALUE);
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, allowed && result.notEqual(nullHandle()), resultClassName);
        if (allowed && result.notEqual(nullHandle())) { // do not create a second array
            jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), result);
        }
        if (!allowed) {
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit reflective array instantiation: " +
                            getClassNameOr(jni, resultClass, "(?)", "(?)"))) {
                jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangRuntimeException, message.get());
            }
        }
        return allowed;
    }

    private static boolean getResource(JNIEnvironment jni, Breakpoint bp) {
        return handleGetResources(jni, bp, false);
    }

    private static boolean getResources(JNIEnvironment jni, Breakpoint bp) {
        return handleGetResources(jni, bp, true);
    }

    private static boolean handleGetResources(JNIEnvironment jni, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        boolean result = false;
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetResources(jni, name, callerClass));
        if (allowed) {
            JNIObjectHandle returnValue = Support.callObjectMethodL(jni, self, bp.method, name);
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
        boolean hasElements = Support.callBooleanMethod(jni, obj, agent.handles().javaUtilEnumerationHasMoreElements);
        if (clearException(jni)) {
            hasElements = false;
        }
        return hasElements;
    }

    private static void forceGetResourceReturn(JNIEnvironment env, boolean returnsEnumeration) {
        JNIObjectHandle newResult = nullHandle();
        if (returnsEnumeration) {
            JNIObjectHandle javaUtilCollections = agent.handles().getJavaUtilCollections(env);
            JNIMethodId emptyEnumeration = agent.handles().getJavaUtilCollectionsEmptyEnumeration(env);
            if (javaUtilCollections.notEqual(nullHandle()) && emptyEnumeration.isNonNull()) {
                newResult = Support.callObjectMethod(env, javaUtilCollections, emptyEnumeration);
                if (clearException(env)) {
                    newResult = nullHandle();
                }
            }
        }
        jvmtiFunctions().ForceEarlyReturnObject().invoke(jvmtiEnv(), nullHandle(), newResult);
    }

    private static boolean getSystemResource(JNIEnvironment jni, Breakpoint bp) {
        return handleGetSystemResources(jni, bp, false);
    }

    private static boolean getSystemResources(JNIEnvironment jni, Breakpoint bp) {
        return handleGetSystemResources(jni, bp, true);
    }

    private static boolean handleGetSystemResources(JNIEnvironment jni, Breakpoint bp, boolean returnsEnumeration) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(0);
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetSystemResources(jni, name, callerClass));
        boolean result = false;
        if (allowed) {
            JNIObjectHandle returnValue = Support.callStaticObjectMethodL(jni, bp.clazz, bp.method, name);
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

    private static boolean newProxyInstance(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean allowed = proxyVerifier == null || proxyVerifier.verifyNewProxyInstance(jni, ifaceNames, callerClass);
        boolean result = false;
        if (allowed) {
            JNIObjectHandle invokeHandler = getObjectArgument(2);
            result = nullHandle().notEqual(Support.callStaticObjectMethodLLL(jni, bp.clazz, bp.method, classLoader, ifaces, invokeHandler));
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

    private static boolean getProxyClass(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean allowed = proxyVerifier == null || proxyVerifier.verifyGetProxyClass(jni, ifaceNames, callerClass);
        boolean result = false;
        if (allowed) {
            result = nullHandle().notEqual(Support.callStaticObjectMethodLL(jni, bp.clazz, bp.method, classLoader, ifaces));
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
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit proxy class for interfaces: " + interfaceString)) {
            jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangSecurityException, message.get());
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

    private static boolean getBundleImplJDK8OrEarlier(JNIEnvironment jni, Breakpoint bp) {
        JNIObjectHandle callerClass = getCallerClass(2); // actual caller of a getBundle method
        JNIObjectHandle baseName = getObjectArgument(0);
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetBundle(jni, baseName, callerClass));
        JNIObjectHandle result = nullHandle();
        if (allowed) {
            JNIObjectHandle locale = getObjectArgument(1);
            JNIObjectHandle loader = getObjectArgument(2);
            JNIObjectHandle control = getObjectArgument(3);
            result = Support.callStaticObjectMethodLLLL(jni, bp.clazz, bp.method, baseName, locale, loader, control);
            if (clearException(jni)) {
                result = nullHandle();
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "getBundleImplJDK8OrEarlier", result.notEqual(nullHandle()),
                        fromJniString(jni, baseName), TraceWriter.UNKNOWN_VALUE, TraceWriter.UNKNOWN_VALUE, TraceWriter.UNKNOWN_VALUE);
        return endGetBundleImpl(jni, baseName, allowed);
    }

    private static boolean getBundleImplJDK11OrLater(JNIEnvironment jni, Breakpoint bp) {
        JNIMethodId intermediateMethod = getCallerMethod(2);
        JNIMethodId callerMethod; // caller of getBundle(), not immediate caller
        if (intermediateMethod.equal(agent.handles().tryGetJavaUtilResourceBundleGetBundleImplSLCC(jni))) {
            // getBundleImpl <- getBundleImpl <- getBundleImpl(S,L,C,C) <- getBundle <- [caller]
            callerMethod = getCallerMethod(4);
        } else { // getBundleImpl <- getBundle(Impl|FromModule) <- getBundle <- [caller]
            callerMethod = getCallerMethod(3);
        }
        JNIObjectHandle callerClass = getMethodDeclaringClass(callerMethod);
        JNIObjectHandle baseName = getObjectArgument(2);
        boolean allowed = (resourceVerifier == null || resourceVerifier.verifyGetBundle(jni, baseName, callerClass));
        JNIObjectHandle result = nullHandle();
        if (allowed) {
            JNIObjectHandle callerModule = getObjectArgument(0);
            JNIObjectHandle module = getObjectArgument(1);
            JNIObjectHandle locale = getObjectArgument(3);
            JNIObjectHandle control = getObjectArgument(4);
            result = Support.callStaticObjectMethodLLLLL(jni, bp.clazz, bp.method, callerModule, module, baseName, locale, control);
            if (clearException(jni)) {
                result = nullHandle();
            }
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "getBundleImplJDK11OrLater", result.notEqual(nullHandle()),
                        TraceWriter.UNKNOWN_VALUE, TraceWriter.UNKNOWN_VALUE, fromJniString(jni, baseName), TraceWriter.UNKNOWN_VALUE, TraceWriter.UNKNOWN_VALUE);
        return endGetBundleImpl(jni, baseName, allowed);
    }

    private static boolean endGetBundleImpl(JNIEnvironment jni, JNIObjectHandle baseName, boolean allowed) {
        if (!allowed) {
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to resource bundle: " + fromJniString(jni, baseName))) {
                JNIObjectHandle msg = jniFunctions().getNewStringUTF().invoke(jni, message.get());
                JNIObjectHandle ex = Support.newObjectLLL(jni, agent.handles().javaUtilMissingResourceException, agent.handles().javaUtilMissingResourceExceptionCtor3, msg, nullHandle(),
                                nullHandle());
                jniFunctions().getThrow().invoke(jni, ex);
            }
        }
        return allowed;
    }

    private static boolean loadClass(JNIEnvironment jni, Breakpoint bp) {
        assert experimentalClassLoaderSupport;
        /*
         * There is no easy way to tell if it was the virtual machine that called the class loader
         * because if so, the caller is simply the Java method that triggered loading the class. We
         * have to check the current bytecode in the caller method whether it is in fact a call to
         * loadClass().
         */
        JNIObjectHandle callerClass = nullHandle();
        JvmtiFrameInfo frameInfo = StackValue.get(JvmtiFrameInfo.class);
        CIntPointer frameCountPtr = StackValue.get(CIntPointer.class);
        if (jvmtiFunctions().GetStackTrace().invoke(jvmtiEnv(), nullHandle(), 1, 1, frameInfo, frameCountPtr) == JvmtiError.JVMTI_ERROR_NONE && frameCountPtr.read() == 1) {
            callerClass = getMethodDeclaringClass(frameInfo.getMethod());
            if (callerClass.notEqual(nullHandle()) && jniFunctions().getIsAssignableFrom().invoke(jni, callerClass, agent.handles().javaLangClassLoader)) {
                // ignore recursive class loader calls, we must have seen the root invocation
                return true;
            }
            MethodLocation location = new MethodLocation(frameInfo.getMethod(), NumUtil.safeToInt(frameInfo.getLocation()));
            if (!observedExplicitLoadClassCallSites.containsKey(location)) {
                if (!isLoadClassInvocation(callerClass, location.method, location.bci, bp.specification.methodName, bp.specification.signature)) {
                    return true;
                }
                observedExplicitLoadClassCallSites.put(location, Boolean.TRUE);
            }
        }
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        String className = fromJniString(jni, name);
        boolean allowed = (accessVerifier == null || accessVerifier.verifyLoadClass(jni, callerClass, className));
        Object result = false;
        if (allowed) {
            result = nullHandle().notEqual(Support.callObjectMethodL(jni, self, bp.method, name));
            if (clearException(jni)) {
                result = false;
            }
        }
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result, className);
        if (!allowed) {
            try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to class: " + className)) {
                jniFunctions().getThrowNew().invoke(jni, agent.handles().javaLangClassNotFoundException, message.get());
            }
        }
        return allowed;
    }

    private static boolean isLoadClassInvocation(JNIObjectHandle clazz, JNIMethodId method, int bci, String methodName, String signature) {
        CIntPointer lengthPtr = StackValue.get(CIntPointer.class);
        CCharPointerPointer bytecodesPtr = StackValue.get(CCharPointerPointer.class);
        if (jvmtiFunctions().GetBytecodes().invoke(jvmtiEnv(), method, lengthPtr, bytecodesPtr) != JvmtiError.JVMTI_ERROR_NONE) {
            return false;
        }
        int cpi;
        CCharPointer bytecodes = bytecodesPtr.read();
        try {
            if (bci + 2 /* index bytes */ >= lengthPtr.read()) {
                return false;
            }
            int instruction = Byte.toUnsignedInt(bytecodes.read(bci));
            if (instruction != 0xb6) { // invokevirtual
                return false;
            }
            /*
             * According to Java VM Specification section 5.5, these checks should be sufficient
             * because invokevirtual should not trigger loading a class, but we still see cases
             * where this happens, so we further look at invoked method invoked at that location...
             */
            int indexbyte1 = Byte.toUnsignedInt(bytecodes.read(bci + 1));
            int indexbyte2 = Byte.toUnsignedInt(bytecodes.read(bci + 2));
            cpi = (indexbyte1 << 8) | indexbyte2;
        } finally {
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), bytecodes);
        }
        CIntPointer constantPoolCountPtr = StackValue.get(CIntPointer.class);
        CIntPointer constantPoolByteCountPtr = StackValue.get(CIntPointer.class);
        CCharPointerPointer constantPoolBytesPtr = StackValue.get(CCharPointerPointer.class);
        if (jvmtiFunctions().GetConstantPool().invoke(jvmtiEnv(), clazz, constantPoolCountPtr, constantPoolByteCountPtr, constantPoolBytesPtr) != JvmtiError.JVMTI_ERROR_NONE) {
            return false;
        }
        CCharPointer constantPool = constantPoolBytesPtr.read();
        try {
            ByteBuffer buffer = CTypeConversion.asByteBuffer(constantPool, constantPoolByteCountPtr.read());
            buffer.order(ByteOrder.BIG_ENDIAN);
            try {
                MethodReference ref = new ConstantPoolTool(buffer).readMethodReference(cpi);
                return methodName.contentEquals(ref.name) && signature.contentEquals(ref.descriptor);
            } catch (ConstantPoolTool.ConstantPoolException e) {
                return false; // unsupported class file format?
            }
        } finally {
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), constantPool);
        }
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
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static void onBreakpoint(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIMethodId method, @SuppressWarnings("unused") long location) {

        if (recursive.get()) {
            return;
        }
        recursive.set(true);
        try {
            Breakpoint bp = installedBreakpoints.get(method.rawValue());
            if (bp.specification.handler.dispatch(jni, bp)) {
                guarantee(!testException(jni));
            }
        } catch (Throwable t) {
            VMError.shouldNotReachHere(t);
        } finally {
            recursive.set(false);
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static void onNativeMethodBind(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIMethodId method, CodePointer address, WordPointer newAddressPtr) {

        if (recursive.get()) {
            return;
        }
        nativeBreakpointsInitLock.lock();
        try {
            if (nativeBreakpoints != null) {
                NativeBreakpoint bp = nativeBreakpoints.get(method.rawValue());
                if (bp != null) {
                    bindNativeBreakpoint(jni, bp, address, newAddressPtr);
                }
            } else { // breakpoints are not yet initialized, remember and install breakpoint later
                boundNativeMethods.put(method.rawValue(), address.rawValue());
            }
        } finally {
            nativeBreakpointsInitLock.unlock();
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static void onClassPrepare(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni,
                    @SuppressWarnings("unused") JNIObjectHandle thread, JNIObjectHandle clazz) {

        assert experimentalClassLoaderSupport;
        installBreakpointIfClassLoader(jni, clazz, installedBreakpoints);
    }

    private static void installBreakpointIfClassLoader(JNIEnvironment jni, JNIObjectHandle clazz, Map<Long, Breakpoint> breakpoints) {
        if (jniFunctions().getIsAssignableFrom().invoke(jni, clazz, agent.handles().javaLangClassLoader)) {
            String className = getClassNameOrNull(jni, clazz);
            if (className != null) {
                BreakpointSpecification proto = CLASSLOADER_LOAD_CLASS_BREAKPOINT_SPECIFICATION;
                JNIMethodId method = resolveBreakpointMethod(jni, clazz, proto.methodName, proto.signature, true);
                if (method.isNonNull() && jvmtiFunctions().SetBreakpoint().invoke(jvmtiEnv(), method, 0L) == JvmtiError.JVMTI_ERROR_NONE) {
                    BreakpointSpecification spec = new BreakpointSpecification(className, proto.methodName, proto.signature, proto.handler, proto.optional);
                    JNIObjectHandle gclazz = agent.handles().newTrackedGlobalRef(jni, clazz);
                    breakpoints.put(method.rawValue(), new Breakpoint(spec, gclazz, method));
                }
            }
        }
    }

    private static final CEntryPointLiteral<CFunctionPointer> onBreakpointLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onBreakpoint",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, long.class);

    private static final CEntryPointLiteral<CFunctionPointer> onNativeMethodBindLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onNativeMethodBind",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, CodePointer.class, WordPointer.class);

    private static final CEntryPointLiteral<CFunctionPointer> onClassPrepareLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onClassPrepare",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class);

    public static void onLoad(JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, TraceWriter writer, ReflectAccessVerifier verifier,
                    ProxyAccessVerifier prverifier, ResourceAccessVerifier resverifier, NativeImageAgent nativeImageTracingAgent, boolean exptlClassLoaderSupport) {

        BreakpointInterceptor.traceWriter = writer;
        BreakpointInterceptor.accessVerifier = verifier;
        BreakpointInterceptor.proxyVerifier = prverifier;
        BreakpointInterceptor.resourceVerifier = resverifier;
        BreakpointInterceptor.agent = nativeImageTracingAgent;
        BreakpointInterceptor.experimentalClassLoaderSupport = exptlClassLoaderSupport;

        JvmtiCapabilities capabilities = UnmanagedMemory.calloc(SizeOf.get(JvmtiCapabilities.class));
        check(jvmti.getFunctions().GetCapabilities().invoke(jvmti, capabilities));
        capabilities.setCanGenerateBreakpointEvents(1);
        capabilities.setCanAccessLocalVariables(1);
        capabilities.setCanForceEarlyReturn(1);
        capabilities.setCanGenerateNativeMethodBindEvents(1);
        if (exptlClassLoaderSupport) {
            capabilities.setCanGetBytecodes(1);
            capabilities.setCanGetConstantPool(1);

            CIntPointer formatPtr = StackValue.get(CIntPointer.class);
            guarantee(jvmti.getFunctions().GetJLocationFormat().invoke(jvmti, formatPtr) == JvmtiError.JVMTI_ERROR_NONE &&
                            formatPtr.read() == JvmtiLocationFormat.JVMTI_JLOCATION_JVMBCI.getCValue(), "Expecting BCI locations");
        }
        check(jvmti.getFunctions().AddCapabilities().invoke(jvmti, capabilities));
        UnmanagedMemory.free(capabilities);

        callbacks.setBreakpoint(onBreakpointLiteral.getFunctionPointer());
        callbacks.setNativeMethodBind(onNativeMethodBindLiteral.getFunctionPointer());
        if (exptlClassLoaderSupport) {
            callbacks.setClassPrepare(onClassPrepareLiteral.getFunctionPointer());
        }

        BreakpointInterceptor.boundNativeMethods = new HashMap<>();
        Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_NATIVE_METHOD_BIND, nullHandle()));
    }

    public static void onVMInit(JvmtiEnv jvmti, JNIEnvironment jni) {
        Map<Long, Breakpoint> breakpoints;
        if (experimentalClassLoaderSupport) {
            // Breakpoints are added when class loaders are added and must be thread-safe
            breakpoints = new ConcurrentHashMap<>(BREAKPOINT_SPECIFICATIONS.length);
            observedExplicitLoadClassCallSites = new ConcurrentHashMap<>();

            // Now that we can set breakpoints, check all currently loaded classes
            CIntPointer classCountPtr = StackValue.get(CIntPointer.class);
            WordPointer classesPtr = StackValue.get(WordPointer.class);
            check(jvmtiFunctions().GetLoadedClasses().invoke(jvmti, classCountPtr, classesPtr));
            WordPointer classesArray = classesPtr.read();
            for (int i = 0; i < classCountPtr.read(); i++) {
                JNIObjectHandle clazz = classesArray.read(i);
                installBreakpointIfClassLoader(jni, clazz, breakpoints);
            }
            check(jvmtiFunctions().Deallocate().invoke(jvmti, classesArray));
        } else {
            breakpoints = new HashMap<>(BREAKPOINT_SPECIFICATIONS.length);
        }

        JNIObjectHandle lastClass = nullHandle();
        String lastClassName = null;
        for (BreakpointSpecification br : BREAKPOINT_SPECIFICATIONS) {
            JNIObjectHandle clazz = nullHandle();
            if (lastClassName != null && lastClassName.equals(br.className)) {
                clazz = lastClass;
            }
            Breakpoint bp = installBreakpoint(jni, br, breakpoints, clazz);
            if (bp != null) {
                lastClass = bp.clazz;
                lastClassName = br.className;
            }
        }
        installedBreakpoints = breakpoints;

        nativeBreakpointsInitLock.lock();
        try {
            nativeBreakpoints = new HashMap<>(NATIVE_BREAKPOINT_SPECIFICATIONS.length);
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
                    nativeBreakpoints.put(method.rawValue(), bp);
                    Long original = boundNativeMethods.get(method.rawValue());
                    if (original != null) { // already bound, replace
                        bindNativeBreakpoint(jni, bp, WordFactory.pointer(original), nullPointer());
                    }
                }
            }
            boundNativeMethods = null;
        } finally {
            nativeBreakpointsInitLock.unlock();
        }

        Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullHandle()));
        if (experimentalClassLoaderSupport) {
            Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullHandle()));
        }
    }

    private static Breakpoint installBreakpoint(JNIEnvironment jni, BreakpointSpecification br, Map<Long, Breakpoint> map, JNIObjectHandle knownClass) {
        JNIObjectHandle clazz = knownClass;
        if (clazz.equal(nullHandle())) {
            clazz = resolveBreakpointClass(jni, br.className, br.optional);
            if (clazz.equal(nullHandle())) {
                guarantee(br.optional);
                return null;
            }
        }
        JNIMethodId method = resolveBreakpointMethod(jni, clazz, br.methodName, br.signature, br.optional);
        JvmtiError result = jvmtiFunctions().SetBreakpoint().invoke(jvmtiEnv(), method, 0L);
        guarantee(result == JvmtiError.JVMTI_ERROR_NONE || br.optional, "Setting breakpoint failed");
        Breakpoint bp = new Breakpoint(br, clazz, method);
        guarantee(map.put(method.rawValue(), bp) == null, "Duplicate breakpoint: " + bp);
        return bp;
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
        clazz = agent.handles().newTrackedGlobalRef(jni, clazz);
        checkNoException(jni);
        return clazz;
    }

    private static JNIMethodId resolveBreakpointMethod(JNIEnvironment jni, JNIObjectHandle clazz, String methodName, String signature, boolean optional) {
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

    private static void bindNativeBreakpoint(JNIEnvironment jni, NativeBreakpoint bp, CodePointer originalAddress, WordPointer newAddressPtr) {
        assert !recursive.get();
        bp.replacedFunction = originalAddress;
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

    public static void onUnload() {
        installedBreakpoints = null;
        nativeBreakpoints = null;
        observedExplicitLoadClassCallSites = null;
        accessVerifier = null;
        proxyVerifier = null;
        resourceVerifier = null;
        traceWriter = null;
    }

    private interface BreakpointHandler {
        boolean dispatch(JNIEnvironment jni, Breakpoint bp);
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
                    brk("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;", BreakpointInterceptor::newArrayInstance),
                    brk("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;", BreakpointInterceptor::newArrayInstanceMulti),

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

                    optionalBrk("java/util/ResourceBundle",
                                    "getBundleImpl",
                                    "(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/ClassLoader;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
                                    BreakpointInterceptor::getBundleImplJDK8OrEarlier),
                    optionalBrk("java/util/ResourceBundle",
                                    "getBundleImpl",
                                    "(Ljava/lang/Module;Ljava/lang/Module;Ljava/lang/String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
                                    BreakpointInterceptor::getBundleImplJDK11OrLater),

                    // In Java 9+, these are Java methods that call private methods
                    optionalBrk("sun/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", BreakpointInterceptor::objectFieldOffset),
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", BreakpointInterceptor::objectFieldOffset),
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/Class;Ljava/lang/String;)J", BreakpointInterceptor::objectFieldOffsetByName),
    };

    private static final BreakpointSpecification CLASSLOADER_LOAD_CLASS_BREAKPOINT_SPECIFICATION = optionalBrk("java/lang/ClassLoader", "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::loadClass);

    private static final NativeBreakpointSpecification[] NATIVE_BREAKPOINT_SPECIFICATIONS = {
                    NATIVE_OBJECTFIELDOFFSET_BREAKPOINT_SPEC,
    };

    private static BreakpointSpecification brk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, false);
    }

    private static BreakpointSpecification optionalBrk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, true);
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

        @Override
        public String toString() {
            return className + ":" + methodName + signature + (optional ? " (optional)" : "");
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
        NativeBreakpoint installed;

        NativeBreakpointSpecification(String className, String methodName, String signature, CEntryPointLiteral<?> handlerLiteral) {
            super(className, methodName, signature, true);
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

        @Override
        public String toString() {
            return specification.toString();
        }
    }

    private static final class Breakpoint extends AbstractBreakpoint<BreakpointSpecification> {
        Breakpoint(BreakpointSpecification specification, JNIObjectHandle clazz, JNIMethodId method) {
            super(specification, clazz, method);
        }
    }

    private static final class NativeBreakpoint extends AbstractBreakpoint<NativeBreakpointSpecification> {
        CodePointer replacedFunction;

        NativeBreakpoint(NativeBreakpointSpecification specification, JNIObjectHandle clazz, JNIMethodId method) {
            super(specification, clazz, method);

            assert specification.installed == null : "must be installed exactly once";
            specification.installed = this;
        }
    }

    private static final class MethodLocation {
        final JNIMethodId method;
        final int bci;

        MethodLocation(JNIMethodId method, int bci) {
            this.method = method;
            this.bci = bci;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != this && obj instanceof MethodLocation) {
                MethodLocation other = (MethodLocation) obj;
                return method.equal(other.method) && bci == other.bci;
            }
            return (obj == this);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(method.rawValue()) + bci;
        }
    }

    private BreakpointInterceptor() {
    }
}
