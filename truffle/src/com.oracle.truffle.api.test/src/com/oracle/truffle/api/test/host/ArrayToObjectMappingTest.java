/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Test;

public class ArrayToObjectMappingTest {

    @Test
    public void testArrayAsList() {
        try (Context c = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            Value fn = c.asValue((Function<Object, Object>) f -> {
                if (f instanceof List) {
                    return "List";
                }
                return f.getClass().getName();
            });
            Value result = fn.execute(new ArrayAndMembers("a", "b", "c", "d"));
            Assert.assertEquals("List", result.asString());
        }
    }

    @Test
    public void testArrayAsMapLong() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL).targetTypeMapping(Value.class, Object.class,
                        v -> v.hasMembers() && v.hasArrayElements(),
                        v -> v.as(new TypeLiteral<Map<Long, Object>>() {
                        })).build();
        try (Context c = Context.newBuilder("js").allowHostAccess(hostAccess).build()) {
            Value fn = c.asValue((Function<Object, Object>) f -> {
                if (f instanceof Map) {
                    Assert.assertEquals(4, ((Map<?, ?>) f).size());
                    return "Map";
                }
                return f.getClass().getName();
            });
            Value result = fn.execute(new ArrayAndMembers("a", "b", "c", "d"));
            Assert.assertEquals("Map", result.asString());
        }
    }

    @Test
    public void testArrayAsMapGeneric() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL).targetTypeMapping(Value.class, Object.class,
                        v -> v.hasMembers() && v.hasArrayElements(),
                        v -> v.as(Map.class)).build();
        try (Context c = Context.newBuilder("js").allowHostAccess(hostAccess).build()) {
            Value fn = c.asValue((Function<Object, Object>) f -> {
                if (f instanceof Map) {
                    Assert.assertEquals(0, ((Map<?, ?>) f).size());
                    return "Map";
                }
                return f.getClass().getName();
            });
            Value result = fn.execute(new ArrayAndMembers("a", "b", "c", "d"));
            Assert.assertEquals("Map", result.asString());
        }
    }

    static final class ArrayAndMembers implements ProxyArray, ProxyObject {

        private final Object[] arr;

        ArrayAndMembers(Object... arr) {
            this.arr = arr;
        }

        @Override
        public Object get(long index) {
            return arr[(int) index];
        }

        @Override
        public void set(long index, Value value) {
            arr[(int) index] = value;
        }

        @Override
        public long getSize() {
            return arr.length;
        }

        @Override
        public Object getMemberKeys() {
            return ProxyArray.fromArray();
        }

        @Override
        public boolean hasMember(String key) {
            return false;
        }

        @Override
        public Object getMember(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException();
        }
    }
}
