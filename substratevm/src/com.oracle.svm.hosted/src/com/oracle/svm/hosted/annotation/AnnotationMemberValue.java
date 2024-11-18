/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationType;

public abstract class AnnotationMemberValue {
    static AnnotationMemberValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
        char tag = (char) buf.get();
        switch (tag) {
            case 'e':
                return AnnotationEnumValue.extract(buf, cp, container, skip);
            case 'c':
                return AnnotationClassValue.extract(buf, cp, container, skip);
            case 's':
                return AnnotationStringValue.extract(buf, cp, skip);
            case '@':
                return AnnotationValue.extract(buf, cp, container, true, skip);
            case '[':
                return AnnotationArrayValue.extract(buf, cp, container, skip);
            default:
                return AnnotationPrimitiveValue.extract(buf, cp, tag, skip);
        }
    }

    static AnnotationMemberValue from(Class<?> type, Object value) {
        if (type.isAnnotation()) {
            return new AnnotationValue((Annotation) value);
        } else if (type.isEnum()) {
            return new AnnotationEnumValue((Enum<?>) value);
        } else if (type == Class.class) {
            return new AnnotationClassValue((Class<?>) value);
        } else if (type == String.class) {
            return new AnnotationStringValue((String) value);
        } else if (type.isArray()) {
            return new AnnotationArrayValue(type.getComponentType(), value);
        } else {
            return new AnnotationPrimitiveValue(type, value);
        }
    }

    public static AnnotationMemberValue getMemberValue(Annotation annotation, String memberName, Method memberAccessor, AnnotationType annotationType) {
        AnnotationMemberValue memberValue;
        try {
            memberAccessor.setAccessible(true);
            memberValue = AnnotationMemberValue.from(annotationType.memberTypes().get(memberName), memberAccessor.invoke(annotation));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AnnotationMetadata.AnnotationExtractionError(annotation, e);
        }
        return memberValue;
    }

    public List<Class<?>> getTypes() {
        return Collections.emptyList();
    }

    public abstract char getTag();

    public abstract Object get(Class<?> memberType);
}
