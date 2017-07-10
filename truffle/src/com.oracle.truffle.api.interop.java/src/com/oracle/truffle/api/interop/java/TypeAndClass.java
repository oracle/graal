/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.TruffleOptions;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

final class TypeAndClass<T> {
    static final TypeAndClass<Object> ANY = new TypeAndClass<>(null, Object.class);

    private final Type type;
    final Class<T> clazz;

    TypeAndClass(Type type, Class<T> clazz) {
        this.type = type;
        this.clazz = clazz;
    }

    T cast(Object o) {
        return clazz.cast(o);
    }

    TypeAndClass<?> getParameterType(int i) {
        if (!TruffleOptions.AOT && type instanceof ParameterizedType) {
            ParameterizedType parametrizedType = (ParameterizedType) type;
            final Type[] arr = parametrizedType.getActualTypeArguments();
            Class<?> elementClass = Object.class;
            if (arr.length > i) {
                Type elementType = arr[i];
                if (elementType instanceof ParameterizedType) {
                    elementType = ((ParameterizedType) elementType).getRawType();
                }
                if (elementType instanceof Class<?>) {
                    elementClass = (Class<?>) elementType;
                }
            }
            return new TypeAndClass<>(arr[i], elementClass);
        }
        return ANY;
    }

    @Override
    public String toString() {
        return "[" + clazz + ": " + Objects.toString(type) + "]";
    }

    static TypeAndClass<?> forReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return ANY;
        }
        return new TypeAndClass<>(method.getGenericReturnType(), method.getReturnType());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TypeAndClass<?>)) {
            return false;
        }
        TypeAndClass<?> other = (TypeAndClass<?>) obj;
        return Objects.equals(clazz, other.clazz) && Objects.equals(type, other.type);
    }
}
