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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.json.JsonWriter;

public class JsonWriterTest {

    @Test
    public void testNull() throws IOException {
        Assert.assertEquals("null", toJson(null));
        Assert.assertEquals("\"null\"", toJson("null"));
    }

    @Test
    public void testString() throws IOException {
        Assert.assertEquals("\"\"", toJson(""));
        Assert.assertEquals("\"string\"", toJson("string"));
        Assert.assertEquals("\"42\"", toJson("42"));
    }

    @Test
    public void testQuoting() throws IOException {
        Assert.assertEquals("\"\\\"\"", toJson("\""));
        Assert.assertEquals("\"\\t\\n\"", toJson("\t\n"));
        Assert.assertEquals("\"'\"", toJson("'"));
    }

    @Test
    public void testNumber() throws IOException {
        Assert.assertEquals("0", toJson(0));
        Assert.assertEquals("42", toJson(42));
        Assert.assertEquals("42.5", toJson(42.5f));
        Assert.assertEquals("42.5", toJson(42.5));
    }

    @Test
    public void testBoolean() throws IOException {
        Assert.assertEquals("true", toJson(true));
        Assert.assertEquals("false", toJson(false));
        Assert.assertEquals("\"false\"", toJson("false"));
    }

    @Test
    public void testList0() throws IOException {
        var input = List.of();
        Assert.assertEquals("[]", toJson(input));
    }

    @Test
    public void testList1() throws IOException {
        var input = List.of(1, 2, 3);
        Assert.assertEquals("[1,2,3]", toJson(input));
    }

    @Test
    public void testList2() throws IOException {
        var input = List.of(1, List.of(2, 3));
        Assert.assertEquals("[1,[2,3]]", toJson(input));
    }

    @Test
    public void testMap0() throws IOException {
        var input = (Map<Object, Object>) new EconomicHashMap<>();
        Assert.assertEquals("{}", toJson(input));
    }

    @Test
    public void testMap1() throws IOException {
        var input = CollectionsUtil.mapOf(
                        "k1", 1,
                        "k2", 2);
        var output = toJson(input);
        // Could be in either order
        Assert.assertTrue("{\"k1\":1,\"k2\":2}".equals(output) || "{\"k2\":2,\"k1\":1}".equals(output));
    }

    @Test
    public void testMap2() throws IOException {
        var input = CollectionsUtil.mapOf(
                        "k1", List.of(1, 2, 3),
                        "k2", List.of());
        var output = toJson(input);
        // Could be in either order
        Assert.assertTrue("{\"k1\":[1,2,3],\"k2\":[]}".equals(output) || "{\"k2\":[],\"k1\":[1,2,3]}".equals(output));
    }

    @Test
    public void testMap3() throws IOException {
        var input = CollectionsUtil.mapOf(
                        "k1", CollectionsUtil.mapOf("k1", List.of(1, 2, 3)),
                        "k2", "");
        var output = toJson(input);
        // Could be in either order
        Assert.assertTrue("{\"k1\":{\"k1\":[1,2,3]},\"k2\":\"\"}".equals(output) || "{\"k2\":\"\",\"k1\":{\"k1\":[1,2,3]}}".equals(output));
    }

    protected JsonWriter createWriter(Writer w) {
        return new JsonWriter(w);
    }

    private <T> String toJson(T input) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = createWriter(sw)) {
            writer.print(input);
        }
        return sw.toString();
    }
}
