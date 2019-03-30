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

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import static org.graalvm.word.WordFactory.nullPointer;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.agent.jvmti.JvmtiFrameInfo;
import com.oracle.svm.agent.jvmti.JvmtiInterface;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public final class Support {

    public static void initialize(JvmtiEnv jvmti, JNIEnvironment localJni) {
        VMError.guarantee(jvmtiEnv.isNull() && jniFunctions.isNull() && handles == null);

        WordPointer functionsPtr = StackValue.get(WordPointer.class);
        check(jvmti.getFunctions().GetJNIFunctionTable().invoke(jvmti, functionsPtr));
        guarantee(functionsPtr.read() != nullPointer(), "Functions table must be initialized exactly once");

        jvmtiEnv = jvmti;
        jniFunctions = functionsPtr.read();
        handles = new JavaHandles(localJni);
    }

    public static void destroy(JNIEnvironment env) {
        jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), jniFunctions);
        handles().destroy(env);
        handles = null;
        jniFunctions = nullPointer();
        jvmtiEnv = nullPointer();
    }

    /** JVMTI environments, unlike those of JNI, can be safely shared across threads. */
    private static JvmtiEnv jvmtiEnv;

    /** The original unmodified JNI function table. */
    private static JNINativeInterface jniFunctions;

    private static JavaHandles handles;

    public static JvmtiEnv jvmtiEnv() {
        return jvmtiEnv;
    }

    public static JvmtiInterface jvmtiFunctions() {
        return jvmtiEnv.getFunctions();
    }

    public static JNINativeInterface jniFunctions() {
        return jniFunctions;
    }

    public static JavaHandles handles() {
        return handles;
    }

    public static final class JavaHandles {

        public final JNIMethodId javaLangClassGetName;
        public final JNIMethodId javaLangClassForName3;
        public final JNIMethodId javaLangReflectMemberGetDeclaringClass;
        public final JNIMethodId javaUtilEnumerationHasMoreElements;
        public final JNIObjectHandle javaLangSecurityException;
        public final JNIObjectHandle javaLangNoClassDefFoundError;
        public final JNIObjectHandle javaLangNoSuchMethodError;
        public final JNIObjectHandle javaLangNoSuchMethodException;
        public final JNIObjectHandle javaLangNoSuchFieldError;
        public final JNIObjectHandle javaLangNoSuchFieldException;
        public final JNIObjectHandle javaLangClassNotFoundException;

        // HotSpot crashes when looking these up eagerly
        private JNIObjectHandle javaLangReflectField;
        private JNIObjectHandle javaLangReflectMethod;
        private JNIObjectHandle javaLangReflectConstructor;

        private JNIObjectHandle javaUtilCollections;
        private JNIMethodId javaUtilCollectionsEmptyEnumeration;

        private JavaHandles(JNIEnvironment env) {
            JNIObjectHandle javaLangClass;
            try (CCharPointerHolder name = toCString("java/lang/Class")) {
                javaLangClass = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(javaLangClass.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("getName"); CCharPointerHolder signature = toCString("()Ljava/lang/String;")) {
                javaLangClassGetName = jniFunctions.getGetMethodID().invoke(env, javaLangClass, name.get(), signature.get());
                guarantee(javaLangClassGetName.isNonNull());
            }
            try (CCharPointerHolder name = toCString("forName"); CCharPointerHolder signature = toCString("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")) {
                javaLangClassForName3 = jniFunctions.getGetStaticMethodID().invoke(env, javaLangClass, name.get(), signature.get());
                guarantee(javaLangClassForName3.isNonNull());
            }

            JNIObjectHandle javaLangReflectMember;
            try (CCharPointerHolder name = toCString("java/lang/reflect/Member")) {
                javaLangReflectMember = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(javaLangReflectMember.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("getDeclaringClass"); CCharPointerHolder signature = toCString("()Ljava/lang/Class;")) {
                javaLangReflectMemberGetDeclaringClass = jniFunctions.getGetMethodID().invoke(env, javaLangReflectMember, name.get(), signature.get());
                guarantee(javaLangReflectMemberGetDeclaringClass.isNonNull());
            }

            JNIObjectHandle javaUtilEnumeration;
            try (CCharPointerHolder name = toCString("java/util/Enumeration")) {
                javaUtilEnumeration = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(javaUtilEnumeration.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("hasMoreElements"); CCharPointerHolder signature = toCString("()Z")) {
                javaUtilEnumerationHasMoreElements = jniFunctions.getGetMethodID().invoke(env, javaUtilEnumeration, name.get(), signature.get());
                guarantee(javaUtilEnumerationHasMoreElements.isNonNull());
            }

            try (CCharPointerHolder name = toCString("java/lang/SecurityException")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangSecurityException = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangSecurityException.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/NoClassDefFoundError")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangNoClassDefFoundError = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangNoClassDefFoundError.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/NoSuchMethodError")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangNoSuchMethodError = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangNoSuchMethodError.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/NoSuchMethodException")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangNoSuchMethodException = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangNoSuchMethodException.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/NoSuchFieldError")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangNoSuchFieldError = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangNoSuchFieldError.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/NoSuchFieldException")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangNoSuchFieldException = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangNoSuchFieldException.notEqual(nullHandle()));
            }
            try (CCharPointerHolder name = toCString("java/lang/ClassNotFoundException")) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                javaLangClassNotFoundException = jniFunctions.getNewGlobalRef().invoke(env, h);
                guarantee(javaLangClassNotFoundException.notEqual(nullHandle()));
            }
        }

        public JNIObjectHandle getJavaLangReflectField(JNIEnvironment env) {
            if (javaLangReflectField.equal(nullHandle())) {
                try (CCharPointerHolder name = toCString("java/lang/reflect/Field")) {
                    JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                    guarantee(h.notEqual(nullHandle()));
                    javaLangReflectField = jniFunctions.getNewGlobalRef().invoke(env, h);
                    guarantee(javaLangReflectField.notEqual(nullHandle()));
                }
            }
            return javaLangReflectField;
        }

        public JNIObjectHandle getJavaLangReflectMethod(JNIEnvironment env) {
            if (javaLangReflectMethod.equal(nullHandle())) {
                try (CCharPointerHolder name = toCString("java/lang/reflect/Method")) {
                    JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                    guarantee(h.notEqual(nullHandle()));
                    javaLangReflectMethod = jniFunctions.getNewGlobalRef().invoke(env, h);
                    guarantee(javaLangReflectMethod.notEqual(nullHandle()));
                }
            }
            return javaLangReflectMethod;
        }

        public JNIObjectHandle getJavaLangReflectConstructor(JNIEnvironment env) {
            if (javaLangReflectConstructor.equal(nullHandle())) {
                try (CCharPointerHolder name = toCString("java/lang/reflect/Constructor")) {
                    JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                    guarantee(h.notEqual(nullHandle()));
                    javaLangReflectConstructor = jniFunctions.getNewGlobalRef().invoke(env, h);
                    guarantee(javaLangReflectConstructor.notEqual(nullHandle()));
                }
            }
            return javaLangReflectConstructor;
        }

        public JNIObjectHandle getJavaUtilCollections(JNIEnvironment env) {
            if (javaUtilCollections.equal(nullHandle())) {
                try (CCharPointerHolder name = toCString("java/util/Collections")) {
                    JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                    guarantee(h.notEqual(nullHandle()));
                    javaUtilCollections = jniFunctions.getNewGlobalRef().invoke(env, h);
                    guarantee(javaUtilCollections.notEqual(nullHandle()));
                }
            }
            return javaUtilCollections;
        }

        public JNIMethodId getJavaUtilCollectionsEmptyEnumeration(JNIEnvironment env) {
            if (javaUtilCollectionsEmptyEnumeration.isNull()) {
                JNIObjectHandle collections = getJavaUtilCollections(env);
                try (CCharPointerHolder name = toCString("emptyEnumeration"); CCharPointerHolder signature = toCString("()Ljava/util/Enumeration;")) {
                    javaUtilCollectionsEmptyEnumeration = jniFunctions.getGetStaticMethodID().invoke(env, collections, name.get(), signature.get());
                    guarantee(javaUtilCollectionsEmptyEnumeration.isNonNull());
                }
            }
            return javaUtilCollectionsEmptyEnumeration;
        }

        public void destroy(JNIEnvironment env) {
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangSecurityException);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangNoClassDefFoundError);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangNoSuchMethodError);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangNoSuchMethodException);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangNoSuchFieldError);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangNoSuchFieldException);
            jniFunctions().getDeleteGlobalRef().invoke(env, javaLangClassNotFoundException);

            if (javaLangReflectField.notEqual(nullHandle())) {
                jniFunctions().getDeleteGlobalRef().invoke(env, javaLangReflectField);
            }
            if (javaLangReflectMethod.notEqual(nullHandle())) {
                jniFunctions().getDeleteGlobalRef().invoke(env, javaLangReflectMethod);
            }
            if (javaLangReflectConstructor.notEqual(nullHandle())) {
                jniFunctions().getDeleteGlobalRef().invoke(env, javaLangReflectConstructor);
            }

            if (javaUtilCollections.notEqual(nullHandle())) {
                jniFunctions().getDeleteGlobalRef().invoke(env, javaUtilCollections);
            }
        }
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

    public static CCharPointerHolder toCString(String s) {
        // TODO: this is supposed to produce modified UTF-8 when used with JNI.
        return CTypeConversion.toCString(s);
    }

    public static JNIObjectHandle getCallerClass(int depth) {
        JvmtiFrameInfo frameInfo = StackValue.get(JvmtiFrameInfo.class);
        CIntPointer countPtr = StackValue.get(CIntPointer.class);
        JvmtiError result = jvmtiFunctions().GetStackTrace().invoke(jvmtiEnv(), nullHandle(), depth, 1, frameInfo, countPtr);
        if (result == JvmtiError.JVMTI_ERROR_NONE && countPtr.read() == 1) {
            WordPointer declaringPtr = StackValue.get(WordPointer.class);
            result = jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), frameInfo.getMethod(), declaringPtr);
            if (result == JvmtiError.JVMTI_ERROR_NONE) {
                return declaringPtr.read();
            }
        }
        return nullHandle();
    }

    public static JNIObjectHandle getObjectArgument(int slot) {
        WordPointer handlePtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetLocalObject().invoke(jvmtiEnv(), nullHandle(), 0, slot, handlePtr) != JvmtiError.JVMTI_ERROR_NONE) {
            return nullHandle();
        }
        return handlePtr.read();
    }

    public static Object getClassNameOr(JNIEnvironment env, JNIObjectHandle clazz, Object forNullHandle, Object forNullNameOrException) {
        if (clazz.notEqual(nullHandle())) {
            JNIObjectHandle clazzName = Support.jniFunctions().<JNIFunctionPointerTypes.CallObjectMethod0FunctionPointer> getCallObjectMethod()
                            .invoke(env, clazz, Support.handles().javaLangClassGetName);
            Object result = Support.fromJniString(env, clazzName);
            if (result == null || clearException(env)) {
                result = forNullNameOrException;
            }
            return result;
        }
        return forNullHandle;
    }

    public static Object getClassNameOrNull(JNIEnvironment env, JNIObjectHandle clazz) {
        return getClassNameOr(env, clazz, null, null);
    }

    static JNIObjectHandle getMethodDeclaringClass(JNIMethodId method) {
        WordPointer declaringClass = StackValue.get(WordPointer.class);
        if (method.isNull() || jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), method, declaringClass) != JvmtiError.JVMTI_ERROR_NONE) {
            declaringClass.write(nullPointer());
        }
        return declaringClass.read();
    }

    static JNIObjectHandle getFieldDeclaringClass(JNIObjectHandle clazz, JNIFieldId method) {
        WordPointer declaringClass = StackValue.get(WordPointer.class);
        if (method.isNull() || jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, method, declaringClass) != JvmtiError.JVMTI_ERROR_NONE) {
            declaringClass.write(nullPointer());
        }
        return declaringClass.read();
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

    public static void checkNoException(JNIEnvironment localEnv) {
        VMError.guarantee(!testException(localEnv));
    }

    public static void check(JvmtiError resultCode) {
        guarantee(resultCode.equals(JvmtiError.JVMTI_ERROR_NONE));
    }

    public static void checkJni(int resultCode) {
        guarantee(resultCode == JNIErrors.JNI_OK());
    }

    public interface WordPredicate<T extends WordBase> {
        boolean test(T t);
    }

    public interface WordSupplier<T extends WordBase> {
        T get();
    }

    private Support() {
    }
}
