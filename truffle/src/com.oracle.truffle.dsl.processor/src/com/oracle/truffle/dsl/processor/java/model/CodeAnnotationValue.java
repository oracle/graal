/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.List;
import java.util.Objects;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class CodeAnnotationValue implements AnnotationValue {

    private final Object value;

    public CodeAnnotationValue(Object value) {
        Objects.requireNonNull(value);
        if ((value instanceof AnnotationMirror) || (value instanceof List<?>) || (value instanceof Boolean) || (value instanceof Byte) || (value instanceof Character) || (value instanceof Double) ||
                        (value instanceof VariableElement) || (value instanceof Float) || (value instanceof Integer) || (value instanceof Long) || (value instanceof Short) ||
                        (value instanceof String) || (value instanceof TypeMirror)) {
            this.value = value;
        } else {
            throw new IllegalArgumentException("Invalid annotation value type " + value.getClass().getName());
        }
    }

    @Override
    public Object getValue() {
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
        if (value instanceof AnnotationMirror) {
            return v.visitAnnotation((AnnotationMirror) value, p);
        } else if (value instanceof List<?>) {
            return v.visitArray((List<? extends AnnotationValue>) value, p);
        } else if (value instanceof Boolean) {
            return v.visitBoolean((boolean) value, p);
        } else if (value instanceof Byte) {
            return v.visitByte((byte) value, p);
        } else if (value instanceof Character) {
            return v.visitChar((char) value, p);
        } else if (value instanceof Double) {
            return v.visitDouble((double) value, p);
        } else if (value instanceof VariableElement) {
            return v.visitEnumConstant((VariableElement) value, p);
        } else if (value instanceof Float) {
            return v.visitFloat((float) value, p);
        } else if (value instanceof Integer) {
            return v.visitInt((int) value, p);
        } else if (value instanceof Long) {
            return v.visitLong((long) value, p);
        } else if (value instanceof Short) {
            return v.visitShort((short) value, p);
        } else if (value instanceof String) {
            return v.visitString((String) value, p);
        } else if (value instanceof TypeMirror) {
            return v.visitType((TypeMirror) value, p);
        } else {
            return v.visitUnknown(this, p);
        }
    }

}
