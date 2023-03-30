/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils.throwMissingRegistrationErrors;

import java.util.List;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.hosted.ImageClassLoader;

public class ReflectionRegistryAdapter extends RegistryAdapter {
    private final RuntimeReflectionSupport reflectionSupport;

    ReflectionRegistryAdapter(RuntimeReflectionSupport reflectionSupport, ImageClassLoader classLoader) {
        super(reflectionSupport, classLoader);
        this.reflectionSupport = reflectionSupport;
    }

    @Override
    public TypeResult<ConditionalElement<Class<?>>> resolveType(ConfigurationCondition condition, String typeName, boolean allowPrimitives) {
        TypeResult<ConditionalElement<Class<?>>> result = super.resolveType(condition, typeName, allowPrimitives);
        if (!result.isPresent()) {
            Throwable classLookupException = result.getException();
            if (classLookupException instanceof LinkageError) {
                reflectionSupport.registerClassLookupException(condition, typeName, classLookupException);
            } else if (throwMissingRegistrationErrors() && classLookupException instanceof ClassNotFoundException) {
                reflectionSupport.registerClassLookup(condition, typeName);
            }
        }
        return result;
    }

    @Override
    public void registerPublicClasses(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllClassesQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerDeclaredClasses(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllDeclaredClassesQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerRecordComponents(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllRecordComponentsQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerPermittedSubclasses(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllPermittedSubclassesQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerNestMembers(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllNestMembersQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerSigners(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllSignersQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerPublicFields(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllFieldsQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerDeclaredFields(ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllDeclaredFieldsQuery(type.getCondition(), type.getElement());
    }

    @Override
    public void registerPublicMethods(boolean queriedOnly, ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllMethodsQuery(type.getCondition(), queriedOnly, type.getElement());
    }

    @Override
    public void registerDeclaredMethods(boolean queriedOnly, ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllDeclaredMethodsQuery(type.getCondition(), queriedOnly, type.getElement());
    }

    @Override
    public void registerPublicConstructors(boolean queriedOnly, ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllConstructorsQuery(type.getCondition(), queriedOnly, type.getElement());
    }

    @Override
    public void registerDeclaredConstructors(boolean queriedOnly, ConditionalElement<Class<?>> type) {
        reflectionSupport.registerAllDeclaredConstructorsQuery(type.getCondition(), queriedOnly, type.getElement());
    }

    @Override
    public void registerField(ConditionalElement<Class<?>> type, String fieldName, boolean allowWrite) throws NoSuchFieldException {
        try {
            super.registerField(type, fieldName, allowWrite);
        } catch (NoSuchFieldException e) {
            if (throwMissingRegistrationErrors()) {
                reflectionSupport.registerFieldLookup(type.getCondition(), type.getElement(), fieldName);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void registerMethod(boolean queriedOnly, ConditionalElement<Class<?>> type, String methodName, List<ConditionalElement<Class<?>>> methodParameterTypes) throws NoSuchMethodException {
        try {
            super.registerMethod(queriedOnly, type, methodName, methodParameterTypes);
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                reflectionSupport.registerMethodLookup(type.getCondition(), type.getElement(), methodName, getParameterTypes(methodParameterTypes));
            } else {
                throw e;
            }
        }
    }

    @Override
    public void registerConstructor(boolean queriedOnly, ConditionalElement<Class<?>> type, List<ConditionalElement<Class<?>>> methodParameterTypes) throws NoSuchMethodException {
        try {
            super.registerConstructor(queriedOnly, type, methodParameterTypes);
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                reflectionSupport.registerConstructorLookup(type.getCondition(), type.getElement(), getParameterTypes(methodParameterTypes));
            } else {
                throw e;
            }
        }
    }
}
