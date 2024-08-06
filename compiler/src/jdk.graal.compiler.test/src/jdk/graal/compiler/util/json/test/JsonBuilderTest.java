/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.json.test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Tests for {@link JsonBuilder}, checking that printed values round-trip when parsed with
 * {@link JsonParser}.
 */
public class JsonBuilderTest {

    private static final String KEY1 = "key with \\ and \"";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String KEY4 = "key4";
    private static final String VALUE1 = "value with \\";

    private StringWriter stringWriter;
    private JsonWriter jsonWriter;

    /**
     * Asserts that the given map contains exactly the given keys.
     */
    private static void assertKeys(EconomicMap<String, Object> map, String... expectedKeys) {
        Set<String> mapKeys = new HashSet<>(map.size());
        map.getKeys().forEach(mapKeys::add);

        assertEquals(mapKeys, new HashSet<>(Arrays.asList(expectedKeys)));
    }

    @Before
    public void setup() {
        stringWriter = new StringWriter();
        jsonWriter = new JsonWriter(stringWriter);
    }

    @After
    public void teardown() throws IOException {
        jsonWriter.close();
    }

    @SuppressWarnings("unchecked")
    private EconomicMap<String, Object> parseAsJsonObject() throws IOException {
        return (EconomicMap<String, Object>) parseJson();
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseAsJsonArray() throws IOException {
        return (List<Object>) parseJson();
    }

    /**
     * Parses the currently written contents.
     */
    private Object parseJson() throws IOException {
        return new JsonParser(stringWriter.toString()).parse();
    }

    @Test
    public void testBasicObject() throws IOException {
        try (var ob = jsonWriter.objectBuilder()) {
            ob.append(KEY1, VALUE1);
        }

        EconomicMap<String, Object> map = parseAsJsonObject();

        assertKeys(map, KEY1);
        assertEquals(VALUE1, map.get(KEY1));
    }

    @Test
    public void testBasicArray() throws IOException {
        try (var ab = jsonWriter.arrayBuilder()) {
            ab.append(VALUE1);
        }

        List<Object> array = parseAsJsonArray();

        assertEquals(List.of(VALUE1), array);
    }

    @Test
    public void testString() throws IOException {
        jsonWriter.valueBuilder().value(VALUE1);

        String str = (String) parseJson();
        assertEquals(VALUE1, str);
    }

    @Test
    public void testInteger() throws IOException {
        jsonWriter.valueBuilder().value(1234);

        int i = (int) parseJson();
        assertEquals(1234, i);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNestedObject() throws IOException {
        try (var ob = jsonWriter.objectBuilder()) {
            ob.append(KEY1, VALUE1);
            try (var ob2 = ob.append(KEY2).object()) {
                ob2.append(KEY1, VALUE1);
            }
        }

        EconomicMap<String, Object> map = parseAsJsonObject();

        assertKeys(map, KEY1, KEY2);
        assertEquals(VALUE1, map.get(KEY1));

        EconomicMap<String, Object> nestedMap = (EconomicMap<String, Object>) map.get(KEY2);
        assertKeys(nestedMap, KEY1);

        assertEquals(VALUE1, nestedMap.get(KEY1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLargeObject() throws IOException {
        final List<Object> arrayValues = List.of(VALUE1, 1, 2, Long.MAX_VALUE, true, false, 1.1d, -1223434.34d);

        try (var ob = jsonWriter.objectBuilder()) {
            ob.append(KEY1, VALUE1);

            // Add elements individually
            try (var ab = ob.append(KEY2).array()) {
                for (Object arrayValue : arrayValues) {
                    ab.append(arrayValue);
                }
            }

            // Add elements all at once
            ob.append(KEY3).value(arrayValues);

            try (var ob2 = ob.append(KEY4).object()) {
                ob2.append(KEY1, arrayValues);
            }
        }

        EconomicMap<String, Object> map = parseAsJsonObject();

        assertKeys(map, KEY1, KEY2, KEY3, KEY4);
        assertEquals(VALUE1, map.get(KEY1));

        List<Object> nestedArray1 = (List<Object>) map.get(KEY2);
        assertEquals(arrayValues, nestedArray1);

        List<Object> nestedArray2 = (List<Object>) map.get(KEY3);
        assertEquals(arrayValues, nestedArray2);

        EconomicMap<String, Object> nestedMap = (EconomicMap<String, Object>) map.get(KEY4);
        assertKeys(nestedMap, KEY1);

        assertEquals(arrayValues, nestedMap.get(KEY1));
    }

    @Test(expected = IllegalStateException.class)
    public void testMultipleValues() throws IOException {
        JsonBuilder.ValueBuilder vb = jsonWriter.valueBuilder();
        vb.value(1);
        // This second write causes an IllegalStateException
        vb.value(2);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testIncompleteArrayValue() throws IOException {
        try (var ab = jsonWriter.arrayBuilder()) {
            ab.append(1);
            // Because this never prints a value, control is never given back to the ArrayBuilder,
            // which will, on closing, produce a ConcurrentModificationException
            ab.nextEntry();
        }
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testIncompleteObjectValue() throws IOException {
        try (var ob = jsonWriter.objectBuilder()) {
            ob.append(KEY1, VALUE1);
            // Because this never prints a value, control is never given back to the ObjectBuilder,
            // which will, on closing, produce a ConcurrentModificationException
            ob.append(KEY2);
        }
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testWrongNesting() throws IOException {
        try (var ob = jsonWriter.objectBuilder()) {
            ob.append(KEY1, VALUE1);
            try (var ab = ob.append(KEY2).array()) {
                ab.append(1);
                // ob isn't currently responsible for writing and throws an exception.
                ob.append(KEY3, 2);
            }
        }
    }
}
