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
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyPrimitive;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.graalvm.polyglot.tck.LanguageProvider;

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
        // Java primitives
        result.add(Snippet.newBuilder("boolean", export(context, new ValueSupplier<>(false)), TypeDescriptor.BOOLEAN).build());
        result.add(Snippet.newBuilder("byte", export(context, new ValueSupplier<>(Byte.MIN_VALUE)), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("short", export(context, new ValueSupplier<>(Short.MIN_VALUE)), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("char", export(context, new ValueSupplier<>(' ')), TypeDescriptor.STRING).build());
        // Integer.MIN_VALUE is NA for fast-r
        result.add(Snippet.newBuilder("int", export(context, new ValueSupplier<>(Integer.MAX_VALUE)), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("long", export(context, new ValueSupplier<>(Long.MIN_VALUE)), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("float", export(context, new ValueSupplier<>(Float.MAX_VALUE)), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("double", export(context, new ValueSupplier<>(Double.MAX_VALUE)), TypeDescriptor.NUMBER).build());
        // String
        result.add(Snippet.newBuilder("java.lang.String", export(context, new ValueSupplier<>("TEST")), TypeDescriptor.STRING).build());
        // Arrays
        result.add(Snippet.newBuilder("Array<int>", export(context, new ValueSupplier<>(new int[]{1, 2})),
                        TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.array(TypeDescriptor.NUMBER))).build());
        result.add(Snippet.newBuilder("Array<java.lang.Object>", export(context, new ValueSupplier<>(new Object[]{1, "TEST"})),
                        TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.ARRAY)).build());
        // Proxies
        result.add(Snippet.newBuilder("Proxy<boolean>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(false))), TypeDescriptor.BOOLEAN).build());
        result.add(Snippet.newBuilder("Proxy<byte>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Byte.MIN_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<short>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Short.MIN_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<char>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(' '))), TypeDescriptor.STRING).build());
        result.add(Snippet.newBuilder("Proxy<int>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Integer.MAX_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<long>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Long.MIN_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<float>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Float.MAX_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<double>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl(Double.MAX_VALUE))), TypeDescriptor.NUMBER).build());
        result.add(Snippet.newBuilder("Proxy<java.lang.String>", export(context, new ValueSupplier<>(new ProxyPrimitiveImpl("TEST"))), TypeDescriptor.STRING).build());
        result.add(Snippet.newBuilder("Proxy<Array<int>>", export(context, new ValueSupplier<>(ProxyArray.fromArray(1, 2))),
                        TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.array(TypeDescriptor.NUMBER))).build());
        result.add(Snippet.newBuilder("Proxy<Array<java.lang.Object>>", export(context, new ValueSupplier<>(ProxyArray.fromArray(1, "TEST"))),
                        TypeDescriptor.union(TypeDescriptor.OBJECT, TypeDescriptor.ARRAY)).build());
        final Map<String, Object> props = new HashMap<>();
        props.put("name", "test");
        result.add(Snippet.newBuilder("Proxy<java.lang.Object>", export(context, new ValueSupplier<>(ProxyObject.fromMap(props))), TypeDescriptor.OBJECT).build());
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Value createIdentityFunction(final Context context) {
        return null;
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

    private static Value export(final Context context, final Supplier<Object> s) {
        context.exportSymbol("tmp", s);
        return context.importSymbol("tmp");
    }

    private static final class ProxyPrimitiveImpl implements ProxyPrimitive {
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
}
