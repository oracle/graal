/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vmaccess;

import java.util.List;
import java.util.Map;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.ErrorElement;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Validates the JVMCI representation of annotation element values before a VMAccess provider
 * materializes an annotation proxy. Annotation parsing and conversion from live annotations
 * normally produce values that either match the declared annotation schema or represent malformed
 * data with a deferred {@link ErrorElement}. However, the public {@link AnnotationValue}
 * constructor accepts caller-synthesized values without comparing them to the declared member
 * types, so conversion boundaries must perform that validation explicitly.
 */
public final class AnnotationValueValidation {
    /**
     * Prevents instantiation of this utility class.
     */
    private AnnotationValueValidation() {
    }

    /**
     * Validates each explicitly supplied, recognized element of {@code annotationValue} against
     * the member type described by {@code annotationValueType}, recursively checking array
     * elements. Error elements are accepted because they represent failures that must remain
     * deferred until the affected annotation member is accessed. Missing members and defaults are
     * not checked here; providers preserve missing required members and supply defaults while
     * materializing the annotation proxy.
     *
     * @param annotationValue the annotation metadata containing the explicit elements
     * @param annotationValueType the declared member types used for validation
     * @throws IllegalArgumentException if an element does not match its declared member type
     */
    public static void validateElements(AnnotationValue annotationValue, AnnotationValueType annotationValueType) {
        for (Map.Entry<String, Object> entry : annotationValue.getElements().entrySet()) {
            ResolvedJavaType memberType = annotationValueType.memberTypes().get(entry.getKey());
            if (memberType != null && !matchesElementType(entry.getValue(), memberType)) {
                throw new IllegalArgumentException("Annotation member " + annotationValue.getAnnotationType().toJavaName() + "." + entry.getKey() +
                                " does not match declared type " + memberType.toJavaName());
            }
        }
    }

    /**
     * Determines whether a JVMCI annotation element representation matches its declared member
     * type, recursively validating array elements.
     */
    private static boolean matchesElementType(Object value, ResolvedJavaType expectedType) {
        if (value == null) {
            return false;
        }
        if (value instanceof ErrorElement) {
            return true;
        }
        if (expectedType.isArray()) {
            if (!(value instanceof List<?> elements)) {
                return false;
            }
            for (Object element : elements) {
                if (!matchesElementType(element, expectedType.getComponentType())) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof List<?>) {
            return false;
        }
        if (value instanceof AnnotationValue nestedAnnotation && nestedAnnotation.isError()) {
            return expectedType.isAnnotation();
        }
        return AnnotationValueType.matchesElementType(value, expectedType);
    }
}
