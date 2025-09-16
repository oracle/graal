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
package com.oracle.svm.core.hub;

import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaField;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaRecordComponent;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaType;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.reflect.target.ReflectionObjectFactory;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;

/**
 * Instances of this class are used to represent the reflection metadata for Dynamic hubs loaded at
 * runtime. Note, the use of the term 'Runtime' is not to be confused with e.g.
 * {@link org.graalvm.nativeimage.impl.RuntimeReflectionSupport} where 'Runtime' is used to refer to
 * something that was prepared at build time but used at runtime. Here, 'Runtime' means that the
 * reflection metadata is fully prepared at runtime in response to loading the class dynamically.
 */
public final class RuntimeReflectionMetadata implements ReflectionMetadata {

    private final CremaResolvedJavaType type;

    public RuntimeReflectionMetadata(CremaResolvedJavaType type) {
        this.type = type;
    }

    @Override
    public int getClassFlags() {
        return type.getModifiers();
    }

    public Field[] getDeclaredFields(DynamicHub declaringClass, boolean publicOnly, @SuppressWarnings("unused") int layerNum) {
        ArrayList<Field> result = new ArrayList<>();
        includeFields(declaringClass, publicOnly, type.getDeclaredFields(), result);
        return result.toArray(new Field[0]);
    }

    private static void includeFields(DynamicHub declaringClass, boolean publicOnly, CremaResolvedJavaField[] fields, ArrayList<Field> result) {
        for (CremaResolvedJavaField field : fields) {
            if (!publicOnly || Modifier.isPublic(field.getModifiers())) {
                result.add(fromResolvedField(declaringClass, field));
            }
        }
    }

    private static Field fromResolvedField(DynamicHub declaringClass, CremaResolvedJavaField resolvedField) {
        return ReflectionObjectFactory.newField(
                        RuntimeConditionSet.unmodifiableEmptySet(),
                        DynamicHub.toClass(declaringClass),
                        resolvedField.getName(),
                        toClassOrThrow(resolvedField.getType(), resolvedField.getDeclaringClass()),
                        resolvedField.getModifiers(),
                        resolvedField.isTrustedFinal(),
                        resolvedField.getGenericSignature(),
                        resolvedField.getRawAnnotations(),
                        resolvedField.getOffset(),
                        null,
                        resolvedField.getRawTypeAnnotations());
    }

    @Override
    public Method[] getDeclaredMethods(DynamicHub declaringClass, boolean publicOnly, @SuppressWarnings("unused") int layerNum) {
        CremaResolvedJavaMethod[] declaredMethods = type.getDeclaredCremaMethods();
        ArrayList<Method> result = new ArrayList<>();
        for (CremaResolvedJavaMethod declaredMethod : declaredMethods) {
            if (!publicOnly || Modifier.isPublic(declaredMethod.getModifiers())) {
                result.add(fromResolvedMethod(declaringClass, declaredMethod));
            }
        }
        return result.toArray(new Method[0]);
    }

    private Method fromResolvedMethod(DynamicHub declaringClass, CremaResolvedJavaMethod resolvedJavaMethod) {
        Class<?> receiverType = DynamicHub.toClass(declaringClass);
        Class<?>[] parameterTypes = toClassArrayOrThrow(resolvedJavaMethod.getSignature().toParameterTypes(null), type);
        return ReflectionObjectFactory.newMethod(
                        RuntimeConditionSet.unmodifiableEmptySet(),
                        receiverType,
                        resolvedJavaMethod.getName(),
                        parameterTypes,
                        toClassOrThrow(resolvedJavaMethod.getSignature().getReturnType(type), type),
                        /* (GR-69097) resolvedJavaMethod.getDeclaredExceptions() */
                        toClassArrayOrThrow(new JavaType[0], type),
                        resolvedJavaMethod.getModifiers(),
                        resolvedJavaMethod.getGenericSignature(),
                        /* (GR-69096) resolvedJavaMethod.getRawAnnotations() */
                        new byte[0],
                        /* (GR-69096) resolvedJavaMethod.getRawParameterAnnotations() */
                        new byte[0],
                        /* (GR-69096) resolvedJavaMethod.getRawAnnotationDefault() */
                        new byte[0],
                        resolvedJavaMethod.getAccessor(receiverType, parameterTypes),
                        /* (GR-69096) resolvedJavaMethod.getRawParameters() */
                        null,
                        /* (GR-69096) resolvedJavaMethod.getRawTypeAnnotations() */
                        new byte[0],
                        declaringClass.getLayerId());
    }

