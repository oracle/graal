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
package com.oracle.svm.jvmtiagentbase;

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import static org.graalvm.word.WordFactory.nullPointer;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiInterface;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;

/**
 * A utility class that contains helper methods for JNI/JVMTI that agents can use.
 */
public final class Support {

    public static boolean isInitialized() {
        boolean initialized = jvmtiEnv.isNonNull();
        assert initialized == jniFunctions.isNonNull();
        return initialized;
    }

    public static void initialize(JvmtiEnv jvmti) {
        VMError.guarantee(!isInitialized());

        WordPointer functionsPtr = StackValue.get(WordPointer.class);
        check(jvmti.getFunctions().GetJNIFunctionTable().invoke(jvmti, functionsPtr));
        guarantee(functionsPtr.read() != nullPointer(), "Functions table must be initialized exactly once");

        jvmtiEnv = jvmti;
        jniFunctions = functionsPtr.read();
    }

    public static void destroy() {
        jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), jniFunctions);
        jniFunctions = nullPointer();
        jvmtiEnv = nullPointer();
    }

    public static String getSystemProperty(JvmtiEnv jvmti, String propertyName) {
        try (CCharPointerHolder propertyKey = toCString(propertyName)) {
            String propertyValue = null;
            CCharPointerPointer propertyValuePtr = StackValue.get(CCharPointerPointer.class);
            if (jvmti.getFunctions().GetSystemProperty().invoke(jvmti, propertyKey.get(), propertyValuePtr) == JvmtiError.JVMTI_ERROR_NONE) {
                propertyValue = fromCString(propertyValuePtr.read());
                check(jvmti.getFunctions().Deallocate().invoke(jvmti, propertyValuePtr.read()));
            }
            return propertyValue;
        }
    }

    public static String[] getSystemProperties(JvmtiEnv jvmti) {
        CIntPointer countPtr = StackValue.get(CIntPointer.class);
        WordPointer propertyPtr = StackValue.get(WordPointer.class);
        check(jvmti.getFunctions().GetSystemProperties().invoke(jvmti, countPtr, propertyPtr));
        int numEntries = countPtr.read();
        CCharPointerPointer properties = propertyPtr.read();
        String[] result = new String[numEntries];
        for (int i = 0; i < numEntries; i++) {
            CCharPointer rawEntry = properties.read(i);
            result[i] = fromCString(rawEntry);
            check(jvmti.getFunctions().Deallocate().invoke(jvmti, rawEntry));
        }
        check(jvmti.getFunctions().Deallocate().invoke(jvmti, properties));
        return result;
    }

    /** JVMTI environments, unlike those of JNI, can be safely shared across threads. */
    private static JvmtiEnv jvmtiEnv;

    /** The original unmodified JNI function table. */
    private static JNINativeInterface jniFunctions;

    public static JvmtiEnv jvmtiEnv() {
        return jvmtiEnv;
    }

    public static JvmtiInterface jvmtiFunctions() {
        return jvmtiEnv.getFunctions();
    }

    public static JNINativeInterface jniFunctions() {
        return jniFunctions;
    }

    public static String fromCString(CCharPointer s) {
        return CTypeConversion.toJavaString(s, SubstrateUtil.strlen(s), StandardCharsets.UTF_8);
    }

    public static String fromJniString(JNIEnvironment env, JNIObjectHandle handle) {
        if (handle.notEqual(nullHandle())) {
            CCharPointer cstr = jniFunctions().getGetStringUTFChars().invoke(env, handle, nullPointer());
            if (cstr.isNonNull()) {
                try {
                    return fromCString(cstr);
                } finally {
                    jniFunctions().getReleaseStringUTFChars().invoke(env, handle, cstr);
                }
            }
        }
        return null;
    }

    public static JNIObjectHandle toJniString(JNIEnvironment jni, String string) {
        try (CCharPointerHolder cString = toCString(string)) {
            return jniFunctions().getNewStringUTF().invoke(jni, cString.get());
        }
    }

    public static CCharPointerHolder toCString(String s) {
        // TODO: this is supposed to produce modified UTF-8 when used with JNI.
        return CTypeConversion.toCString(s);
    }

    public static JNIObjectHandle getCallerClass(int depth) {
        return getMethodDeclaringClass(getCallerMethod(depth));
    }

    public static JNIObjectHandle getDirectCallerClass() {
        return getCallerClass(1);
    }

    public static JNIMethodId getCallerMethod(int depth) {
        JvmtiFrameInfo frameInfo = StackValue.get(JvmtiFrameInfo.class);
        CIntPointer countPtr = StackValue.get(CIntPointer.class);
        JvmtiError result = jvmtiFunctions().GetStackTrace().invoke(jvmtiEnv(), nullHandle(), depth, 1, frameInfo, countPtr);
        if (result == JvmtiError.JVMTI_ERROR_NONE && countPtr.read() == 1) {
            return frameInfo.getMethod();
        }
        return nullPointer();
    }

    public static JNIObjectHandle getObjectArgument(int slot) {
        WordPointer handlePtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, slot, handlePtr) != JvmtiError.JVMTI_ERROR_NONE) {
            return nullHandle();
        }
        return handlePtr.read();
    }

    public static String getClassNameOr(JNIEnvironment env, JNIObjectHandle clazz, String forNullHandle, String forNullNameOrException) {
        if (clazz.notEqual(nullHandle())) {
            JNIObjectHandle clazzName = callObjectMethod(env, clazz, JvmtiAgentBase.singleton().handles().javaLangClassGetName);
            String result = Support.fromJniString(env, clazzName);
            if (result == null || clearException(env)) {
                result = forNullNameOrException;
            }
            return result;
        }
        return forNullHandle;
    }

    public static String getClassNameOrNull(JNIEnvironment env, JNIObjectHandle clazz) {
        return getClassNameOr(env, clazz, null, null);
    }

    public static JNIObjectHandle getMethodDeclaringClass(JNIMethodId method) {
        WordPointer declaringClass = StackValue.get(WordPointer.class);
        if (method.isNull() || jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), method, declaringClass) != JvmtiError.JVMTI_ERROR_NONE) {
            declaringClass.write(nullPointer());
        }
        return declaringClass.read();
    }

    public static JNIObjectHandle getFieldDeclaringClass(JNIObjectHandle clazz, JNIFieldId method) {
        WordPointer declaringClass = StackValue.get(WordPointer.class);
        if (method.isNull() || jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, method, declaringClass) != JvmtiError.JVMTI_ERROR_NONE) {
            declaringClass.write(nullPointer());
        }
        return declaringClass.read();
    }

    public static String getFieldName(JNIObjectHandle clazz, JNIFieldId field) {
        String name = null;
        CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
        if (jvmtiFunctions().GetFieldName().invoke(jvmtiEnv(), clazz, field, namePtr, nullPointer(), nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
            name = fromCString(namePtr.read());
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
        }
        return name;
    }

    public static boolean clearException(JNIEnvironment localEnv) {
        if (jniFunctions().getExceptionCheck().invoke(localEnv)) {
            jniFunctions().getExceptionClear().invoke(localEnv);
            return true;
        }
        return false;
    }

    public static boolean testException(JNIEnvironment localEnv) {
        if (jniFunctions().getExceptionCheck().invoke(localEnv)) {
            jniFunctions().getExceptionDescribe().invoke(localEnv);
            return true;
        }
        return false;
    }

    /*
     * We use the Call*A functions that take a jvalue* for the Java arguments because that doesn't
     * require that calling conventions for a varargs call are the same as those for a regular call
     * (e.g. macOS on AArch), and doesn't require that smaller-than-word types can be passed as
     * words, instead using the known layout of the jvalue union.
     */

    public static JNIObjectHandle callObjectMethod(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method) {
        return jniFunctions().getCallObjectMethodA().invoke(env, obj, method, nullPointer());
    }

    public static JNIObjectHandle callObjectMethodL(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method, JNIObjectHandle l0) {
        JNIValue args = StackValue.get(1, JNIValue.class);
        args.setObject(l0);
        return jniFunctions().getCallObjectMethodA().invoke(env, obj, method, args);
    }

    public static JNIObjectHandle callObjectMethodLL(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method, JNIObjectHandle l0, JNIObjectHandle l1) {
        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        return jniFunctions().getCallObjectMethodA().invoke(env, obj, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, JNIObjectHandle l0) {
        JNIValue args = StackValue.get(1, JNIValue.class);
        args.setObject(l0);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodLL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, JNIObjectHandle l0, JNIObjectHandle l1) {
        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodLIL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, JNIObjectHandle l0, int i1, JNIObjectHandle l2) {
        JNIValue args = StackValue.get(3, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setInt(i1);
        args.addressOf(2).setObject(l2);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodLLL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method, JNIObjectHandle l0, JNIObjectHandle l1, JNIObjectHandle l2) {
        JNIValue args = StackValue.get(3, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        args.addressOf(2).setObject(l2);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodLLLL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method,
                    JNIObjectHandle l0, JNIObjectHandle l1, JNIObjectHandle l2, JNIObjectHandle l3) {

        JNIValue args = StackValue.get(4, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        args.addressOf(2).setObject(l2);
        args.addressOf(3).setObject(l3);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static JNIObjectHandle callStaticObjectMethodLLLLL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId method,
                    JNIObjectHandle l0, JNIObjectHandle l1, JNIObjectHandle l2, JNIObjectHandle l3, JNIObjectHandle l4) {

        JNIValue args = StackValue.get(5, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        args.addressOf(2).setObject(l2);
        args.addressOf(3).setObject(l3);
        args.addressOf(4).setObject(l4);
        return jniFunctions().getCallStaticObjectMethodA().invoke(env, clazz, method, args);
    }

    public static boolean callBooleanMethod(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method) {
        return jniFunctions().getCallBooleanMethodA().invoke(env, obj, method, nullPointer());
    }

    public static long callLongMethodL(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method, JNIObjectHandle l0) {
        JNIValue args = StackValue.get(1, JNIValue.class);
        args.addressOf(0).setObject(l0);
        return jniFunctions().getCallLongMethodA().invoke(env, obj, method, args);
    }

    public static long callLongMethodLL(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method, JNIObjectHandle l0, JNIObjectHandle l1) {
        JNIValue args = StackValue.get(2, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        return jniFunctions().getCallLongMethodA().invoke(env, obj, method, args);
    }

    public static int callIntMethodL(JNIEnvironment env, JNIObjectHandle obj, JNIMethodId method, JNIObjectHandle l0) {
        JNIValue args = StackValue.get(1, JNIValue.class);
        args.addressOf(0).setObject(l0);
        return jniFunctions().getCallIntMethodA().invoke(env, obj, method, args);
    }

    public static JNIObjectHandle newObjectLLL(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId ctor, JNIObjectHandle l0, JNIObjectHandle l1, JNIObjectHandle l2) {
        JNIValue args = StackValue.get(3, JNIValue.class);
        args.addressOf(0).setObject(l0);
        args.addressOf(1).setObject(l1);
        args.addressOf(2).setObject(l2);
        return jniFunctions().getNewObjectA().invoke(env, clazz, ctor, args);
    }

    public static void checkNoException(JNIEnvironment localEnv) {
        VMError.guarantee(!testException(localEnv));
    }

    public static void check(JvmtiError resultCode) {
        guarantee(resultCode.equals(JvmtiError.JVMTI_ERROR_NONE));
    }

    public static void checkJni(int resultCode) {
        guarantee(resultCode == JNIErrors.JNI_OK());
    }

    public interface WordSupplier<T extends WordBase> {
        T get();
    }

    private Support() {
    }
}
