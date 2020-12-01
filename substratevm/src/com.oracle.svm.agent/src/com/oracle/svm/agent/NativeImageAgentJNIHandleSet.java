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

import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;

public class NativeImageAgentJNIHandleSet extends JNIHandleSet {

    final JNIObjectHandle javaLangClass;
    final JNIMethodId javaLangClassForName3;
    final JNIMethodId javaLangClassGetDeclaredMethod;
    final JNIMethodId javaLangClassGetDeclaredConstructor;
    final JNIMethodId javaLangClassGetDeclaredField;
    final JNIMethodId javaLangClassGetName;

    final JNIMethodId javaLangReflectMemberGetName;
    final JNIMethodId javaLangReflectMemberGetDeclaringClass;

    final JNIMethodId javaUtilEnumerationHasMoreElements;

    final JNIObjectHandle javaLangClassLoader;

    final JNIMethodId javaLangInvokeMemberNameGetDeclaringClass;
    final JNIMethodId javaLangInvokeMemberNameGetName;
    final JNIMethodId javaLangInvokeMemberNameGetParameterTypes;
    final JNIMethodId javaLangInvokeMemberNameIsMethod;
    final JNIMethodId javaLangInvokeMemberNameIsConstructor;
    final JNIMethodId javaLangInvokeMemberNameIsField;

    private JNIMethodId javaUtilResourceBundleGetBundleImplSLCC;
    private boolean queriedJavaUtilResourceBundleGetBundleImplSLCC;

    NativeImageAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        javaLangClass = newClassGlobalRef(env, "java/lang/Class");
        javaLangClassForName3 = getMethodId(env, javaLangClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", true);
        javaLangClassGetDeclaredMethod = getMethodId(env, javaLangClass, "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        javaLangClassGetDeclaredConstructor = getMethodId(env, javaLangClass, "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
        javaLangClassGetDeclaredField = getMethodId(env, javaLangClass, "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        javaLangClassGetName = getMethodId(env, javaLangClass, "getName", "()Ljava/lang/String;", false);

        JNIObjectHandle javaLangReflectMember = findClass(env, "java/lang/reflect/Member");
        javaLangReflectMemberGetName = getMethodId(env, javaLangReflectMember, "getName", "()Ljava/lang/String;", false);
        javaLangReflectMemberGetDeclaringClass = getMethodId(env, javaLangReflectMember, "getDeclaringClass", "()Ljava/lang/Class;", false);

        JNIObjectHandle javaUtilEnumeration = findClass(env, "java/util/Enumeration");
        javaUtilEnumerationHasMoreElements = getMethodId(env, javaUtilEnumeration, "hasMoreElements", "()Z", false);

        javaLangClassLoader = newClassGlobalRef(env, "java/lang/ClassLoader");

        JNIObjectHandle javaLangInvokeMemberName = findClass(env, "java/lang/invoke/MemberName");
        javaLangInvokeMemberNameGetDeclaringClass = getMethodId(env, javaLangInvokeMemberName, "getDeclaringClass", "()Ljava/lang/Class;", false);
        javaLangInvokeMemberNameGetName = getMethodId(env, javaLangInvokeMemberName, "getName", "()Ljava/lang/String;", false);
        javaLangInvokeMemberNameGetParameterTypes = getMethodId(env, javaLangInvokeMemberName, "getParameterTypes", "()[Ljava/lang/Class;", false);
        javaLangInvokeMemberNameIsMethod = getMethodId(env, javaLangInvokeMemberName, "isMethod", "()Z", false);
        javaLangInvokeMemberNameIsConstructor = getMethodId(env, javaLangInvokeMemberName, "isConstructor", "()Z", false);
        javaLangInvokeMemberNameIsField = getMethodId(env, javaLangInvokeMemberName, "isField", "()Z", false);
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
