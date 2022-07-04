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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.ExceptionProxy;

public final class AnnotationClassValue extends AnnotationMemberValue {
    private final Class<?> value;

    static AnnotationMemberValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
        Object typeOrException = AnnotationMetadata.extractType(buf, cp, container, skip);
        if (skip) {
            return null;
        }
        if (typeOrException instanceof ExceptionProxy) {
            return new AnnotationExceptionProxyValue((ExceptionProxy) typeOrException);
        }
        return new AnnotationClassValue((Class<?>) typeOrException);
    }

    AnnotationClassValue(Class<?> value) {
        this.value = value;
    }

    public Class<?> getValue() {
        return value;
    }

    @Override
    public List<Class<?>> getTypes() {
        return Collections.singletonList(value);
    }

    @Override
    public char getTag() {
        return 'c';
    }

    @Override
    public Object get(Class<?> memberType) {
        return AnnotationMetadata.checkResult(value, memberType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationClassValue that = (AnnotationClassValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
