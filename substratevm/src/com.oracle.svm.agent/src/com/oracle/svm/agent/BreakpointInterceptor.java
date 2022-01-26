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

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.checkNoException;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOr;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.getObjectArgument;
import static com.oracle.svm.jvmtiagentbase.Support.handleException;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.testException;
import static com.oracle.svm.jvmtiagentbase.Support.toCString;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_BREAKPOINT;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_CLASS_FILE_LOAD_HOOK;
import static com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEvent.JVMTI_EVENT_CLASS_PREPARE;
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
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.stackaccess.InterceptedState;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.JNIObjectHandles;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;
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
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiLocationFormat;

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

    /*
     * NOTE: support for breakpoints in native methods has been removed.
     *
     * Restore from version control if needed.
     */

    /** Enables experimental support for instrumenting class lookups via {@code ClassLoader}. */
    private static boolean experimentalClassLoaderSupport = false;

    /** Enables experimental support for class definitions via {@code ClassLoader.defineClass}. */
    private static boolean experimentalClassDefineSupport = false;

    /** Enables tracking of reflection queries for fine-tuned configuration. */
    private static boolean trackReflectionMetadata = false;

    /**
     * Locations in methods where explicit calls to {@code ClassLoader.loadClass} have been found.
     */
    private static ConcurrentMap<MethodLocation, Boolean> observedExplicitLoadClassCallSites;

    private static final ThreadLocal<Boolean> recursive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /* Classes from these class loaders are assumed to not be dynamically loaded. */
    private static JNIObjectHandle[] builtinClassLoaders;

    private static void traceBreakpoint(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, String function, Object result,
                    JNIMethodId[] stackTrace, Object... args) {
        if (tracer != null) {
            tracer.traceCall("reflect",
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

    private static boolean forName(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(0);
        String className = fromJniString(jni, name);

        boolean classLoaderValid = true;
        WordPointer classLoaderPtr = StackValue.get(WordPointer.class);
        if (bp.method == agent.handles().javaLangClassForName3) {
            classLoaderValid = (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, 2, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
        } else {
            classLoaderPtr.write(nullHandle());
            if (callerClass.notEqual(nullHandle())) {
                /*
                 * NOTE: we use our direct caller class, but this class might be skipped over by
                 * Class.forName(nameOnly) in its security stackwalk for @CallerSensitive, leading
                 * to different behavior of our call and the original call.
                 */
                classLoaderValid = (jvmtiFunctions().GetClassLoader().invoke(jvmtiEnv(), callerClass, classLoaderPtr) == JvmtiError.JVMTI_ERROR_NONE);
            }
        }
        Object result = Tracer.UNKNOWN_VALUE;
        if (classLoaderValid) {
            /*
             * Even if the original call requested class initialization, disable it because
             * recursion checks keep us from seeing events of interest during initialization.
             */
            int initialize = 0;
            JNIObjectHandle loadedClass = Support.callStaticObjectMethodLIL(jni, bp.clazz, agent.handles().javaLangClassForName3, name, initialize, classLoaderPtr.read());
            if (clearException(jni)) {
                loadedClass = nullHandle();
            }
            result = loadedClass.notEqual(nullHandle());
        }

        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), className);
        return true;
    }

    private static boolean getFields(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetFields(jni, bp, state);
    }

    private static boolean getDeclaredFields(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetFields(jni, bp, state);
    }

    private static boolean handleGetFields(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getMethods(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, bp, state);
    }

    private static boolean getDeclaredMethods(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, bp, state);
    }

    private static boolean getConstructors(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, bp, state);
    }

    private static boolean getDeclaredConstructors(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethods(jni, bp, state);
    }

    private static boolean handleGetMethods(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getClasses(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, bp, state);
    }

    private static boolean getDeclaredClasses(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, bp, state);
    }

    private static boolean getPermittedSubclasses(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetClasses(jni, bp, state);
    }

    private static boolean handleGetClasses(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, null, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean getField(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetField(jni, bp, false, state);
    }

    private static boolean getDeclaredField(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetField(jni, bp, true, state);
    }

    private static boolean handleGetField(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
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
        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), getClassOrSingleProxyInterface(jni, declaring), callerClass, bp.specification.methodName, result.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static boolean objectFieldOffsetByName(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle declaring = getObjectArgument(1);
        JNIObjectHandle name = getObjectArgument(2);
        Support.callLongMethodLL(jni, self, bp.method, declaring, name);
        boolean validResult = !clearException(jni);

        JNIObjectHandle clazz = getMethodDeclaringClass(bp.method);
        traceBreakpoint(jni, clazz, declaring, callerClass, "objectFieldOffset", validResult, state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static boolean getConstructor(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle paramTypesHandle = getObjectArgument(1);
        JNIObjectHandle result = Support.callObjectMethodL(jni, self, bp.method, paramTypesHandle);
        if (clearException(jni)) {
            result = nullHandle();
        }
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), nullHandle(), callerClass, bp.specification.methodName, nullHandle().notEqual(result), state.getFullStackTraceOrNull(),
                        paramTypes);
        return true;
    }

    private static boolean getMethod(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethod(jni, bp, false, state);
    }

    private static boolean getDeclaredMethod(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetMethod(jni, bp, true, state);
    }

    private static boolean handleGetMethod(JNIEnvironment jni, Breakpoint bp, boolean declaredOnly, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
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
        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, self), getClassOrSingleProxyInterface(jni, declaring), callerClass, bp.specification.methodName, result.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), name, paramTypes);
        return true;
    }

    private static boolean getEnclosingMethod(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
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
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, enclosing.notEqual(nullHandle()) ? result : false, state.getFullStackTraceOrNull());
        return true;
    }

    private static boolean invokeMethod(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleInvokeMethod(jni, bp, state, true);
    }

    private static boolean unreflectMethod(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleInvokeMethod(jni, bp, state, false);
    }

    private static boolean handleInvokeMethod(JNIEnvironment jni, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state, boolean isInvoke) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle method = getObjectArgument(isInvoke ? 0 : 1);

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

        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, declaring), getClassOrSingleProxyInterface(jni, declaring), callerClass, "invokeMethod", declaring.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), name, paramTypes);

        /*
         * Calling Class.newInstance through Method.invoke should register the class for reflective
         * instantiation
         */
        if (isInvoke && isClassNewInstance(jni, declaring, name)) {
            JNIObjectHandle clazz = getObjectArgument(1);
            JNIMethodId result = newInstanceMethodID(jni, clazz);
            traceBreakpoint(jni, clazz, nullHandle(), callerClass, "newInstance", result.notEqual(nullHandle()), state.getFullStackTraceOrNull());
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

    private static boolean invokeConstructor(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleInvokeConstructor(jni, bp, state, getObjectArgument(0));
    }

    private static boolean unreflectConstructor(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleInvokeConstructor(jni, bp, state, getObjectArgument(1));
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

        traceBreakpoint(jni, getClassOrSingleProxyInterface(jni, declaring), getClassOrSingleProxyInterface(jni, declaring), callerClass, "invokeConstructor", declaring.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), paramTypes);
        return true;
    }

    private static boolean newInstance(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIMethodId result = newInstanceMethodID(jni, self);
        traceBreakpoint(jni, self, nullHandle(), callerClass, bp.specification.methodName, result.notEqual(nullHandle()), state.getFullStackTraceOrNull());
        return true;
    }

    private static JNIMethodId newInstanceMethodID(JNIEnvironment jni, JNIObjectHandle clazz) {
        JNIMethodId result = nullPointer();
        String name = "<init>";
        String signature = "()V";
        if (clazz.notEqual(nullHandle())) {
            try (CCharPointerHolder ctorName = toCString(name); CCharPointerHolder ctorSignature = toCString(signature)) {
                result = jniFunctions().getGetMethodID().invoke(jni, clazz, ctorName.get(), ctorSignature.get());
            }
            if (clearException(jni)) {
                result = nullPointer();
            }
        }
        return result;
    }

    private static boolean newArrayInstance(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle componentClass = getObjectArgument(0);
        CIntPointer lengthPtr = StackValue.get(CIntPointer.class);
        boolean lengthValid = (jvmtiFunctions().GetLocalInt().invoke(jvmtiEnv(), nullHandle(), 0, 1, lengthPtr) == JvmtiError.JVMTI_ERROR_NONE);

        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(componentClass);
        args.addressOf(1).setInt(lengthPtr.read());

        return newArrayInstance0(jni, bp, args, lengthValid, state);
    }

    private static boolean newArrayInstanceMulti(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle componentClass = getObjectArgument(0);
        JNIObjectHandle dimensionsArray = getObjectArgument(1);

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
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), resultClassName);
        return true;
    }

    private static boolean getResource(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetResources(jni, bp, false, state);
    }

    private static boolean getResources(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetResources(jni, bp, true, state);
    }

    private static boolean handleGetResources(JNIEnvironment jni, Breakpoint bp, boolean returnsEnumeration, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        boolean result;
        JNIObjectHandle returnValue = Support.callObjectMethodL(jni, self, bp.method, name);
        result = returnValue.notEqual(nullHandle());
        if (clearException(jni)) {
            result = false;
        }
        if (result && returnsEnumeration) {
            result = hasEnumerationElements(jni, returnValue);
        }
        JNIObjectHandle selfClazz = nullHandle(); // self is java.lang.ClassLoader, get its class
        if (self.notEqual(nullHandle())) {
            selfClazz = jniFunctions().getGetObjectClass().invoke(jni, self);
            if (clearException(jni)) {
                selfClazz = nullHandle();
            }
        }
        traceBreakpoint(jni, selfClazz, nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static boolean hasEnumerationElements(JNIEnvironment jni, JNIObjectHandle obj) {
        boolean hasElements = Support.callBooleanMethod(jni, obj, agent.handles().javaUtilEnumerationHasMoreElements);
        if (clearException(jni)) {
            hasElements = false;
        }
        return hasElements;
    }

    private static boolean getSystemResource(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetSystemResources(jni, bp, false, state);
    }

    private static boolean getSystemResources(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        return handleGetSystemResources(jni, bp, true, state);
    }

    private static boolean handleGetSystemResources(JNIEnvironment jni, Breakpoint bp, boolean returnsEnumeration, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle name = getObjectArgument(0);
        JNIObjectHandle returnValue = Support.callStaticObjectMethodL(jni, bp.clazz, bp.method, name);
        boolean result = returnValue.notEqual(nullHandle());
        if (clearException(jni)) {
            result = false;
        }
        if (result && returnsEnumeration) {
            result = hasEnumerationElements(jni, returnValue);
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), fromJniString(jni, name));
        return true;
    }

    private static boolean newProxyInstance(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        JNIObjectHandle invokeHandler = getObjectArgument(2);
        boolean result = nullHandle().notEqual(Support.callStaticObjectMethodLLL(jni, bp.clazz, bp.method, classLoader, ifaces, invokeHandler));
        if (clearException(jni)) {
            result = false;
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), Tracer.UNKNOWN_VALUE, ifaceNames,
                        Tracer.UNKNOWN_VALUE);
        return true;
    }

    private static boolean getProxyClass(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle classLoader = getObjectArgument(0);
        JNIObjectHandle ifaces = getObjectArgument(1);
        Object ifaceNames = getClassArrayNames(jni, ifaces);
        boolean result = nullHandle().notEqual(Support.callStaticObjectMethodLL(jni, bp.clazz, bp.method, classLoader, ifaces));
        if (clearException(jni)) {
            result = false;
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, bp.specification.methodName, result, state.getFullStackTraceOrNull(), Tracer.UNKNOWN_VALUE, ifaceNames);
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

    private static boolean getBundleImplJDK8OrEarlier(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        /* actual caller of a getBundle method */
        JNIObjectHandle callerClass = state.getCallerClass(2);
        JNIObjectHandle baseName = getObjectArgument(0);
        JNIObjectHandle locale = getObjectArgument(1);
        JNIObjectHandle loader = getObjectArgument(2);
        JNIObjectHandle control = getObjectArgument(3);
        JNIObjectHandle result = Support.callStaticObjectMethodLLLL(jni, bp.clazz, bp.method, baseName, locale, loader, control);
        BundleInfo bundleInfo = BundleInfo.NONE;
        if (clearException(jni)) {
            result = nullHandle();
        } else {
            bundleInfo = extractBundleInfo(jni, result);
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "getBundleImplJDK8OrEarlier", result.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), fromJniString(jni, baseName), Tracer.UNKNOWN_VALUE, Tracer.UNKNOWN_VALUE, Tracer.UNKNOWN_VALUE, bundleInfo.classNames, bundleInfo.locales);
        return true;
    }

    private static boolean getBundleImplJDK11OrLater(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIMethodId intermediateMethod = state.getCallerMethod(2);
        JNIMethodId callerMethod; // caller of getBundle(), not immediate caller
        if (intermediateMethod.equal(agent.handles().tryGetJavaUtilResourceBundleGetBundleImplSLCC(jni))) {
            // getBundleImpl <- getBundleImpl <- getBundleImpl(S,L,C,C) <- getBundle <- [caller]
            callerMethod = state.getCallerMethod(4);
        } else { // getBundleImpl <- getBundle(Impl|FromModule) <- getBundle <- [caller]
            callerMethod = state.getCallerMethod(3);
        }
        JNIObjectHandle callerClass = getMethodDeclaringClass(callerMethod);
        JNIObjectHandle callerModule = getObjectArgument(0);
        JNIObjectHandle module = getObjectArgument(1);
        JNIObjectHandle baseName = getObjectArgument(2);
        JNIObjectHandle locale = getObjectArgument(3);
        JNIObjectHandle control = getObjectArgument(4);
        JNIObjectHandle result = Support.callStaticObjectMethodLLLLL(jni, bp.clazz, bp.method, callerModule, module, baseName, locale, control);
        BundleInfo bundleInfo = BundleInfo.NONE;
        if (clearException(jni)) {
            result = nullHandle();
        } else {
            bundleInfo = extractBundleInfo(jni, result);
        }
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "getBundleImplJDK11OrLater", result.notEqual(nullHandle()),
                        state.getFullStackTraceOrNull(), Tracer.UNKNOWN_VALUE, Tracer.UNKNOWN_VALUE, fromJniString(jni, baseName), Tracer.UNKNOWN_VALUE, Tracer.UNKNOWN_VALUE, bundleInfo.classNames,
                        bundleInfo.locales);
        return true;
    }

    private static String readLocaleTag(JNIEnvironment jni, JNIObjectHandle locale) {
        JNIObjectHandle languageTag = Support.callObjectMethod(jni, locale, agent.handles().getJavaUtilLocaleToLanguageTag(jni));
        if (clearException(jni)) {
            /*- return root locale */
            return "";
        }
        return fromJniString(jni, languageTag);
    }

    private static final class BundleInfo {

        static final BundleInfo NONE = new BundleInfo(new String[0], new String[0]);

        final String[] classNames;
        final String[] locales;

        BundleInfo(String[] classNames, String[] locales) {
            this.classNames = classNames;
            this.locales = locales;
        }
    }

    /**
     * Traverses the bundle parent chain and collects classnames and locales of all encountered
     * bundles.
     *
     */
    private static BundleInfo extractBundleInfo(JNIEnvironment jni, JNIObjectHandle bundle) {
        List<String> locales = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        JNIObjectHandle curr = bundle;
        while (curr.notEqual(nullHandle())) {
            JNIObjectHandle locale = Support.callObjectMethod(jni, curr, agent.handles().getJavaUtilResourceBundleGetLocale(jni));
            if (clearException(jni)) {
                return BundleInfo.NONE;
            }
            String localeTag = readLocaleTag(jni, locale);
            if (localeTag.equals("und")) {
                /*- Root locale is serialized into "und" */
                localeTag = "";
            }
            JNIObjectHandle clazz = Support.callObjectMethod(jni, curr, agent.handles().javaLangObjectGetClass);
            if (!clearException(jni)) {
                JNIObjectHandle classNameHandle = Support.callObjectMethod(jni, clazz, agent.handles().javaLangClassGetName);
                if (!clearException(jni)) {
                    classNames.add(fromJniString(jni, classNameHandle));
                    locales.add(localeTag);
                }
            }
            curr = getResourceBundleParent(jni, curr);
        }
        return new BundleInfo(classNames.toArray(new String[0]), locales.toArray(new String[0]));
    }

    private static JNIObjectHandle getResourceBundleParent(JNIEnvironment jni, JNIObjectHandle bundle) {
        JNIObjectHandle parent = Support.readObjectField(jni, bundle, agent.handles().getJavaUtilResourceBundleParentField(jni));
        if (!clearException(jni)) {
            return parent;
        }
        return nullHandle();
    }

    private static boolean loadClass(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
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
        JNIObjectHandle self = getObjectArgument(0);
        JNIObjectHandle name = getObjectArgument(1);
        String className = fromJniString(jni, name);
        JNIObjectHandle clazz = Support.callObjectMethodL(jni, self, bp.method, name);
        if (clearException(jni)) {
            clazz = nullHandle();
        }
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, bp.specification.methodName, clazz.notEqual(nullHandle()), state.getFullStackTraceOrNull(), className);
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

    private static boolean findMethodHandle(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle declaringClass = getObjectArgument(1);
        JNIObjectHandle methodName = getObjectArgument(2);
        JNIObjectHandle methodType = getObjectArgument(3);

        JNIObjectHandle result = Support.callObjectMethodLLL(jni, lookup, bp.method, declaringClass, methodName, methodType);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), result, state.getFullStackTraceOrNull());
    }

    private static boolean findSpecialHandle(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle declaringClass = getObjectArgument(1);
        JNIObjectHandle methodName = getObjectArgument(2);
        JNIObjectHandle methodType = getObjectArgument(3);
        JNIObjectHandle specialCaller = getObjectArgument(4);

        JNIObjectHandle result = Support.callObjectMethodLLLL(jni, lookup, bp.method, declaringClass, methodName, methodType, specialCaller);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), result, state.getFullStackTraceOrNull());
    }

    private static boolean bindHandle(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle receiver = getObjectArgument(1);
        JNIObjectHandle methodName = getObjectArgument(2);
        JNIObjectHandle methodType = getObjectArgument(3);

        JNIObjectHandle result = Support.callObjectMethodLLL(jni, lookup, bp.method, receiver, methodName, methodType);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        JNIObjectHandle declaringClass = Support.callObjectMethod(jni, receiver, agent.handles().javaLangObjectGetClass);
        if (clearException(jni)) {
            declaringClass = nullHandle();
        }

        return methodMethodHandle(jni, declaringClass, callerClass, methodName, getParamTypes(jni, methodType), result, state.getFullStackTraceOrNull());
    }

    private static boolean methodMethodHandle(JNIEnvironment jni, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, JNIObjectHandle nameHandle, JNIObjectHandle paramTypesHandle,
                    JNIObjectHandle result, JNIMethodId[] stackTrace) {
        String name = fromJniString(jni, nameHandle);
        Object paramTypes = getClassArrayNames(jni, paramTypesHandle);
        traceBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findMethodHandle", result.notEqual(nullHandle()), stackTrace, name, paramTypes);
        return true;
    }

    private static boolean findConstructorHandle(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle declaringClass = getObjectArgument(1);
        JNIObjectHandle methodType = getObjectArgument(2);

        JNIObjectHandle result = Support.callObjectMethodLL(jni, lookup, bp.method, declaringClass, methodType);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        Object paramTypes = getClassArrayNames(jni, getParamTypes(jni, methodType));
        traceBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findConstructorHandle", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), paramTypes);
        return true;
    }

    private static JNIObjectHandle getParamTypes(JNIEnvironment jni, JNIObjectHandle methodType) {
        JNIObjectHandle paramTypesHandle = Support.callObjectMethod(jni, methodType, agent.handles().getJavaLangInvokeMethodTypeParameterArray(jni));
        if (clearException(jni)) {
            paramTypesHandle = nullHandle();
        }
        return paramTypesHandle;
    }

    private static boolean findFieldHandle(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle declaringClass = getObjectArgument(1);
        JNIObjectHandle fieldName = getObjectArgument(2);
        JNIObjectHandle fieldType = getObjectArgument(3);

        JNIObjectHandle result = Support.callObjectMethodLLL(jni, lookup, bp.method, declaringClass, fieldName, fieldType);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        String name = fromJniString(jni, fieldName);
        traceBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findFieldHandle", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean findClass(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle className = getObjectArgument(1);

        JNIObjectHandle result = Support.callObjectMethodL(jni, lookup, bp.method, className);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        String name = fromJniString(jni, className);
        traceBreakpoint(jni, bp.clazz, nullHandle(), callerClass, "findClass", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean unreflectField(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle field = getObjectArgument(1);

        JNIObjectHandle result = Support.callObjectMethodL(jni, lookup, bp.method, field);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessException);

        JNIObjectHandle declaringClass = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
        if (clearException(jni)) {
            declaringClass = nullHandle();
        }

        JNIObjectHandle fieldNameHandle = Support.callObjectMethod(jni, field, agent.handles().javaLangReflectMemberGetName);
        if (clearException(jni)) {
            fieldNameHandle = nullHandle();
        }

        String fieldName = fromJniString(jni, fieldNameHandle);
        traceBreakpoint(jni, declaringClass, nullHandle(), callerClass, "unreflectField", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), fieldName);
        return true;
    }

    private static boolean asInterfaceInstance(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle intfc = getObjectArgument(0);
        JNIObjectHandle methodHandle = getObjectArgument(1);

        JNIObjectHandle result = Support.callStaticObjectMethodLL(jni, bp.clazz, bp.method, intfc, methodHandle);
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangInvokeWrongMethodTypeException, agent.handles().javaLangIllegalArgumentException);

        JNIObjectHandle intfcNameHandle = Support.callObjectMethod(jni, intfc, agent.handles().javaLangClassGetName);
        if (clearException(jni)) {
            intfcNameHandle = nullHandle();
        }
        String intfcName = fromJniString(jni, intfcNameHandle);
        traceBreakpoint(jni, intfc, nullHandle(), callerClass, "asInterfaceInstance", result.notEqual(nullHandle()), state.getFullStackTraceOrNull());
        String[] intfcNames = new String[]{intfcName};
        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "newMethodHandleProxyInstance", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), (Object) intfcNames);
        return true;
    }

    private static boolean constantBootstrapGetStaticFinal(JNIEnvironment jni, Breakpoint bp, InterceptedState state, boolean hasDeclaringClass) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle lookup = getObjectArgument(0);
        JNIObjectHandle fieldName = getObjectArgument(1);
        JNIObjectHandle type = getObjectArgument(2);
        JNIObjectHandle declaringClass = hasDeclaringClass ? getObjectArgument(3) : type;

        JNIObjectHandle result;
        if (hasDeclaringClass) {
            result = Support.callStaticObjectMethodLLLL(jni, bp.clazz, bp.method, lookup, fieldName, type, declaringClass);
        } else {
            result = Support.callStaticObjectMethodLLL(jni, bp.clazz, bp.method, lookup, fieldName, type);
        }
        result = shouldIncludeMethod(jni, result, agent.handles().javaLangIllegalAccessError);

        String name = fromJniString(jni, fieldName);
        traceBreakpoint(jni, declaringClass, nullHandle(), callerClass, "findFieldHandle", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), name);
        return true;
    }

    private static boolean methodTypeFromDescriptor(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {
        JNIObjectHandle callerClass = state.getDirectCallerClass();
        JNIObjectHandle descriptor = getObjectArgument(0);
        JNIObjectHandle classLoader = getObjectArgument(1);

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

        traceBreakpoint(jni, nullHandle(), nullHandle(), callerClass, "methodTypeDescriptor", result.notEqual(nullHandle()), state.getFullStackTraceOrNull(), types);
        return true;
    }

    private static JNIObjectHandle shouldIncludeMethod(JNIEnvironment jni, JNIObjectHandle result, JNIObjectHandle... acceptedExceptions) {
        JNIObjectHandle exception = handleException(jni, true);
        if (exception.notEqual(nullHandle())) {
            for (JNIObjectHandle acceptedException : acceptedExceptions) {
                if (jniFunctions().getIsInstanceOf().invoke(jni, exception, acceptedException)) {
                    /*
                     * We include methods if the lookup returned an IllegalAccessException or a
                     * WrongMethodTypeException to make sure the right exception is thrown at
                     * runtime, instead of a NoSuchMethodException.
                     */
                    return JNIObjectHandles.createLocal(Boolean.TRUE);
                }
            }
            return nullHandle();
        }
        return result;
    }

    private static boolean objectStreamClassConstructor(JNIEnvironment jni, Breakpoint bp, InterceptedState state) {

        JNIObjectHandle serializeTargetClass = getObjectArgument(1);
        String serializeTargetClassName = getClassNameOrNull(jni, serializeTargetClass);

        JNIObjectHandle objectStreamClassInstance = Support.newObjectL(jni, bp.clazz, bp.method, serializeTargetClass);
        boolean validObjectStreamClassInstance = nullHandle().notEqual(objectStreamClassInstance);
        if (clearException(jni)) {
            validObjectStreamClassInstance = false;
        }

        // Skip Lambda class serialization
        if (serializeTargetClassName.contains("$$Lambda$")) {
            return true;
        }

        List<String> transitiveSerializeTargets = new ArrayList<>();
        transitiveSerializeTargets.add(serializeTargetClassName);

        /*
         * When the ObjectStreamClass instance is created for the given serializeTargetClass, some
         * additional ObjectStreamClass instances (usually the super classes) are created
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
                            String oscClassName = getClassNameOrNull(jni, oscClazz);
                            transitiveSerializeTargets.add(oscClassName);
                        }
                    }
                }
            }
        }
        for (String className : transitiveSerializeTargets) {
            if (tracer != null) {
                tracer.traceCall("serialization",
                                "ObjectStreamClass.<init>",
                                null,
                                null,
                                null,
                                validObjectStreamClassInstance,
                                state.getFullStackTraceOrNull(),
                                /*- String serializationTargetClass, String customTargetConstructorClass */
                                className, null);

                guarantee(!testException(jni));
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
    private static boolean customTargetConstructorSerialization(JNIEnvironment jni, @SuppressWarnings("unused") Breakpoint bp, InterceptedState state) {
        JNIObjectHandle serializeTargetClass = getObjectArgument(1);
        String serializeTargetClassName = getClassNameOrNull(jni, serializeTargetClass);

        // Skip Lambda class serialization.
        if (serializeTargetClassName.contains("$$Lambda$")) {
            return true;
        }

        JNIObjectHandle customConstructorObj = getObjectArgument(2);
        JNIObjectHandle customConstructorClass = jniFunctions().getGetObjectClass().invoke(jni, customConstructorObj);
        JNIMethodId getDeclaringClassNameMethodID = agent.handles().getJavaLangReflectConstructorDeclaringClassName(jni, customConstructorClass);
        JNIObjectHandle declaredClassNameObj = Support.callObjectMethod(jni, customConstructorObj, getDeclaringClassNameMethodID);
        String customConstructorClassName = fromJniString(jni, declaredClassNameObj);

        if (tracer != null) {
            tracer.traceCall("serialization",
                            "ObjectStreamClass.<init>",
                            null,
                            null,
                            null,
                            true,
                            state.getFullStackTraceOrNull(),
                            /*- String serializationTargetClass, String customTargetConstructorClass */
                            serializeTargetClassName, customConstructorClassName);

            guarantee(!testException(jni));
        }
        return true;
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
            InterceptedState state = interceptedStateSupplier.get();
            if (bp.specification.handler.dispatch(jni, bp, state)) {
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

    private static final CEntryPointLiteral<CFunctionPointer> onClassPrepareLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onClassPrepare",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<CFunctionPointer> onClassFileLoadHookLiteral = CEntryPointLiteral.create(BreakpointInterceptor.class, "onClassFileLoadHook",
                    JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class, JNIObjectHandle.class, CCharPointer.class, JNIObjectHandle.class, int.class, CCharPointer.class, CIntPointer.class,
                    CCharPointerPointer.class);

    public static void onLoad(JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, Tracer writer, NativeImageAgent nativeImageTracingAgent,
                    Supplier<InterceptedState> currentThreadJavaStackAccessSupplier,
                    boolean exptlClassLoaderSupport, boolean exptlClassDefineSupport, boolean trackReflectionData) {
        BreakpointInterceptor.tracer = writer;
        BreakpointInterceptor.agent = nativeImageTracingAgent;
        BreakpointInterceptor.interceptedStateSupplier = currentThreadJavaStackAccessSupplier;
        BreakpointInterceptor.experimentalClassLoaderSupport = exptlClassLoaderSupport;
        BreakpointInterceptor.experimentalClassDefineSupport = exptlClassDefineSupport;
        BreakpointInterceptor.trackReflectionMetadata = trackReflectionData;

        JvmtiCapabilities capabilities = UnmanagedMemory.calloc(SizeOf.get(JvmtiCapabilities.class));
        check(jvmti.getFunctions().GetCapabilities().invoke(jvmti, capabilities));
        capabilities.setCanGenerateBreakpointEvents(1);
        capabilities.setCanAccessLocalVariables(1);
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
            breakpointSpecifications = new BreakpointSpecification[BREAKPOINT_SPECIFICATIONS.length + REFLECTION_QUERIES_BREAKPOINT_SPECIFICATIONS.length];
            System.arraycopy(BREAKPOINT_SPECIFICATIONS, 0, breakpointSpecifications, 0, BREAKPOINT_SPECIFICATIONS.length);
            System.arraycopy(REFLECTION_QUERIES_BREAKPOINT_SPECIFICATIONS, 0, breakpointSpecifications, BREAKPOINT_SPECIFICATIONS.length, REFLECTION_QUERIES_BREAKPOINT_SPECIFICATIONS.length);
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

        if (experimentalClassDefineSupport) {
            setupClassLoadEvent(jvmti, jni);
        }

        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullHandle()));
        if (experimentalClassLoaderSupport) {
            check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JvmtiEventMode.JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullHandle()));
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

    public static void onUnload() {
        builtinClassLoaders = null;
        installedBreakpoints = null;
        observedExplicitLoadClassCallSites = null;
        tracer = null;
    }

    private interface BreakpointHandler {
        boolean dispatch(JNIEnvironment jni, Breakpoint bp, InterceptedState state);
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

                    brk("java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeMethod),
                    brk("sun/reflect/misc/MethodUtil", "invoke", "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeMethod),
                    brk("java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", BreakpointInterceptor::invokeConstructor),
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

                    brk("java/io/ObjectStreamClass", "<init>", "(Ljava/lang/Class;)V", BreakpointInterceptor::objectStreamClassConstructor),
                    brk("jdk/internal/reflect/ReflectionFactory",
                                    "newConstructorForSerialization",
                                    "(Ljava/lang/Class;Ljava/lang/reflect/Constructor;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::customTargetConstructorSerialization),
                    optionalBrk("java/util/ResourceBundle",
                                    "getBundleImpl",
                                    "(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/ClassLoader;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
                                    BreakpointInterceptor::getBundleImplJDK8OrEarlier),
                    optionalBrk("java/util/ResourceBundle",
                                    "getBundleImpl",
                                    "(Ljava/lang/Module;Ljava/lang/Module;Ljava/lang/String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;",
                                    BreakpointInterceptor::getBundleImplJDK11OrLater),

                    // In Java 9+, these are Java methods that call private methods
                    optionalBrk("jdk/internal/misc/Unsafe", "objectFieldOffset", "(Ljava/lang/Class;Ljava/lang/String;)J", BreakpointInterceptor::objectFieldOffsetByName),

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
                                    (jni, bp, state) -> BreakpointInterceptor.constantBootstrapGetStaticFinal(jni, bp, state, true)),
                    optionalBrk("java/lang/invoke/ConstantBootstraps", "getStaticFinal",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                                    (jni, bp, state) -> BreakpointInterceptor.constantBootstrapGetStaticFinal(jni, bp, state, false)),
                    optionalBrk("java/lang/invoke/MethodType", "fromMethodDescriptorString",
                                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                                    BreakpointInterceptor::methodTypeFromDescriptor),
                    optionalBrk("java/lang/Class", "getPermittedSubclasses", "()[Ljava/lang/Class;",
                                    BreakpointInterceptor::getPermittedSubclasses)
    };

    private static final BreakpointSpecification CLASSLOADER_LOAD_CLASS_BREAKPOINT_SPECIFICATION = optionalBrk("java/lang/ClassLoader", "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;", BreakpointInterceptor::loadClass);

    private static final BreakpointSpecification[] REFLECTION_QUERIES_BREAKPOINT_SPECIFICATIONS = {
                    brk("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethods),
                    brk("java/lang/Class", "getConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructors),
                    brk("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethods),
                    brk("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getDeclaredConstructors),

                    brk("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getMethod),
                    brk("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),
                    brk("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", BreakpointInterceptor::getDeclaredMethod),
                    brk("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", BreakpointInterceptor::getConstructor),
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
