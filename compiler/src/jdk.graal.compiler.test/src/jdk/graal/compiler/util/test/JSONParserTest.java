/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.json.JSONFormatter;
import jdk.graal.compiler.util.json.JSONParser;
import jdk.graal.compiler.util.json.JSONParserException;

public class JSONParserTest {

    private static final String simpleJSON = "{\"outer\": {\n" +
                    "    \"value\": \"test\",\n" +
                    "    \"inner\": {\n" +
                    "        \"title\": \"GraalVM\",\n" +
                    "        \"array\": [true, false, null],\n" +
                    "        \"version\": 42.5,\n" +
                    "        \"versionLong\": 9007199254740991,\n" +
                    "        \"size\": -1.2e2,\n" +
                    "        \"escapes\": \" \\\\ \\\" \\/ \\n \\b \\f \\r \\t \\u09aF \"\n" +
                    "    }\n" +
                    "}}";

    @Test
    public void testSimpleJSONString() throws IOException {
        JSONParser parser = new JSONParser(simpleJSON);
        testSimpleIntl(parser);
    }

    @Test
    public void testSimpleJSONReader() throws IOException {
        JSONParser parser = new JSONParser(new StringReader(simpleJSON));
        testSimpleIntl(parser);
    }

    @SuppressWarnings("unchecked")
    private static void testSimpleIntl(JSONParser parser) throws IOException {
        Object result = parser.parse();

        Assert.assertTrue(result != null);
        Assert.assertTrue(result instanceof EconomicMap);

        EconomicMap<String, Object> map = (EconomicMap<String, Object>) result;
        EconomicMap<String, Object> outer = getMap(map, "outer");
        EconomicMap<String, Object> inner = getMap(outer, "inner");

        Assert.assertEquals("test", outer.get("value"));

        Assert.assertEquals("GraalVM", inner.get("title"));
        List<Object> array = getList(inner, "array");
        Assert.assertEquals(42.5, inner.get("version"));
        Assert.assertEquals(9007199254740991L, inner.get("versionLong"));
        Assert.assertEquals(-120.0, inner.get("size"));
        Assert.assertEquals(" \\ \" / \n \b \f \r \t \u09AF ", inner.get("escapes"));

        Assert.assertEquals(Boolean.TRUE, array.get(0));
        Assert.assertEquals(Boolean.FALSE, array.get(1));
        Assert.assertEquals(null, array.get(2));
    }

    @Test
    public void testErrors() throws IOException {
        JSONParser parser = new JSONParser(new StringReader(simpleJSON));
        testSimpleIntl(parser);

        testErrorIntl("{ \"a\": \"\\uABCX\" }", "Invalid hex digit");
        testErrorIntl("{ \"a\": trux }", "json literal");

        testErrorIntl("{ \"a\": .0123}", "Invalid JSON number format");
        testErrorIntl("{ \"a\": 1.2e-x}", "Invalid JSON number format");
        testErrorIntl("{ \"a\": 1.x}", "Invalid JSON number format");
        testErrorIntl("{ \"a\": -x}", "Invalid JSON number format");

        testErrorIntl("{ \"a\": \"" + (char) 0 + "\"}", "String contains control character");

        testErrorIntl("{ \"a\": [", ", or ]");
        testErrorIntl("{ \"a\": [,] }", "Trailing comma is not allowed in JSON");
        testErrorIntl("{ \"a\": [1,] }", "Trailing comma is not allowed in JSON");
        testErrorIntl("{ \"a\": [1 2] }", ", or ]");

        testErrorIntl("{ \"a\" .0123}", "Expected :");

        testErrorIntl("{ \"a\": \"\\v\"}", "Invalid escape character");

        testErrorIntl("{ \"a\": \"string", "Missing close quote");

        testErrorIntl("{ \"a\": true", ", or }");
        testErrorIntl("{ \"a\": true \"b\" }", ", or }");

        testErrorIntl("", "json literal");
        testErrorIntl("keyword", "json literal");

        testErrorIntl("{} something else", "eof");

        testErrorIntl("{,}", "Trailing comma is not allowed in JSON");
        testErrorIntl("{ \"a\": true, }", "Trailing comma is not allowed in JSON");
        testErrorIntl("{ \"a\": true false}", ", or }");

        // offending token in new line; tests findBOLN
        testErrorIntl("{ \"a\": true \n\nfalse}", ", or }");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFormatterTrivial() throws IOException {
        String json = "{\"a\": [\"\\u0019\", true, 42.5], \"b\": null}";
        JSONParser parser = new JSONParser(json);
        String result = JSONFormatter.formatJSON((EconomicMap<String, Object>) parser.parse());
        Assert.assertEquals(json, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFormatterSimpleJSON() throws IOException {
        JSONParser parser = new JSONParser(simpleJSON);
        String result = JSONFormatter.formatJSON((EconomicMap<String, Object>) parser.parse(), true);

        // don't fully check the content, but have some dummy tests
        Assert.assertTrue(result.contains("9007199254740991"));
        Assert.assertTrue(result.contains("\\n"));
        Assert.assertTrue(result.contains("\\b"));
        Assert.assertTrue(result.contains("\\f"));
        Assert.assertTrue(result.contains("\\n"));
        Assert.assertTrue(result.contains("\\t"));
        // Assert.assertTrue(result.contains("\\u09af")); //unicode used, no quoting
    }

    @Test
    public void parseAllowedKeysSimple() throws IOException {
        String source = " { \"foo\": 1, \"notFoo\": 2, \"bar\": 3 } ";
        JSONParser parser = new JSONParser(source);
        EconomicMap<String, Object> map = parser.parseAllowedKeys(List.of("foo", "bar", "baz"));
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(1, map.get("foo"));
        Assert.assertEquals(3, map.get("bar"));
    }

    @Test
    public void parseAllowedKeysEarlyExit() throws IOException {
        String source = "{\"foo\": 1, invalid syntax ";
        JSONParser parser = new JSONParser(source);
        EconomicMap<String, Object> map = parser.parseAllowedKeys(List.of("foo"));
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(1, map.get("foo"));
    }

    @Test
    public void parseAllowedKeysEmpty() throws IOException {
        Assert.assertTrue(new JSONParser("invalid syntax").parseAllowedKeys(List.of()).isEmpty());
    }

    @Test
    public void parseAllowedKeysErrors() throws IOException {
        for (String source : List.of("", "[]", "{,}", "{\"a\": 1,}", "{\"a\": 1 \"")) {
            try {
                new JSONParser(source).parseAllowedKeys(List.of("foo"));
                Assert.fail("Should have failed to parse: " + source);
            } catch (JSONParserException ignored) {
            }
        }
    }

    private static void testErrorIntl(String json, String expectedMessage) throws IOException {
        JSONParser parser = new JSONParser(json);
        try {
            parser.parse();
            Assert.fail("passed when a failure was expected. Expected error: " + expectedMessage);
        } catch (JSONParserException ex) {
            Assert.assertTrue(ex.getMessage().contains(expectedMessage));
        }
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, Object> getMap(EconomicMap<String, Object> map, String key) {
        return (EconomicMap<String, Object>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(EconomicMap<String, Object> map, String key) {
        return (List<Object>) map.get(key);
    }
}
