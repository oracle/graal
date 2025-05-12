/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;
import com.oracle.svm.hosted.reflect.proxy.ProxyRegistry;
import com.oracle.svm.util.TypeResult;

public class ReflectionRegistryAdapter extends RegistryAdapter {
    private final RuntimeReflectionSupport reflectionSupport;
    private final ProxyRegistry proxyRegistry;
    private final RuntimeSerializationSupport<RegistrationCondition> serializationSupport;

    ReflectionRegistryAdapter(RuntimeReflectionSupport reflectionSupport, ProxyRegistry proxyRegistry, RuntimeSerializationSupport<RegistrationCondition> serializationSupport,
                    ImageClassLoader classLoader) {
        super(reflectionSupport, classLoader);
        this.reflectionSupport = reflectionSupport;
        this.proxyRegistry = proxyRegistry;
        this.serializationSupport = serializationSupport;
    }

    @Override
    public void registerType(RegistrationCondition condition, Class<?> type) {
        super.registerType(condition, type);
        if (Proxy.isProxyClass(type)) {
            proxyRegistry.accept(condition, Arrays.stream(type.getInterfaces()).map(Class::getTypeName).toList());
        }
    }

    @Override
    public TypeResult<Class<?>> resolveType(RegistrationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives) {
        TypeResult<Class<?>> result = super.resolveType(condition, typeDescriptor, allowPrimitives);
        if (!result.isPresent() && typeDescriptor instanceof NamedConfigurationTypeDescriptor namedDescriptor) {
            Throwable classLookupException = result.getException();
            if (classLookupException instanceof LinkageError) {
                reflectionSupport.registerClassLookupException(condition, namedDescriptor.name(), classLookupException);
            }
        }
        return result;
    }

    @Override
    public void registerPublicClasses(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllClassesQuery(condition, type);
    }

    @Override
    public void registerDeclaredClasses(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllDeclaredClassesQuery(condition, type);
    }

    @Override
    public void registerRecordComponents(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllRecordComponentsQuery(condition, type);
    }

    @Override
    public void registerPermittedSubclasses(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllPermittedSubclassesQuery(condition, type);
    }

    @Override
    public void registerNestMembers(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllNestMembersQuery(condition, type);
    }

    @Override
    public void registerSigners(RegistrationCondition condition, Class<?> type) {
        reflectionSupport.registerAllSignersQuery(condition, type);
    }

    @Override
    public void registerPublicFields(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        ((ReflectionDataBuilder) reflectionSupport).registerAllFieldsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerDeclaredFields(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        ((ReflectionDataBuilder) reflectionSupport).registerAllDeclaredFieldsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerPublicMethods(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        reflectionSupport.registerAllMethodsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerDeclaredMethods(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        reflectionSupport.registerAllDeclaredMethodsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerPublicConstructors(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        reflectionSupport.registerAllConstructorsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerDeclaredConstructors(RegistrationCondition condition, boolean queriedOnly, Class<?> type) {
        reflectionSupport.registerAllDeclaredConstructorsQuery(condition, queriedOnly, type);
    }

    @Override
    public void registerAsSerializable(RegistrationCondition condition, Class<?> clazz) {
        serializationSupport.register(condition, clazz);
    }
}
