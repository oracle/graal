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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Support for parsing class file annotation attributes.
 */
public class AnnotationValueSupport {

    /**
     * Gets the annotation directly present on {@code annotated} whose type is
     * {@code annotationType}. Class initialization is not triggered for enum types referenced by
     * the returned annotation. This method ignores inherited annotations.
     *
     * @param annotationType the type object corresponding to the annotation interface type
     * @return {@code annotated}'s annotation for the specified annotation type if directly present
     *         on this element, else null
     * @throws IllegalArgumentException if {@code annotationType} is not an annotation interface
     *             type
     */
    public static AnnotationValue getDeclaredAnnotationValue(ResolvedJavaType annotationType, Annotated annotated) {
        if (!annotationType.isAnnotation()) {
            throw new IllegalArgumentException(annotationType.toJavaName() + " is not an annotation interface");
        }
        return getDeclaredAnnotationValues(annotated).get(annotationType);
    }

    /**
     * Gets the annotations directly present on {@code annotated}. Class initialization is not
     * triggered for enum types referenced by the returned annotations. This method ignores
     * inherited annotations.
     *
     * @return an immutable map from annotation type to annotation of the annotations directly
     *         present on this element
     */
    public static Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(Annotated annotated) {
        AnnotationsInfo info = getDeclaredAnnotationInfo(annotated);
        if (info == null) {
            return Collections.emptyMap();
        }
        return AnnotationValueParser.parseAnnotations(info.bytes(), info.constPool(), info.container());
    }

    private static AnnotationsInfo getDeclaredAnnotationInfo(Annotated annotated) {
        return annotated.getDeclaredAnnotationInfo();
    }

    /**
     * Gets the type annotations for {@code annotated} that back the implementation of
     * {@link Method#getAnnotatedReturnType()}, {@link Method#getAnnotatedReceiverType()},
     * {@link Method#getAnnotatedExceptionTypes()}, {@link Method#getAnnotatedParameterTypes()},
     * {@link Field#getAnnotatedType()}, {@link RecordComponent#getAnnotatedType()},
     * {@link Class#getAnnotatedSuperclass()} or {@link Class#getAnnotatedInterfaces()}. This method
     * returns an empty list if there are no type annotations.
     */
    public static List<TypeAnnotationValue> getTypeAnnotationValues(Annotated annotated) {
        AnnotationsInfo info = annotated.getTypeAnnotationInfo();
        if (info == null) {
            return List.of();
        }
        return TypeAnnotationValueParser.parseTypeAnnotations(info.bytes(), info.constPool(), info.container());
    }

    /**
     * Returns a list of lists of {@link AnnotationValue}s that represents the
     * {@code RuntimeVisibleParameterAnnotations} for {@code method}. Note that this differs from
     * {@link Method#getParameterAnnotations()} in that it excludes entries for synthetic and
     * mandated parameters.
     *
     * @return null if there are no parameter annotations for {@code method} otherwise an immutable
     *         list of immutable lists of parameter annotations
     */
    public static List<List<AnnotationValue>> getParameterAnnotationValues(ResolvedJavaMethod method) {
        AnnotationsInfo info = method.getParameterAnnotationInfo();
        if (info == null) {
            return List.of();
        }
        return AnnotationValueParser.parseParameterAnnotations(info.bytes(), info.constPool(), info.container());
    }

    /**
     * Returns the default value for the annotation member represented by {@code method}. Returns
     * null if no default is associated with {@code method}, or if {@code method} does not represent
     * a declared member of an annotation type.
     *
     * @see Method#getDefaultValue()
     * @return the default value for the annotation member represented by this object. The type of
     *         the returned value is specified by {@link AnnotationValue#get}
     */
    public static Object getAnnotationDefaultValue(ResolvedJavaMethod method) {
        AnnotationsInfo info = method.getAnnotationDefaultInfo();
        if (info == null) {
            return null;
        }
        ResolvedJavaType container = info.container();
        ResolvedJavaType memberType = method.getSignature().getReturnType(container).resolve(container);
        return AnnotationValueParser.parseMemberValue(memberType, ByteBuffer.wrap(info.bytes()), info.constPool(), container);
    }
}
