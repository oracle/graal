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
package com.oracle.truffle.api.test.polyglot;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.TypeLiteral;
import org.junit.Test;

public class TypeLiteralTest {

    @Test
    public void testSimpleMap() {
        TypeLiteral<?> literal = new TypeLiteral<Map<String, Object[]>>() {
        };
        Type type = literal.getType();
        Class<?> rawType = literal.getRawType();
        assertSame(Map.class, rawType);
        assertThat(type, instanceOf(ParameterizedType.class));
        ParameterizedType map = (ParameterizedType) type;
        assertSame(String.class, map.getActualTypeArguments()[0]);
        assertSame(Object[].class, map.getActualTypeArguments()[1]);
    }

    @Test
    public void testSimpleArray() {
        TypeLiteral<?> literal = new TypeLiteral<List<String>[]>() {
        };
        Type type = literal.getType();
        Class<?> rawType = literal.getRawType();
        assertSame(List[].class, rawType);
        assertThat(type, instanceOf(GenericArrayType.class));
        GenericArrayType arr = (GenericArrayType) type;
        assertThat(arr.getGenericComponentType(), instanceOf(ParameterizedType.class));
        ParameterizedType list = (ParameterizedType) arr.getGenericComponentType();
        assertSame(String.class, list.getActualTypeArguments()[0]);
    }

    @Test
    public void testComplexLiteral() {
        TypeLiteral<?> literal = new TypeLiteral<List<Map<Integer, Map<String, Map<? extends Number, Function<Long, ?>>[]>>>>() {
        };
        Type type = literal.getType();
        Class<?> rawType = literal.getRawType();
        assertSame(List.class, rawType);
        assertThat(type, instanceOf(ParameterizedType.class));
        ParameterizedType list = (ParameterizedType) literal.getType();
        assertThat(list.getActualTypeArguments()[0], instanceOf(ParameterizedType.class));

        ParameterizedType map1 = (ParameterizedType) list.getActualTypeArguments()[0];
        assertSame(Map.class, map1.getRawType());
        assertSame(Integer.class, map1.getActualTypeArguments()[0]);
        assertThat(map1.getActualTypeArguments()[1], instanceOf(ParameterizedType.class));

        ParameterizedType map2 = (ParameterizedType) map1.getActualTypeArguments()[1];
        assertSame(Map.class, map2.getRawType());
        assertSame(String.class, map2.getActualTypeArguments()[0]);
        assertThat(map2.getActualTypeArguments()[1], instanceOf(GenericArrayType.class));

        GenericArrayType arr = (GenericArrayType) map2.getActualTypeArguments()[1];
        assertThat(arr.getGenericComponentType(), instanceOf(ParameterizedType.class));

        ParameterizedType map3 = (ParameterizedType) arr.getGenericComponentType();
        assertSame(Map.class, map3.getRawType());
        assertThat(map3.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        WildcardType key3 = (WildcardType) map3.getActualTypeArguments()[0];
        assertSame(Number.class, key3.getUpperBounds()[0]);
        assertEquals(0, key3.getLowerBounds().length);
        assertThat(map3.getActualTypeArguments()[1], instanceOf(ParameterizedType.class));

        ParameterizedType function = (ParameterizedType) map3.getActualTypeArguments()[1];
        assertSame(Function.class, function.getRawType());
        assertSame(Long.class, function.getActualTypeArguments()[0]);
        assertThat(function.getActualTypeArguments()[1], instanceOf(WildcardType.class));
        WildcardType wc = (WildcardType) function.getActualTypeArguments()[1];
        assertSame(Object.class, wc.getUpperBounds()[0]);
        assertEquals(0, wc.getLowerBounds().length);
    }
}