    @Override
    public Constructor<?>[] getDeclaredConstructors(DynamicHub declaringClass, boolean publicOnly, @SuppressWarnings("unused") int layerNum) {
        CremaResolvedJavaMethod[] declaredConstructors = type.getDeclaredConstructors();
        ArrayList<Constructor<?>> result = new ArrayList<>();
        for (CremaResolvedJavaMethod declaredConstructor : declaredConstructors) {
            if (!publicOnly || Modifier.isPublic(declaredConstructor.getModifiers())) {
                result.add(fromResolvedConstructor(declaringClass, declaredConstructor));
            }
        }
        return result.toArray(new Constructor<?>[0]);
    }

    private Constructor<?> fromResolvedConstructor(DynamicHub declaringClass, CremaResolvedJavaMethod resolvedConstructor) {
        Class<?>[] parameterTypes = toClassArrayOrThrow(resolvedConstructor.toParameterTypes(), type);
        return ReflectionObjectFactory.newConstructor(
                        RuntimeConditionSet.unmodifiableEmptySet(),
                        DynamicHub.toClass(declaringClass),
                        parameterTypes,
                        /* (GR-69097) resolvedConstructor.getDeclaredExceptions() */
                        toClassArrayOrThrow(new JavaType[0], type),
                        resolvedConstructor.getModifiers(),
                        resolvedConstructor.getGenericSignature(),
                        /* (GR-69096) resolvedConstructor.getRawAnnotations() */
                        new byte[0],
                        /* (GR-69096) resolvedConstructor.getRawParameterAnnotations() */
                        new byte[0],
                        resolvedConstructor.getAccessor(DynamicHub.toClass(declaringClass), parameterTypes),
                        /* (GR-69096) resolvedConstructor.getRawParameters() */
                        new byte[0],
                        /* (GR-69096) resolvedConstructor.getRawTypeAnnotations() */
                        new byte[0]);
    }

    @Override
    public RecordComponent[] getRecordComponents(DynamicHub declaringClass, @SuppressWarnings("unused") int layerNum) {
        CremaResolvedJavaRecordComponent[] recordComponents = type.getRecordComponents();
        RecordComponent[] result = new RecordComponent[recordComponents.length];
        Class<?> clazz = DynamicHub.toClass(declaringClass);

        for (int i = 0; i < recordComponents.length; i++) {
            CremaResolvedJavaRecordComponent recordComponent = recordComponents[i];
            result[i] = ReflectionObjectFactory.newRecordComponent(
                            clazz,
                            recordComponent.getName(),
                            toClassOrThrow(recordComponent.getType(), type),
                            recordComponent.getSignature(),
                            recordComponent.getRawAnnotations(),
                            recordComponent.getRawTypeAnnotations());
        }
        return result;
    }

    private static Class<?> toClassOrThrow(JavaType javaType, ResolvedJavaType accessingType) {
        if (javaType instanceof UnresolvedJavaType unresolvedJavaType) {
            return CremaSupport.singleton().resolveOrThrow(unresolvedJavaType, accessingType);
        } else /* resolved type */ {
            return CremaSupport.singleton().toClass((ResolvedJavaType) javaType);
        }
    }

    private static Class<?>[] toClassArrayOrThrow(JavaType[] resolvedJavaTypes, ResolvedJavaType declaringClass) {
        Class<?>[] result = new Class<?>[resolvedJavaTypes.length];
        for (int i = 0; i < resolvedJavaTypes.length; i++) {
            result[i] = toClassOrThrow(resolvedJavaTypes[i], declaringClass);
        }
        return result;
    }
}
