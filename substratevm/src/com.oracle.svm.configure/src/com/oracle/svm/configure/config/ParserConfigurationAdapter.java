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
package com.oracle.svm.configure.config;

import java.util.List;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.configure.ReflectionConfigurationParserDelegate;

public class ParserConfigurationAdapter implements ReflectionConfigurationParserDelegate<ConfigurationType> {

    private final TypeConfiguration configuration;

    public ParserConfigurationAdapter(TypeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public TypeResult<ConfigurationCondition> resolveCondition(String typeName) {
        return TypeResult.forType(typeName, ConfigurationCondition.create(typeName));
    }

    @Override
    public TypeResult<ConfigurationType> resolveType(ConfigurationCondition condition, String typeName, boolean allowPrimitives) {
        ConfigurationType type = configuration.get(condition, typeName);
        ConfigurationType result = type != null ? type : new ConfigurationType(condition, typeName);
        return TypeResult.forType(typeName, result);
    }

    @Override
    public void registerType(ConfigurationType type) {
        configuration.add(type);
    }

    @Override
    public void registerField(ConfigurationType type, String fieldName, boolean finalButWritable) {
        type.addField(fieldName, ConfigurationMemberDeclaration.PRESENT, finalButWritable);
    }

    @Override
    public boolean registerAllMethodsWithName(boolean queriedOnly, ConfigurationType type, String methodName) {
        type.addMethodsWithName(methodName, ConfigurationMemberDeclaration.PRESENT, queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public boolean registerAllConstructors(boolean queriedOnly, ConfigurationType type) {
        type.addMethodsWithName(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public void registerUnsafeAllocated(ConfigurationType type) {
        type.setUnsafeAllocated();
    }

    @Override
    public void registerMethod(boolean queriedOnly, ConfigurationType type, String methodName, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(methodName, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerConstructor(boolean queriedOnly, ConfigurationType type, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicClasses(ConfigurationType type) {
        type.setAllPublicClasses();
    }

    @Override
    public void registerDeclaredClasses(ConfigurationType type) {
        type.setAllDeclaredClasses();
    }

    @Override
    public void registerPermittedSubclasses(ConfigurationType type) {
        type.setAllPermittedSubclasses();
    }

    @Override
    public void registerPublicFields(ConfigurationType type) {
        type.setAllPublicFields();
    }

    @Override
    public void registerDeclaredFields(ConfigurationType type) {
        type.setAllDeclaredFields();
    }

    @Override
    public void registerPublicMethods(boolean queriedOnly, ConfigurationType type) {
        type.setAllPublicMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredMethods(boolean queriedOnly, ConfigurationType type) {
        type.setAllDeclaredMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicConstructors(boolean queriedOnly, ConfigurationType type) {
        type.setAllPublicConstructors(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredConstructors(boolean queriedOnly, ConfigurationType type) {
        type.setAllDeclaredConstructors(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public String getTypeName(ConfigurationType type) {
        return type.getQualifiedJavaName();
    }

    @Override
    public String getSimpleName(ConfigurationType type) {
        return getTypeName(type);
    }
}
