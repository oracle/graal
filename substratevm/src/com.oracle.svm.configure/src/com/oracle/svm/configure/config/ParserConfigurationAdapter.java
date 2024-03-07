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
package com.oracle.svm.configure.config;

import java.util.List;

import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberDeclaration;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.configure.ReflectionConfigurationParserDelegate;
import com.oracle.svm.core.util.VMError;

public class ParserConfigurationAdapter implements ReflectionConfigurationParserDelegate<UnresolvedConfigurationCondition, ConfigurationType> {

    private final TypeConfiguration configuration;

    public ParserConfigurationAdapter(TypeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public TypeResult<ConfigurationType> resolveType(UnresolvedConfigurationCondition condition, String typeName, boolean allowPrimitives, boolean includeAllElements) {
        ConfigurationType type = configuration.get(condition, typeName);
        ConfigurationType result = type != null ? type : new ConfigurationType(condition, typeName, includeAllElements);
        return TypeResult.forType(typeName, result);
    }

    @Override
    public void registerType(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        configuration.add(type);
    }

    @Override
    public void registerField(UnresolvedConfigurationCondition condition, ConfigurationType type, String fieldName, boolean finalButWritable) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.addField(fieldName, ConfigurationMemberDeclaration.PRESENT, finalButWritable);
    }

    @Override
    public boolean registerAllMethodsWithName(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, String methodName) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.addMethodsWithName(methodName, ConfigurationMemberDeclaration.PRESENT, queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public boolean registerAllConstructors(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.addMethodsWithName(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
        return true;
    }

    @Override
    public void registerUnsafeAllocated(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is here part of the type");
        type.setUnsafeAllocated();
    }

    @Override
    public void registerMethod(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, String methodName, List<ConfigurationType> methodParameterTypes) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.addMethod(methodName, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerConstructor(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type, List<ConfigurationType> methodParameterTypes) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberDeclaration.PRESENT,
                        queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicClasses(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllPublicClasses();
    }

    @Override
    public void registerDeclaredClasses(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllDeclaredClasses();
    }

    @Override
    public void registerRecordComponents(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllRecordComponents();
    }

    @Override
    public void registerPermittedSubclasses(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllPermittedSubclasses();
    }

    @Override
    public void registerNestMembers(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllNestMembers();
    }

    @Override
    public void registerSigners(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllSigners();
    }

    @Override
    public void registerPublicFields(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllPublicFields();
    }

    @Override
    public void registerDeclaredFields(UnresolvedConfigurationCondition condition, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllDeclaredFields();
    }

    @Override
    public void registerPublicMethods(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllPublicMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredMethods(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllDeclaredMethods(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerPublicConstructors(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllPublicConstructors(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public void registerDeclaredConstructors(UnresolvedConfigurationCondition condition, boolean queriedOnly, ConfigurationType type) {
        VMError.guarantee(condition.equals(type.getCondition()), "condition is already a part of the type");
        type.setAllDeclaredConstructors(queriedOnly ? ConfigurationMemberAccessibility.QUERIED : ConfigurationMemberAccessibility.ACCESSED);
    }

    @Override
    public String getTypeName(ConfigurationType type) {
        return type.getTypeDescriptor().toString();
    }

    @Override
    public String getSimpleName(ConfigurationType type) {
        return getTypeName(type);
    }
}
