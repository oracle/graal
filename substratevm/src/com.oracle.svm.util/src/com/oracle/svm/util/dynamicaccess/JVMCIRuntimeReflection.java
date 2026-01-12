/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util.dynamicaccess;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Mirror of {@link RuntimeReflection} using JVMCI types.
 * <p>
 * This API is deprecated; use {@link JVMCIReflectiveAccess} instead.
 */
public final class JVMCIRuntimeReflection {
    /**
     * @see RuntimeReflection#register(Class...)
     */
    public static void register(ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            RuntimeReflection.register(OriginalClassProvider.getJavaClass(type));
        }
    }

    /**
     * @see RuntimeReflection#register(Executable...)
     */
    public static void register(ResolvedJavaMethod... methods) {
        for (ResolvedJavaMethod method : methods) {
            RuntimeReflection.register(OriginalMethodProvider.getJavaMethod(method));
        }
    }

    /**
     * @see RuntimeReflection#registerAsQueried(Executable...)
     */
    public static void registerAsQueried(ResolvedJavaMethod... methods) {
        for (ResolvedJavaMethod method : methods) {
            RuntimeReflection.registerAsQueried(OriginalMethodProvider.getJavaMethod(method));
        }
    }

    /**
     * @see RuntimeReflection#registerMethodLookup(Class, String, Class...)
     */
    public static void registerMethodLookup(ResolvedJavaType declaringType, String methodName, ResolvedJavaType... parameterTypes) {
        Class<?>[] reflectionParameterTypes = Arrays.stream(parameterTypes).map(OriginalClassProvider::getJavaClass).toArray(Class[]::new);
        RuntimeReflection.registerMethodLookup(OriginalClassProvider.getJavaClass(declaringType), methodName, reflectionParameterTypes);
    }

    /**
     * @see RuntimeReflection#registerConstructorLookup(Class, Class...)
     */
    public static void registerConstructorLookup(ResolvedJavaType declaringType, ResolvedJavaType... parameterTypes) {
        Class<?>[] reflectionParameterTypes = Arrays.stream(parameterTypes).map(OriginalClassProvider::getJavaClass).toArray(Class[]::new);
        RuntimeReflection.registerConstructorLookup(OriginalClassProvider.getJavaClass(declaringType), reflectionParameterTypes);
    }

    /**
     * @see RuntimeReflection#register(Field...)
     */
    public static void register(ResolvedJavaField... fields) {
        for (ResolvedJavaField field : fields) {
            RuntimeReflection.register(OriginalFieldProvider.getJavaField(field));
        }
    }

    /**
     * @see RuntimeReflection#registerFieldLookup(Class, String)
     */
    public static void registerFieldLookup(ResolvedJavaType declaringType, String fieldName) {
        RuntimeReflection.registerFieldLookup(OriginalClassProvider.getJavaClass(declaringType), fieldName);
    }

    /**
     * @see RuntimeReflection#registerAllClasses(Class)
     */
    public static void registerAllClasses(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllClasses(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllDeclaredClasses(Class)
     */
    public static void registerAllDeclaredClasses(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllDeclaredClasses(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllMethods(Class)
     */
    public static void registerAllMethods(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllMethods(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllDeclaredMethods(Class)
     */
    public static void registerAllDeclaredMethods(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllDeclaredMethods(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllConstructors(Class)
     */
    public static void registerAllConstructors(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllConstructors(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllDeclaredConstructors(Class)
     */
    public static void registerAllDeclaredConstructors(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllDeclaredConstructors(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllFields(Class)
     */
    public static void registerAllFields(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllFields(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllDeclaredFields(Class)
     */
    public static void registerAllDeclaredFields(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllDeclaredFields(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllNestMembers(Class)
     */
    public static void registerAllNestMembers(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllNestMembers(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllPermittedSubclasses(Class)
     */
    public static void registerAllPermittedSubclasses(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllPermittedSubclasses(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllRecordComponents(Class)
     */
    public static void registerAllRecordComponents(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllRecordComponents(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerAllSigners(Class)
     */
    public static void registerAllSigners(ResolvedJavaType declaringType) {
        RuntimeReflection.registerAllSigners(OriginalClassProvider.getJavaClass(declaringType));
    }

    /**
     * @see RuntimeReflection#registerForReflectiveInstantiation(Class...)
     */
    public static void registerForReflectiveInstantiation(ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            RuntimeReflection.registerForReflectiveInstantiation(OriginalClassProvider.getJavaClass(type));
        }
    }

    private JVMCIRuntimeReflection() {
    }
}
