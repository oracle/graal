/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.target;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;

import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;

@TargetClass(TypeAnnotationParser.class)
public final class Target_sun_reflect_annotation_TypeAnnotationParser {
    @Alias
    public static native AnnotatedType buildAnnotatedType(byte[] rawAnnotations,
                    Target_jdk_internal_reflect_ConstantPool cp,
                    AnnotatedElement decl,
                    Class<?> container,
                    Type type,
                    TypeAnnotation.TypeAnnotationTarget filter);

    @Alias
    public static native AnnotatedType[] buildAnnotatedTypes(byte[] rawAnnotations,
                    Target_jdk_internal_reflect_ConstantPool cp,
                    AnnotatedElement decl,
                    Class<?> container,
                    Type[] types,
                    TypeAnnotation.TypeAnnotationTarget filter);

    @Alias
    // Checkstyle: stop
    private static TypeAnnotation[] EMPTY_TYPE_ANNOTATION_ARRAY;
    // Checkstyle: resume

    @Substitute
    private static TypeAnnotation[] parseTypeAnnotations(byte[] rawAnnotations,
                    Target_jdk_internal_reflect_ConstantPool cp,
                    AnnotatedElement baseDecl,
                    Class<?> container) {
        if (rawAnnotations == null) {
            return EMPTY_TYPE_ANNOTATION_ARRAY;
        }

        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        buf.order(ConfigurationValues.getTarget().arch.getByteOrder());
        int annotationCount = buf.getShort() & 0xFFFF;
        List<TypeAnnotation> typeAnnotations = new ArrayList<>(annotationCount);

        // Parse each TypeAnnotation
        for (int i = 0; i < annotationCount; i++) {
            TypeAnnotation ta = parseTypeAnnotation(buf, cp, baseDecl, container);
            if (ta != null) {
                typeAnnotations.add(ta);
            }
        }

        return typeAnnotations.toArray(EMPTY_TYPE_ANNOTATION_ARRAY);
    }

    @Alias //
    private static native TypeAnnotation parseTypeAnnotation(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool cp,
                    AnnotatedElement baseDecl,
                    Class<?> container);
}
