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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.graalvm.nativeimage.MissingReflectionRegistrationError;

import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;

public final class MissingReflectionRegistrationUtils {

    public static void forClass(String className) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access class", className),
                        Class.class, null, className, null);
        report(exception);
    }

    public static void forField(Class<?> declaringClass, String fieldName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access field",
                        declaringClass.getTypeName() + "#" + fieldName),
                        Field.class, declaringClass, fieldName, null);
        report(exception);
    }

    public static MissingReflectionRegistrationError errorForQueriedOnlyField(Field field) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("read or write field", field.toString()),
                        field.getClass(), field.getDeclaringClass(), field.getName(), null);
        report(exception);
        /*
         * If report doesn't throw, we throw the exception anyway since this is a Native
         * Image-specific error that is unrecoverable in any case.
         */
        return exception;
    }

    public static void forMethod(Class<?> declaringClass, String methodName, Class<?>[] paramTypes) {
        StringJoiner paramTypeNames = new StringJoiner(", ", "(", ")");
        if (paramTypes != null) {
            for (Class<?> paramType : paramTypes) {
                paramTypeNames.add(paramType.getTypeName());
            }
        }
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access method",
                        declaringClass.getTypeName() + "#" + methodName + paramTypeNames),
                        Method.class, declaringClass, methodName, paramTypes);
        report(exception);
    }

    public static void forQueriedOnlyExecutable(Executable executable) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("invoke method", executable.toString()),
                        executable.getClass(), executable.getDeclaringClass(), executable.getName(), executable.getParameterTypes());
        report(exception);
        /*
         * If report doesn't throw, we throw the exception anyway since this is a Native
         * Image-specific error that is unrecoverable in any case.
         */
        throw exception;
    }

    public static void forBulkQuery(Class<?> declaringClass, String methodName) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access",
                        declaringClass.getTypeName() + "." + methodName + "()"),
                        null, declaringClass, methodName, null);
        report(exception);
    }

    public static void forProxy(Class<?>... interfaces) {
        MissingReflectionRegistrationError exception = new MissingReflectionRegistrationError(errorMessage("access the proxy class inheriting",
                        Arrays.toString(Arrays.stream(interfaces).map(Class::getTypeName).toArray()),
                        "The order of interfaces used to create proxies matters.", "dynamic-proxy"),
                        Proxy.class, null, null, interfaces);
        report(exception);
        /*
         * If report doesn't throw, we throw the exception anyway since this is a Native
         * Image-specific error that is unrecoverable in any case.
         */
        throw exception;
    }

    private static String errorMessage(String failedAction, String elementDescriptor) {
        return errorMessage(failedAction, elementDescriptor, null, "reflection");
    }

    private static String errorMessage(String failedAction, String elementDescriptor, String note, String helpLink) {
        return "The program tried to reflectively " + failedAction + " " + elementDescriptor +
                        " without it being registered for runtime reflection. Add " + elementDescriptor + " to the " + helpLink + " metadata to solve this problem. " +
                        (note != null ? "Note: " + note + " " : "") +
                        "See https://www.graalvm.org/latest/reference-manual/native-image/metadata/#" + helpLink + " for help.";
    }

    private static void report(MissingReflectionRegistrationError exception) {
        StackTraceElement responsibleClass = getResponsibleClass(exception);
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
                    SubstrateAllocationSnippets.class.getName(), Set.of("instanceHubErrorStub"));

    private static StackTraceElement getResponsibleClass(Throwable t) {
        StackTraceElement[] stackTrace = t.getStackTrace();
        boolean returnNext = false;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (reflectionEntryPoints.getOrDefault(stackTraceElement.getClassName(), Set.of()).contains(stackTraceElement.getMethodName())) {
                /*
                 * Multiple functions with the same name can be called in succession, like the
                 * Class.forName caller-sensitive adapters. We skip those until we find a method
                 * that is not a monitored reflection entry point.
                 */
                returnNext = true;
            } else if (returnNext) {
                return stackTraceElement;
            }
        }
        return null;
    }
}
