/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;

public final class MissingReflectionRegistrationUtils extends MissingRegistrationUtils {

    public static void reportClassAccess(String className, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        String json = elementToJSON(namedConfigurationType(className));
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("access the class", quote(className), json, dynamicAccessMetadata),
                        Class.class, null, className, null);
        report(exception);
    }

    public static void reportUnsafeAllocation(Class<?> clazz, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        ConfigurationType type = getConfigurationType(clazz);
        type.setUnsafeAllocated();
        String json = elementToJSON(type);
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("unsafe instantiate", typeDescriptor(clazz), json, dynamicAccessMetadata),
                        Class.class, null, clazz.getTypeName(), null);
        report(exception);
    }

    public static void reportFieldQuery(Class<?> declaringClass, String fieldName) {
        reportFieldQuery(declaringClass, fieldName, null);
    }

    public static void reportFieldQuery(Class<?> declaringClass, String fieldName, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        ConfigurationType type = getConfigurationType(declaringClass);
        String json = elementToJSON(type);
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("access field", quote(declaringClass.getTypeName() + "#" + fieldName), json, dynamicAccessMetadata),
                        Field.class, declaringClass, fieldName, null);
        report(exception);
    }

    public static MissingReflectionRegistrationError reportAccessedField(Field field, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        ConfigurationType type = getConfigurationType(field.getDeclaringClass());
        addField(type, field.getName());
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("read or write field", quote(field.toString()), elementToJSON(type), dynamicAccessMetadata),
                        field.getClass(), field.getDeclaringClass(), field.getName(), null);
        report(exception);
        /*
         * If report doesn't throw, we throw the exception anyway since this is a Native
         * Image-specific error that is unrecoverable in any case.
         */
        throw exception;
    }

    public static void reportExecutableQuery(Class<?> declaringClass, String methodName, Class<?>[] paramTypes) {
        reportExecutableQuery(declaringClass, methodName, paramTypes, null);
    }

    public static void reportExecutableQuery(Class<?> declaringClass, String methodName, Class<?>[] paramTypes, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        StringJoiner paramTypeNames = new StringJoiner(", ", "(", ")");
        if (paramTypes != null) {
            for (Class<?> paramType : paramTypes) {
                paramTypeNames.add(paramType.getTypeName());
            }
        }
        ConfigurationType type = getConfigurationType(declaringClass);
        String json = elementToJSON(type);
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("access method", quote(declaringClass.getTypeName() + "#" + methodName + paramTypeNames), json, dynamicAccessMetadata),
                        Method.class, declaringClass, methodName, paramTypes);
        report(exception);
    }

    public static MissingReflectionRegistrationError reportInvokedExecutable(RuntimeDynamicAccessMetadata dynamicAccessMetadata, Executable executable) {
        ConfigurationType type = getConfigurationType(executable.getDeclaringClass());
        var methodName = executable instanceof Constructor<?> ? "<init>" : executable.getName();
        var executableType = executable instanceof Constructor<?> ? "constructor" : "method";
        addMethod(type, methodName, executable.getParameterTypes());
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("invoke " + executableType, quote(executable.toString()), elementToJSON(type), dynamicAccessMetadata),
                        executable.getClass(), executable.getDeclaringClass(), executable.getName(), executable.getParameterTypes());
        report(exception);
        /*
         * If report doesn't throw, we return the exception so the caller can throw it in
         * unrecoverable cases.
         */
        return exception;
    }

    public static void reportClassQuery(Class<?> declaringClass, String methodName, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        ConfigurationType type = getConfigurationType(declaringClass);
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("access the", typeDescriptor(declaringClass), elementToJSON(type), dynamicAccessMetadata),
                        null, declaringClass, methodName, null);
        report(exception);
    }

    public static MissingReflectionRegistrationError reportProxyAccess(RuntimeDynamicAccessMetadata dynamicAccessMetadata, Class<?>... interfaces) {
        return reportProxyAccess(dynamicAccessMetadata, null, interfaces);
    }

    public static MissingReflectionRegistrationError reportProxyAccess(RuntimeDynamicAccessMetadata dynamicAccessMetadata, String proxyInterfaceOrderHint, Class<?>... interfaces) {
        List<String> interfaceList = new ArrayList<>(interfaces.length);
        for (Class<?> intf : interfaces) {
            interfaceList.add(intf.getTypeName());
        }
        ConfigurationType type = new ConfigurationType(UnresolvedAccessCondition.unconditional(), new ProxyConfigurationTypeDescriptor(interfaceList), true);
        String message = reflectionError("access the proxy class inheriting", interfacesString(interfaces), elementToJSON(type), dynamicAccessMetadata);
        if (proxyInterfaceOrderHint != null && !proxyInterfaceOrderHint.isEmpty()) {
            message += System.lineSeparator() + System.lineSeparator() + proxyInterfaceOrderHint;
        }
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        message,
                        Proxy.class, null, null, interfaces);
        report(exception);
        /*
         * If report doesn't throw, we return the exception so the caller can throw it in
         * unrecoverable cases.
         */
        return exception;
    }

    public static MissingReflectionRegistrationError reportArrayInstantiation(Class<?> elementClass, int dimension) {
        return reportArrayInstantiation(elementClass, dimension, null);
    }

    public static MissingReflectionRegistrationError reportArrayInstantiation(Class<?> elementClass, int dimension, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        String typeName = elementClass.getTypeName() + "[]".repeat(dimension);
        ConfigurationType type = namedConfigurationType(typeName);
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(
                        reflectionError("instantiate the array class", quote(typeName), elementToJSON(type), dynamicAccessMetadata),
                        null, null, null, null);
        report(exception);
        /*
         * If report doesn't throw, we return the exception so the caller can throw it in
         * unrecoverable cases.
         */
        return exception;
    }

    private static String reflectionError(String failedAction, String elementDescriptor, String json, RuntimeDynamicAccessMetadata dynamicAccessMetadata) {
        return appendUnsatisfiedConditions(
                        registrationMessage(failedAction, elementDescriptor, json, "reflectively", "reflection", "reflection"),
                        dynamicAccessMetadata);
    }

    private static void report(MissingReflectionRegistrationError exception) {
        StackTraceElement responsibleClass = getResponsibleClass(exception, reflectionEntryPoints);
        MissingRegistrationUtils.report(exception, responsibleClass);
    }

    /*
     * This is a list of all public JDK methods that end up potentially throwing missing
     * registration errors. This should be implemented using wrapping substitutions once they are
     * available.
     */
    private static final Map<String, Set<String>> reflectionEntryPoints = Map.of(
                    Class.class.getTypeName(), Set.of(
                                    "forName",
                                    "getClasses",
                                    "getDeclaredClasses",
                                    "getConstructor",
                                    "getConstructors",
                                    "getDeclaredConstructor",
                                    "getDeclaredConstructors",
                                    "getField",
                                    "getFields",
                                    "getDeclaredField",
                                    "getDeclaredFields",
                                    "getMethod",
                                    "getMethods",
                                    "getDeclaredMethod",
                                    "getDeclaredMethods",
                                    "getNestMembers",
                                    "getPermittedSubclasses",
                                    "getRecordComponents",
                                    "getSigners",
                                    "arrayType",
                                    "newInstance"),
                    Method.class.getTypeName(), Set.of("invoke"),
                    Constructor.class.getTypeName(), Set.of("newInstance"),
                    Proxy.class.getTypeName(), Set.of("getProxyClass", "newProxyInstance"),
                    "java.lang.reflect.ReflectAccess", Set.of("newInstance"),
                    "jdk.internal.access.JavaLangAccess", Set.of("getDeclaredPublicMethods"),
                    "sun.misc.Unsafe", Set.of("allocateInstance"),
                    /* For jdk.internal.misc.Unsafe.allocateInstance(), which is intrinsified */
                    SubstrateAllocationSnippets.class.getName(), Set.of("slowPathHubOrUnsafeInstantiationError"));
}
