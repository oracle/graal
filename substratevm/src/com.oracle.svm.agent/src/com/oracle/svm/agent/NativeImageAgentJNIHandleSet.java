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

import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;

import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class NativeImageAgentJNIHandleSet extends JNIHandleSet {

    final JNIMethodId javaLangClassForName3;
    final JNIMethodId javaLangReflectMemberGetName;
    final JNIMethodId javaLangReflectMemberGetDeclaringClass;
    final JNIMethodId javaUtilEnumerationHasMoreElements;
    final JNIMethodId javaUtilMissingResourceExceptionCtor3;
    final JNIObjectHandle javaLangClassLoader;
    public final JNIObjectHandle javaLangSecurityException;
    public final JNIObjectHandle javaLangNoClassDefFoundError;
    public final JNIObjectHandle javaLangNoSuchMethodError;
    final JNIObjectHandle javaLangNoSuchMethodException;
    public final JNIObjectHandle javaLangNoSuchFieldError;
    final JNIObjectHandle javaLangNoSuchFieldException;
    final JNIObjectHandle javaLangClassNotFoundException;
    final JNIObjectHandle javaLangRuntimeException;
    final JNIObjectHandle javaUtilMissingResourceException;

    // HotSpot crashes when looking these up eagerly
    private JNIObjectHandle javaLangReflectField;
    private JNIObjectHandle javaLangReflectMethod;
    private JNIObjectHandle javaLangReflectConstructor;

    private JNIObjectHandle javaUtilCollections;
    private JNIMethodId javaUtilCollectionsEmptyEnumeration;

    private JNIMethodId javaUtilResourceBundleGetBundleImplSLCC;
    private boolean queriedJavaUtilResourceBundleGetBundleImplSLCC;

    NativeImageAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        JNIObjectHandle javaLangClass = findClass(env, "java/lang/Class");
        javaLangClassForName3 = getMethodId(env, javaLangClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", true);

        JNIObjectHandle javaLangReflectMember = findClass(env, "java/lang/reflect/Member");
        javaLangReflectMemberGetName = getMethodId(env, javaLangReflectMember, "getName", "()Ljava/lang/String;", false);
        javaLangReflectMemberGetDeclaringClass = getMethodId(env, javaLangReflectMember, "getDeclaringClass", "()Ljava/lang/Class;", false);

        JNIObjectHandle javaUtilEnumeration = findClass(env, "java/util/Enumeration");
        javaUtilEnumerationHasMoreElements = getMethodId(env, javaUtilEnumeration, "hasMoreElements", "()Z", false);

        javaLangClassLoader = newClassGlobalRef(env, "java/lang/ClassLoader");
        javaLangSecurityException = newClassGlobalRef(env, "java/lang/SecurityException");
        javaLangNoClassDefFoundError = newClassGlobalRef(env, "java/lang/NoClassDefFoundError");
        javaLangNoSuchMethodError = newClassGlobalRef(env, "java/lang/NoSuchMethodError");
        javaLangNoSuchMethodException = newClassGlobalRef(env, "java/lang/NoSuchMethodException");
        javaLangNoSuchFieldError = newClassGlobalRef(env, "java/lang/NoSuchFieldError");
        javaLangNoSuchFieldException = newClassGlobalRef(env, "java/lang/NoSuchFieldException");
        javaLangClassNotFoundException = newClassGlobalRef(env, "java/lang/ClassNotFoundException");
        javaLangRuntimeException = newClassGlobalRef(env, "java/lang/RuntimeException");
        javaUtilMissingResourceException = newClassGlobalRef(env, "java/util/MissingResourceException");
        javaUtilMissingResourceExceptionCtor3 = getMethodId(env, javaUtilMissingResourceException, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
    }

    public JNIObjectHandle getJavaLangReflectField(JNIEnvironment env) {
        if (javaLangReflectField.equal(nullHandle())) {
            javaLangReflectField = newClassGlobalRef(env, "java/lang/reflect/Field");
        }
        return javaLangReflectField;
    }

    JNIObjectHandle getJavaLangReflectMethod(JNIEnvironment env) {
        if (javaLangReflectMethod.equal(nullHandle())) {
            javaLangReflectMethod = newClassGlobalRef(env, "java/lang/reflect/Method");
        }
        return javaLangReflectMethod;
    }

    JNIObjectHandle getJavaLangReflectConstructor(JNIEnvironment env) {
        if (javaLangReflectConstructor.equal(nullHandle())) {
            javaLangReflectConstructor = newClassGlobalRef(env, "java/lang/reflect/Constructor");
        }
        return javaLangReflectConstructor;
    }

    JNIObjectHandle getJavaUtilCollections(JNIEnvironment env) {
        if (javaUtilCollections.equal(nullHandle())) {
            javaUtilCollections = newClassGlobalRef(env, "java/util/Collections");
        }
        return javaUtilCollections;
    }

    JNIMethodId getJavaUtilCollectionsEmptyEnumeration(JNIEnvironment env) {
        if (javaUtilCollectionsEmptyEnumeration.isNull()) {
            javaUtilCollectionsEmptyEnumeration = getMethodId(env, getJavaUtilCollections(env), "emptyEnumeration", "()Ljava/util/Enumeration;", true);
        }
        return javaUtilCollectionsEmptyEnumeration;
    }

    JNIMethodId tryGetJavaUtilResourceBundleGetBundleImplSLCC(JNIEnvironment env) {
        if (!queriedJavaUtilResourceBundleGetBundleImplSLCC) {
            JNIObjectHandle javaUtilResourceBundle = findClass(env, "java/util/ResourceBundle");
            javaUtilResourceBundleGetBundleImplSLCC = getMethodIdOptional(env, javaUtilResourceBundle, "getBundleImpl",
                            "(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/Class;Ljava/util/ResourceBundle$Control;)Ljava/util/ResourceBundle;", true);
            queriedJavaUtilResourceBundleGetBundleImplSLCC = true;
        }
        return javaUtilResourceBundleGetBundleImplSLCC;
    }
}
