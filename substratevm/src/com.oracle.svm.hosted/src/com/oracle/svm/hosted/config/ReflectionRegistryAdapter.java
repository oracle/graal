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
import org.graalvm.nativeimage.impl.RuntimeProxyRegistrySupport;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
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
    private final RuntimeProxyRegistrySupport proxyRegistry;
    private final RuntimeSerializationSupport<AccessCondition> serializationSupport;
    private final RuntimeJNIAccessSupport jniSupport;

    ReflectionRegistryAdapter(RuntimeReflectionSupport reflectionSupport, RuntimeProxyRegistrySupport proxyRegistry, RuntimeSerializationSupport<AccessCondition> serializationSupport,
                    RuntimeJNIAccessSupport jniSupport, ImageClassLoader classLoader) {
        super(reflectionSupport, classLoader);
        this.reflectionSupport = reflectionSupport;
        this.proxyRegistry = proxyRegistry;
        this.serializationSupport = serializationSupport;
        this.jniSupport = jniSupport;
    }

    @Override
    public void registerType(AccessCondition condition, Class<?> type) {
        super.registerType(condition, type);
        if (Proxy.isProxyClass(type)) {
            proxyRegistry.registerProxy(condition, false, type.getInterfaces());
        }
    }

    @Override
    public TypeResult<Class<?>> resolveType(AccessCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        TypeResult<Class<?>> result = super.resolveType(condition, typeDescriptor, allowPrimitives, jniAccessible);
        registerTypeResolutionErrors(result, condition, typeDescriptor, jniAccessible);
        return result;
    }

    @Override
    public TypeResult<List<Class<?>>> resolveTypes(AccessCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        TypeResult<List<Class<?>>> result = super.resolveTypes(condition, typeDescriptor, allowPrimitives, jniAccessible);
        registerTypeResolutionErrors(result, condition, typeDescriptor, jniAccessible);
        return result;
    }

    private void registerTypeResolutionErrors(TypeResult<?> result, AccessCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean jniAccessible) {
        if (!result.isPresent() && typeDescriptor instanceof NamedConfigurationTypeDescriptor namedDescriptor) {
            Throwable classLookupException = result.getException();
            if (classLookupException instanceof LinkageError) {
                String reflectionName = ClassNameSupport.typeNameToReflectionName(namedDescriptor.name());
                reflectionSupport.registerClassLookupException(condition, reflectionName, classLookupException);
            } else if (throwMissingRegistrationErrors() && jniAccessible & classLookupException instanceof ClassNotFoundException) {
                String jniName = ClassNameSupport.typeNameToJNIName(namedDescriptor.name());
                jniSupport.registerClassLookup(condition, false, jniName);
            }
        }
    }

    @Override
    public void registerPublicClasses(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllClassesQuery(condition, false, type);
    }

    @Override
    public void registerDeclaredClasses(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllDeclaredClassesQuery(condition, false, type);
    }

    @Override
    public void registerRecordComponents(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllRecordComponentsQuery(condition, type);
    }

    @Override
    public void registerPermittedSubclasses(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllPermittedSubclassesQuery(condition, false, type);
    }

    @Override
    public void registerNestMembers(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllNestMembersQuery(condition, false, type);
    }

    @Override
    public void registerSigners(AccessCondition condition, Class<?> type) {
        reflectionSupport.registerAllSignersQuery(condition, type);
    }

    @Override
    public void registerPublicFields(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (queriedOnly && reflectionSupport instanceof ReflectionDataBuilder reflectionDataBuilder) {
            reflectionDataBuilder.registerAllFieldsQuery(condition, true, false, type);
        } else if (!queriedOnly) {
            reflectionSupport.registerAllFields(condition, false, type);
            if (jniAccessible) {
                jniSupport.register(condition, false, false, type.getFields());
            }
        }
    }

    @Override
    public void registerDeclaredFields(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (queriedOnly && reflectionSupport instanceof ReflectionDataBuilder reflectionDataBuilder) {
            reflectionDataBuilder.registerAllDeclaredFieldsQuery(condition, true, false, type);
        } else if (!queriedOnly) {
            reflectionSupport.registerAllDeclaredFields(condition, false, type);
            if (jniAccessible) {
                jniSupport.register(condition, false, false, type.getDeclaredFields());
            }
        }
    }

    @Override
    public void registerPublicMethods(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllMethodsQuery(condition, queriedOnly, false, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, false, type.getMethods());
        }
    }

    @Override
    public void registerDeclaredMethods(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllDeclaredMethodsQuery(condition, queriedOnly, false, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, false, type.getDeclaredMethods());
        }
    }

    @Override
    public void registerPublicConstructors(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllConstructorsQuery(condition, queriedOnly, false, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, false, type.getConstructors());
        }
    }

    @Override
    public void registerDeclaredConstructors(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        reflectionSupport.registerAllDeclaredConstructorsQuery(condition, queriedOnly, false, type);
        if (!queriedOnly && jniAccessible) {
            jniSupport.register(condition, false, false, type.getDeclaredConstructors());
        }
    }

    @Override
    public void registerAsSerializable(AccessCondition condition, Class<?> clazz) {
        serializationSupport.register(condition, false, clazz);
    }

    @Override
    public void registerAsJniAccessed(AccessCondition condition, Class<?> clazz) {
        jniSupport.register(condition, clazz);
    }

    @Override
    protected void registerField(AccessCondition condition, boolean allowWrite, boolean jniAccessible, Field field) {
        super.registerField(condition, allowWrite, jniAccessible, field);
        if (jniAccessible) {
            jniSupport.register(condition, allowWrite, false, field);
        }
    }

    @Override
    protected void registerFieldNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, String fieldName) {
        super.registerFieldNegativeQuery(condition, jniAccessible, type, fieldName);
        if (jniAccessible) {
            jniSupport.registerFieldLookup(condition, false, type, fieldName);
        }
    }

    @Override
    protected void registerExecutable(AccessCondition condition, boolean queriedOnly, boolean jniAccessible, Executable... executable) {
        super.registerExecutable(condition, queriedOnly, jniAccessible, executable);
        if (jniAccessible) {
            jniSupport.register(condition, queriedOnly, false, executable);
        }
    }

    @Override
    protected void registerMethodNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, String methodName, List<Class<?>> methodParameterTypes) {
        super.registerMethodNegativeQuery(condition, jniAccessible, type, methodName, methodParameterTypes);
        if (jniAccessible) {
            jniSupport.registerMethodLookup(condition, false, type, methodName, getParameterTypes(methodParameterTypes));
        }
    }

    @Override
    protected void registerConstructorNegativeQuery(AccessCondition condition, boolean jniAccessible, Class<?> type, List<Class<?>> constructorParameterTypes) {
        super.registerConstructorNegativeQuery(condition, jniAccessible, type, constructorParameterTypes);
        if (jniAccessible) {
            jniSupport.registerConstructorLookup(condition, false, type, getParameterTypes(constructorParameterTypes));
        }
    }
}
