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
import com.oracle.svm.core.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.core.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.core.configure.ReflectionConfigurationParserDelegate;

public class ParserConfigurationAdapter implements ReflectionConfigurationParserDelegate<ConfigurationType> {

    private final TypeConfiguration configuration;

    public ParserConfigurationAdapter(TypeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public TypeResult<ConfigurationType> resolveType(ConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives) {
        if (typeDescriptor instanceof NamedConfigurationTypeDescriptor namedDescriptor) {
            String typeName = namedDescriptor.name();
            ConfigurationType type = configuration.get(condition, typeName);
            ConfigurationType result = type != null ? type : new ConfigurationType(condition, typeName);
            return TypeResult.forType(typeName, result);
        } else {
            return TypeResult.forException(typeDescriptor.toString(), null);
        }
    }

    @Override
    public void registerType(ConfigurationCondition condition, ConfigurationType type) {
        configuration.add(type);
    }

    @Override
    public void registerField(ConfigurationCondition condition, ConfigurationType type, String fieldName, boolean finalButWritable) {
        type.addField(fieldName, ConfigurationMemberDeclaration.PRESENT, finalButWritable);
    }

    @Override
    public boolean registerAllMethodsWithName(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, String methodName) {
        type.addMethodsWithName(methodName, ConfigurationMemberDeclaration.PRESENT, queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public boolean registerAllConstructors(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.addMethodsWithName(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public void registerUnsafeAllocated(ConfigurationCondition condition, ConfigurationType type) {
        type.setUnsafeAllocated();
    }

    @Override
    public void registerMethod(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, String methodName, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(methodName, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerConstructor(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicClasses(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllPublicClasses();
    }

    @Override
    public void registerDeclaredClasses(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllDeclaredClasses();
    }

    @Override
    public void registerRecordComponents(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllRecordComponents();
    }

    @Override
    public void registerPermittedSubclasses(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllPermittedSubclasses();
    }

    @Override
    public void registerNestMembers(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllNestMembers();
    }

    @Override
    public void registerSigners(ConfigurationCondition condition, ConfigurationType type) {
        type.setAllSigners();
    }

    @Override
    public void registerPublicFields(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.setAllPublicFields();
    }

    @Override
    public void registerDeclaredFields(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.setAllDeclaredFields();
    }

    @Override
    public void registerPublicMethods(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.setAllPublicMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredMethods(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.setAllDeclaredMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicConstructors(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        type.setAllPublicConstructors(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredConstructors(ConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
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
