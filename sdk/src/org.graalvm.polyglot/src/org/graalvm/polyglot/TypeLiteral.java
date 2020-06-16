/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.polyglot;

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
 *
 * @since 19.0
 * @see org.graalvm.polyglot.Value#as(TypeLiteral)
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
     *
     * @since 19.0
     */
    public final Type getType() {
        return this.type;
    }

    /**
     * Returns the raw class type of the literal.
     *
     * @since 19.0
     */
    public final Class<T> getRawType() {
        return rawType;
    }

    /**
     * @since 19.0
     */
    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * @since 19.0
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * @since 19.0
     */
    @Override
    public final String toString() {
        return "TypeLiteral<" + type + ">";
    }

}
