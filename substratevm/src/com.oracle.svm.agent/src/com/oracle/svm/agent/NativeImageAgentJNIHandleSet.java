/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
    final JNIMethodId javaLangObjectToString;

    final JNIObjectHandle javaLangStackOverflowError;

    private JNIMethodId javaLangInvokeMethodTypeParameterArray = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodTypeReturnType = WordFactory.nullPointer();
    final JNIObjectHandle javaLangIllegalAccessException;
    final JNIObjectHandle javaLangIllegalAccessError;
    final JNIObjectHandle javaLangInvokeWrongMethodTypeException;
    final JNIObjectHandle javaLangIllegalArgumentException;

    private JNIMethodId javaUtilResourceBundleGetBundleImplSLCC;
    private boolean queriedJavaUtilResourceBundleGetBundleImplSLCC;

    private JNIMethodId javaIoObjectStreamClassForClass;
    private JNIMethodId javaIoObjectStreamClassGetName;
    private JNIMethodId javaIoObjectStreamClassGetClassDataLayout0;
    private JNIObjectHandle javaIOObjectStreamClassClassDataSlot;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotDesc;
    private JNIFieldId javaIOObjectStreamClassClassDataSlotHasData;

    private JNIMethodId javaUtilZipZipFileGetName;
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

    private JNIObjectHandle javaUtilOptional;
    private JNIMethodId javaUtilOptionalIsEmpty;
    private JNIMethodId javaUtilOptionalGet;

    private JNIMethodId javaUtilListToArray;

    final JNIFieldId javaLangInvokeSerializedLambdaCapturingClass;

    final JNIMethodId javaLangModuleGetName;

    private JNIMethodId javaLangInvokeCallSiteMakeSite = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSiteImpl = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodHandleNativesLinkCallSite = WordFactory.nullPointer();

    private JNIMethodId javaLangForeignFunctionDescriptorReturnLayout = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignFunctionDescriptorArgumentLayouts = WordFactory.nullPointer();

    private JNIObjectHandle javaLangForeignMemorySegment = WordFactory.nullPointer();
    private JNIObjectHandle javaLangForeignStructLayout = WordFactory.nullPointer();
    private JNIObjectHandle javaLangForeignUnionLayout = WordFactory.nullPointer();
    private JNIObjectHandle javaLangForeignSequenceLayout = WordFactory.nullPointer();
    private JNIObjectHandle javaLangForeignValueLayout = WordFactory.nullPointer();
    private JNIObjectHandle javaLangForeignPaddingLayout = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignGroupLayoutMemberLayouts = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignMemoryLayoutByteSize = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignValueLayoutCarrier = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignSequenceLayoutElementCount = WordFactory.nullPointer();
    private JNIMethodId javaLangForeignSequenceLayoutElementLayout = WordFactory.nullPointer();
    private JNIMethodId jdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment = WordFactory.nullPointer();
    private JNIMethodId jdkInternalForeignLayoutAbstractLayoutByteAlignment = WordFactory.nullPointer();

    private JNIObjectHandle javaLangConstantDirectMethodHandleDesc = WordFactory.nullPointer();
    private JNIMethodId javaLangInvokeMethodHandleDescribeConstable = WordFactory.nullPointer();
    private JNIMethodId javaLangConstantDirectMethodHandleDescRefKind = WordFactory.nullPointer();
    private JNIMethodId javaLangConstantDirectMethodHandleDescOwner = WordFactory.nullPointer();
    private JNIMethodId javaLangConstantDirectMethodHandleDescMethodName = WordFactory.nullPointer();
    private JNIMethodId javaLangConstantClassDescDescriptorString = WordFactory.nullPointer();

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

        JNIObjectHandle reflectLoader = findClassOptional(env, "jdk/internal/reflect/DelegatingClassLoader"); // JDK11-23
        jdkInternalReflectDelegatingClassLoader = reflectLoader.equal(nullHandle()) ? nullHandle() : newTrackedGlobalRef(env, reflectLoader);

        JNIObjectHandle javaLangObject = findClass(env, "java/lang/Object");
        javaLangObjectGetClass = getMethodId(env, javaLangObject, "getClass", "()Ljava/lang/Class;", false);
        javaLangObjectToString = getMethodId(env, javaLangObject, "toString", "()Ljava/lang/String;", false);

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

    private void initializeForeignHandles(JNIEnvironment env) {
        javaLangForeignMemorySegment = newClassGlobalRef(env, "java/lang/foreign/MemorySegment");
        javaLangForeignStructLayout = newClassGlobalRef(env, "java/lang/foreign/StructLayout");
        javaLangForeignUnionLayout = newClassGlobalRef(env, "java/lang/foreign/UnionLayout");
        javaLangForeignSequenceLayout = newClassGlobalRef(env, "java/lang/foreign/SequenceLayout");
        javaLangForeignValueLayout = newClassGlobalRef(env, "java/lang/foreign/ValueLayout");
        javaLangForeignPaddingLayout = newClassGlobalRef(env, "java/lang/foreign/PaddingLayout");

        JNIObjectHandle groupLayout = findClass(env, "java/lang/foreign/GroupLayout");
        javaLangForeignGroupLayoutMemberLayouts = getMethodId(env, groupLayout, "memberLayouts", "()Ljava/util/List;", false);

        JNIObjectHandle memoryLayout = findClass(env, "java/lang/foreign/MemoryLayout");
        javaLangForeignMemoryLayoutByteSize = getMethodId(env, memoryLayout, "byteSize", "()J", false);

        javaLangForeignValueLayoutCarrier = getMethodId(env, javaLangForeignValueLayout, "carrier", "()Ljava/lang/Class;", false);

        javaLangForeignSequenceLayoutElementCount = getMethodId(env, javaLangForeignSequenceLayout, "elementCount", "()J", false);
        javaLangForeignSequenceLayoutElementLayout = getMethodId(env, javaLangForeignSequenceLayout, "elementLayout", "()Ljava/lang/foreign/MemoryLayout;", false);

        JNIObjectHandle javaLangInvokeAbstractLayout = findClass(env, "jdk/internal/foreign/layout/AbstractLayout");
        jdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment = getMethodId(env, javaLangInvokeAbstractLayout, "hasNaturalAlignment", "()Z", false);
        jdkInternalForeignLayoutAbstractLayoutByteAlignment = getMethodId(env, javaLangInvokeAbstractLayout, "byteAlignment", "()J", false);

        JNIObjectHandle javaLangInvokeMethodHandle = findClass(env, "java/lang/invoke/MethodHandle");
        javaLangInvokeMethodHandleDescribeConstable = getMethodId(env, javaLangInvokeMethodHandle, "describeConstable", "()Ljava/util/Optional;", false);

        javaLangConstantDirectMethodHandleDesc = newClassGlobalRef(env, "java/lang/constant/DirectMethodHandleDesc");
        javaLangConstantDirectMethodHandleDescRefKind = getMethodId(env, javaLangConstantDirectMethodHandleDesc, "refKind", "()I", false);
        javaLangConstantDirectMethodHandleDescOwner = getMethodId(env, javaLangConstantDirectMethodHandleDesc, "owner", "()Ljava/lang/constant/ClassDesc;", false);
        javaLangConstantDirectMethodHandleDescMethodName = getMethodId(env, javaLangConstantDirectMethodHandleDesc, "methodName", "()Ljava/lang/String;", false);

        JNIObjectHandle javaLangConstantClassDesc = findClass(env, "java/lang/constant/ClassDesc");
        javaLangConstantClassDescDescriptorString = getMethodId(env, javaLangConstantClassDesc, "descriptorString", "()Ljava/lang/String;", false);
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

    JNIMethodId getJavaIoObjectStreamClassForClass(JNIEnvironment env, JNIObjectHandle javaIoObjectStreamClassParam) {
        if (javaIoObjectStreamClassForClass.equal(nullHandle())) {
            javaIoObjectStreamClassForClass = getMethodId(env, javaIoObjectStreamClassParam, "forClass", "()Ljava/lang/Class;", false);
        }
        return javaIoObjectStreamClassForClass;
    }

    JNIMethodId getJavaIoObjectStreamClassGetName(JNIEnvironment env) {
        if (javaIoObjectStreamClassGetName.equal(nullHandle())) {
            JNIObjectHandle javaIoObjectStreamClass = findClass(env, "java/io/ObjectStreamClass");
            javaIoObjectStreamClassGetName = getMethodId(env, javaIoObjectStreamClass, "getName", "()Ljava/lang/String;", false);
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

    JNIMethodId getJavaUtilZipZipFileGetName(JNIEnvironment env) {
        if (javaUtilZipZipFileGetName.equal(nullHandle())) {
            JNIObjectHandle javaUtilZipZipFile = findClass(env, "java/util/zip/ZipFile");
            javaUtilZipZipFileGetName = getMethodId(env, javaUtilZipZipFile, "getName", "()Ljava/lang/String;", false);
        }
        return javaUtilZipZipFileGetName;
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

    JNIObjectHandle getJavaUtilOptional(JNIEnvironment env) {
        if (javaUtilOptional.equal(nullHandle())) {
            javaUtilOptional = newClassGlobalRef(env, "java/util/Optional");
        }
        return javaUtilOptional;
    }

    JNIMethodId getJavaUtilOptionalIsEmpty(JNIEnvironment env) {
        if (javaUtilOptionalIsEmpty.isNull()) {
            javaUtilOptionalIsEmpty = getMethodId(env, getJavaUtilOptional(env), "isEmpty", "()Z", false);
        }
        return javaUtilOptionalIsEmpty;
    }

    JNIMethodId getJavaUtilOptionalGet(JNIEnvironment env) {
        if (javaUtilOptionalGet.isNull()) {
            javaUtilOptionalGet = getMethodId(env, getJavaUtilOptional(env), "get", "()Ljava/lang/Object;", false);
        }
        return javaUtilOptionalGet;
    }

    JNIMethodId getJavaUtilListToArray(JNIEnvironment env) {
        if (javaUtilListToArray.isNull()) {
            JNIObjectHandle javaUtilList = findClass(env, "java/util/List");
            javaUtilListToArray = getMethodId(env, javaUtilList, "toArray", "()[Ljava/lang/Object;", false);
        }
        return javaUtilListToArray;
    }

    JNIMethodId getJavaLangForeignFunctionDescriptorReturnLayout(JNIEnvironment env) {
        if (javaLangForeignFunctionDescriptorReturnLayout.isNull()) {
            JNIObjectHandle javaLangForeignFunctionDescriptor = findClass(env, "java/lang/foreign/FunctionDescriptor");
            javaLangForeignFunctionDescriptorReturnLayout = getMethodIdOptional(env, javaLangForeignFunctionDescriptor, "returnLayout",
                            "()Ljava/util/Optional;", false);
        }
        return javaLangForeignFunctionDescriptorReturnLayout;
    }

    JNIMethodId getJavaLangForeignFunctionDescriptorArgumentLayouts(JNIEnvironment env) {
        if (javaLangForeignFunctionDescriptorArgumentLayouts.isNull()) {
            JNIObjectHandle javaLangForeignFunctionDescriptor = findClass(env, "java/lang/foreign/FunctionDescriptor");
            javaLangForeignFunctionDescriptorArgumentLayouts = getMethodIdOptional(env, javaLangForeignFunctionDescriptor, "argumentLayouts",
                            "()Ljava/util/List;", false);
        }
        return javaLangForeignFunctionDescriptorArgumentLayouts;
    }

    JNIMethodId getJavaLangForeignGroupLayoutMemberLayouts(JNIEnvironment env) {
        if (javaLangForeignGroupLayoutMemberLayouts.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangForeignGroupLayoutMemberLayouts;
    }

    JNIMethodId getJavaLangForeignSequenceLayoutElementCount(JNIEnvironment env) {
        if (javaLangForeignSequenceLayoutElementCount.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangForeignSequenceLayoutElementCount;
    }

    JNIMethodId getJavaLangForeignSequenceLayoutElementLayout(JNIEnvironment env) {
        if (javaLangForeignSequenceLayoutElementLayout.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangForeignSequenceLayoutElementLayout;
    }

    JNIMethodId getJavaLangForeignValueLayoutCarrier(JNIEnvironment env) {
        if (javaLangForeignValueLayoutCarrier.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangForeignValueLayoutCarrier;
    }

    JNIMethodId getJavaLangForeignMemoryLayoutByteSize(JNIEnvironment env) {
        if (javaLangForeignMemoryLayoutByteSize.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangForeignMemoryLayoutByteSize;
    }

    JNIObjectHandle getJavaLangForeignPaddingLayout(JNIEnvironment env) {
        if (javaLangForeignPaddingLayout.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignPaddingLayout;
    }

    JNIObjectHandle getJavaLangForeignUnionLayout(JNIEnvironment env) {
        if (javaLangForeignUnionLayout.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignUnionLayout;
    }

    JNIObjectHandle getJavaLangForeignStructLayout(JNIEnvironment env) {
        if (javaLangForeignStructLayout.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignStructLayout;
    }

    JNIObjectHandle getJavaLangForeignSequenceLayout(JNIEnvironment env) {
        if (javaLangForeignSequenceLayout.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignSequenceLayout;
    }

    JNIObjectHandle getJavaLangForeignValueLayout(JNIEnvironment env) {
        if (javaLangForeignValueLayout.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignValueLayout;
    }

    JNIObjectHandle getJavaLangForeignMemorySegment(JNIEnvironment env) {
        if (javaLangForeignMemorySegment.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangForeignMemorySegment;
    }

    public JNIMethodId getJdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment(JNIEnvironment env) {
        if (jdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment.isNull()) {
            initializeForeignHandles(env);
        }
        return jdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment;
    }

    public JNIMethodId getJdkInternalForeignLayoutAbstractLayoutByteAlignment(JNIEnvironment env) {
        if (jdkInternalForeignLayoutAbstractLayoutByteAlignment.isNull()) {
            initializeForeignHandles(env);
        }
        return jdkInternalForeignLayoutAbstractLayoutByteAlignment;
    }

    public JNIMethodId getJavaLangInvokeMethodHandleDescribeConstable(JNIEnvironment env) {
        if (javaLangInvokeMethodHandleDescribeConstable.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangInvokeMethodHandleDescribeConstable;
    }

    public JNIObjectHandle getJavaLangConstantDirectMethodHandleDesc(JNIEnvironment env) {
        if (javaLangConstantDirectMethodHandleDesc.equal(nullHandle())) {
            initializeForeignHandles(env);
        }
        return javaLangConstantDirectMethodHandleDesc;
    }

    public JNIMethodId getJavaLangConstantDirectMethodHandleDescRefKind(JNIEnvironment env) {
        if (javaLangConstantDirectMethodHandleDescRefKind.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangConstantDirectMethodHandleDescRefKind;
    }

    public JNIMethodId getJavaLangConstantDirectMethodHandleDescOwner(JNIEnvironment env) {
        if (javaLangConstantDirectMethodHandleDescOwner.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangConstantDirectMethodHandleDescOwner;
    }

    public JNIMethodId getJavaLangConstantDirectMethodHandleDescMethodName(JNIEnvironment env) {
        if (javaLangConstantDirectMethodHandleDescMethodName.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangConstantDirectMethodHandleDescMethodName;
    }

    public JNIMethodId getJavaLangConstantClassDescDescriptorString(JNIEnvironment env) {
        if (javaLangConstantClassDescDescriptorString.isNull()) {
            initializeForeignHandles(env);
        }
        return javaLangConstantClassDescDescriptorString;
    }
}
