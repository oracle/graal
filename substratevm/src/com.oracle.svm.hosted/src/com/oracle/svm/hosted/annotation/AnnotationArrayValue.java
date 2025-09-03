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

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.ExceptionProxy;

public final class AnnotationArrayValue extends AnnotationMemberValue {
    private static final AnnotationArrayValue EMPTY_ARRAY_VALUE = new AnnotationArrayValue();

    private final AnnotationMemberValue[] elements;

    static AnnotationArrayValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
        int length = buf.getShort() & 0xFFFF;
        if (length == 0) {
            return EMPTY_ARRAY_VALUE;
        }
        AnnotationMemberValue[] elements = new AnnotationMemberValue[length];
        for (int i = 0; i < length; ++i) {
            elements[i] = AnnotationMemberValue.extract(buf, cp, container, skip);
        }
        return skip ? null : new AnnotationArrayValue(elements);
    }

    AnnotationArrayValue(Class<?> elementType, Object values) {
        int length = Array.getLength(values);
        this.elements = new AnnotationMemberValue[length];
        for (int i = 0; i < length; ++i) {
            this.elements[i] = AnnotationMemberValue.from(elementType, Array.get(values, i));
        }
    }

    private AnnotationArrayValue(AnnotationMemberValue... elements) {
        this.elements = elements;
    }

    public int getElementCount() {
        return elements.length;
    }

    public void forEachElement(Consumer<AnnotationMemberValue> callback) {
        for (AnnotationMemberValue element : elements) {
            callback.accept(element);
        }
    }

    @Override
    public List<Class<?>> getTypes() {
        List<Class<?>> types = new ArrayList<>();
        for (AnnotationMemberValue element : elements) {
            types.addAll(element.getTypes());
        }
        return types;
    }

    @Override
    public char getTag() {
        return '[';
    }

    @Override
    public Object get(Class<?> memberType) {
        Class<?> componentType = memberType.getComponentType();
        Object result = Array.newInstance(memberType.getComponentType(), elements.length);
        int tag = 0;
        boolean typeMismatch = false;
        for (int i = 0; i < elements.length; ++i) {
            Object value = elements[i].get(componentType);
            if (value instanceof ExceptionProxy) {
                typeMismatch = true;
                tag = elements[i].getTag();
            } else {
                Array.set(result, i, value);
            }
        }
        if (typeMismatch) {
            return AnnotationMetadata.createAnnotationTypeMismatchExceptionProxy("Array with component tag: " + (tag == 0 ? "0" : (char) tag));
        }
        return AnnotationMetadata.checkResult(result, memberType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationArrayValue that = (AnnotationArrayValue) o;
        return Arrays.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }
}
