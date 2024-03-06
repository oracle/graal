/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.checkJni;
import static com.oracle.svm.jvmtiagentbase.Support.checkNoException;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOr;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.getObjectArgument;
import static com.oracle.svm.jvmtiagentbase.Support.getReceiver;
import static com.oracle.svm.jvmtiagentbase.Support.handleException;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.testException;
import static com.oracle.svm.jvmtiagentbase.Support.toCString;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_BREAKPOINT;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_CLASS_FILE_LOAD_HOOK;
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

import com.oracle.svm.agent.stackaccess.EagerlyLoadedJavaStackAccess;
import com.oracle.svm.agent.stackaccess.InterceptedState;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIMode;
import com.oracle.svm.core.jni.headers.JNINativeMethod;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jni.headers.JNIValue;
import com.oracle.svm.core.reflect.proxy.DynamicProxySupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jvmtiagentbase.AgentIsolate;
import com.oracle.svm.jvmtiagentbase.ConstantPoolTool;
import com.oracle.svm.jvmtiagentbase.ConstantPoolTool.MethodReference;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiCapabilities;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventMode;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiInterface;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiLocationFormat;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.java.LambdaUtils;

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
    private static Tracer tracer;
    private static NativeImageAgent agent;
    private static Supplier<InterceptedState> interceptedStateSupplier;

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

    /** Enables experimental support for class definitions via {@code ClassLoader.defineClass}. */
    private static boolean experimentalClassDefineSupport = false;

    /** Enables experimental support for tracking {@code Unsafe.allocateInstance}. */
    private static boolean experimentalUnsafeAllocationSupport = false;

    /** Enables tracking of reflection queries for fine-tuned configuration. */
    private static boolean trackReflectionMetadata = false;

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

    /* Classes from these class loaders are assumed to not be dynamically loaded. */
    private static JNIObjectHandle[] builtinClassLoaders;

    private static void traceReflectBreakpoint(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, String function, Object result,
                    JNIMethodId[] stackTrace, Object... args) {
        traceBreakpoint(env, "reflect", clazz, declaringClass, callerClass, function, result, stackTrace, args);
    }

    private static void traceSerializeBreakpoint(JNIEnvironment env, String function, Object result,
                    JNIMethodId[] stackTrace, Object... args) {
        traceBreakpoint(env, "serialization", nullHandle(), nullHandle(), nullHandle(), function, result, stackTrace, args);
    }

    private static void traceBreakpoint(JNIEnvironment env, String context, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, String function, Object result,
                    JNIMethodId[] stackTrace, Object[] args) {
        if (tracer != null) {
            tracer.traceCall(context,
                            function,
                            getClassNameOr(env, clazz, null, Tracer.UNKNOWN_VALUE),
                            getClassNameOr(env, declaringClass, null, Tracer.UNKNOWN_VALUE),
                            getClassNameOr(env, callerClass, null, Tracer.UNKNOWN_VALUE),
                            result,
                            stackTrace,
                            args);
            JNIObjectHandle exception = handleException(env, false);
            if (exception.notEqual(nullHandle())) {
                /*
                 * A stack overflow error happening during a breakpoint should be handled by the
                 * program, not the agent.
                 */
                guarantee(jniFunctions().getIsInstanceOf().invoke(env, exception, agent.handles().javaLangStackOverflowError));
                return;
            }
            clearException(env);
        }
    }

    private static boolean forName(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(thread, 0);
        String className = fromJniString(jni, name);
        if (className == null) {
            return true; /* No point in tracing this. */
        }
        traceReflectBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull(), className);
        return true;
    }

    private static boolean getFields(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetFields(jni, thread, bp, state);
    }

    private static boolean getDeclaredFields(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetFields(jni, thread, bp, state);
    }

    private static boolean handleGetFields(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getMethods(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, thread, bp, state);
    }

    private static boolean getDeclaredMethods(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, thread, bp, state);
    }

    private static boolean getConstructors(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, thread, bp, state);
    }

    private static boolean getDeclaredConstructors(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, thread, bp, state);
    }

    private static boolean handleGetMethods(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        /* When reflection metadata tracking is disabled, all methods are considered invoked */
        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, trackReflectionMetadata ? null : true,
                        state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getClasses(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean getDeclaredClasses(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean getRecordComponents(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean getPermittedSubclasses(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean getNestMembers(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean getSigners(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, thread, bp, state);
    }

    private static boolean handleGetClasses(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        traceReflectBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getField(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetField(jni, thread, bp, false, state);
    }

    private static boolean getDeclaredField(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetField(jni, thread, bp, true, state);
    }

    private static boolean handleGetField(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, boolean declaredOnly, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        JNIObjectHandle name = getObjectArgument(thread, 1);
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
        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), getClassOrSingleProxyInterface(jni, declaring), callerClass, bp.specification.methodName, name.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static final CEntryPointLiteral<AllocateInstanceFunctionPointer> nativeAllocateInstance = CEntryPointLiteral.create(
                    BreakpointInterceptor.class, "nativeAllocateInstance", JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class);

    private static final NativeBreakpointSpecification NATIVE_ALLOCATE_INSTANCE_BREAKPOINT_SPEC = new NativeBreakpointSpecification(
                    "jdk/internal/misc/Unsafe", "allocateInstance", "(Ljava/lang/Class;)Ljava/lang/Object;", nativeAllocateInstance);

    private interface AllocateInstanceFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        long invoke(JNIEnvironment jni, JNIObjectHandle self, JNIObjectHandle field);
    }

    /** Native breakpoint for the {@code jdk/internal/misc/Unsafe#allocateInstance} method. */
    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    static long nativeAllocateInstance(JNIEnvironment jni, JNIObjectHandle self, JNIObjectHandle clazz) {
        VMError.guarantee(NATIVE_ALLOCATE_INSTANCE_BREAKPOINT_SPEC.installed != null &&
                        NATIVE_ALLOCATE_INSTANCE_BREAKPOINT_SPEC.installed.replacedFunction.isNonNull(), "incompletely installed");

        AllocateInstanceFunctionPointer original = (AllocateInstanceFunctionPointer) NATIVE_ALLOCATE_INSTANCE_BREAKPOINT_SPEC.installed.replacedFunction;
        long result = original.invoke(jni, self, clazz);
        if (!Support.isInitialized()) { // in case of a (very) late call
            return result;
        }
        boolean validResult = !clearException(jni);
        InterceptedState state = interceptedStateSupplier.get();
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        traceAllocateInstance(jni, clazz, validResult, state, callerClass);
        if (!validResult) { // invoke again for exception--pure function.
            return original.invoke(jni, self, clazz);
        }
        return result;
    }

    private static void traceAllocateInstance(JNIEnvironment jni, JNIObjectHandle clazz, boolean validResult, InterceptedState state, JNIObjectHandle callerClass) {
        if (clazz.notEqual(nullHandle())) {
            if (validResult) {
                traceReflectBreakpoint(jni, clazz, nullHandle(), callerClass, "allocateInstance", true, state.getFullStackTraceOrNull());
            }
        }
    }

    private static boolean objectFieldOffsetByName(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        JNIObjectHandle declaring = getObjectArgument(thread, 1);
        JNIObjectHandle name = getObjectArgument(thread, 2);
        Support.callLongMethodLL(jni, self, bp.method, declaring, name);
        boolean validResult = !clearException(jni);

        JNIObjectHandle clazz = getMethodDeclaringClass(bp.method);
        traceReflectBreakpoint(jni, clazz, declaring, callerClass, "objectFieldOffset", validResult, state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static boolean getConstructor(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        JNIObjectHandle paramTypesHandle = getObjectArgument(thread, 1);
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, true, state.getFullStackTraceOrNull(),
                        paramTypes);
        return true;
    }

    private static boolean getMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethod(jni, thread, bp, false, state);
    }

    private static boolean getDeclaredMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetMethod(jni, thread, bp, true, state);
    }

    private static boolean handleGetMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, boolean declaredOnly, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        JNIObjectHandle nameHandle = getObjectArgument(thread, 1);
        JNIObjectHandle paramTypesHandle = getObjectArgument(thread, 2);
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
        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), getClassOrSingleProxyInterface(jni, declaring), callerClass, bp.specification.methodName,
                        nameHandle.notEqual(nullHandle()), state.getFullStackTraceOrNull(), name, paramTypes);
        return true;
    }

    private static boolean getEnclosingMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        Object result = Tracer.EXPLICIT_NULL;
        JNIObjectHandle enclosing = Support.callObjectMethod(jni, self, bp.method);
        String name;
        String signature;
        if (!clearException(jni) && enclosing.notEqual(nullHandle())) {
            result = Tracer.UNKNOWN_VALUE;
            JNIMethodId enclosingID = jniFunctions().getFromReflectedMethod().invoke(jni, enclosing);
            if (!clearException(jni) && enclosingID.isNonNull()) {
                WordPointer holderPtr = StackValue.get(WordPointer.class);
                if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), enclosingID, holderPtr) == JvmtiError.JVMTI_ERROR_NONE) {
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
        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, enclosing.notEqual(nullHandle()) ? result : false, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean invokeMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleInvokeMethod(jni, thread, bp, state, true);
    }

    private static boolean unreflectMethod(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleInvokeMethod(jni, thread, bp, state, false);
    }

    private static boolean handleInvokeMethod(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state, boolean isInvoke) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle method = getObjectArgument(thread, isInvoke ? 0 : 1);

        JNIObjectHandle declaring = Support.callObjectMethod(jni, method, agent.handles().javaLangReflectMemberGetDeclaringClass);
        if (clearException(jni)) {
            declaring = nullHandle();
        }

        JNIObjectHandle nameHandle = Support.callObjectMethod(jni, method, agent.handles().javaLangReflectMemberGetName);
        if (clearException(jni)) {
            nameHandle = nullHandle();
        }
        String name = fromJniString(jni, nameHandle);

        JNIObjectHandle paramTypesHandle = Support.callObjectMethod(jni, method, agent.handles().getJavaLangReflectExecutableGetParameterTypes(jni));
        if (clearException(jni)) {
            paramTypesHandle = nullHandle();
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);

        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, declaring), getClassOrSingleProxyInterface(jni, declaring), callerClass, "invokeMethod", declaring.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), name, paramTypes);

        /*
         * Calling Class.newInstance through Method.invoke should register the class for reflective
         * instantiation
         */
        if (isInvoke && isClassNewInstance(jni, declaring, name)) {
            JNIObjectHandle clazz = getObjectArgument(thread, 1);
            traceReflectBreakpoint(jni, clazz, nullHandle(), callerClass, "newInstance", clazz.notEqual(nullHandle()), state.getFullStackTraceOrNull());
        }
        return true;
    }

    private static boolean isClassNewInstance(JNIEnvironment jni, JNIObjectHandle declaring, String name) {
        if (!"newInstance".equals(name)) {
            return false;
        }
        JNIObjectHandle classNameHandle = Support.callObjectMethod(jni, declaring, agent.handles().javaLangClassGetName);
        if (clearException(jni)) {
            classNameHandle = nullHandle();
        }
        String className = fromJniString(jni, classNameHandle);
        return "java.lang.Class".equals(className);
    }

    private static boolean invokeConstructor(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleInvokeConstructor(jni, bp, state, getReceiver(thread));
    }

    private static boolean unreflectConstructor(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleInvokeConstructor(jni, bp, state, getObjectArgument(thread, 1));
    }

    private static boolean handleInvokeConstructor(JNIEnvironment jni, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state, JNIObjectHandle constructor) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();

        JNIObjectHandle declaring = Support.callObjectMethod(jni, constructor, agent.handles().javaLangReflectMemberGetDeclaringClass);
        if (clearException(jni)) {
            declaring = nullHandle();
        }

        JNIObjectHandle paramTypesHandle = Support.callObjectMethod(jni, constructor, agent.handles().getJavaLangReflectExecutableGetParameterTypes(jni));
        if (clearException(jni)) {
            paramTypesHandle = nullHandle();
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);

        traceReflectBreakpoint(jni, getClassOrSingleProxyInterface(jni, declaring), getClassOrSingleProxyInterface(jni, declaring), callerClass, "invokeConstructor", declaring.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), paramTypes);
        return true;
    }

    private static boolean newInstance(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        traceReflectBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, self.notEqual(nullHandle()), state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean newArrayInstance(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(getObjectArgument(thread, 0));
        args.addressOf(1).setInt(0);
        // We ignore the actual array length because we have observed reading it to cause serious
        // slowdowns in multithreaded programs because it requires full safepoint operations.

        return newArrayInstance0(jni, bp, args, true, state);
    }

    private static boolean newArrayInstanceMulti(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle componentClass = getObjectArgument(thread, 0);
        JNIObjectHandle dimensionsArray = getObjectArgument(thread, 1);

        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(componentClass);
        args.addressOf(1).setObject(dimensionsArray);

        return newArrayInstance0(jni, bp, args, dimensionsArray.notEqual(nullHandle()), state);
    }

    private static boolean newArrayInstance0(JNIEnvironment jni, Breakpoint bp, JNIValue args, boolean argsValid, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
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
        String resultClassName = getClassNameOr(jni, resultClass, null, Tracer.UNKNOWN_VALUE);
        traceReflectBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), resultClassName);
        return true;
    }

    private static boolean handleResourceRegistration(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle callerClass, String function, JNIMethodId[] stackTrace, String resourceName,
                    String moduleName) {
        if (resourceName == null) {
            return true; /* No point in tracing this: resource path is null */
        }

        if (moduleName == null) {
            traceReflectBreakpoint(env, clazz, nullHandle(), callerClass, function, true, stackTrace, resourceName);
        } else {
            traceReflectBreakpoint(env, clazz, nullHandle(), callerClass, function, true, stackTrace, moduleName, resourceName);
        }

        return true;
    }

    private static boolean findResource(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle module = getObjectArgument(thread, 1);
        JNIObjectHandle name = getObjectArgument(thread, 2);

        return handleResourceRegistration(jni, nullHandle(), callerClass, bp.specification.methodName, state.getFullStackTraceOrNull(), fromJniString(jni, name), fromJniString(jni, module));
    }

    private static boolean getResource(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetResources(jni, thread, bp, state);
    }

    private static boolean getResources(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetResources(jni, thread, bp, state);
    }

    private static boolean handleGetResources(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getReceiver(thread);
        JNIObjectHandle name = getObjectArgument(thread, 1);
        JNIObjectHandle selfClazz = nullHandle(); // self is java.lang.ClassLoader, get its class
        if (self.notEqual(nullHandle())) {
            selfClazz = jniFunctions().getGetObjectClass().invoke(jni, self);
            if (clearException(jni)) {
                selfClazz = nullHandle();
            }
        }

        return handleResourceRegistration(jni, selfClazz, callerClass, bp.specification.methodName, state.getFullStackTraceOrNull(), fromJniString(jni, name), null);
    }

    private static boolean getSystemResource(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetSystemResources(jni, thread, bp, state);
    }

    private static boolean getSystemResources(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        return handleGetSystemResources(jni, thread, bp, state);
    }

    private static boolean handleGetSystemResources(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(thread, 0);

        return handleResourceRegistration(jni, nullHandle(), callerClass, bp.specification.methodName, state.getFullStackTraceOrNull(), fromJniString(jni, name), null);
    }

    private static boolean newProxyInstance(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(thread, 0);
        JNIObjectHandle ifaces = getObjectArgument(thread, 1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        JNIObjectHandle invokeHandler = getObjectArgument(thread, 2);
        boolean result = nullHandle().notEqual(Support.callStaticObjectMethodLLL(jni, bp.clazz, bp.method, classLoader, ifaces, invokeHandler));
        if (clearException(jni)) {
            result = false;
        }
        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), Tracer.UNKNOWN_VALUE, ifaceNames,
                        Tracer.UNKNOWN_VALUE);
        return true;
    }

    private static boolean getProxyClass(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(thread, 0);
        JNIObjectHandle ifaces = getObjectArgument(thread, 1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean result = nullHandle().notEqual(Support.callStaticObjectMethodLL(jni, bp.clazz, bp.method, classLoader, ifaces));
        if (clearException(jni)) {
            result = false;
        }
        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), Tracer.UNKNOWN_VALUE, ifaceNames);
        return true;
    }

    private static Object getClassArrayNames(JNIEnvironment jni, JNIObjectHandle classArray) {
        Object classNames = Tracer.EXPLICIT_NULL;
        if (classArray.notEqual(nullHandle())) {
            classNames = Tracer.UNKNOWN_VALUE;
            int length = jniFunctions().getGetArrayLength().invoke(jni, classArray);
            if (!clearException(jni) && length >= 0) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    JNIObjectHandle clazz = jniFunctions().getGetObjectArrayElement().invoke(jni, classArray, i);
                    if (!clearException(jni)) {
                        list.add(getClassNameOr(jni, clazz, Tracer.EXPLICIT_NULL, Tracer.UNKNOWN_VALUE));
                    } else {
                        list.add(Tracer.UNKNOWN_VALUE);
                    }
                }
                classNames = list.toArray(new String[0]);
            }
        }
        return classNames;
    }

    private static boolean getBundleImpl(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIMethodId intermediateMethod = state.getCallerMethod(2);
        JNIMethodId callerMethod; // caller of getBundle(), not immediate caller
        if (intermediateMethod.equal(agent.handles().tryGetJavaUtilResourceBundleGetBundleImplSLCC(jni))) {
            // getBundleImpl <- getBundleImpl <- getBundleImpl(S,L,C,C) <- getBundle <- [caller]
            callerMethod = state.getCallerMethod(4);
        } else { // getBundleImpl <- getBundle(Impl|FromModule) <- getBundle <- [caller]
            callerMethod = state.getCallerMethod(3);
        }
        JNIObjectHandle callerClass = getMethodDeclaringClass(callerMethod);
        JNIObjectHandle baseName = getObjectArgument(thread, 2);
        JNIObjectHandle locale = getObjectArgument(thread, 3);
        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, true, state.getFullStackTraceOrNull(),
                        Tracer.UNKNOWN_VALUE, Tracer.UNKNOWN_VALUE, fromJniString(jni, baseName), readLocaleTag(jni, locale), Tracer.UNKNOWN_VALUE);
        return true;
    }

    private static String readLocaleTag(JNIEnvironment jni, JNIObjectHandle locale) {
        JNIObjectHandle languageTag = Support.callObjectMethod(jni, locale, agent.handles().javaUtilLocaleToLanguageTag);
        if (clearException(jni)) {
            /*- return root locale */
            return "";
        }

        JNIObjectHandle reconstructedLocale = Support.callStaticObjectMethodL(jni, agent.handles().javaUtilLocale, agent.handles().javaUtilLocaleForLanguageTag, languageTag);
        if (clearException(jni)) {
            reconstructedLocale = nullHandle();
        }
        if (Support.callBooleanMethodL(jni, locale, agent.handles().javaUtilLocaleEquals, reconstructedLocale)) {
            return fromJniString(jni, languageTag);
        } else {
            /*
             * Ill-formed locale, we do our best to return a unique description of the locale.
             * Locale.toLanguageTag simply ignores ill-formed locale elements, which creates
             * confusion when trying to determine whether a specific locale was registered.
             */
            String language = getElementString(jni, locale, agent.handles().javaUtilLocaleGetLanguage);
            String country = getElementString(jni, locale, agent.handles().javaUtilLocaleGetCountry);
            String variant = getElementString(jni, locale, agent.handles().javaUtilLocaleGetVariant);

            return String.join("-", language, country, variant);
        }
    }

    private static String getElementString(JNIEnvironment jni, JNIObjectHandle object, JNIMethodId getter) {
        JNIObjectHandle valueHandle = Support.callObjectMethod(jni, object, getter);
        if (clearException(jni)) {
            valueHandle = nullHandle();
        }
        return valueHandle.notEqual(nullHandle()) ? fromJniString(jni, valueHandle) : "";
    }

    private static boolean loadClass(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
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
        if (jvmtiFunctions().GetStackTrace().invoke(jvmtiEnv(), nullHandle(), 1, 1, (WordPointer) frameInfo, frameCountPtr) == JvmtiError.JVMTI_ERROR_NONE && frameCountPtr.read() == 1) {
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
        JNIObjectHandle name = getObjectArgument(thread, 1);
        String className = fromJniString(jni, name);
        traceReflectBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, className != null, state.getFullStackTraceOrNull(), className);
        return true;
    }

    private static boolean findSystemClass(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle className = getObjectArgument(thread, 1);
        traceReflectBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, true, state.getFullStackTraceOrNull(), fromJniString(jni, className));
        return true;
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

    private static boolean findMethodHandle(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle declaringClass = getObjectArgument(thread, 1);
        JNIObjectHandle methodName = getObjectArgument(thread, 2);
        JNIObjectHandle methodType = getObjectArgument(thread, 3);

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), state.getFullStackTraceOrNull());
    }

    private static boolean findSpecialHandle(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle declaringClass = getObjectArgument(thread, 1);
        JNIObjectHandle methodName = getObjectArgument(thread, 2);
        JNIObjectHandle methodType = getObjectArgument(thread, 3);

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), state.getFullStackTraceOrNull());
    }

    private static boolean bindHandle(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle receiver = getObjectArgument(thread, 1);
        JNIObjectHandle methodName = getObjectArgument(thread, 2);
        JNIObjectHandle methodType = getObjectArgument(thread, 3);

        JNIObjectHandle declaringClass = Support.callObjectMethod(jni, receiver, agent.handles().javaLangObjectGetClass);
        if (clearException(jni)) {
            declaringClass = nullHandle();
        }

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), state.getFullStackTraceOrNull());
    }

    private static boolean methodMethodHandle(JNIEnvironment jni, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, JNIObjectHandle nameHandle, JNIObjectHandle paramTypesHandle,
                    JNIMethodId[] stackTrace) {
        String name = fromJniString(jni, nameHandle);
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceReflectBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findMethodHandle", declaringClass.notEqual(nullHandle()) && name != null, stackTrace, name, paramTypes);
        return true;
    }

    private static boolean findConstructorHandle(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle declaringClass = getObjectArgument(thread, 1);
        JNIObjectHandle methodType = getObjectArgument(thread, 2);

        Object paramTypes = getClassArrayNames(jni, getParamTypes(jni, methodType));
        traceReflectBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findConstructorHandle", declaringClass.notEqual(nullHandle()), state.getFullStackTraceOrNull(), paramTypes);
        return true;
    }

    private static JNIObjectHandle getParamTypes(JNIEnvironment jni, JNIObjectHandle methodType) {
        JNIObjectHandle paramTypesHandle = Support.callObjectMethod(jni, methodType, agent.handles().getJavaLangInvokeMethodTypeParameterArray(jni));
        if (clearException(jni)) {
            paramTypesHandle = nullHandle();
        }
        return paramTypesHandle;
    }

    private static boolean findFieldHandle(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle declaringClass = getObjectArgument(thread, 1);
        JNIObjectHandle fieldName = getObjectArgument(thread, 2);

        String name = fromJniString(jni, fieldName);
        traceReflectBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findFieldHandle", declaringClass.notEqual(nullHandle()) && name != null, state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean findClass(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle className = getObjectArgument(thread, 1);

        String name = fromJniString(jni, className);
        traceReflectBreakpoint(jni, bp.clazz, nullHandle(), callerClass, "findClass", name != null, state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean unreflectField(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle field = getObjectArgument(thread, 1);

        JNIObjectHandle declaringClass = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
        if (clearException(jni)) {
            declaringClass = nullHandle();
        }

        JNIObjectHandle fieldNameHandle = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetName);
        if (clearException(jni)) {
            fieldNameHandle = nullHandle();
        }

        String fieldName = fromJniString(jni, fieldNameHandle);
        traceReflectBreakpoint(jni, declaringClass, nullHandle(), callerClass, "unreflectField", declaringClass.notEqual(nullHandle()) && fieldName != null, state.getFullStackTraceOrNull(),
                        fieldName);
        return true;
    }

    private static boolean asInterfaceInstance(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle intfc = getObjectArgument(thread, 0);

        JNIObjectHandle intfcNameHandle = Support.callObjectMethod(jni, intfc, agent.handles().javaLangClassGetName);
        if (clearException(jni)) {
            intfcNameHandle = nullHandle();
        }
        String intfcName = fromJniString(jni, intfcNameHandle);
        traceReflectBreakpoint(jni, intfc, nullHandle(), callerClass, "asInterfaceInstance", intfcName != null, state.getFullStackTraceOrNull());
        String[] intfcNames = new String[]{intfcName};
        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "newMethodHandleProxyInstance", intfcName != null, state.getFullStackTraceOrNull(), (Object) intfcNames);
        return true;
    }

    private static boolean constantBootstrapGetStaticFinal(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state, boolean hasDeclaringClass) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle fieldName = getObjectArgument(thread, 1);
        JNIObjectHandle type = getObjectArgument(thread, 2);
        JNIObjectHandle declaringClass = hasDeclaringClass ? getObjectArgument(thread, 3) : type;

        String name = fromJniString(jni, fieldName);
        traceReflectBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findFieldHandle", declaringClass.notEqual(nullHandle()) && name != null, state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean methodTypeFromDescriptor(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle descriptor = getObjectArgument(thread, 0);
        JNIObjectHandle classLoader = getObjectArgument(thread, 1);

        JNIObjectHandle result = Support.callStaticObjectMethodLL(jni, bp.clazz, bp.method, descriptor, classLoader);
        if (clearException(jni)) {
            result = nullHandle();
        }

        List<String> types = new ArrayList<>();
        if (result.notEqual(nullHandle())) {
            JNIObjectHandle rtype = Support.callObjectMethod(jni, result, agent.handles().getJavaLangInvokeMethodTypeReturnType(jni));
            if (clearException(jni)) {
                rtype = nullHandle();
            }
            String rtypeName = getClassNameOrNull(jni, rtype);
            if (rtypeName != null) {
                types.add(rtypeName);
            }

            JNIObjectHandle ptypes = Support.callObjectMethod(jni, result, agent.handles().getJavaLangInvokeMethodTypeParameterArray(jni));
            if (clearException(jni)) {
                ptypes = nullHandle();
            }
            Object ptypeNames = getClassArrayNames(jni, ptypes);
            if (ptypeNames instanceof String[]) {
                types.addAll(Arrays.asList((String[]) ptypeNames));
            }
        }

        traceReflectBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "methodTypeDescriptor", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), types);
        return true;
    }

    /**
     * This method should be intercepted when we are predefining a lambda class. This is the only
     * spot in the lambda-class creation pipeline where we can get lambda-class bytecode so the
     * class can be predefined. We do not want to predefine all lambda classes, but only the ones
     * that are actually created at runtime, so we have a method that checks wheter the lambda
     * should be predefined or not.
     */
    private static boolean onMethodHandleClassFileInit(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        String className = Support.fromJniString(jni, getObjectArgument(thread, 1));

        if (LambdaUtils.isLambdaClassName(className)) {
            if (shouldIgnoreLambdaClassForPredefinition(jni)) {
                return true;
            }

            JNIObjectHandle bytesArray = getObjectArgument(thread, 3);
            int length = jniFunctions().getGetArrayLength().invoke(jni, bytesArray);
            byte[] data = new byte[length];

            CCharPointer bytesArrayCharPointer = jni.getFunctions().getGetByteArrayElements().invoke(jni, bytesArray, WordFactory.nullPointer());
            if (bytesArrayCharPointer.isNonNull()) {
                try {
                    CTypeConversion.asByteBuffer(bytesArrayCharPointer, length).get(data);
                } finally {
                    jni.getFunctions().getReleaseByteArrayElements().invoke(jni, bytesArray, bytesArrayCharPointer, JNIMode.JNI_ABORT());
                }

                className += LambdaUtils.digest(data);
                tracer.traceCall("classloading", "onMethodHandleClassFileInit", null, null, null, null, state.getFullStackTraceOrNull(), className, data);
            }
        }
        return true;
    }

    /**
     * This method is used to check whether a lambda class should be predefined or not. Only lambdas
     * that are created at runtime should be predefined, and we should ignore the others. This
     * method checks if the specific sequence of methods exists in the stacktrace and base on that
     * decides if the lambda class should be ignored.
     */
    private static boolean shouldIgnoreLambdaClassForPredefinition(JNIEnvironment env) {
        JNIMethodId[] stackTraceMethodIds = EagerlyLoadedJavaStackAccess.stackAccessSupplier().get().getFullStackTraceOrNull();
        JNIMethodId javaLangInvokeCallSiteMakeSite = agent.handles().getJavaLangInvokeCallSiteMakeSite(env);
        JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSiteImpl = agent.handles().getJavaLangInvokeMethodHandleNativesLinkCallSiteImpl(env);
        JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSite = agent.handles().getJavaLangInvokeMethodHandleNativesLinkCallSite(env);

        /*
         * Sequence {@code java.lang.invoke.CallSite.makeSite}, {@code
         * java.lang.invoke.MethodHandleNatives.linkCallSiteImpl}, {@code
         * java.lang.invoke.MethodHandleNatives.linkCallSite} in the stacktrace indicates that
         * lambda class won't be created at runtime on the Native Image, so it should not be
         * registered for predefiniton.
         */
        for (int i = 0; i < stackTraceMethodIds.length - 2; i++) {
            if (stackTraceMethodIds[i] == javaLangInvokeCallSiteMakeSite &&
                            stackTraceMethodIds[i + 1] == javaLangInvokeMethodHandleNativesLinkCallSiteImpl &&
                            stackTraceMethodIds[i + 2] == javaLangInvokeMethodHandleNativesLinkCallSite) {
                return true;
            }
        }

        return false;
    }

    /**
     * We have to find a class that captures a lambda function, so it can be registered by the
     * agent. We have to get a SerializedLambda instance first. After that we get a lambda capturing
     * class from that instance using JNIHandleSet#getFieldId to get field id and
     * JNIObjectHandle#invoke on to get that field value. We get a name of the capturing class and
     * tell the agent to register it.
     */
    private static boolean serializedLambdaReadResolve(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle serializedLambdaInstance = getReceiver(thread);
        JNIObjectHandle capturingClass = jniFunctions().getGetObjectField().invoke(jni, serializedLambdaInstance,
                        agent.handles().javaLangInvokeSerializedLambdaCapturingClass);

        String capturingClassName = getClassNameOrNull(jni, capturingClass);
        boolean validCapturingClass = nullHandle().notEqual(capturingClass);

        traceSerializeBreakpoint(jni, "SerializedLambda.readResolve", validCapturingClass, state.getFullStackTraceOrNull(), capturingClassName);
        return true;
    }

    private static boolean readClassDescriptor(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle desc = getObjectArgument(thread, 1);
        JNIMethodId descriptor = agent.handles().getJavaIoObjectStreamClassGetName(jni);
        var name = Support.callObjectMethod(jni, desc, descriptor);
        if (clearException(jni)) {
            name = nullHandle();
        }
        var className = fromJniString(jni, name);
        traceSerializeBreakpoint(jni, "ObjectInputStream.readClassDescriptor", true, state.getFullStackTraceOrNull(), className, null);
        return true;
    }

    private static boolean objectStreamClassConstructor(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state) {

        JNIObjectHandle serializeTargetClass = getObjectArgument(thread, 1);
        if (Support.isSerializable(jni, serializeTargetClass)) {
            String serializeTargetClassName = getClassNameOrNull(jni, serializeTargetClass);

            JNIObjectHandle objectStreamClassInstance = Support.newObjectL(jni, bp.clazz, bp.method, serializeTargetClass);
            boolean validObjectStreamClassInstance = nullHandle().notEqual(objectStreamClassInstance);
            if (clearException(jni)) {
                validObjectStreamClassInstance = false;
            }

            List<String> transitiveSerializeTargets = new ArrayList<>();
            transitiveSerializeTargets.add(serializeTargetClassName);

            /*
             * When the ObjectStreamClass instance is created for the given serializeTargetClass,
             * some additional ObjectStreamClass instances (usually the super classes) are created
             * recursively. Call ObjectStreamClass.getClassDataLayout0() can get all of them.
             */
            JNIMethodId getClassDataLayout0MId = agent.handles().getJavaIoObjectStreamClassGetClassDataLayout0(jni, bp.clazz);
            JNIObjectHandle dataLayoutArray = Support.callObjectMethod(jni, objectStreamClassInstance, getClassDataLayout0MId);
            if (!clearException(jni) && nullHandle().notEqual(dataLayoutArray)) {
                int length = jniFunctions().getGetArrayLength().invoke(jni, dataLayoutArray);
                // If only 1 element is got from getClassDataLayout0(). it is base ObjectStreamClass
                // instance itself.
                if (!clearException(jni) && length > 1) {
                    JNIFieldId hasDataFId = agent.handles().getJavaIOObjectStreamClassClassDataSlotHasData(jni);
                    JNIFieldId descFId = agent.handles().getJavaIOObjectStreamClassClassDataSlotDesc(jni);
                    JNIMethodId javaIoObjectStreamClassForClassMId = agent.handles().getJavaIoObjectStreamClassForClass(jni, bp.clazz);
                    for (int i = 0; i < length; i++) {
                        JNIObjectHandle classDataSlot = jniFunctions().getGetObjectArrayElement().invoke(jni, dataLayoutArray, i);
                        boolean hasData = jniFunctions().getGetBooleanField().invoke(jni, classDataSlot, hasDataFId);
                        if (hasData) {
                            JNIObjectHandle oscInstanceInSlot = jniFunctions().getGetObjectField().invoke(jni, classDataSlot, descFId);
                            if (!jniFunctions().getIsSameObject().invoke(jni, oscInstanceInSlot, objectStreamClassInstance)) {
                                JNIObjectHandle oscClazz = Support.callObjectMethod(jni, oscInstanceInSlot, javaIoObjectStreamClassForClassMId);
                                if (Support.isSerializable(jni, oscClazz)) {
                                    String oscClassName = getClassNameOrNull(jni, oscClazz);
                                    transitiveSerializeTargets.add(oscClassName);
                                }
                            }
                        }
                    }
                }
            }
            for (String className : transitiveSerializeTargets) {
                if (DynamicProxySupport.PROXY_CLASS_NAME_PATTERN.matcher(className).matches()) {
                    JNIObjectHandle interfaces = Support.callObjectMethod(jni, serializeTargetClass, agent.handles().javaLangClassGetInterfaces);
                    Object interfaceNames = getClassArrayNames(jni, interfaces);
                    traceSerializeBreakpoint(jni, "ProxyClassSerialization", validObjectStreamClassInstance, state.getFullStackTraceOrNull(), interfaceNames);
                } else {
                    traceSerializeBreakpoint(jni, "ObjectStreamClass.<init>", validObjectStreamClassInstance, state.getFullStackTraceOrNull(), className, null);
                }
            }
        }
        return true;
    }

    /**
     * In rare occasions, the application can demand custom target constructor for serialization,
     * using sun.reflect.ReflectionFactory#newConstructorForSerialization(java.lang.Class,
     * java.lang.reflect.Constructor) on JDK8 or
     * jdk.internal.reflect.ReflectionFactory#newConstructorForSerialization(java.lang.Class,
     * java.lang.reflect.Constructor) on JDK11. We need to catch constructor class and create entry
     * for that pair (serialization class, custom class constructor) in serialization configuration.
     */
    private static boolean customTargetConstructorSerialization(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle serializeTargetClass = getObjectArgument(thread, 1);
        if (Support.isSerializable(jni, serializeTargetClass)) {
            String serializeTargetClassName = getClassNameOrNull(jni, serializeTargetClass);

            JNIObjectHandle customConstructorObj = getObjectArgument(thread, 2);
            JNIObjectHandle customConstructorClass = jniFunctions().getGetObjectClass().invoke(jni, customConstructorObj);
            JNIMethodId getDeclaringClassNameMethodID = agent.handles().getJavaLangReflectConstructorDeclaringClassName(jni, customConstructorClass);
            JNIObjectHandle declaredClassNameObj = Support.callObjectMethod(jni, customConstructorObj, getDeclaringClassNameMethodID);
            String customConstructorClassName = fromJniString(jni, declaredClassNameObj);
            traceSerializeBreakpoint(jni, "ObjectStreamClass.<init>", true, state.getFullStackTraceOrNull(), serializeTargetClassName, customConstructorClassName);
        }
        return true;
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static void onBreakpoint(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle thread, JNIMethodId method, @SuppressWarnings("unused") long location) {
        if (recursive.get()) {
            return;
        }
        recursive.set(true);
        try {
            JNIObjectHandle rectifiedThread = rectifyCurrentThread(thread);
            if (rectifiedThread.equal(nullHandle())) {
                return;
            }

            Breakpoint bp = installedBreakpoints.get(method.rawValue());
            InterceptedState state = interceptedStateSupplier.get();
            if (bp.specification.handler.dispatch(jni, rectifiedThread, bp, state)) {
                guarantee(!testException(jni));
            }
        } catch (Throwable t) {
            VMError.shouldNotReachHere(t);
        } finally {
            recursive.set(false);
        }
    }

    /**
     * The JVMTI implementation of JDK 19 can pass the platform thread as current thread for events
     * in a virtual thread that happen while temporarily switching to the carrier thread (such as
     * scheduling an unpark). It also ignores the frames of a virtual thread when passing
     * {@code NULL} to {@code GetLocal*} to refer to the current thread (JDK-8292657). This method
     * calls {@code GetCurrentThread}, which seems to always return the virtual thread and can be
     * used to properly read the locals in the breakpoint.
     */
    private static JNIObjectHandle rectifyCurrentThread(JNIObjectHandle thread) {
        if (Support.jvmtiVersion() != JvmtiInterface.JVMTI_VERSION_19) {
            return thread;
        }

        WordPointer threadPtr = StackValue.get(WordPointer.class);
        JvmtiError error = jvmtiFunctions().GetCurrentThread().invoke(jvmtiEnv(), threadPtr);
        if (error == JvmtiError.JVMTI_ERROR_WRONG_PHASE) {
            return nullHandle();
        }
        check(error);
        return threadPtr.read();
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

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static void onClassFileLoadHook(@SuppressWarnings("unused") JvmtiEnv jvmti, JNIEnvironment jni, @SuppressWarnings("unused") JNIObjectHandle classBeingRedefined,
                    JNIObjectHandle loader, CCharPointer name, @SuppressWarnings("unused") JNIObjectHandle protectionDomain, int classDataLen, CCharPointer classData,
                    @SuppressWarnings("unused") CIntPointer newClassDataLen, @SuppressWarnings("unused") CCharPointerPointer newClassData) {
        InterceptedState state = interceptedStateSupplier.get();
        if (loader.equal(nullHandle())) { // boot class loader
            return;
        }
        String className = fromCString(name);
        if (className != null && AccessAdvisor.PROXY_CLASS_NAME_PATTERN.matcher(className).matches()) {
            // Proxy classes are handled using a different mechanism, so we ignore them here
            return;
        }
        for (JNIObjectHandle builtinLoader : builtinClassLoaders) {
            if (jniFunctions().getIsSameObject().invoke(jni, loader, builtinLoader)) {
                return;
            }
        }
        if (jniFunctions().getIsInstanceOf().invoke(jni, loader, agent.handles().jdkInternalReflectDelegatingClassLoader)) {
            return;
        }
        byte[] data = new byte[classDataLen];
        CTypeConversion.asByteBuffer(classData, classDataLen).get(data);
        tracer.traceCall("classloading", "onClassFileLoadHook", null, null, null, null, state.getFullStackTraceOrNull(), className, data);
    }

    private static final CEntryPointLiteral<CFunctionPointer> onBreakpointLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onBreakpoint",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, long.class);

    private static final CEntryPointLiteral<CFunctionPointer> onNativeMethodBindLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onNativeMethodBind",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, CodePointer.class, WordPointer.class);

    private static final CEntryPointLiteral<CFunctionPointer> onClassPrepareLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onClassPrepare",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<CFunctionPointer> onClassFileLoadHookLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onClassFileLoadHook",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class, CCharPointer.class, JNIObjectHandle.class, int.class, CCharPointer.class, CIntPointer.class,
                    CCharPointerPointer.class);

    public static void onLoad(JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, Tracer writer, NativeImageAgent nativeImageTracingAgent,
                    Supplier<InterceptedState> currentThreadJavaStackAccessSupplier,
                    boolean exptlClassLoaderSupport, boolean exptlClassDefineSupport, boolean exptlUnsafeAllocationSupport, boolean trackReflectionData) {
        BreakpointInterceptor.tracer = writer;
        BreakpointInterceptor.agent = nativeImageTracingAgent;
        BreakpointInterceptor.interceptedStateSupplier = currentThreadJavaStackAccessSupplier;
        BreakpointInterceptor.experimentalClassLoaderSupport = exptlClassLoaderSupport;
        BreakpointInterceptor.experimentalClassDefineSupport = exptlClassDefineSupport;
        BreakpointInterceptor.experimentalUnsafeAllocationSupport = exptlUnsafeAllocationSupport;
        BreakpointInterceptor.trackReflectionMetadata = trackReflectionData;

        JvmtiCapabilities capabilities = UnmanagedMemory.calloc(SizeOf.get(JvmtiCapabilities.class));
        check(jvmti.getFunctions().GetCapabilities().invoke(jvmti, capabilities));
        capabilities.setCanGenerateBreakpointEvents(1);
        capabilities.setCanAccessLocalVariables(1);

        if (exptlUnsafeAllocationSupport) {
            capabilities.setCanGenerateNativeMethodBindEvents(1);
            callbacks.setNativeMethodBind(onNativeMethodBindLiteral.getFunctionPointer());
            BreakpointInterceptor.boundNativeMethods = new HashMap<>();
        }

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

        if (exptlClassDefineSupport) {
            callbacks.setClassFileLoadHook(onClassFileLoadHookLiteral.getFunctionPointer());
        }

        if (exptlClassLoaderSupport) {
            callbacks.setClassPrepare(onClassPrepareLiteral.getFunctionPointer());
        }

        if (exptlUnsafeAllocationSupport) {
            Support.check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_NATIVE_METHOD_BIND, nullHandle()));
        }
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
        BreakpointSpecification[] breakpointSpecifications = BREAKPOINT_SPECIFICATIONS;
        if (trackReflectionMetadata) {
            breakpointSpecifications = new BreakpointSpecification[BREAKPOINT_SPECIFICATIONS.length + REFLECTION_ACCESS_BREAKPOINT_SPECIFICATIONS.length];
            System.arraycopy(BREAKPOINT_SPECIFICATIONS, 0, breakpointSpecifications, 0, BREAKPOINT_SPECIFICATIONS.length);
            System.arraycopy(REFLECTION_ACCESS_BREAKPOINT_SPECIFICATIONS, 0, breakpointSpecifications, BREAKPOINT_SPECIFICATIONS.length, REFLECTION_ACCESS_BREAKPOINT_SPECIFICATIONS.length);
        }
        if (experimentalClassDefineSupport) {
            BreakpointSpecification[] existingBreakpointSpecifications = breakpointSpecifications;
            breakpointSpecifications = Arrays.copyOf(existingBreakpointSpecifications, existingBreakpointSpecifications.length + CLASS_PREDEFINITION_BREAKPOINT_SPECIFICATIONS.length);
            System.arraycopy(CLASS_PREDEFINITION_BREAKPOINT_SPECIFICATIONS, 0, breakpointSpecifications, existingBreakpointSpecifications.length,
                            CLASS_PREDEFINITION_BREAKPOINT_SPECIFICATIONS.length);
        }
        for (BreakpointSpecification br : breakpointSpecifications) {
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

        if (experimentalUnsafeAllocationSupport) {
            setupNativeBreakpoints(jni, lastClass, lastClassName);
        }

        if (experimentalClassDefineSupport) {
            setupClassLoadEvent(jvmti, jni);
        }

        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullHandle()));
        if (experimentalClassLoaderSupport) {
            check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullHandle()));
        }
    }

    private static void setupNativeBreakpoints(JNIEnvironment jni, JNIObjectHandle previousClass, String previousClassName) {
        JNIObjectHandle lastClass = previousClass;
        String lastClassName = previousClassName;
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
    }

    private static void setupClassLoadEvent(JvmtiEnv jvmti, JNIEnvironment jni) {
        JNIObjectHandle classLoader = agent.handles().javaLangClassLoader;

        JNIMethodId getSystemClassLoader = agent.handles().getMethodId(jni, classLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;", true);
        JNIObjectHandle systemLoader = Support.callStaticObjectMethod(jni, classLoader, getSystemClassLoader);
        checkNoException(jni);
        guarantee(systemLoader.notEqual(nullHandle()));

        JNIMethodId getPlatformLoader = agent.handles().getMethodIdOptional(jni, classLoader, "getPlatformClassLoader", "()Ljava/lang/ClassLoader;", true);
        JNIMethodId getAppLoader = agent.handles().getMethodIdOptional(jni, classLoader, "getBuiltinAppClassLoader", "()Ljava/lang/ClassLoader;", true);
        if (getPlatformLoader.isNonNull() && getAppLoader.isNonNull()) { // only on JDK 9 and later
            JNIObjectHandle platformLoader = Support.callObjectMethod(jni, classLoader, getPlatformLoader);
            checkNoException(jni);
            JNIObjectHandle appLoader = Support.callObjectMethod(jni, classLoader, getAppLoader);
            checkNoException(jni);
            guarantee(platformLoader.notEqual(nullHandle()) && appLoader.notEqual(nullHandle()));

            if (!jniFunctions().getIsSameObject().invoke(jni, systemLoader, appLoader)) {
                builtinClassLoaders = new JNIObjectHandle[3];
                builtinClassLoaders[2] = agent.handles().newTrackedGlobalRef(jni, appLoader);
            } else {
                builtinClassLoaders = new JNIObjectHandle[2];
            }
            builtinClassLoaders[1] = agent.handles().newTrackedGlobalRef(jni, platformLoader);
        } else {
            guarantee(getPlatformLoader.isNull() && getAppLoader.isNull());
            builtinClassLoaders = new JNIObjectHandle[1];
        }
        builtinClassLoaders[0] = agent.handles().newTrackedGlobalRef(jni, systemLoader);

        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullHandle()));
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
        if (result != JvmtiError.JVMTI_ERROR_NONE) {
            guarantee(br.optional, "Setting breakpoint failed");
            return null;
        }
        Breakpoint bp = new Breakpoint(br, clazz, method);
        if (map.put(method.rawValue(), bp) != null) {
            throw VMError.shouldNotReachHere("Duplicate breakpoint: " + bp);
        }
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

    /**
     * If the given class is a proxy implementing a single interface, returns this interface. This
     * prevents classes with arbitrary names from being exposed outside the agent, since those names
     * only make sense within a single execution of the program.
     *
     * @param env JNI environment of the thread running the JVMTI callback.
     * @param clazz Handle to the class.
     * @return The interface, or the original class if it is not a proxy or implements multiple
     *         interfaces.
     */
    public static JNIObjectHandle getClassOrSingleProxyInterface(JNIEnvironment env, JNIObjectHandle clazz) {
        boolean isProxy = Support.callStaticBooleanMethodL(env, agent.handles().getJavaLangReflectProxy(env), agent.handles().getJavaLangReflectProxyIsProxyClass(env), clazz);
        if (Support.clearException(env) || !isProxy) {
            return clazz;
        }

        JNIObjectHandle interfaces = Support.callObjectMethod(env, clazz, agent.handles().javaLangClassGetInterfaces);
        if (Support.clearException(env) || interfaces.equal(nullHandle())) {
            return clazz;
        }

        int interfacesLength = Support.jniFunctions().getGetArrayLength().invoke(env, interfaces);
        guarantee(!Support.clearException(env));
        if (interfacesLength != 1) {
            return clazz;
        }

        JNIObjectHandle iface = Support.jniFunctions().getGetObjectArrayElement().invoke(env, interfaces, 0);
        guarantee(!Support.clearException(env) && iface.notEqual(nullHandle()));

        return iface;
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
        builtinClassLoaders = null;
        installedBreakpoints = null;
        nativeBreakpoints = null;
        observedExplicitLoadClassCallSites = null;
        tracer = null;
    }

    private interface BreakpointHandler {
        boolean dispatch(JNIEnvironment jni, JNIObjectHandle thread, Breakpoint bp, InterceptedState state);
    }

    private static final BreakpointSpecification[] BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::forName),
                    brk("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", BreakpointInterceptor::forName),

                    brk("java/lang/Class", "getFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getFields),
                    brk("java/lang/Class", "getClasses", "()[Ljava/lang/Class;", BreakpointInterceptor::getClasses),
                    brk("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", BreakpointInterceptor::getDeclaredFields),
                    brk("java/lang/Class", "getDeclaredClasses", "()[Ljava/lang/Class;", BreakpointInterceptor::getDeclaredClasses),

                    brk("java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getField),
                    brk("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", BreakpointInterceptor::getDeclaredField),

                    brk("java/lang/Class", "getEnclosingMethod", "()Ljava/lang/reflect/Method;", BreakpointInterceptor::getEnclosingMethod),
                    brk("java/lang/Class", "getEnclosingConstructor", "()Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getEnclosingMethod),

                    brk("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethods),
                    brk("java/lang/Class", "getConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructors),
                    brk("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethods),
                    brk("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getDeclaredConstructors),

                    brk("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethod),
                    brk("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),
                    brk("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethod),
                    brk("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),

                    brk("java/lang/Class", "newInstance", "()Ljava/lang/Object;", BreakpointInterceptor::newInstance),
                    brk("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;", BreakpointInterceptor::newArrayInstance),
                    brk("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;", BreakpointInterceptor::newArrayInstanceMulti),

                    brk("java/lang/ClassLoader", "findSystemClass", "(Ljava/lang/String;)Ljava/lang/Class;",
                                    BreakpointInterceptor::findSystemClass),

                    brk("jdk/internal/loader/BuiltinClassLoader", "findResource", "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::findResource),
                    brk("jdk/internal/loader/BuiltinClassLoader", "findResourceAsStream", "(Ljava/lang/String;Ljava/lang/String;)Ljava/io/InputStream;", BreakpointInterceptor::findResource),
                    brk("jdk/internal/loader/Loader", "findResource", "(Ljava/lang/String;Ljava/lang/String;)Ljava/net/URL;", BreakpointInterceptor::findResource),

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

                    brk("java/lang/invoke/SerializedLambda", "readResolve", "()Ljava/lang/Object;", BreakpointInterceptor::serializedLambdaReadResolve),
                    brk("java/io/ObjectInputStream", "resolveClass", "(Ljava/io/ObjectStreamClass;)Ljava/lang/Class;", BreakpointInterceptor::readClassDescriptor),
                    brk("java/io/ObjectStreamClass", "<init>", "(Ljava/lang/Class;)V", BreakpointInterceptor::objectStreamClassConstructor),
                    brk("jdk/internal/reflect/ReflectionFactory",
                                    "newConstructorForSerialization",
                                    "(Ljava/lang/Class;Ljava/lang/reflect/Constructor;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::customTargetConstructorSerialization),
                    optionalBrk("java/util/ResourceBundle",
                                    "getBundleImpl",
                                    "(Ljava/lang/Module;Ljava/lang/Module;Ljava/lang/String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
                                    BreakpointInterceptor::getBundleImpl),

                    // In Java 9+, these are Java methods that call private methods
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/Class;Ljava/lang/String;)J", BreakpointInterceptor::objectFieldOffsetByName),

                    brk("sun/misc/Unsafe", "allocateInstance", "(Ljava/lang/Class;)Ljava/lang/Object;", BreakpointInterceptor::allocateInstance),

                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findStatic",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findMethodHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findVirtual",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findMethodHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findConstructor",
                                    "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findConstructorHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findSpecial",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findSpecialHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "bind",
                                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::bindHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findGetter",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findSetter",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findStaticGetter",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findStaticSetter",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    /* VarHandles were introduced in Java 9 */
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findVarHandle",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findStaticVarHandle",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;",
                                    BreakpointInterceptor::findFieldHandle),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "findClass",
                                    "(Ljava/lang/String;)Ljava/lang/Class;",
                                    BreakpointInterceptor::findClass),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "unreflect",
                                    "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::unreflectMethod),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "unreflectConstructor",
                                    "(Ljava/lang/reflect/Constructor;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::unreflectConstructor),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "unreflectGetter",
                                    "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::unreflectField),
                    optionalBrk("java/lang/invoke/MethodHandles$Lookup", "unreflectSetter",
                                    "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;",
                                    BreakpointInterceptor::unreflectField),
                    optionalBrk("java/lang/invoke/MethodHandleProxies", "asInterfaceInstance",
                                    "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
                                    BreakpointInterceptor::asInterfaceInstance),
                    optionalBrk("java/lang/invoke/ConstantBootstraps", "getStaticFinal",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;",
                                    (jni, thread, bp, state) -> BreakpointInterceptor.constantBootstrapGetStaticFinal(jni, thread, bp, state, true)),
                    optionalBrk("java/lang/invoke/ConstantBootstraps", "getStaticFinal",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                                    (jni, thread, bp, state) -> BreakpointInterceptor.constantBootstrapGetStaticFinal(jni, thread, bp, state, false)),
                    optionalBrk("java/lang/invoke/MethodType", "fromMethodDescriptorString",
                                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                                    BreakpointInterceptor::methodTypeFromDescriptor),
                    optionalBrk("java/lang/Class", "getRecordComponents", "()[Ljava/lang/reflect/RecordComponent;",
                                    BreakpointInterceptor::getRecordComponents),
                    optionalBrk("java/lang/Class", "getPermittedSubclasses", "()[Ljava/lang/Class;",
                                    BreakpointInterceptor::getPermittedSubclasses),
                    optionalBrk("java/lang/Class", "getNestMembers", "()[Ljava/lang/Class;",
                                    BreakpointInterceptor::getNestMembers),
                    optionalBrk("java/lang/Class", "getSigners", "()[Ljava/lang/Object;",
                                    BreakpointInterceptor::getSigners)
    };

    private static boolean allocateInstance(JNIEnvironment jni, JNIObjectHandle thread, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle clazz = getObjectArgument(thread, 1);
        traceAllocateInstance(jni, clazz, !clearException(jni), state, callerClass);
        return true;
    }

    private static final BreakpointSpecification CLASSLOADER_LOAD_CLASS_BREAKPOINT_SPECIFICATION = optionalBrk("java/lang/ClassLoader", "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::loadClass);

    private static final NativeBreakpointSpecification[] NATIVE_BREAKPOINT_SPECIFICATIONS = {
                    NATIVE_ALLOCATE_INSTANCE_BREAKPOINT_SPEC
    };

    private static final BreakpointSpecification[] REFLECTION_ACCESS_BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeMethod),
                    brk("sun/reflect/misc/MethodUtil", "invoke", "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeMethod),
                    brk("java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeConstructor),
    };

    private static final BreakpointSpecification[] CLASS_PREDEFINITION_BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/invoke/MethodHandles$Lookup$ClassFile", "<init>", "(Ljava/lang/String;I[B)V", BreakpointInterceptor::onMethodHandleClassFileInit),
    };

    private static BreakpointSpecification brk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, false);
    }

    private static BreakpointSpecification optionalBrk(String className, String methodName, String signature, BreakpointHandler handler) {
        return new BreakpointSpecification(className, methodName, signature, handler, true);
    }

    private static class NativeBreakpointSpecification extends AbstractBreakpointSpecification {
        final CEntryPointLiteral<?> handlerLiteral;
        NativeBreakpoint installed;

        NativeBreakpointSpecification(String className, String methodName, String signature, CEntryPointLiteral<?> handlerLiteral) {
            super(className, methodName, signature, true);
            this.handlerLiteral = handlerLiteral;
        }
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

    private static class Breakpoint extends AbstractBreakpoint<BreakpointSpecification> {
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
