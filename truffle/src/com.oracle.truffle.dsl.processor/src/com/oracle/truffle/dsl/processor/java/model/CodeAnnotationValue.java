/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
