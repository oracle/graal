/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge.nativebridge;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Represents a generic type {@code T}. Java doesn't yet provide a way to represent generic types,
 * so this class does. Forces clients to create a subclass of this class which enables retrieval of
 * the type information even at runtime.
 *
 * <p>
 * For example, to create a type literal for {@code List<String>}, you can create an empty anonymous
 * inner class:
 *
 * <p>
 * {@code TypeLiteral<List<String>> list = new TypeLiteral<List<String>>() {};}
 *
 */
public abstract class TypeLiteral<T> {

    private final Type type;
    private final Class<T> rawType;

    /**
     * Constructs a new type literal. Derives represented class from type parameter.
     *
     * <p>
     * Clients create an empty anonymous subclass. Doing so embeds the type parameter in the
     * anonymous class's type hierarchy so we can reconstitute it at runtime despite erasure.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    protected TypeLiteral() {
        this.type = extractLiteralType(this.getClass());
        this.rawType = (Class<T>) extractRawType(type);
    }

    private static Type extractLiteralType(@SuppressWarnings("rawtypes") Class<? extends TypeLiteral> literalClass) {
        Type superType = literalClass.getGenericSuperclass();
        Type typeArgument = null;
        while (true) {
            if (superType instanceof ParameterizedType) {
                ParameterizedType parametrizedType = (ParameterizedType) superType;
                if (parametrizedType.getRawType() == TypeLiteral.class) {
                    // found
                    typeArgument = parametrizedType.getActualTypeArguments()[0];
                    break;
                } else {
                    throw new AssertionError("Unsupported type hierarchy for type literal.");
                }
            } else if (superType instanceof Class<?>) {
                if (superType == TypeLiteral.class) {
                    typeArgument = Object.class;
                    break;
                } else {
                    superType = ((Class<?>) superType).getGenericSuperclass();
                }
            } else {
                throw new AssertionError("Unsupported type hierarchy for type literal.");
            }
        }
        return typeArgument;
    }

    private static Class<?> extractRawType(Type type) {
        Class<?> rawType;
        if (type instanceof Class) {
            rawType = (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            rawType = (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            rawType = arrayTypeFromComponentType(extractRawType(((GenericArrayType) type).getGenericComponentType()));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return rawType;
    }

    private static Class<?> arrayTypeFromComponentType(Class<?> componentType) {
        return Array.newInstance(componentType, 0).getClass();
    }

    /**
     * Returns the type literal including generic type information.
     */
    public final Type getType() {
        return this.type;
    }

    /**
     * Returns the raw class type of the literal.
     */
    public final Class<T> getRawType() {
        return rawType;
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final String toString() {
        return "TypeLiteral<" + type + ">";
    }

}
