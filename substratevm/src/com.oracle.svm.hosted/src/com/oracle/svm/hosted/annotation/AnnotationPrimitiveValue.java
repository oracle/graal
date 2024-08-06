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

import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.internal.reflect.ConstantPool;
import sun.invoke.util.Wrapper;

public final class AnnotationPrimitiveValue extends AnnotationMemberValue {
    private final char tag;
    private final Object value;

    static AnnotationPrimitiveValue extract(ByteBuffer buf, ConstantPool cp, char tag, boolean skip) {
        int constIndex = buf.getShort() & 0xFFFF;
        if (skip) {
            return null;
        }
        Object value;
        switch (tag) {
            case 'B':
                value = (byte) cp.getIntAt(constIndex);
                break;
            case 'C':
                value = (char) cp.getIntAt(constIndex);
                break;
            case 'D':
                value = cp.getDoubleAt(constIndex);
                break;
            case 'F':
                value = cp.getFloatAt(constIndex);
                break;
            case 'I':
                value = cp.getIntAt(constIndex);
                break;
            case 'J':
                value = cp.getLongAt(constIndex);
                break;
            case 'S':
                value = (short) cp.getIntAt(constIndex);
                break;
            case 'Z':
                value = cp.getIntAt(constIndex) != 0;
                break;
            default:
                throw new AnnotationMetadata.AnnotationExtractionError(tag, "Invalid annotation encoding. Unknown tag");
        }
        assert Wrapper.forWrapperType(value.getClass()).basicTypeChar() == tag;
        return new AnnotationPrimitiveValue(tag, value);
    }

    AnnotationPrimitiveValue(Class<?> type, Object value) {
        this((type.isPrimitive() ? Wrapper.forPrimitiveType(type) : Wrapper.forWrapperType(type)).basicTypeChar(), value);
    }

    private AnnotationPrimitiveValue(char tag, Object value) {
        this.tag = tag;
        this.value = value;
    }

    @Override
    public char getTag() {
        return tag;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object get(Class<?> memberType) {
        Class<?> boxedType = memberType.isPrimitive() ? Wrapper.forPrimitiveType(memberType).wrapperType() : memberType;
        return AnnotationMetadata.checkResult(value, boxedType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationPrimitiveValue that = (AnnotationPrimitiveValue) o;
        return tag == that.tag && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, value);
    }
}
