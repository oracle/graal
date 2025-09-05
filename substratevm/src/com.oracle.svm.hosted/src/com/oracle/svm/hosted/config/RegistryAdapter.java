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
package com.oracle.svm.hosted.config;

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeProxyRegistrySupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.LambdaConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.ReflectionConfigurationParserDelegate;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.TypeResult;

public class RegistryAdapter implements ReflectionConfigurationParserDelegate<ConfigurationCondition, Class<?>> {
    protected final ReflectionRegistry registry;
    private final ImageClassLoader classLoader;

    public static RegistryAdapter create(ReflectionRegistry registry, RuntimeProxyRegistrySupport proxyRegistry, RuntimeSerializationSupport<ConfigurationCondition> serializationSupport,
                    RuntimeJNIAccessSupport jniSupport, ImageClassLoader classLoader) {
        if (registry instanceof RuntimeReflectionSupport) {
            return new ReflectionRegistryAdapter((RuntimeReflectionSupport) registry, proxyRegistry, serializationSupport, jniSupport, classLoader);
        } else if (registry instanceof RuntimeJNIAccessSupport) {
            return new JNIRegistryAdapter(registry, classLoader);
        } else {
            return new RegistryAdapter(registry, classLoader);
        }
    }

    RegistryAdapter(ReflectionRegistry registry, ImageClassLoader classLoader) {
        this.registry = registry;
        this.classLoader = classLoader;
    }

    @Override
    public void registerType(ConfigurationCondition condition, Class<?> type) {
        registry.register(condition, type);
    }

    @Override
    public TypeResult<Class<?>> resolveType(ConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        return TypeResult.toSingleElement(resolveTypes(condition, typeDescriptor, allowPrimitives, jniAccessible));
    }

    @Override
    public TypeResult<List<Class<?>>> resolveTypes(ConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        TypeResult<List<Class<?>>> result = resolveTypesInternal(typeDescriptor, allowPrimitives);
        if (typeDescriptor.getDescriptorType() == ConfigurationTypeDescriptor.Kind.NAMED && !result.isPresent()) {
            if (throwMissingRegistrationErrors() && result.getException() instanceof ClassNotFoundException) {
                registry.registerClassLookup(condition, result.getName());
            }
        }
        return result;
    }

    private TypeResult<Class<?>> resolveTypeInternal(ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives) {
        switch (typeDescriptor.getDescriptorType()) {
            case NAMED -> {
                return resolveNamedType((NamedConfigurationTypeDescriptor) typeDescriptor, allowPrimitives);
            }
            case PROXY -> {
                return resolveProxyType((ProxyConfigurationTypeDescriptor) typeDescriptor);
            }
            default -> {
                throw VMError.shouldNotReachHere("Unknown type descriptor kind: %s", typeDescriptor.getDescriptorType());
            }
        }
    }

    private TypeResult<List<Class<?>>> resolveTypesInternal(ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives) {
        if (Objects.requireNonNull(typeDescriptor.getDescriptorType()) == ConfigurationTypeDescriptor.Kind.LAMBDA) {
            return resolveLambdaType((LambdaConfigurationTypeDescriptor) typeDescriptor);
        }
        return TypeResult.toList(resolveTypeInternal(typeDescriptor, allowPrimitives));
    }

    private TypeResult<Class<?>> resolveNamedType(NamedConfigurationTypeDescriptor namedDescriptor, boolean allowPrimitives) {
        String reflectionName = ClassNameSupport.typeNameToReflectionName(namedDescriptor.name());
        TypeResult<Class<?>> result = classLoader.findClass(reflectionName, allowPrimitives);
        if (!result.isPresent() && result.getException() instanceof NoClassDefFoundError) {
            /*
             * In certain cases when the class name is identical to an existing class name except
             * for lettercase, `ClassLoader.findClass` throws a `NoClassDefFoundError` but
             * `Class.forName` throws a `ClassNotFoundException`.
             */
            try {
                Class.forName(reflectionName);
            } catch (ClassNotFoundException notFoundException) {
                result = TypeResult.forException(reflectionName, notFoundException);
            } catch (Throwable t) {
                // ignore
            }
        }
        return result;
    }

    private TypeResult<Class<?>> resolveProxyType(ProxyConfigurationTypeDescriptor typeDescriptor) {
        String typeName = typeDescriptor.toString();
        List<TypeResult<Class<?>>> interfaceResults = typeDescriptor.interfaceNames().stream()
                        .map(interfaceTypeName -> resolveNamedType(NamedConfigurationTypeDescriptor.fromTypeName(interfaceTypeName), false)).toList();
        List<Class<?>> interfaces = new ArrayList<>();
        for (TypeResult<Class<?>> intf : interfaceResults) {
            if (!intf.isPresent()) {
                return TypeResult.forException(typeName, intf.getException());
            }
            interfaces.add(intf.get());
        }
        try {
            DynamicProxyRegistry proxyRegistry = ImageSingletons.lookup(DynamicProxyRegistry.class);
            Class<?> proxyClass = proxyRegistry.getProxyClassHosted(interfaces.toArray(Class<?>[]::new));
            return TypeResult.forType(typeName, proxyClass);
        } catch (Throwable t) {
            return TypeResult.forException(typeName, t);
        }
    }

