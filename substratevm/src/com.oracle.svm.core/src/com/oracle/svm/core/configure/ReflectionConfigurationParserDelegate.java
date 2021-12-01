/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.TypeResult;

public interface ReflectionConfigurationParserDelegate<T> {

    TypeResult<ConfigurationCondition> resolveCondition(String typeName);

    TypeResult<T> resolveType(ConfigurationCondition condition, String typeName);

    void registerType(T type);

    void registerPublicClasses(T type);

    void registerDeclaredClasses(T type);

    void registerPermittedSubclasses(T type);

    void registerPublicFields(T type);

    void registerDeclaredFields(T type);

    void registerPublicMethods(boolean queriedOnly, T type);

    void registerDeclaredMethods(boolean queriedOnly, T type);

    void registerPublicConstructors(boolean queriedOnly, T type);

    void registerDeclaredConstructors(boolean queriedOnly, T type);

    void registerField(T type, String fieldName, boolean allowWrite) throws NoSuchFieldException;

    boolean registerAllMethodsWithName(boolean queriedOnly, T type, String methodName);

    void registerMethod(boolean queriedOnly, T type, String methodName, List<T> methodParameterTypes) throws NoSuchMethodException;

    void registerConstructor(boolean queriedOnly, T type, List<T> methodParameterTypes) throws NoSuchMethodException;

    boolean registerAllConstructors(boolean queriedOnly, T type);

    String getTypeName(T type);

    String getSimpleName(T type);

}
