/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