    private TypeResult<List<Class<?>>> resolveLambdaType(LambdaConfigurationTypeDescriptor typeDescriptor) {
        TypeResult<Class<?>> declaringClass = resolveTypeInternal(typeDescriptor.declaringClass(), false);
        if (!declaringClass.isPresent()) {
            return TypeResult.forException(typeDescriptor.toString(), declaringClass.getException());
        }
        TypeResult<Method> declaringMethod = null;
        if (typeDescriptor.declaringMethod() != null) {
            declaringMethod = resolveMethod(declaringClass.get(), typeDescriptor.declaringMethod());
            if (!declaringMethod.isPresent()) {
                return TypeResult.forException(typeDescriptor.toString(), declaringMethod.getException());
            }
        }
        List<Class<?>> implementedInterfaces = new ArrayList<>();
        for (NamedConfigurationTypeDescriptor interfaceDescriptor : typeDescriptor.interfaces()) {
            TypeResult<Class<?>> intf = resolveNamedType(interfaceDescriptor, false);
            if (!intf.isPresent()) {
                return TypeResult.forException(typeDescriptor.toString(), intf.getException());
            }
            implementedInterfaces.add(intf.get());
        }

        List<Class<?>> lambdaClasses;
        try {
            if (declaringMethod == null) {
                lambdaClasses = LambdaParser.getLambdaClassesInClass(declaringClass.get(), implementedInterfaces);
            } else {
                lambdaClasses = LambdaParser.getLambdaClassesInMethod(declaringMethod.get(), implementedInterfaces);
            }
            return !lambdaClasses.isEmpty() ? TypeResult.forType(typeDescriptor.toString(), lambdaClasses)
                            : exceptionResult(typeDescriptor, declaringClass, declaringMethod, implementedInterfaces, null);
        } catch (Throwable t) {
            return exceptionResult(typeDescriptor, declaringClass, declaringMethod, implementedInterfaces, t);
        }
    }

    private static TypeResult<List<Class<?>>> exceptionResult(ConfigurationTypeDescriptor typeDescriptor, TypeResult<Class<?>> declaringClass, TypeResult<Method> declaringMethod,
                    List<Class<?>> implementedInterfaces, Throwable cause) {
        NoClassDefFoundError error = new NoClassDefFoundError(
                        "No lambda class found in " + (declaringMethod != null ? declaringMethod.get() : declaringClass.get()) + " implementing " + implementedInterfaces);
        if (cause != null) {
            error.initCause(cause);
        }
        return TypeResult.forException(typeDescriptor.toString(), error);
    }

    private TypeResult<Method> resolveMethod(Class<?> declaringClass, ConfigurationParser.ConfigurationMethodDescriptor methodDescriptor) {
        String name = methodDescriptor.name();
        List<Class<?>> parameterTypes = new ArrayList<>();
        for (NamedConfigurationTypeDescriptor parameterType : methodDescriptor.parameterTypes()) {
            TypeResult<Class<?>> resolvedParameterType = resolveNamedType(parameterType, true);
            if (!resolvedParameterType.isPresent()) {
                return TypeResult.forException(resolvedParameterType.getName(), resolvedParameterType.getException());
            }
            parameterTypes.add(resolvedParameterType.get());
        }
        try {
            return TypeResult.forType(methodDescriptor.toString(), declaringClass.getDeclaredMethod(name, parameterTypes.toArray(Class<?>[]::new)));
        } catch (NoSuchMethodException e) {
            return TypeResult.forException(methodDescriptor.toString(), e);
        }
    }

