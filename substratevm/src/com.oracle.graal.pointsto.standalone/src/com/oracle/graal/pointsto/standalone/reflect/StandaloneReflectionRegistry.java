/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.ReflectionRegistry;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl;

public class StandaloneReflectionRegistry implements ReflectionRegistry {
    private BeforeAnalysisAccessImpl access;
    private final Set<Class<?>> reflectClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> reflectQueriedMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> reflectAccessedMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Field> reflectFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Type> processedTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public StandaloneReflectionRegistry(BeforeAnalysisAccessImpl a) {
        access = a;
    }

    @Override
    public void register(ConfigurationCondition condition, boolean unsafeAllocated, Class<?> clazz) {
        /*
         * Conditional registry has not been implemented in standalone pointsto yet, which is the
         * same with method and field registry.
         */
        if (!reflectClasses.contains(clazz)) {
            access.registerAsUsed(clazz);
            reflectClasses.add(clazz);
        }
    }

    @Override
    public void register(ConfigurationCondition condition, boolean queriedOnly, Executable... methods) {
        for (Executable method : methods) {
            if (reflectAccessedMethods.contains(method) || (queriedOnly && reflectQueriedMethods.contains(method))) {
                continue;
            }
            registerTypesForMethod(method);
            if (queriedOnly) {
                reflectQueriedMethods.add(method);
            } else {
                int mod = method.getModifiers();
                if (!Modifier.isAbstract(mod)) {
                    boolean isInvokeSpecial = (method instanceof Constructor) || Modifier.isFinal(mod);
                    access.registerAsInvoked(method, isInvokeSpecial, "Configured reflection");
                }
                reflectAccessedMethods.add(method);
            }
        }
    }

    private void registerTypesForMethod(Executable executable) {
        AnalysisMethod analysisMethod = access.getMetaAccess().lookupJavaMethod(executable);
        access.registerAsUsed(executable.getDeclaringClass());
        AnalysisType type = analysisMethod.getDeclaringClass();
        if (!type.isAbstract()) {
            type.registerAsInHeap("Reflect class.");
        }

        Arrays.stream(executable.getTypeParameters()).forEach(this::makeTypeReachable);
        Arrays.stream(analysisMethod.getGenericParameterTypes()).forEach(this::makeTypeReachable);
        if (!analysisMethod.isConstructor()) {
            makeTypeReachable(((Method) executable).getGenericReturnType());
        }
        Arrays.stream(executable.getGenericExceptionTypes()).forEach(this::makeTypeReachable);
    }

    private void makeTypeReachable(Type type) {
        if (type == null || processedTypes.contains(type)) {
            return;
        }
        processedTypes.add(type);
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            access.registerAsUsed(clazz);
        } else if (type instanceof TypeVariable<?>) {
            for (Type bound : ((TypeVariable<?>) type).getBounds()) {
                makeTypeReachable(bound);
            }
        } else if (type instanceof GenericArrayType) {
            makeTypeReachable(((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type actualType : parameterizedType.getActualTypeArguments()) {
                makeTypeReachable(actualType);
            }
            makeTypeReachable(parameterizedType.getRawType());
            makeTypeReachable(parameterizedType.getOwnerType());
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                makeTypeReachable(lowerBound);
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                makeTypeReachable(upperBound);
            }
        }
    }

    @Override
    public void register(ConfigurationCondition condition, boolean finalIsWritable, Field... fields) {
        for (Field field : fields) {
            if (reflectFields.contains(field)) {
                continue;
            }
            access.registerAsAccessed(field);
            Class<?> fieldType = field.getType();
            if (!reflectClasses.contains(fieldType)) {
                access.registerAsUsed(fieldType);
            }
        }
    }
}
