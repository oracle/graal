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

import org.graalvm.word.WordFactory;

import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;

public class NativeImageAgentJNIHandleSet extends JNIHandleSet {

    final JNIObjectHandle javaLangClass;
    final JNIMethodId javaLangClassForName3;
    final JNIMethodId javaLangClassGetName;

    final JNIMethodId javaLangReflectMemberGetName;
    final JNIMethodId javaLangReflectMemberGetDeclaringClass;

    final JNIMethodId javaUtilEnumerationHasMoreElements;

    final JNIObjectHandle javaLangClassLoader;
    final JNIMethodId javaLangClassLoaderGetResource;

    final JNIObjectHandle jdkInternalReflectDelegatingClassLoader;

    final JNIMethodId javaLangObjectGetClass;

    private JNIMethodId javaLangInvokeMethodTypeParameterArray = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodTypeReturnType = WordFactory.nullPointer();
    final JNIObjectHandle javaLangIllegalAccessException;
    final JNIObjectHandle javaLangInvokeWrongMethodTypeException;
    final JNIObjectHandle javaLangIllegalArgumentException;

    private JNIMethodId javaUtilResourceBundleGetBundleImplSLCC;
    private boolean queriedJavaUtilResourceBundleGetBundleImplSLCC;

    private JNIMethodId javaIoObjectStreamClassForClass;
    private JNIMethodId javaIoObjectStreamClassGetClassDataLayout0;
    private JNIObjectHandle javaIOObjectStreamClassClassDataSlot;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotDesc;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotHasData;

    private JNIMethodId javaLangReflectConstructorDeclaringClassName;

    NativeImageAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        javaLangClass = newClassGlobalRef(env, "java/lang/Class");
        javaLangClassForName3 = getMethodId(env, javaLangClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", true);
        javaLangClassGetName = getMethodId(env, javaLangClass, "getName", "()Ljava/lang/String;", false);

        JNIObjectHandle javaLangReflectMember = findClass(env, "java/lang/reflect/Member");
        javaLangReflectMemberGetName = getMethodId(env, javaLangReflectMember, "getName", "()Ljava/lang/String;", false);
        javaLangReflectMemberGetDeclaringClass = getMethodId(env, javaLangReflectMember, "getDeclaringClass", "()Ljava/lang/Class;", false);

        JNIObjectHandle javaUtilEnumeration = findClass(env, "java/util/Enumeration");
        javaUtilEnumerationHasMoreElements = getMethodId(env, javaUtilEnumeration, "hasMoreElements", "()Z", false);

        javaLangClassLoader = newClassGlobalRef(env, "java/lang/ClassLoader");
        javaLangClassLoaderGetResource = getMethodId(env, javaLangClassLoader, "getResource", "(Ljava/lang/String;)Ljava/net/URL;", false);

        JNIObjectHandle reflectLoader = findClassOptional(env, "jdk/internal/reflect/DelegatingClassLoader"); // JDK11+
        if (reflectLoader.equal(nullHandle())) {
            reflectLoader = findClass(env, "sun/reflect/DelegatingClassLoader"); // JDK 8
        }
        jdkInternalReflectDelegatingClassLoader = newTrackedGlobalRef(env, reflectLoader);

        JNIObjectHandle javaLangObject = findClass(env, "java/lang/Object");
        javaLangObjectGetClass = getMethodId(env, javaLangObject, "getClass", "()Ljava/lang/Class;", false);

        javaLangIllegalAccessException = newClassGlobalRef(env, "java/lang/IllegalAccessException");
        javaLangInvokeWrongMethodTypeException = newClassGlobalRef(env, "java/lang/invoke/WrongMethodTypeException");
        javaLangIllegalArgumentException = newClassGlobalRef(env, "java/lang/IllegalArgumentException");
    }

    JNIMethodId getJavaLangInvokeMethodTypeReturnType(JNIEnvironment env) {
        if (javaLangInvokeMethodTypeReturnType.isNull()) {
            JNIObjectHandle javaLangInvokeMethodType = newClassGlobalRef(env, "java/lang/invoke/MethodType");
            javaLangInvokeMethodTypeReturnType = getMethodId(env, javaLangInvokeMethodType, "returnType", "()Ljava/lang/Class;", false);
        }
        return javaLangInvokeMethodTypeReturnType;
    }

    JNIMethodId getJavaLangInvokeMethodTypeParameterArray(JNIEnvironment env) {
        if (javaLangInvokeMethodTypeParameterArray.isNull()) {
            JNIObjectHandle javaLangInvokeMethodType = newClassGlobalRef(env, "java/lang/invoke/MethodType");
            javaLangInvokeMethodTypeParameterArray = getMethodId(env, javaLangInvokeMethodType, "parameterArray", "()[Ljava/lang/Class;", false);
        }
        return javaLangInvokeMethodTypeParameterArray;
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

    JNIMethodId getJavaIoObjectStreamClassForClass(JNIEnvironment env, JNIObjectHandle javaIoObjectStreamClass) {
        if (javaIoObjectStreamClassForClass.equal(nullHandle())) {
            javaIoObjectStreamClassForClass = getMethodId(env, javaIoObjectStreamClass, "forClass", "()Ljava/lang/Class;", false);
        }
        return javaIoObjectStreamClassForClass;
    }

    JNIMethodId getJavaIoObjectStreamClassGetClassDataLayout0(JNIEnvironment env, JNIObjectHandle javaIoObjectStreamClass) {
        if (javaIoObjectStreamClassGetClassDataLayout0.equal(nullHandle())) {
            javaIoObjectStreamClassGetClassDataLayout0 = getMethodId(env, javaIoObjectStreamClass, "getClassDataLayout0", "()[Ljava/io/ObjectStreamClass$ClassDataSlot;", false);
        }
        return javaIoObjectStreamClassGetClassDataLayout0;
    }

    JNIObjectHandle getJavaIOObjectStreamClassClassDataSlot(JNIEnvironment env) {
        if (javaIOObjectStreamClassClassDataSlot.equal(nullHandle())) {
            javaIOObjectStreamClassClassDataSlot = newClassGlobalRef(env, "java/io/ObjectStreamClass$ClassDataSlot");
        }
        return javaIOObjectStreamClassClassDataSlot;
    }

    JNIFieldId getJavaIOObjectStreamClassClassDataSlotDesc(JNIEnvironment env) {
        if (javaIOObjectStreamClassClassDataSlotDesc.equal(nullHandle())) {
            javaIOObjectStreamClassClassDataSlotDesc = getFieldId(env, getJavaIOObjectStreamClassClassDataSlot(env), "desc", "Ljava/io/ObjectStreamClass;", false);
        }
        return javaIOObjectStreamClassClassDataSlotDesc;
    }

    JNIFieldId getJavaIOObjectStreamClassClassDataSlotHasData(JNIEnvironment env) {
        if (javaIOObjectStreamClassClassDataSlotHasData.equal(nullHandle())) {
            javaIOObjectStreamClassClassDataSlotHasData = getFieldId(env, getJavaIOObjectStreamClassClassDataSlot(env), "hasData", "Z", false);
        }
        return javaIOObjectStreamClassClassDataSlotHasData;
    }

    JNIMethodId getJavaLangReflectConstructorDeclaringClassName(JNIEnvironment env, JNIObjectHandle customSerializationConstructorClass) {
        if (javaLangReflectConstructorDeclaringClassName.equal(nullHandle())) {
            javaLangReflectConstructorDeclaringClassName = getMethodId(env, customSerializationConstructorClass, "getName", "()Ljava/lang/String;", false);
        }
        return javaLangReflectConstructorDeclaringClassName;
    }
}
