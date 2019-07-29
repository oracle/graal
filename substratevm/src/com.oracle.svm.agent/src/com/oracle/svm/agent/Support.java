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
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
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

    static String getSystemProperty(JvmtiEnv jvmti, String propertyName) {
        try (CCharPointerHolder propertyKey = toCString(propertyName)) {
            CCharPointerPointer propertyValuePtr = StackValue.get(CCharPointerPointer.class);
            check(jvmti.getFunctions().GetSystemProperty().invoke(jvmti, propertyKey.get(), propertyValuePtr));
            String propertyValue = fromCString(propertyValuePtr.read());
            check(jvmti.getFunctions().Deallocate().invoke(jvmti, propertyValuePtr.read()));
            return propertyValue;
        } catch (Throwable t) {
            return null;
        }
    }

    static String[] getSystemProperties(JvmtiEnv jvmti) {
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
        private final ReentrantLock globalRefsLock = new ReentrantLock();
        private JNIObjectHandle[] globalRefs = new JNIObjectHandle[16];
        private int globalRefCount = 0;

        public final JNIMethodId javaLangClassGetName;
        public final JNIMethodId javaLangClassForName3;
        public final JNIMethodId javaLangReflectMemberGetName;
        public final JNIMethodId javaLangReflectMemberGetDeclaringClass;
        public final JNIMethodId javaUtilEnumerationHasMoreElements;
        public final JNIObjectHandle javaLangSecurityException;
        public final JNIObjectHandle javaLangNoClassDefFoundError;
        public final JNIObjectHandle javaLangNoSuchMethodError;
        public final JNIObjectHandle javaLangNoSuchMethodException;
        public final JNIObjectHandle javaLangNoSuchFieldError;
        public final JNIObjectHandle javaLangNoSuchFieldException;
        public final JNIObjectHandle javaLangClassNotFoundException;
        public final JNIObjectHandle javaLangRuntimeException;

        // HotSpot crashes when looking these up eagerly
        private JNIObjectHandle javaLangReflectField;
        private JNIObjectHandle javaLangReflectMethod;
        private JNIObjectHandle javaLangReflectConstructor;

        private JNIObjectHandle javaUtilCollections;
        private JNIMethodId javaUtilCollectionsEmptyEnumeration;

        private JavaHandles(JNIEnvironment env) {
            JNIObjectHandle javaLangClass = findClass(env, "java/lang/Class");
            try (CCharPointerHolder name = toCString("getName"); CCharPointerHolder signature = toCString("()Ljava/lang/String;")) {
                javaLangClassGetName = jniFunctions.getGetMethodID().invoke(env, javaLangClass, name.get(), signature.get());
                guarantee(javaLangClassGetName.isNonNull());
            }
            javaLangClassForName3 = getMethodId(env, javaLangClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", true);

            JNIObjectHandle javaLangReflectMember = findClass(env, "java/lang/reflect/Member");
            javaLangReflectMemberGetName = getMethodId(env, javaLangReflectMember, "getName", "()Ljava/lang/String;", false);
            javaLangReflectMemberGetDeclaringClass = getMethodId(env, javaLangReflectMember, "getDeclaringClass", "()Ljava/lang/Class;", false);

            JNIObjectHandle javaUtilEnumeration = findClass(env, "java/util/Enumeration");
            javaUtilEnumerationHasMoreElements = getMethodId(env, javaUtilEnumeration, "hasMoreElements", "()Z", false);

            javaLangSecurityException = newClassGlobalRef(env, "java/lang/SecurityException");
            javaLangNoClassDefFoundError = newClassGlobalRef(env, "java/lang/NoClassDefFoundError");
            javaLangNoSuchMethodError = newClassGlobalRef(env, "java/lang/NoSuchMethodError");
            javaLangNoSuchMethodException = newClassGlobalRef(env, "java/lang/NoSuchMethodException");
            javaLangNoSuchFieldError = newClassGlobalRef(env, "java/lang/NoSuchFieldError");
            javaLangNoSuchFieldException = newClassGlobalRef(env, "java/lang/NoSuchFieldException");
            javaLangClassNotFoundException = newClassGlobalRef(env, "java/lang/ClassNotFoundException");
            javaLangRuntimeException = newClassGlobalRef(env, "java/lang/RuntimeException");
        }

        private static JNIObjectHandle findClass(JNIEnvironment env, String className) {
            try (CCharPointerHolder name = toCString(className)) {
                JNIObjectHandle h = jniFunctions.getFindClass().invoke(env, name.get());
                guarantee(h.notEqual(nullHandle()));
                return h;
            }
        }

        private JNIObjectHandle newClassGlobalRef(JNIEnvironment env, String className) {
            return newGlobalRef(env, findClass(env, className));
        }

        private static JNIMethodId getMethodId(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, boolean isStatic) {
            try (CCharPointerHolder cname = toCString(name); CCharPointerHolder csignature = toCString(signature)) {
                JNIMethodId id;
                if (isStatic) {
                    id = jniFunctions.getGetStaticMethodID().invoke(env, clazz, cname.get(), csignature.get());
                } else {
                    id = jniFunctions.getGetMethodID().invoke(env, clazz, cname.get(), csignature.get());
                }
                guarantee(id.isNonNull());
                return id;
            }
        }

        private JNIObjectHandle newGlobalRef(JNIEnvironment env, JNIObjectHandle ref) {
            JNIObjectHandle global = jniFunctions.getNewGlobalRef().invoke(env, ref);
            guarantee(global.notEqual(nullHandle()));
            globalRefsLock.lock();
            try {
                if (globalRefCount == globalRefs.length) {
                    globalRefs = Arrays.copyOf(globalRefs, globalRefs.length * 2);
                }
                globalRefs[globalRefCount] = global;
                globalRefCount++;
            } finally {
                globalRefsLock.unlock();
            }
            return global;
        }

        public JNIObjectHandle getJavaLangReflectField(JNIEnvironment env) {
            if (javaLangReflectField.equal(nullHandle())) {
                javaLangReflectField = newClassGlobalRef(env, "java/lang/reflect/Field");
            }
            return javaLangReflectField;
        }

        public JNIObjectHandle getJavaLangReflectMethod(JNIEnvironment env) {
            if (javaLangReflectMethod.equal(nullHandle())) {
                javaLangReflectMethod = newClassGlobalRef(env, "java/lang/reflect/Method");
            }
            return javaLangReflectMethod;
        }

        public JNIObjectHandle getJavaLangReflectConstructor(JNIEnvironment env) {
            if (javaLangReflectConstructor.equal(nullHandle())) {
                javaLangReflectConstructor = newClassGlobalRef(env, "java/lang/reflect/Constructor");
            }
            return javaLangReflectConstructor;
        }

        public JNIObjectHandle getJavaUtilCollections(JNIEnvironment env) {
            if (javaUtilCollections.equal(nullHandle())) {
                javaUtilCollections = newClassGlobalRef(env, "java/util/Collections");
            }
            return javaUtilCollections;
        }

        public JNIMethodId getJavaUtilCollectionsEmptyEnumeration(JNIEnvironment env) {
            if (javaUtilCollectionsEmptyEnumeration.isNull()) {
                javaUtilCollectionsEmptyEnumeration = getMethodId(env, getJavaUtilCollections(env), "emptyEnumeration", "()Ljava/util/Enumeration;", true);
            }
            return javaUtilCollectionsEmptyEnumeration;
        }

        public void destroy(JNIEnvironment env) {
            for (int i = 0; i < globalRefCount; i++) {
                jniFunctions().getDeleteGlobalRef().invoke(env, globalRefs[i]);
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
        return getMethodDeclaringClass(getCallerMethod(depth));
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
            JNIObjectHandle clazzName = Support.jniFunctions().<JNIFunctionPointerTypes.CallObjectMethod0FunctionPointer> getCallObjectMethod()
                            .invoke(env, clazz, Support.handles().javaLangClassGetName);
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
