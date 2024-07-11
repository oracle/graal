/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.util.List;

import com.oracle.svm.core.TypeResult;

public interface ReflectionConfigurationParserDelegate<C, T> {

    TypeResult<T> resolveType(C condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives);

    void registerType(C condition, T type);

    void registerPublicClasses(C condition, T type);

    void registerDeclaredClasses(C condition, T type);

    void registerRecordComponents(C condition, T type);

    void registerPermittedSubclasses(C condition, T type);

    void registerNestMembers(C condition, T type);

    void registerSigners(C condition, T type);

    void registerPublicFields(C condition, boolean queriedOnly, T type);

    void registerDeclaredFields(C condition, boolean queriedOnly, T type);

    void registerPublicMethods(C condition, boolean queriedOnly, T type);

    void registerDeclaredMethods(C condition, boolean queriedOnly, T type);

    void registerPublicConstructors(C condition, boolean queriedOnly, T type);

    void registerDeclaredConstructors(C condition, boolean queriedOnly, T type);

    void registerField(C condition, T type, String fieldName, boolean allowWrite) throws NoSuchFieldException;

    boolean registerAllMethodsWithName(C condition, boolean queriedOnly, T type, String methodName);

    void registerMethod(C condition, boolean queriedOnly, T type, String methodName, List<T> methodParameterTypes) throws NoSuchMethodException;

    void registerConstructor(C condition, boolean queriedOnly, T type, List<T> methodParameterTypes) throws NoSuchMethodException;

    boolean registerAllConstructors(C condition, boolean queriedOnly, T type);

    void registerUnsafeAllocated(C condition, T clazz);

    String getTypeName(T type);

    String getSimpleName(T type);

}
