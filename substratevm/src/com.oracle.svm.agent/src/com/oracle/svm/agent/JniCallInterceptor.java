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

import static com.oracle.svm.agent.BreakpointInterceptor.getTypeDescriptor;
import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;
import static com.oracle.svm.jvmtiagentbase.Support.checkJni;
import static com.oracle.svm.jvmtiagentbase.Support.checkNoException;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOr;
import static com.oracle.svm.jvmtiagentbase.Support.getFieldDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.getFieldName;
import static com.oracle.svm.jvmtiagentbase.Support.getMethodDeclaringClass;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.testException;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.function.Supplier;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.stackaccess.InterceptedState;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.DefineClassFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.FindClassFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.FromReflectedFieldFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.FromReflectedMethodFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.GetFieldIDFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.GetMethodIDFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.NewObjectArrayFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.ThrowNewFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.ToReflectedFieldFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIFunctionPointerTypes.ToReflectedMethodFunctionPointer;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNINativeInterface;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.AgentIsolate;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;

final class JniCallInterceptor {
    private static Tracer tracer;

    private static NativeImageAgent agent;

    private static Supplier<InterceptedState> interceptedStateSupplier;

    private static boolean shouldTrace() {
        return tracer != null;
    }

    private static InterceptedState initInterceptedState() {
        return interceptedStateSupplier.get();
    }

    private static void traceCall(JNIEnvironment env, String function, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, Object result, InterceptedState state,
                    Object... args) {
        JNIObjectHandle pending = jniFunctions().getExceptionOccurred().invoke(env);
        clearException(env);

        tracer.traceCall("jni",
                        function,
                        getTypeDescriptor(env, clazz),
                        getTypeDescriptor(env, declaringClass),
                        getClassNameOr(env, callerClass, null, Tracer.UNKNOWN_VALUE),
                        result,
                        state.getFullStackTraceOrNull(),
                        args);
        checkNoException(env);

        if (pending.notEqual(nullHandle())) {
            checkJni(jniFunctions().getThrow().invoke(env, pending));
        }
    }

