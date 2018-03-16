/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public final class JavaHostLanguageProvider implements LanguageProvider {
    private static final String ID = "java-host";

    public JavaHostLanguageProvider() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Collection<? extends Snippet> createValueConstructors(final Context context) {
        final List<Snippet> result = new ArrayList<>();
        final Map<Class<?>, Primitive> primitives = new HashMap<>();
        primitives.put(Boolean.class, Primitive.create("boolean", false, TypeDescriptor.BOOLEAN));
        primitives.put(Byte.class, Primitive.create("byte", Byte.MIN_VALUE, TypeDescriptor.NUMBER));
        primitives.put(Short.class, Primitive.create("short", Short.MIN_VALUE, TypeDescriptor.NUMBER));
        primitives.put(Character.class, Primitive.create("char", ' ', TypeDescriptor.STRING));
        primitives.put(Integer.class, Primitive.create("int", Integer.MAX_VALUE, TypeDescriptor.NUMBER));   // Integer.MIN_VALUE
                                                                                                            // is
                                                                                                            // NA
                                                                                                            // for
                                                                                                            // fast-r
        primitives.put(Long.class, Primitive.create("long", Long.MIN_VALUE, TypeDescriptor.NUMBER));
        primitives.put(Float.class, Primitive.create("float", Float.MAX_VALUE, TypeDescriptor.NUMBER));
        primitives.put(Double.class, Primitive.create("double", Double.MAX_VALUE, TypeDescriptor.NUMBER));
        primitives.put(String.class, Primitive.create("java.lang.String", "TEST", TypeDescriptor.STRING));

        // Java primitives
        for (Primitive primitive : primitives.values()) {
            result.add(createPrimitive(context, primitive));
        }
        // Arrays
        result.add(Snippet.newBuilder("Array<int>", export(context, new ValueSupplier<>(new int[]{1, 2})),
                        TypeDescriptor.array(TypeDescriptor.NUMBER)).build());
        result.add(Snippet.newBuilder("Array<java.lang.Object>", export(context, new ValueSupplier<>(new Object[]{1, "TEST"})),
                        TypeDescriptor.array(TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.STRING))).build());
        // Primitive Proxies
        for (Primitive primitive : primitives.values()) {
            result.add(createProxyPrimitive(context, primitive));
        }
        // Array Proxies
        result.add(createProxyArray(context, null));
        for (Primitive primitive : primitives.values()) {
            result.add(createProxyArray(context, primitive));
        }
        // Object Proxies
        result.add(Snippet.newBuilder("Proxy<java.lang.Object{}>", export(context, new ValueSupplier<>(ProxyObject.fromMap(Collections.emptyMap()))), TypeDescriptor.OBJECT).build());
        final Map<String, Object> props = new HashMap<>();
        props.put("name", "test");
        result.add(Snippet.newBuilder("Proxy<java.lang.Object{name}>", export(context, new ValueSupplier<>(ProxyObject.fromMap(props))), TypeDescriptor.OBJECT).build());
        // Executable Proxies
        // Generic executable
        result.add(Snippet.newBuilder(
                        "ProxyExecutable<...>",
                        export(context, new ValueSupplier<>(new ProxyExecutableImpl())),
                        TypeDescriptor.EXECUTABLE).build());
        // No-args execuable
        result.add(Snippet.newBuilder(
                        "ProxyExecutable<>",
                        export(context, new ValueSupplier<>(new ProxyExecutableImpl(ProxyExecutableImpl.EMPTY, 0))),
                        TypeDescriptor.executable(TypeDescriptor.ANY)).build());
        for (Primitive primitive : new Primitive[]{
                        primitives.get(Boolean.class),
                        primitives.get(Integer.class),
                        primitives.get(String.class)}) {
            result.add(createProxyExecutable(context, primitive));
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Value createIdentityFunction(final Context context) {
        return context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return arguments[0];
            }
        });
    }

    @Override
    public Collection<? extends Snippet> createExpressions(final Context context) {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Snippet> createStatements(final Context context) {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Snippet> createScripts(final Context context) {
        return Collections.emptySet();
    }

    @Override
    public Collection<? extends Source> createInvalidSyntaxScripts(final Context context) {
        return Collections.emptySet();
    }

    private static Snippet createPrimitive(
                    final Context context,
                    final Primitive primitive) {
        return Snippet.newBuilder(
                        primitive.name,
                        export(context,
                                        new ValueSupplier<>(primitive.value)),
                        primitive.type).build();
    }

    private static Snippet createProxyPrimitive(
                    final Context context,
                    final Primitive primitive) {
        return Snippet.newBuilder(
                        String.format("Proxy<%s>", primitive.name),
                        export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(primitive.value))),
                        primitive.type).build();
    }

    private static Snippet createProxyArray(
                    final Context context,
                    final Primitive primitive) {
        return Snippet.newBuilder(
                        String.format("Proxy<Array<%s>>", primitive == null ? "" : primitive.name),
                        export(context, new ValueSupplier<>(primitive == null ? ProxyArray.fromArray() : ProxyArray.fromArray(primitive.value, primitive.value))),
                        primitive == null
                                        ? TypeDescriptor.array(TypeDescriptor.intersection(TypeDescriptor.ARRAY, TypeDescriptor.BOOLEAN, TypeDescriptor.EXECUTABLE, TypeDescriptor.HOST_OBJECT,
                                                        TypeDescriptor.NATIVE_POINTER, TypeDescriptor.NULL, TypeDescriptor.NUMBER, TypeDescriptor.OBJECT, TypeDescriptor.STRING))
                                        : TypeDescriptor.array(primitive.type)).build();
    }

    private static Snippet createProxyExecutable(
                    final Context context,
                    final Primitive primitive) {
        return Snippet.newBuilder(
                        String.format("ProxyExecutable<%s,%s>", primitive.name, primitive.name),
                        export(context, new ValueSupplier<>(new ProxyExecutableImpl(primitive, 2))),
                        TypeDescriptor.executable(primitive.type, primitive.type, primitive.type)).build();
    }

    private static Value export(final Context context, final Supplier<Object> s) {
        return context.asValue(s);
    }

    @SuppressWarnings("deprecation")
    private static final class ProxyPrimitiveImpl implements org.graalvm.polyglot.proxy.ProxyPrimitive {
        private final Object primitiveValue;

        ProxyPrimitiveImpl(final Object primitiveValue) {
            Objects.requireNonNull(primitiveValue);
            this.primitiveValue = primitiveValue;
        }

        @Override
        public Object asPrimitive() {
            return primitiveValue;
        }
    }

    private static final class ValueSupplier<T> implements Supplier<T> {
        private final T value;

        ValueSupplier(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }
    }

    private static final class Primitive {
        final String name;
        final Object value;
        final TypeDescriptor type;

        private Primitive(final String name, final Object value, final TypeDescriptor type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }

        static Primitive create(final String name, final Object value, final TypeDescriptor type) {
            return new Primitive(name, value, type);
        }
    }

    private static final class ProxyExecutableImpl implements ProxyExecutable {
        private static final Consumer<? super Value> EMPTY = new Consumer<Value>() {
            @Override
            public void accept(Value t) {
            }
        };
        private Consumer<? super Value> verifier;
        private final int arity;

        /**
         * Generic executable.
         */
        ProxyExecutableImpl() {
            this(EMPTY, -1);
        }

        /**
         * Executable with concrete parameter types.
         *
         * @param expectedType the expected primitive type
         * @param arity number of required parameters of expectedType
         */
        ProxyExecutableImpl(
                        final Primitive primitive,
                        final int arity) {
            this(createVerifier(primitive), arity);
        }

        ProxyExecutableImpl(
                        final Consumer<? super Value> verifier,
                        final int arity) {
            Objects.requireNonNull(verifier);
            this.verifier = verifier;
            this.arity = arity;
        }

        @Override
        public Object execute(Value... arguments) {
            if (this.arity > arguments.length) {
                throw new AssertionError(String.format("Not enought arguments, required: %d, given: %d", this.arity, arguments.length));
            }
            for (int i = 0; i < arity; i++) {
                verifier.accept(arguments[i]);
            }
            return null;
        }

        private static Consumer<? super Value> createVerifier(final Primitive primitive) {
            if (TypeDescriptor.NUMBER == primitive.type) {
                return new Consumer<Value>() {
                    @Override
                    public void accept(Value value) {
                        if (!value.isNumber()) {
                            throw new AssertionError(String.format("Expected NUMBER, got: %s", value));
                        }
                        if (value.fitsInByte()) {
                            value.asByte();
                        }
                        if (value.fitsInInt()) {
                            value.asInt();
                        }
                        if (value.fitsInLong()) {
                            value.asLong();
                        }
                        if (value.fitsInFloat()) {
                            value.asFloat();
                        }
                        if (value.fitsInDouble()) {
                            value.asDouble();
                        }
                    }
                };
            } else if (TypeDescriptor.BOOLEAN == primitive.type) {
                return new Consumer<Value>() {
                    @Override
                    public void accept(Value value) {
                        value.asBoolean();
                    }
                };
            } else if (TypeDescriptor.STRING == primitive.type) {
                return new Consumer<Value>() {
                    @Override
                    public void accept(Value value) {
                        value.asString();
                    }
                };
            } else {
                return EMPTY;
            }
        }
    }
}
