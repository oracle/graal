/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.polyglot;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

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
 * @since 24.2
 */
public abstract class TypeLiteral<T> {

    private final Class<T> rawType;

    /**
     * Constructs a new type literal. Derives represented class from type parameter.
     *
     * <p>
     * Clients create an empty anonymous subclass. Doing so embeds the type parameter in the
     * anonymous class's type hierarchy, so we can reconstitute it at runtime despite erasure.
     *
     * @since 24.2
     */
    @SuppressWarnings("unchecked")
    protected TypeLiteral() {
        Type guestType = extractLiteralGuestType(this.getClass());
        extractLiteralType(guestType);
        rawType = (Class<T>) extractRawType(guestType);
    }

    public Class<T> getRawType() {
        return rawType;
    }

    private static Type extractLiteralGuestType(@SuppressWarnings("rawtypes") Class<? extends TypeLiteral> literalClass) {
        Type superType = literalClass.getGenericSuperclass();
        Type typeArgument;
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

    /**
     * This method can be used to obtain the reified type literal matching the class type parameter
     * of a mapped foreign object at the given class generic type argument index. The primary use
     * case is a parameterized host class which is custom mapped to a type in an embedded Espresso
     * context.
     *
     * <p>
     * <b>Example of a custom parameterized host class that is type mapped to a guest class:</b>
     * {@link TypeLiteralSnippets}
     *
     * @param foreignObject the type-mapped foreign object
     * @param typeArgumentIndex the type argument index
     * @param <T> the generic type
     * @return the type literal
     *
     * @since 24.2
     */
    public static native <T> TypeLiteral<T> getReifiedType(Object foreignObject, int typeArgumentIndex);

    private static Class<?> arrayTypeFromComponentType(Class<?> componentType) {
        return Array.newInstance(componentType, 0).getClass();
    }

    private native void extractLiteralType(Type guestType);

    // only used by the VM internally
    @SuppressWarnings("unused")
    private final class InternalTypeLiteral extends TypeLiteral<T> {
    }
}

class TypeLiteralSnippets {

    abstract
    // BEGIN: TypeLiteralSnippets.GetTypeLiteral
    class GetTypeLiteral {

        // class defined and loaded in the host
        class CustomHostType<T> extends AbstractList<T> implements List<T> {

            private final ArrayList<T> delegate;

            CustomHostType(ArrayList<T> list) {
                this.delegate = list;
            }

            @Override
            public T get(int index) {
                return delegate.get(index);
            }

            @Override
            public T remove(int index) {
                return delegate.remove(index);
            }

            @Override
            public int size() {
                return delegate.size();
            }
        }

        // class defined and loaded inside an embedded Espresso guest context
        class CustomType<T> {

            private final Object foreignObject;

            CustomType(Object foreignObject) {
                this.foreignObject = foreignObject;
            }

            @SuppressWarnings("unchecked")
            public T get(int index) {
                try {
                    if (Interop.isArrayElementReadable(foreignObject, index)) {
                        Object rawObject = Interop.readArrayElement(foreignObject, index);
                        // use the getReifiedType API method to fetch the type literal of the
                        // foreign that was passed along from the generic target type when entering
                        // Espresso
                        TypeLiteral<T> typeLiteral = TypeLiteral.getReifiedType(foreignObject, 0);
                        if (typeLiteral != null) {
                            // now, if available we can cast the raw foreign object to the specific
                            // generic type
                            return Polyglot.castWithGenerics(rawObject, typeLiteral);
                        } else {
                            return (T) Polyglot.cast(Object.class, rawObject);
                        }
                    }
                } catch (InteropException ignored) {
                }
                throw new UnsupportedOperationException();
            }
        }

        /**
         * This class is defined and loaded within the guest.
         * <p>
         * The converter class used to map from the CustomHostType to the CustomType in the guest.
         * To set up a custom type converter use:
         * {@code builder.option("java.PolyglotTypeConverters.com.oracle.truffle.espresso.test.interop.TypeMappingTest$CustomHostType",
         *                 "com.oracle.truffle.espresso.test.interop.TypeMappingTest$CustomTypeConverter");}
         */
        class CustomTypeConverter<T> implements GuestTypeConversion<CustomType<T>> {

            @Override
            public CustomType<T> toGuest(Object foreignObject) {
                if (Interop.hasArrayElements(foreignObject)) {
                    return new CustomType<>(foreignObject);
                } else {
                    throw new ClassCastException("object " + foreignObject + " cannot be cast to " + CustomType.class.getName());
                }
            }
        }

        class GuestExampleClass {
            @SuppressWarnings("unused")
            void example(CustomType<Long[]> custom) {
                Long[] array = custom.get(0);
                // ... use the properly typed Long array
            }
        }

        // in a host method, now you can pass a custom host object to the embedded context:
        class HostExample {
            void example() {
                /*
                 * Below example code requires reference to the polyglot Espresso context, thus it
                 * is commented out here.
                 */
                // Value clazz =
                // getPolyglotContext().getBindings("java").getMember(GuestExampleClass.class.getName());
                // ArrayList<Long[]> list = new ArrayList<>();
                // list.add(new Long[]{new Long(1), new Long(2), new Long(3)});
                // CustomHostType<Long[]> custom = new CustomHostType<>(list);
                // clazz.invokeMember("testCustomType", custom);
            }
        }
    }
    // END: TypeLiteralSnippets.GetTypeLiteral
}
