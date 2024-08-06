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

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;

public class NativeImageAgentJNIHandleSet extends JNIHandleSet {

    final JNIObjectHandle javaLangClass;
    final JNIMethodId javaLangClassForName3;
    final JNIObjectHandle javaLangClassNotFoundException;
    final JNIMethodId javaLangClassGetName;
    final JNIMethodId javaLangClassGetInterfaces;

    final JNIMethodId javaLangReflectMemberGetName;
    final JNIMethodId javaLangReflectMemberGetDeclaringClass;
    private JNIMethodId javaLangReflectExecutableGetParameterTypes = WordFactory.nullPointer();

    final JNIMethodId javaUtilEnumerationHasMoreElements;

    final JNIObjectHandle javaLangClassLoader;
    final JNIMethodId javaLangClassLoaderGetResource;

    final JNIObjectHandle jdkInternalReflectDelegatingClassLoader;

    final JNIMethodId javaLangObjectGetClass;

    final JNIObjectHandle javaLangStackOverflowError;

    private JNIMethodId javaLangInvokeMethodTypeParameterArray = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodTypeReturnType = WordFactory.nullPointer();
    final JNIObjectHandle javaLangIllegalAccessException;
    final JNIObjectHandle javaLangIllegalAccessError;
    final JNIObjectHandle javaLangInvokeWrongMethodTypeException;
    final JNIObjectHandle javaLangIllegalArgumentException;

    private JNIMethodId javaUtilResourceBundleGetBundleImplSLCC;
    private boolean queriedJavaUtilResourceBundleGetBundleImplSLCC;

    private JNIObjectHandle javaIoObjectStreamClass;
    private JNIMethodId javaIoObjectStreamClassForClass;
    private JNIMethodId javaIoObjectStreamClassGetName;
    private JNIMethodId javaIoObjectStreamClassGetClassDataLayout0;
    private JNIObjectHandle javaIOObjectStreamClassClassDataSlot;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotDesc;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotHasData;

    private JNIMethodId javaLangReflectConstructorDeclaringClassName;

    private JNIObjectHandle javaLangReflectProxy = WordFactory.nullPointer();
    private JNIMethodId javaLangReflectProxyIsProxyClass = WordFactory.nullPointer();

    final JNIObjectHandle javaUtilLocale;
    final JNIMethodId javaUtilLocaleGetLanguage;
    final JNIMethodId javaUtilLocaleGetCountry;
    final JNIMethodId javaUtilLocaleGetVariant;
    final JNIMethodId javaUtilLocaleForLanguageTag;
    final JNIMethodId javaUtilLocaleToLanguageTag;
    final JNIMethodId javaUtilLocaleEquals;

    final JNIFieldId javaLangInvokeSerializedLambdaCapturingClass;

    final JNIMethodId javaLangModuleGetName;

    private JNIMethodId javaLangInvokeCallSiteMakeSite = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSiteImpl = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSite = WordFactory.nullPointer();

    NativeImageAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        javaLangClass = newClassGlobalRef(env, "java/lang/Class");
        javaLangClassForName3 = getMethodId(env, javaLangClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", true);
        javaLangClassNotFoundException = newClassGlobalRef(env, "java/lang/ClassNotFoundException");
        javaLangClassGetName = getMethodId(env, javaLangClass, "getName", "()Ljava/lang/String;", false);
        javaLangClassGetInterfaces = getMethodId(env, javaLangClass, "getInterfaces", "()[Ljava/lang/Class;", false);

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

        javaLangStackOverflowError = newClassGlobalRef(env, "java/lang/StackOverflowError");

        javaLangIllegalAccessException = newClassGlobalRef(env, "java/lang/IllegalAccessException");
        javaLangIllegalAccessError = newClassGlobalRef(env, "java/lang/IllegalAccessError");
        javaLangInvokeWrongMethodTypeException = newClassGlobalRef(env, "java/lang/invoke/WrongMethodTypeException");
        javaLangIllegalArgumentException = newClassGlobalRef(env, "java/lang/IllegalArgumentException");

        JNIObjectHandle serializedLambda = findClass(env, "java/lang/invoke/SerializedLambda");
        javaLangInvokeSerializedLambdaCapturingClass = getFieldId(env, serializedLambda, "capturingClass", "Ljava/lang/Class;", false);

        JNIObjectHandle javaLangModule = findClass(env, "java/lang/Module");
        javaLangModuleGetName = getMethodId(env, javaLangModule, "getName", "()Ljava/lang/String;", false);

        javaUtilLocale = newClassGlobalRef(env, "java/util/Locale");
        javaUtilLocaleGetLanguage = getMethodId(env, javaUtilLocale, "getLanguage", "()Ljava/lang/String;", false);
        javaUtilLocaleGetCountry = getMethodId(env, javaUtilLocale, "getCountry", "()Ljava/lang/String;", false);
        javaUtilLocaleGetVariant = getMethodId(env, javaUtilLocale, "getVariant", "()Ljava/lang/String;", false);
        javaUtilLocaleForLanguageTag = getMethodId(env, javaUtilLocale, "forLanguageTag", "(Ljava/lang/String;)Ljava/util/Locale;", true);
        javaUtilLocaleEquals = getMethodId(env, javaUtilLocale, "equals", "(Ljava/lang/Object;)Z", false);
        javaUtilLocaleToLanguageTag = getMethodId(env, javaUtilLocale, "toLanguageTag", "()Ljava/lang/String;", false);
    }