    @CEntryPoint(name = "DefineClass")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle defineClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle loader, CCharPointer buf, int bufLen) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle result = jniFunctions().getDefineClass().invoke(env, name, loader, buf, bufLen);
        if (shouldTrace()) {
            traceCall(env, "DefineClass", nullHandle(), nullHandle(), callerClass, name.notEqual(nullHandle()), state, fromCString(name));
        }
        return result;
    }

    private static JNIObjectHandle getCallerClass(InterceptedState state, JNIEnvironment env) {
        try {
            return state.getDirectCallerClass();
        } finally {
            checkNoException(env);
        }
    }

    @CEntryPoint(name = "FindClass")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle findClass(JNIEnvironment env, CCharPointer name) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle result = jniFunctions().getFindClass().invoke(env, name);
        if (nullHandle().equal(result) || clearException(env)) {
            result = nullHandle();
        }
        if (shouldTrace()) {
            String className = fromCString(name);
            if (className != null) {
                traceCall(env, "FindClass", nullHandle(), nullHandle(), callerClass, name.notEqual(nullHandle()), state, className);
            }
        }
        return result;
    }

    @CEntryPoint(name = "AllocObject")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    static JNIObjectHandle allocObject(JNIEnvironment env, JNIObjectHandle clazz) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle result = jniFunctions().getAllocObject().invoke(env, clazz);
        if (nullHandle().equal(result) || clearException(env)) {
            result = nullHandle();
        }
        if (shouldTrace()) {
            traceCall(env, "AllocObject", clazz, nullHandle(), callerClass, clazz.notEqual(nullHandle()), state);
        }
        return result;

    }

    @CEntryPoint(name = "GetMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId getMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIMethodId result = jniFunctions().getGetMethodID().invoke(env, clazz, name, signature);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name.notEqual(nullHandle()) && signature.notEqual(nullHandle());
            traceCall(env, "GetMethodID", clazz, getMethodDeclaringClass(result), callerClass, shouldHandleCall, state, fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId getStaticMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIMethodId result = jniFunctions().getGetStaticMethodID().invoke(env, clazz, name, signature);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name.notEqual(nullHandle()) && signature.notEqual(nullHandle());
            traceCall(env, "GetStaticMethodID", clazz, getMethodDeclaringClass(result), callerClass, shouldHandleCall, state, fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId getFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIFieldId result = jniFunctions().getGetFieldID().invoke(env, clazz, name, signature);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name.notEqual(nullHandle()) && signature.notEqual(nullHandle());
            traceCall(env, "GetFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, shouldHandleCall, state, fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId getStaticFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIFieldId result = jniFunctions().getGetStaticFieldID().invoke(env, clazz, name, signature);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name.notEqual(nullHandle()) && signature.notEqual(nullHandle());
            traceCall(env, "GetStaticFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, shouldHandleCall, state, fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "ThrowNew")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static int throwNew(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer message) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        int result = jniFunctions().getThrowNew().invoke(env, clazz, message);
        if (shouldTrace()) {
            traceCall(env, "ThrowNew", clazz, nullHandle(), callerClass, clazz.notEqual(nullHandle()), state, Tracer.UNKNOWN_VALUE);
        }
        return result;
    }

    @CEntryPoint(name = "FromReflectedMethod")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId fromReflectedMethod(JNIEnvironment env, JNIObjectHandle method) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIMethodId result = jniFunctions().getFromReflectedMethod().invoke(env, method);
        JNIObjectHandle declaring = nullHandle();
        String name = null;
        String signature = null;
        if (result.isNonNull()) {
            declaring = getMethodDeclaringClass(result);
            CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
            CCharPointerPointer signaturePtr = StackValue.get(CCharPointerPointer.class);
            if (jvmtiFunctions().GetMethodName().invoke(jvmtiEnv(), result, namePtr, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
                name = fromCString(namePtr.read());
                signature = fromCString(signaturePtr.read());
                jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
                jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
            }
        }
        if (shouldTrace()) {
            traceCall(env, "FromReflectedMethod", declaring, nullHandle(), callerClass, result.isNonNull(), state, name, signature);
        }
        return result;
    }

    @CEntryPoint(name = "FromReflectedField")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId fromReflectedField(JNIEnvironment env, JNIObjectHandle field) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIFieldId result = jniFunctions().getFromReflectedField().invoke(env, field);
        JNIObjectHandle declaring = nullHandle();
        String name = Tracer.EXPLICIT_NULL;
        if (result.isNonNull()) {
            declaring = Support.callObjectMethod(env, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
            name = getFieldName(declaring, result);
        }
        if (shouldTrace()) {
            traceCall(env, "FromReflectedField", declaring, nullHandle(), callerClass, result.isNonNull(), state, name);
        }
        return result;
    }

    @CEntryPoint(name = "ToReflectedMethod")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle toReflectedMethod(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, boolean isStatic) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle declaring = getMethodDeclaringClass(method);
        String name = null;
        String signature = null;
        CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
        CCharPointerPointer signaturePtr = StackValue.get(CCharPointerPointer.class);
        if (jvmtiFunctions().GetMethodName().invoke(jvmtiEnv(), method, namePtr, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
            name = fromCString(namePtr.read());
            signature = fromCString(signaturePtr.read());
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
        }
        JNIObjectHandle result = jniFunctions().getToReflectedMethod().invoke(env, clazz, method, isStatic);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name != null && signature != null;
            traceCall(env, "ToReflectedMethod", clazz, declaring, callerClass, shouldHandleCall, state, name, signature);
        }
        return result;
    }

    @CEntryPoint(name = "ToReflectedField")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle toReflectedField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId field, boolean isStatic) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle declaring = getFieldDeclaringClass(clazz, field);
        String name = getFieldName(clazz, field);
        JNIObjectHandle result = jniFunctions().getToReflectedField().invoke(env, clazz, field, isStatic);
        if (shouldTrace()) {
            boolean shouldHandleCall = clazz.notEqual(nullHandle()) && name != null;
            traceCall(env, "ToReflectedField", clazz, declaring, callerClass, shouldHandleCall, state, name);
        }
        return result;
    }

    @CEntryPoint(name = "NewObjectArray")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle newObjectArray(JNIEnvironment env, int length, JNIObjectHandle elementClass, JNIObjectHandle initialElement) {
        InterceptedState state = initInterceptedState();
        JNIObjectHandle callerClass = getCallerClass(state, env);
        JNIObjectHandle result = jniFunctions().getNewObjectArray().invoke(env, length, elementClass, initialElement);
        JNIObjectHandle resultClass = nullHandle();
        if (result.notEqual(nullHandle()) && !testException(env)) {
            resultClass = jniFunctions().getGetObjectClass().invoke(env, result);
            if (clearException(env)) {
                resultClass = nullHandle();
            }
        }
        if (shouldTrace()) {
            traceCall(env, "NewObjectArray", resultClass, nullHandle(), callerClass, elementClass.notEqual(nullHandle()), state);
        }
        return result;
    }

    public static void onLoad(Tracer writer, NativeImageAgent nativeImageTracingAgent, Supplier<InterceptedState> interceptedStateProvider) {
        tracer = writer;
        JniCallInterceptor.interceptedStateSupplier = interceptedStateProvider;
        JniCallInterceptor.agent = nativeImageTracingAgent;
    }

    public static void onVMStart(JvmtiEnv jvmti) {
        WordPointer functionsPtr = StackValue.get(WordPointer.class);
        check(jvmti.getFunctions().GetJNIFunctionTable().invoke(jvmti, functionsPtr));
        JNINativeInterface functions = functionsPtr.read();
        functions.setDefineClass(defineClassLiteral.getFunctionPointer());
        functions.setFindClass(findClassLiteral.getFunctionPointer());
        functions.setAllocObject(allocObjectLiteral.getFunctionPointer());
        functions.setGetMethodID(getMethodIDLiteral.getFunctionPointer());
        functions.setGetStaticMethodID(getStaticMethodIDLiteral.getFunctionPointer());
        functions.setGetFieldID(getFieldIDLiteral.getFunctionPointer());
        functions.setGetStaticFieldID(getStaticFieldIDLiteral.getFunctionPointer());
        functions.setThrowNew(throwNewLiteral.getFunctionPointer());
        functions.setFromReflectedMethod(fromReflectedMethodLiteral.getFunctionPointer());
        functions.setToReflectedMethod(toReflectedMethodLiteral.getFunctionPointer());
        functions.setFromReflectedField(fromReflectedFieldLiteral.getFunctionPointer());
        functions.setToReflectedField(toReflectedFieldLiteral.getFunctionPointer());
        functions.setNewObjectArray(newObjectArrayLiteral.getFunctionPointer());
        check(jvmti.getFunctions().SetJNIFunctionTable().invoke(jvmti, functions));
        check(jvmti.getFunctions().Deallocate().invoke(jvmti, functions));
    }

    public static void onUnload() {
        jvmtiFunctions().SetJNIFunctionTable().invoke(jvmtiEnv(), jniFunctions()); // restore

        tracer = null;
    }

    private static final CEntryPointLiteral<DefineClassFunctionPointer> defineClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "defineClass", JNIEnvironment.class, CCharPointer.class, JNIObjectHandle.class, CCharPointer.class, int.class);

    private static final CEntryPointLiteral<FindClassFunctionPointer> findClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "findClass", JNIEnvironment.class, CCharPointer.class);

    private static final CEntryPointLiteral<FindClassFunctionPointer> allocObjectLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "allocObject", JNIEnvironment.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<GetMethodIDFunctionPointer> getMethodIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getMethodID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetMethodIDFunctionPointer> getStaticMethodIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getStaticMethodID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetFieldIDFunctionPointer> getFieldIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getFieldID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetFieldIDFunctionPointer> getStaticFieldIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getStaticFieldID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<ThrowNewFunctionPointer> throwNewLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "throwNew", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class);

    private static final CEntryPointLiteral<FromReflectedMethodFunctionPointer> fromReflectedMethodLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "fromReflectedMethod", JNIEnvironment.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<FromReflectedFieldFunctionPointer> fromReflectedFieldLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "fromReflectedField", JNIEnvironment.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<ToReflectedMethodFunctionPointer> toReflectedMethodLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "toReflectedMethod", JNIEnvironment.class, JNIObjectHandle.class, JNIMethodId.class, boolean.class);

    private static final CEntryPointLiteral<ToReflectedFieldFunctionPointer> toReflectedFieldLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "toReflectedField", JNIEnvironment.class, JNIObjectHandle.class, JNIFieldId.class, boolean.class);

    private static final CEntryPointLiteral<NewObjectArrayFunctionPointer> newObjectArrayLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "newObjectArray", JNIEnvironment.class, int.class, JNIObjectHandle.class, JNIObjectHandle.class);
}
