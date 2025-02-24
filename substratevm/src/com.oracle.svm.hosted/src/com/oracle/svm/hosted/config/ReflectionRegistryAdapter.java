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

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeProxyCreationSupport;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;
import com.oracle.svm.util.TypeResult;

public class ReflectionRegistryAdapter extends RegistryAdapter {
    private final RuntimeReflectionSupport reflectionSupport;
    private final RuntimeProxyCreationSupport proxyRegistry;
    private final RuntimeSerializationSupport<RegistrationCondition> serializationSupport;
    private final RuntimeJNIAccessSupport jniSupport;

    ReflectionRegistryAdapter(RuntimeReflectionSupport reflectionSupport, RuntimeProxyCreationSupport proxyRegistry, RuntimeSerializationSupport<RegistrationCondition> serializationSupport,
                    RuntimeJNIAccessSupport jniSupport, ImageClassLoader classLoader) {
        super(reflectionSupport, classLoader);
        this.reflectionSupport = reflectionSupport;
        this.proxyRegistry = proxyRegistry;
        this.serializationSupport = serializationSupport;
        this.jniSupport = jniSupport;
    }

    @Override
    public void registerType(RegistrationCondition condition, Class<?> type) {
        super.registerType(condition, type);
        if (Proxy.isProxyClass(type)) {
            proxyRegistry.addProxyClass(condition, type.getInterfaces());
        }
    }

    @Override
    public TypeResult<Class<?>> resolveType(RegistrationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        TypeResult<Class<?>> result = super.resolveType(condition, typeDescriptor, allowPrimitives, jniAccessible);
        if (!result.isPresent() && typeDescriptor instanceof NamedConfigurationTypeDescriptor namedDescriptor) {
            Throwable classLookupException = result.getException();
            if (classLookupException instanceof LinkageError) {
                String reflectionName = ClassNameSupport.typeNameToReflectionName(namedDescriptor.name());
                reflectionSupport.registerClassLookupException(condition, reflectionName, classLookupException);
            } else if (throwMissingRegistrationErrors() && jniAccessible & classLookupException instanceof ClassNotFoundException) {
                String jniName = ClassNameSupport.typeNameToJNIName(namedDescriptor.name());
                jniSupport.registerClassLookup(condition, jniName);
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
    public void registerPublicFields(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (queriedOnly && reflectionSupport instanceof ReflectionDataBuilder reflectionDataBuilder) {
            reflectionDataBuilder.registerAllFieldsQuery(condition, true, type);
        } else if (!queriedOnly) {
            reflectionSupport.registerAllFields(condition, type);
            if (jniAccessible) {
                jniSupport.register(condition, false, type.getFields());
            }
        }
    }

    @Override
    public void registerDeclaredFields(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (queriedOnly && reflectionSupport instanceof ReflectionDataBuilder reflectionDataBuilder) {
            reflectionDataBuilder.registerAllDeclaredFieldsQuery(condition, true, type);
        } else if (!queriedOnly) {
            reflectionSupport.registerAllDeclaredFields(condition, type);
            if (jniAccessible) {
                jniSupport.register(condition, false, type.getDeclaredFields());
            }
        }
    }

    @Override
    public void registerPublicMethods(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllMethodsQuery(condition, queriedOnly, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, type.getMethods());
        }
    }

    @Override
    public void registerDeclaredMethods(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllDeclaredMethodsQuery(condition, queriedOnly, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, type.getDeclaredMethods());
        }
    }

    @Override
    public void registerPublicConstructors(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllConstructorsQuery(condition, queriedOnly, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, type.getConstructors());
        }
    }

    @Override
    public void registerDeclaredConstructors(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllDeclaredConstructorsQuery(condition, queriedOnly, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, type.getDeclaredConstructors());
        }
    }

    @Override
    public void registerAsSerializable(RegistrationCondition condition, Class<?> clazz) {
        serializationSupport.register(condition, clazz);
    }

    @Override
    public void registerAsJniAccessed(RegistrationCondition condition, Class<?> clazz) {
        jniSupport.register(condition, clazz);
    }

    @Override
    protected void registerField(RegistrationCondition condition, boolean allowWrite, boolean jniAccessible, Field field) {
        super.registerField(condition, allowWrite, jniAccessible, field);
        if (jniAccessible) {
            jniSupport.register(condition, allowWrite, field);
        }
    }

    @Override
    protected void registerFieldNegativeQuery(RegistrationCondition condition, boolean jniAccessible, Class<?> type, String fieldName) {
        super.registerFieldNegativeQuery(condition, jniAccessible, type, fieldName);
        if (jniAccessible) {
            jniSupport.registerFieldLookup(condition, type, fieldName);
        }
    }

    @Override
    protected void registerExecutable(RegistrationCondition condition, boolean queriedOnly, boolean jniAccessible, Executable... executable) {
        super.registerExecutable(condition, queriedOnly, jniAccessible, executable);
        if (jniAccessible) {
            jniSupport.register(condition, queriedOnly, executable);
        }
    }

    @Override
    protected void registerMethodNegativeQuery(RegistrationCondition condition, boolean jniAccessible, Class<?> type, String methodName, List<Class<?>> methodParameterTypes) {
        super.registerMethodNegativeQuery(condition, jniAccessible, type, methodName, methodParameterTypes);
        if (jniAccessible) {
            jniSupport.registerMethodLookup(condition, type, methodName, getParameterTypes(methodParameterTypes));
        }
    }

    @Override
    protected void registerConstructorNegativeQuery(RegistrationCondition condition, boolean jniAccessible, Class<?> type, List<Class<?>> constructorParameterTypes) {
        super.registerConstructorNegativeQuery(condition, jniAccessible, type, constructorParameterTypes);
        if (jniAccessible) {
            jniSupport.registerConstructorLookup(condition, type, getParameterTypes(constructorParameterTypes));
        }
    }
}