    JNIMethodId getJavaLangReflectExecutableGetParameterTypes(JNIEnvironment env) {
        if (javaLangReflectExecutableGetParameterTypes.isNull()) {
            JNIObjectHandle javaLangReflectExecutable = findClass(env, "java/lang/reflect/Executable");
            javaLangReflectExecutableGetParameterTypes = getMethodId(env, javaLangReflectExecutable, "getParameterTypes", "()[Ljava/lang/Class;", false);
        }
        return javaLangReflectExecutableGetParameterTypes;
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

    JNIObjectHandle getJavaIOObjectStreamClass(JNIEnvironment env) {
        if (javaIoObjectStreamClass.equal(nullHandle())) {
            javaIoObjectStreamClass = findClass(env, "java/io/ObjectStreamClass");
        }
        return javaIoObjectStreamClass;
    }

    JNIMethodId getJavaIoObjectStreamClassForClass(JNIEnvironment env, JNIObjectHandle javaIoObjectStreamClassParam) {
        if (javaIoObjectStreamClassForClass.equal(nullHandle())) {
            javaIoObjectStreamClassForClass = getMethodId(env, javaIoObjectStreamClassParam, "forClass", "()Ljava/lang/Class;", false);
        }
        return javaIoObjectStreamClassForClass;
    }

    JNIMethodId getJavaIoObjectStreamClassGetName(JNIEnvironment env) {
        if (javaIoObjectStreamClassGetName.equal(nullHandle())) {
            javaIoObjectStreamClassGetName = getMethodId(env, getJavaIOObjectStreamClass(env), "getName", "()Ljava/lang/String;", false);
        }
        return javaIoObjectStreamClassGetName;
    }

    JNIMethodId getJavaIoObjectStreamClassGetClassDataLayout0(JNIEnvironment env, JNIObjectHandle javaIoObjectStreamClassParam) {
        if (javaIoObjectStreamClassGetClassDataLayout0.equal(nullHandle())) {
            javaIoObjectStreamClassGetClassDataLayout0 = getMethodId(env, javaIoObjectStreamClassParam, "getClassDataLayout0", "()[Ljava/io/ObjectStreamClass$ClassDataSlot;", false);
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

    JNIObjectHandle getJavaLangReflectProxy(JNIEnvironment env) {
        if (javaLangReflectProxy.equal(nullHandle())) {
            javaLangReflectProxy = newClassGlobalRef(env, "java/lang/reflect/Proxy");
        }
        return javaLangReflectProxy;
    }

    JNIMethodId getJavaLangReflectProxyIsProxyClass(JNIEnvironment env) {
        if (javaLangReflectProxyIsProxyClass.equal(nullHandle())) {
            javaLangReflectProxyIsProxyClass = getMethodId(env, getJavaLangReflectProxy(env), "isProxyClass", "(Ljava/lang/Class;)Z", true);
        }
        return javaLangReflectProxyIsProxyClass;
    }

    public JNIMethodId getJavaLangInvokeCallSiteMakeSite(JNIEnvironment env) {
        if (javaLangInvokeCallSiteMakeSite.isNull()) {
            JNIObjectHandle javaLangInvokeCallSite = findClass(env, "java/lang/invoke/CallSite");
            javaLangInvokeCallSiteMakeSite = getMethodId(env, javaLangInvokeCallSite, "makeSite",
                            "(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", true);
        }
        return javaLangInvokeCallSiteMakeSite;
    }

    public JNIMethodId getJavaLangInvokeMethodHandleNativesLinkCallSiteImpl(JNIEnvironment env) {
        if (javaLangInvokeMethodHandleNativesLinkCallSiteImpl.isNull()) {
            JNIObjectHandle javaLangInvokeMethodHandleNatives = findClass(env, "java/lang/invoke/MethodHandleNatives");
            javaLangInvokeMethodHandleNativesLinkCallSiteImpl = getMethodId(env, javaLangInvokeMethodHandleNatives, "linkCallSiteImpl",
                            "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;",
                            true);
        }
        return javaLangInvokeMethodHandleNativesLinkCallSiteImpl;
    }

    public JNIMethodId getJavaLangInvokeMethodHandleNativesLinkCallSite(JNIEnvironment env) {
        if (javaLangInvokeMethodHandleNativesLinkCallSite.isNull()) {
            JNIObjectHandle javaLangInvokeMethodHandleNatives = findClass(env, "java/lang/invoke/MethodHandleNatives");
            javaLangInvokeMethodHandleNativesLinkCallSite = getMethodIdOptional(env, javaLangInvokeMethodHandleNatives, "linkCallSite",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;", true);
        }
        return javaLangInvokeMethodHandleNativesLinkCallSite;
    }
}
