/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.util;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.webimage.api.JS;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Various reflection utilities that the normal Java reflection API does not expose.
 */
public class ReflectUtil {
    private static final HashSet<Method> OBJECT_METHODS;
    private static final ConcurrentHashMap<ResolvedJavaType, Object> SAM_CACHE;

    static {
        OBJECT_METHODS = new HashSet<>();
        OBJECT_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        SAM_CACHE = new ConcurrentHashMap<>();
    }

    /**
     * Checks if the interface type is a {@link FunctionalInterface}, and returns its only single
     * abstract method if it is.
     */
    private static Optional<Method> singleAbstractMethodForInterface(Class<?> javaInterface) {
        if (!javaInterface.isInterface()) {
            return Optional.empty();
        }

        if (javaInterface.getAnnotation(FunctionalInterface.class) == null) {
            return Optional.empty();
        }

        Method sam = null;
        for (final Method method : javaInterface.getMethods()) {
            if (OBJECT_METHODS.contains(method)) {
                // Skip the methods from the Object class.
                continue;
            }
            if (method.isDefault()) {
                // Default methods do not count as single-abstract-method candidates.
                continue;
            }
            sam = method;
            break;
        }

        if (sam == null) {
            return Optional.empty();
        }

        return Optional.of(sam);
    }

    /**
     * Optionally returns a method of the class if that method implements the single abstract method
     * of some functional interface that the class implements, and there is only one functional
     * interface that the class implements.
     */
    @SuppressWarnings("unchecked")
    public static <M extends ResolvedJavaMethod & OriginalMethodProvider, T extends ResolvedJavaType & OriginalClassProvider> Optional<M> singleAbstractMethodForClass(MetaAccessProvider metaAccess,
                    T classType) {
        Optional<M> sam = (Optional<M>) SAM_CACHE.get(classType);
        if (sam != null) {
            return sam;
        }
        sam = findSingleAbstractMethodForClass(metaAccess, classType);
        SAM_CACHE.putIfAbsent(classType, sam);
        return (Optional<M>) SAM_CACHE.get(classType);
    }

    @SuppressWarnings("unchecked")
    private static <M extends ResolvedJavaMethod & OriginalMethodProvider, T extends ResolvedJavaType & OriginalClassProvider> Optional<M> findSingleAbstractMethodForClass(
                    MetaAccessProvider metaAccess, T classType) {
        Class<?> javaClass = OriginalClassProvider.getJavaClass(classType);
        LinkedHashSet<Class<?>> interfaces = new LinkedHashSet<>();
        findAllInterfaces(javaClass, interfaces);

        Optional<Method> javaSam = Optional.empty();
        for (final Class<?> i : interfaces) {
            Optional<Method> candidate = singleAbstractMethodForInterface(i);
            if (!candidate.isPresent()) {
                // This interface is not functional.
                continue;
            }
            if (javaSam.isPresent()) {
                // Two candidates, so there is no single functional interface.
                return Optional.empty();
            }
            javaSam = candidate;
        }

        if (!javaSam.isPresent()) {
            return Optional.empty();
        }

        M sam = null;
        ResolvedJavaType functionalInterface = metaAccess.lookupJavaType(javaSam.get().getDeclaringClass());
        for (final ResolvedJavaMethod candidate : functionalInterface.getDeclaredMethods(false)) {
            if (Objects.equals(OriginalMethodProvider.getJavaMethod(candidate), javaSam.get())) {
                sam = (M) candidate;
                break;
            }
        }
        if (sam == null) {
            return Optional.empty();
        }
        ResolvedJavaMethod resolved = classType.resolveConcreteMethod(sam, null);
        return Optional.ofNullable((M) resolved);
    }

    private static void findAllInterfaces(Class<?> type, LinkedHashSet<Class<?>> interfaces) {
        if (interfaces.contains(type)) {
            return;
        }

        if (type.isInterface()) {
            interfaces.add(type);
        }

        for (final Class<?> superInterface : type.getInterfaces()) {
            findAllInterfaces(superInterface, interfaces);
        }

        if (type.getSuperclass() != null) {
            findAllInterfaces(type.getSuperclass(), interfaces);
        }
    }

    /**
     * Finds all methods annotated with {@link JS} as well as all the methods it overrides.
     */
    public static Set<Method> findBaseMethodsOfJSAnnotated(ImageClassLoader imageClassLoader) {
        Set<Method> methods = new HashSet<>();

        List<Method> annotatedMethods = imageClassLoader.findAnnotatedMethods(JS.class);

        for (Method annotatedMethod : annotatedMethods) {
            findBaseMethods(annotatedMethod, annotatedMethod.getDeclaringClass(), methods);
        }

        return methods;
    }

    /**
     * Finds all methods the given {@code originalMethod} overrides (including itself) from the
     * given class upwards and adds them to the given set.
     */
    private static void findBaseMethods(Method originalMethod, Class<?> clazz, Set<Method> jsOverridenMethodSet) {
        if (clazz == null) {
            return;
        }

        // This is either the same method as originalMethod or a method it overrides.
        Method baseMethod = ReflectionUtil.lookupMethod(true, clazz, originalMethod.getName(), originalMethod.getParameterTypes());

        if (baseMethod != null) {
            jsOverridenMethodSet.add(baseMethod);
        }

        for (Class<?> clazzInterface : clazz.getInterfaces()) {
            findBaseMethods(originalMethod, clazzInterface, jsOverridenMethodSet);
        }

        findBaseMethods(originalMethod, clazz.getSuperclass(), jsOverridenMethodSet);
    }

}
