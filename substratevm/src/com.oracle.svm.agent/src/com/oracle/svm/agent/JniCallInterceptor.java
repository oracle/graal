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
import static com.oracle.svm.agent.Support.getClassNameOr;
import static com.oracle.svm.agent.Support.getFieldDeclaringClass;
import static com.oracle.svm.agent.Support.getMethodDeclaringClass;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.jvmtiEnv;
import static com.oracle.svm.agent.Support.jvmtiFunctions;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.restrict.JniAccessVerifier;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.DefineClassFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.FindClassFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.GetFieldIDFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.GetMemberIDFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

final class JniCallInterceptor {
    private static TraceWriter traceWriter;

    private static JniAccessVerifier accessVerifier;

    private static boolean shouldTrace() {
        return traceWriter != null;
    }

    private static void traceCall(JNIEnvironment env, String function, JNIObjectHandle clazz, JNIObjectHandle declaringClass, JNIObjectHandle callerClass, Object result, Object... args) {
        JNIObjectHandle pending = jniFunctions().getExceptionOccurred().invoke(env);
        clearException(env);

        traceWriter.traceCall("jni",
                        function,
                        getClassNameOr(env, clazz, null, TraceWriter.UNKNOWN_VALUE),
                        getClassNameOr(env, declaringClass, null, TraceWriter.UNKNOWN_VALUE),
                        getClassNameOr(env, callerClass, null, TraceWriter.UNKNOWN_VALUE),
                        result,
                        args);
        checkNoException(env);

        if (pending.notEqual(nullHandle())) {
            checkJni(jniFunctions().getThrow().invoke(env, pending));
        }
    }

    @CEntryPoint(name = "DefineClass")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIObjectHandle defineClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle loader, CCharPointer buf, int bufLen) {
        JNIObjectHandle callerClass = getCallerClass(env);
        if (accessVerifier != null && !accessVerifier.verifyDefineClass(env, name, loader, buf, bufLen, callerClass)) {
            return nullHandle();
        }
        JNIObjectHandle result = jniFunctions().getDefineClass().invoke(env, name, loader, buf, bufLen);
        if (shouldTrace()) {
            traceCall(env, "DefineClass", nullHandle(), nullHandle(), callerClass, result.notEqual(nullHandle()), fromCString(name));
        }
        return result;
    }

    private static JNIObjectHandle getCallerClass(JNIEnvironment env) {
        try {
            return Support.getCallerClass(0);
        } finally {
            checkNoException(env);
        }
    }

    @CEntryPoint(name = "FindClass")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIObjectHandle findClass(JNIEnvironment env, CCharPointer name) {
        JNIObjectHandle callerClass = getCallerClass(env);
        if (accessVerifier != null && !accessVerifier.verifyFindClass(env, name, callerClass)) {
            return nullHandle();
        }
        JNIObjectHandle result = jniFunctions().getFindClass().invoke(env, name);
        if (shouldTrace()) {
            traceCall(env, "FindClass", nullHandle(), nullHandle(), callerClass, result.notEqual(nullHandle()), fromCString(name));
        }
        return result;
    }

    @CEntryPoint(name = "GetMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIMethodId getMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIMethodId result = jniFunctions().getGetMethodID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetMethodID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetMethodID() above can have initialized `clazz` as a side effect
            return nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetMethodID", clazz, getMethodDeclaringClass(result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIMethodId getStaticMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIMethodId result = jniFunctions().getGetStaticMethodID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetMethodID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetStaticMethodID() above can have initialized `clazz` as a side effect
            return nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetStaticMethodID", clazz, getMethodDeclaringClass(result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIFieldId getFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIFieldId result = jniFunctions().getGetFieldID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetFieldID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetFieldID() above can have initialized `clazz` as a side effect
            return nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    private static JNIFieldId getStaticFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIFieldId result = jniFunctions().getGetStaticFieldID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetFieldID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetStaticFieldID() above can have initialized `clazz` as a side effect
            return nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetStaticFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    public static void onLoad(TraceWriter writer, JniAccessVerifier verifier) {
        accessVerifier = verifier;
        traceWriter = writer;
    }

    public static void onVMStart(JvmtiEnv jvmti) {
        WordPointer functionsPtr = StackValue.get(WordPointer.class);
        check(jvmti.getFunctions().GetJNIFunctionTable().invoke(jvmti, functionsPtr));
        JNINativeInterface functions = functionsPtr.read();
        functions.setDefineClass(defineClassLiteral.getFunctionPointer());
        functions.setFindClass(findClassLiteral.getFunctionPointer());
        functions.setGetMethodID(getMethodIDLiteral.getFunctionPointer());
        functions.setGetStaticMethodID(getStaticMethodIDLiteral.getFunctionPointer());
        functions.setGetFieldID(getFieldIDLiteral.getFunctionPointer());
        functions.setGetStaticFieldID(getStaticFieldIDLiteral.getFunctionPointer());
        check(jvmti.getFunctions().SetJNIFunctionTable().invoke(jvmti, functions));
        check(jvmti.getFunctions().Deallocate().invoke(jvmti, functions));
    }

    public static void onUnload() {
        jvmtiFunctions().SetJNIFunctionTable().invoke(jvmtiEnv(), jniFunctions()); // restore

        accessVerifier = null;
        traceWriter = null;
    }

    private static final CEntryPointLiteral<DefineClassFunctionPointer> defineClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "defineClass", JNIEnvironment.class, CCharPointer.class, JNIObjectHandle.class, CCharPointer.class, int.class);

    private static final CEntryPointLiteral<FindClassFunctionPointer> findClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "findClass", JNIEnvironment.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetMemberIDFunctionPointer> getMethodIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getMethodID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetMemberIDFunctionPointer> getStaticMethodIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getStaticMethodID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetFieldIDFunctionPointer> getFieldIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getFieldID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);

    private static final CEntryPointLiteral<GetFieldIDFunctionPointer> getStaticFieldIDLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "getStaticFieldID", JNIEnvironment.class, JNIObjectHandle.class, CCharPointer.class, CCharPointer.class);
}
