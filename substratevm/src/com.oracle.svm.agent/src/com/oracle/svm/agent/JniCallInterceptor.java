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
import static com.oracle.svm.jvmtiagentbase.Support.toCString;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.jvmtiagentbase.AgentIsolate;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.agent.restrict.JniAccessVerifier;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.DefineClassFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.FindClassFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.FromReflectedFieldFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.FromReflectedMethodFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.GetFieldIDFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.GetMethodIDFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.NewObjectArrayFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.ThrowNewFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.ToReflectedFieldFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.ToReflectedMethodFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

final class JniCallInterceptor {
    private static TraceWriter traceWriter;

    private static JniAccessVerifier accessVerifier;
    private static NativeImageAgent agent;

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
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle defineClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle loader, CCharPointer buf, int bufLen) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIObjectHandle result = nullHandle();
        if (accessVerifier == null || accessVerifier.verifyDefineClass(env, name, loader, buf, bufLen, callerClass)) {
            result = jniFunctions().getDefineClass().invoke(env, name, loader, buf, bufLen);
        }
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
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle findClass(JNIEnvironment env, CCharPointer name) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIObjectHandle result = nullHandle();
        if (accessVerifier == null || accessVerifier.verifyFindClass(env, name, callerClass)) {
            result = jniFunctions().getFindClass().invoke(env, name);
        }
        if (shouldTrace()) {
            traceCall(env, "FindClass", nullHandle(), nullHandle(), callerClass, result.notEqual(nullHandle()), fromCString(name));
        }
        return result;
    }

    @CEntryPoint(name = "GetMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId getMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIMethodId result = jniFunctions().getGetMethodID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetMethodID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetMethodID() above can have initialized `clazz` as a side effect
            result = nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetMethodID", clazz, getMethodDeclaringClass(result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticMethodID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId getStaticMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIMethodId result = jniFunctions().getGetStaticMethodID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetMethodID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetStaticMethodID() above can have initialized `clazz` as a side effect
            result = nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetStaticMethodID", clazz, getMethodDeclaringClass(result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId getFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIFieldId result = jniFunctions().getGetFieldID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetFieldID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetFieldID() above can have initialized `clazz` as a side effect
            result = nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "GetStaticFieldID")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId getStaticFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer name, CCharPointer signature) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIFieldId result = jniFunctions().getGetStaticFieldID().invoke(env, clazz, name, signature);
        if (result.isNonNull() && accessVerifier != null && !accessVerifier.verifyGetFieldID(env, clazz, name, signature, result, callerClass)) {
            // NOTE: GetStaticFieldID() above can have initialized `clazz` as a side effect
            result = nullPointer();
        }
        if (shouldTrace()) {
            traceCall(env, "GetStaticFieldID", clazz, getFieldDeclaringClass(clazz, result), callerClass, result.isNonNull(), fromCString(name), fromCString(signature));
        }
        return result;
    }

    @CEntryPoint(name = "ThrowNew")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static int throwNew(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer message) {
        JNIObjectHandle callerClass = getCallerClass(env);
        int result;
        if (accessVerifier == null || accessVerifier.verifyThrowNew(env, clazz, callerClass)) {
            result = jniFunctions().getThrowNew().invoke(env, clazz, message);
        } else { // throw NoSuchMethodError like HotSpot
            try (CCharPointerHolder errorMessage = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                            getClassNameOr(env, clazz, "(null)", "(?)") + "." + ConfigurationMethod.CONSTRUCTOR_NAME + "(Ljava/lang/String;)V")) {

                jniFunctions().getThrowNew().invoke(env, agent.handles().javaLangNoSuchMethodError, errorMessage.get());
            }
            result = JNIErrors.JNI_ERR();
        }
        if (shouldTrace()) {
            traceCall(env, "ThrowNew", clazz, nullHandle(), callerClass, (result == JNIErrors.JNI_OK()), TraceWriter.UNKNOWN_VALUE);
        }
        return result;
    }

    @CEntryPoint(name = "FromReflectedMethod")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIMethodId fromReflectedMethod(JNIEnvironment env, JNIObjectHandle method) {
        JNIObjectHandle callerClass = getCallerClass(env);
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

            if (accessVerifier != null && !accessVerifier.verifyFromReflectedMethod(env, declaring, name, signature, result, callerClass)) {
                result = nullPointer();
            }
        }
        if (shouldTrace()) {
            traceCall(env, "FromReflectedMethod", declaring, nullHandle(), callerClass, result.isNonNull(), name, signature);
        }
        return result;
    }

    @CEntryPoint(name = "FromReflectedField")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIFieldId fromReflectedField(JNIEnvironment env, JNIObjectHandle field) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIFieldId result = jniFunctions().getFromReflectedField().invoke(env, field);
        JNIObjectHandle declaring = nullHandle();
        String name = TraceWriter.EXPLICIT_NULL;
        if (result.isNonNull()) {
            declaring = Support.callObjectMethod(env, field, agent.handles().javaLangReflectMemberGetDeclaringClass);
            name = getFieldName(declaring, result);
            if (accessVerifier != null && !accessVerifier.verifyFromReflectedField(env, declaring, name, result, callerClass)) {
                result = nullPointer();
            }
        }
        if (shouldTrace()) {
            traceCall(env, "FromReflectedField", declaring, nullHandle(), callerClass, result.isNonNull(), name);
        }
        return result;
    }

    @CEntryPoint(name = "ToReflectedMethod")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle toReflectedMethod(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, boolean isStatic) {
        JNIObjectHandle callerClass = getCallerClass(env);
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
        JNIObjectHandle result = nullHandle();
        if (accessVerifier == null || accessVerifier.verifyToReflectedMethod(env, clazz, declaring, method, name, signature, callerClass)) {
            result = jniFunctions().getToReflectedMethod().invoke(env, clazz, method, isStatic);
        }
        if (shouldTrace()) {
            traceCall(env, "ToReflectedMethod", clazz, declaring, callerClass, result.notEqual(nullHandle()), name, signature);
        }
        return result;
    }

    @CEntryPoint(name = "ToReflectedField")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle toReflectedField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId field, boolean isStatic) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIObjectHandle declaring = getFieldDeclaringClass(clazz, field);
        String name = getFieldName(clazz, field);
        JNIObjectHandle result = nullHandle();
        if (accessVerifier == null || accessVerifier.verifyToReflectedField(env, clazz, declaring, name, field, callerClass)) {
            result = jniFunctions().getToReflectedField().invoke(env, clazz, field, isStatic);
        }
        if (shouldTrace()) {
            traceCall(env, "ToReflectedField", clazz, declaring, callerClass, result.notEqual(nullHandle()), name);
        }
        return result;
    }

    @CEntryPoint(name = "NewObjectArray")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class)
    private static JNIObjectHandle newObjectArray(JNIEnvironment env, int length, JNIObjectHandle elementClass, JNIObjectHandle initialElement) {
        JNIObjectHandle callerClass = getCallerClass(env);
        JNIObjectHandle result = jniFunctions().getNewObjectArray().invoke(env, length, elementClass, initialElement);
        JNIObjectHandle resultClass = nullHandle();
        if (result.notEqual(nullHandle()) && !testException(env)) {
            resultClass = jniFunctions().getGetObjectClass().invoke(env, result);
            if (clearException(env)) {
                resultClass = nullHandle();
            }
        }
        if (accessVerifier != null && !accessVerifier.verifyNewObjectArray(env, resultClass, callerClass)) {
            result = nullHandle();
        }
        if (shouldTrace()) {
            traceCall(env, "NewObjectArray", resultClass, nullHandle(), callerClass, result.notEqual(nullHandle()));
        }
        return result;
    }

    public static void onLoad(TraceWriter writer, JniAccessVerifier verifier, NativeImageAgent nativeImageTracingAgent) {
        accessVerifier = verifier;
        traceWriter = writer;
        JniCallInterceptor.agent = nativeImageTracingAgent;
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

        accessVerifier = null;
        traceWriter = null;
    }

    private static final CEntryPointLiteral<DefineClassFunctionPointer> defineClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "defineClass", JNIEnvironment.class, CCharPointer.class, JNIObjectHandle.class, CCharPointer.class, int.class);

    private static final CEntryPointLiteral<FindClassFunctionPointer> findClassLiteral = CEntryPointLiteral.create(JniCallInterceptor.class,
                    "findClass", JNIEnvironment.class, CCharPointer.class);

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
