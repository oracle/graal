/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.configure.ReflectionConfigurationParserDelegate;

public class ParserConfigurationAdapter implements ReflectionConfigurationParserDelegate<ConfigurationType> {

    private final TypeConfiguration configuration;

    public ParserConfigurationAdapter(TypeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ConfigurationType resolveType(String typeName) {
        ConfigurationType type = configuration.get(typeName);
        return (type != null) ? type : new ConfigurationType(typeName);
    }

    @Override
    public void registerType(ConfigurationType type) {
        configuration.add(type);
    }

    @Override
    public void registerField(ConfigurationType type, String fieldName, boolean finalButWritable, boolean allowUnsafeAccess) {
        type.addField(fieldName, ConfigurationMemberKind.PRESENT, finalButWritable, allowUnsafeAccess);
    }

    @Override
    public boolean registerAllMethodsWithName(ConfigurationType type, String methodName) {
        type.addMethodsWithName(methodName, ConfigurationMemberKind.PRESENT);
        return true;
    }

    @Override
    public boolean registerAllConstructors(ConfigurationType type) {
        type.addMethodsWithName(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMemberKind.PRESENT);
        return true;
    }

    @Override
    public void registerMethod(ConfigurationType type, String methodName, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(methodName, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberKind.PRESENT);
    }

    @Override
    public void registerConstructor(ConfigurationType type, List<ConfigurationType> methodParameterTypes) {
        type.addMethod(ConfigurationMethod.CONSTRUCTOR_NAME, ConfigurationMethod.toInternalParamsSignature(methodParameterTypes), ConfigurationMemberKind.PRESENT);
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
    public void registerPublicFields(ConfigurationType type) {
        type.setAllPublicFields();
    }

    @Override
    public void registerDeclaredFields(ConfigurationType type) {
        type.setAllDeclaredFields();
    }

    @Override
    public void registerPublicMethods(ConfigurationType type) {
        type.setAllPublicMethods();
    }

    @Override
    public void registerDeclaredMethods(ConfigurationType type) {
        type.setAllDeclaredMethods();
    }

    @Override
    public void registerPublicConstructors(ConfigurationType type) {
        type.setAllPublicConstructors();
    }

    @Override
    public void registerDeclaredConstructors(ConfigurationType type) {
        type.setAllDeclaredConstructors();
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
