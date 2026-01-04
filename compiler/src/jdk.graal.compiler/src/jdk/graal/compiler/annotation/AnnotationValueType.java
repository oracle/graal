/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents the types and default values for the elements of an annotation.
 */
public final class AnnotationValueType {
    private final Map<String, ResolvedJavaType> memberTypes;
    private final Map<String, Object> memberDefaults;

    private static final Map<ResolvedJavaType, AnnotationValueType> ANNOTATION_TYPES = new ConcurrentHashMap<>();

    /**
     * Gets the {@link AnnotationValueType} for {@code annotationClass}.
     */
    public static AnnotationValueType getInstance(
                    ResolvedJavaType annotationClass) {
        return ANNOTATION_TYPES.computeIfAbsent(annotationClass, AnnotationValueType::new);
    }

    private AnnotationValueType(ResolvedJavaType annotationClass) {
        if (!annotationClass.isAnnotation()) {
            throw new IllegalArgumentException("Not an annotation type");
        }

        ResolvedJavaMethod[] methods = annotationClass.getDeclaredMethods();

        memberTypes = new EconomicHashMap<>(methods.length + 1);
        memberDefaults = new EconomicHashMap<>(0);

        for (ResolvedJavaMethod method : methods) {
            if (method.isPublic() &&
                            method.isAbstract() &&
                            !method.isSynthetic()) {
                if (method.getSignature().getParameterCount(false) != 0) {
                    throw new IllegalArgumentException(method + " has params");
                }
                String name = method.getName();
                ResolvedJavaType memberType = method.getSignature().getReturnType(annotationClass).resolve(annotationClass);
                memberTypes.put(name, memberType);

                Object defaultValue = AnnotationValueSupport.getAnnotationDefaultValue(method);
                if (defaultValue != null) {
                    memberDefaults.put(name, defaultValue);
                }
            }
        }
    }

    /**
     * Determines if the type of {@code elementValue} matches {@code elementType}.
     *
     * @param elementValue a value of a type returned by {@link AnnotationValue#get}
     * @param elementType an annotation element type (i.e. the return type of an annotation
     *            interface method)
     */
    public static boolean matchesElementType(Object elementValue, ResolvedJavaType elementType) {
        if (elementValue instanceof AnnotationValue av) {
            return elementType.equals(av.getAnnotationType());
        }
        if (elementValue instanceof ResolvedJavaType) {
            return elementType.getName().equals("Ljava/lang/Class;");
        }
        if (elementValue instanceof EnumElement ee) {
            return ee.enumType.equals(elementType);
        }
        if (elementType.isPrimitive()) {
            return elementValue.getClass() == elementType.getJavaKind().toBoxedJavaClass();
        }
        return elementType.toJavaName().equals(elementValue.getClass().getName());
    }

    /**
     * Gets the element types for the annotation represented by this object.
     *
     * @return a map from element name to element type
     */
    public Map<String, ResolvedJavaType> memberTypes() {
        return memberTypes;
    }

    /**
     * Gets the element defaults for the annotation represented by this object.
     *
     * @return a map from element name to the default value for the element if it has one, else
     *         {@code null}
     */
    public Map<String, Object> memberDefaults() {
        return memberDefaults;
    }

    @Override
    public String toString() {
        return "Annotation Type:\n" +
                        "   Member types: " + memberTypes + "\n" +
                        "   Member defaults: " + memberDefaults;
    }
}