    @Override
    public void registerPublicClasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerDeclaredClasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerRecordComponents(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerPermittedSubclasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerNestMembers(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerSigners(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerPublicFields(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (!queriedOnly) {
            registry.register(condition, false, type.getFields());
        }
    }

    @Override
    public void registerDeclaredFields(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (!queriedOnly) {
            registry.register(condition, false, type.getDeclaredFields());
        }
    }

    @Override
    public void registerPublicMethods(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getMethods());
    }

    @Override
    public void registerDeclaredMethods(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getDeclaredMethods());
    }

    @Override
    public void registerPublicConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getConstructors());
    }

    @Override
    public void registerDeclaredConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getDeclaredConstructors());
    }

    @Override
    @SuppressWarnings("unused")
    public final void registerField(ConfigurationCondition condition, Class<?> type, String fieldName, boolean allowWrite, boolean jniAccessible) throws NoSuchFieldException {
        try {
            registerField(condition, allowWrite, jniAccessible, type.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            if (throwMissingRegistrationErrors()) {
                registerFieldNegativeQuery(condition, jniAccessible, type, fieldName);
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("unused")
    protected void registerField(ConfigurationCondition condition, boolean allowWrite, boolean jniAccessible, Field field) {
        registry.register(condition, allowWrite, field);
    }

    @SuppressWarnings("unused")
    protected void registerFieldNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, String fieldName) {
        registry.registerFieldLookup(condition, type, fieldName);
    }

    @Override
    public boolean registerAllMethodsWithName(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type, String methodName) {
        boolean found = false;
        Executable[] methods = type.getDeclaredMethods();
        for (Executable method : methods) {
            if (method.getName().equals(methodName)) {
                registerExecutable(condition, queriedOnly, jniAccessible, method);
                found = true;
            }
        }
        return found;
    }

    @Override
    public boolean registerAllConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        Executable[] methods = type.getDeclaredConstructors();
        registerExecutable(condition, queriedOnly, jniAccessible, methods);
        return methods.length > 0;
    }

    @Override
    public void registerUnsafeAllocated(ConfigurationCondition condition, Class<?> clazz) {
        if (!clazz.isArray() && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
            registry.register(condition, true, clazz);
            /*
             * Ignore otherwise as the implementation of allocateInstance will anyhow throw an
             * exception.
             */
        }
    }

    @Override
    public final void registerMethod(ConfigurationCondition condition, boolean queriedOnly, Class<?> type, String methodName, List<Class<?>> methodParameterTypes, boolean jniAccessible)
                    throws NoSuchMethodException {
        try {
            Class<?>[] parameterTypesArray = getParameterTypes(methodParameterTypes);
            Method method;
            try {
                method = type.getDeclaredMethod(methodName, parameterTypesArray);
            } catch (NoClassDefFoundError e) {
                /*
                 * getDeclaredMethod() builds a set of all the declared methods, which can fail when
                 * a symbolic reference from another method to a type (via parameters, return value)
                 * cannot be resolved. getMethod() builds a different set of methods and can still
                 * succeed. This case must be handled for predefined classes when, during the run
                 * observed by the agent, a referenced class was not loaded and is not available now
                 * precisely because the application used getMethod() instead of
                 * getDeclaredMethod().
                 */
                try {
                    method = type.getMethod(methodName, parameterTypesArray);
                } catch (Throwable ignored) {
                    throw e;
                }
            }
            registerExecutable(condition, queriedOnly, jniAccessible, method);
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                registerMethodNegativeQuery(condition, jniAccessible, type, methodName, methodParameterTypes);
            } else {
                throw e;
            }
        }
    }

    @Override
    public final void registerConstructor(ConfigurationCondition condition, boolean queriedOnly, Class<?> type, List<Class<?>> methodParameterTypes, boolean jniAccessible)
                    throws NoSuchMethodException {
        Class<?>[] parameterTypesArray = getParameterTypes(methodParameterTypes);
        try {
            registerExecutable(condition, queriedOnly, jniAccessible, type.getDeclaredConstructor(parameterTypesArray));
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                registerConstructorNegativeQuery(condition, jniAccessible, type, methodParameterTypes);
            } else {
                throw e;
            }
        }
    }

    static Class<?>[] getParameterTypes(List<Class<?>> methodParameterTypes) {
        return methodParameterTypes.toArray(Class<?>[]::new);
    }

    @SuppressWarnings("unused")
    protected void registerExecutable(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Executable... executable) {
        registry.register(condition, queriedOnly, executable);
    }

    @SuppressWarnings("unused")
    protected void registerMethodNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, String methodName, List<Class<?>> methodParameterTypes) {
        registry.registerMethodLookup(condition, type, methodName, getParameterTypes(methodParameterTypes));
    }

    @SuppressWarnings("unused")
    protected void registerConstructorNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, List<Class<?>> constructorParameterTypes) {
        registry.registerConstructorLookup(condition, type, getParameterTypes(constructorParameterTypes));
    }

    @Override
    public void registerAsSerializable(ConfigurationCondition condition, Class<?> clazz) {
    }

    @Override
    public void registerAsJniAccessed(ConfigurationCondition condition, Class<?> clazz) {
    }

    @Override
    public String getTypeName(Class<?> type) {
        return type.getTypeName();
    }

    @Override
    public String getSimpleName(Class<?> type) {
        return ClassUtil.getUnqualifiedName(type);
    }
}
