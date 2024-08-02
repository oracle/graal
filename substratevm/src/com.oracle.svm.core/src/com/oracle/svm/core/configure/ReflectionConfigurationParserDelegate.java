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

    TypeResult<T> resolveType(C condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, String reason);

    void registerType(C condition, T type, String reason);

    void registerPublicClasses(C condition, T type, String reason);

    void registerDeclaredClasses(C condition, T type, String reason);

    void registerRecordComponents(C condition, T type, String reason);

    void registerPermittedSubclasses(C condition, T type, String reason);

    void registerNestMembers(C condition, T type, String reason);

    void registerSigners(C condition, T type, String reason);

    void registerPublicFields(C condition, boolean queriedOnly, T type, String reason);

    void registerDeclaredFields(C condition, boolean queriedOnly, T type, String reason);

    void registerPublicMethods(C condition, boolean queriedOnly, T type, String reason);

    void registerDeclaredMethods(C condition, boolean queriedOnly, T type, String reason);

    void registerPublicConstructors(C condition, boolean queriedOnly, T type, String reason);

    void registerDeclaredConstructors(C condition, boolean queriedOnly, T type, String reason);

    void registerField(C condition, T type, String fieldName, boolean allowWrite, String reason) throws NoSuchFieldException;

    boolean registerAllMethodsWithName(C condition, boolean queriedOnly, T type, String methodName, String reason);

    void registerMethod(C condition, boolean queriedOnly, T type, String methodName, List<T> methodParameterTypes, String reason) throws NoSuchMethodException;

    void registerConstructor(C condition, boolean queriedOnly, T type, List<T> methodParameterTypes, String reason) throws NoSuchMethodException;

    boolean registerAllConstructors(C condition, boolean queriedOnly, T type, String reason);

    void registerUnsafeAllocated(C condition, T clazz, String reason);

    String getTypeName(T type);

    String getSimpleName(T type);

}
