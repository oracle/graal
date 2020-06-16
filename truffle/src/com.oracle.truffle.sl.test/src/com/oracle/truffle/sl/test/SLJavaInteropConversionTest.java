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
package com.oracle.truffle.sl.test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.sl.SLLanguage;
import org.graalvm.polyglot.HostAccess;

public class SLJavaInteropConversionTest {
    public static class Validator {
        @HostAccess.Export
        @SuppressWarnings("unchecked")
        public int validateObject(Object value1, Value value2) {
            assertThat(value1, instanceOf(Map.class));
            assertTrue(!((Map<?, ?>) value1).isEmpty());
            assertThat(((Map<String, ?>) value1).keySet(), hasItems("a", "b"));
            assertThat(value2, instanceOf(Value.class));
            assertTrue(value2.hasMembers());
            assertThat(value2.getMemberKeys(), hasItems("a", "b"));
            return 42;
        }

        @HostAccess.Export
        public int validateMap(Map<String, Object> map1, Map<String, Value> map2) {
            assertEquals(2, map1.size());
            assertThat(map1.keySet(), hasItems("a", "b"));
            for (Object value : map1.values()) {
                assertThat(value, instanceOf(Map.class));
            }

            assertEquals(2, map2.size());
            assertThat(map2.keySet(), hasItems("a", "b"));
            for (Object value : map2.values()) {
                assertThat(value, instanceOf(Value.class));
            }
            return 42;
        }

        @HostAccess.Export
        public int validateList(List<Object> list1, List<Value> list2) {
            assertEquals(2, list1.size());
            for (Object value : list1) {
                assertThat(value, instanceOf(Map.class));
            }

            assertEquals(2, list2.size());
            for (Object value : list2) {
                assertThat(value, instanceOf(Value.class));
            }
            return 42;
        }
    }

    @Test
    public void testGR7318Object() throws Exception {
        String sourceText = "function test(validator) {\n" +
                        "  obj = new();\n" +
                        "  obj.a = new();\n" +
                        "  obj.b = new();\n" +
                        "  return validator.validateObject(obj, obj);\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            Value res = test.execute(new Validator());
            assertTrue(res.isNumber() && res.asInt() == 42);
        }
    }

    @Test
    public void testGR7318Map() throws Exception {
        String sourceText = "function test(validator) {\n" +
                        "  obj = new();\n" +
                        "  obj.a = new();\n" +
                        "  obj.b = new();\n" +
                        "  return validator.validateMap(obj, obj);\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            Value res = test.execute(new Validator());
            assertTrue(res.isNumber() && res.asInt() == 42);
        }
    }

    @Test
    public void testGR7318List() throws Exception {
        String sourceText = "function test(validator, array) {\n" +
                        "  array[0] = new();\n" +
                        "  array[1] = new();\n" +
                        "  return validator.validateList(array, array);\n" +
                        "}";
        try (Context context = Context.newBuilder(SLLanguage.ID).allowHostAccess(HostAccess.ALL).build()) {
            context.eval(Source.newBuilder(SLLanguage.ID, sourceText, "Test").build());
            Value test = context.getBindings(SLLanguage.ID).getMember("test");
            Value res = test.execute(new Validator(), new Object[2]);
            assertTrue(res.isNumber() && res.asInt() == 42);
        }
    }
}
