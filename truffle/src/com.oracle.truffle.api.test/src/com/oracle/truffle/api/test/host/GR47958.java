/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.host.AsCollectionsTest.ListBasedTO;
import com.oracle.truffle.api.test.host.AsCollectionsTest.MapBasedTO;

/**
 * Reproducer for https://github.com/oracle/graal/issues/7082.
 */
public class GR47958 {

    @FunctionalInterface
    public interface GenericListConsumer {
        @HostAccess.Export
        String call(List<List<CharSequence>> arg);
    }

    @FunctionalInterface
    public interface GenericMapConsumer {
        @HostAccess.Export
        String call(Map<String, List<CharSequence>> arg);
    }

    @SuppressWarnings("static-method")
    private String toString(List<List<CharSequence>> arg) {
        return String.valueOf(arg.stream().flatMap(List<CharSequence>::stream).collect(Collectors.joining(", ")));
    }

    @SuppressWarnings("static-method")
    private String toString(Map<String, List<CharSequence>> arg) {
        return String.valueOf(arg.entrySet().stream().map(e -> e.getKey() + ", " + String.join(", ", e.getValue())).collect(Collectors.joining(", ")));
    }

    @Test
    public void testPolyglotListReproducer() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            for (var invoker : List.<BiFunction<Value, Object, Value>> of(
                            (target, array) -> target.invokeMember("call", array),
                            (target, array) -> target.execute(array))) {
                Value target;
                Value result;
                Object array;

                target = context.asValue((GenericListConsumer) this::toString);
                array = new ListBasedTO(List.of(new ListBasedTO(List.of("hello")), new ListBasedTO(List.of("world")), new ListBasedTO(List.of())));
                result = invoker.apply(target, array);
                Assert.assertEquals("hello, world", result.toString());

                target = context.asValue((GenericListConsumer) this::toString);
                array = new ListBasedTO(List.of(new ListBasedTO(List.of("hello"))));
                result = invoker.apply(target, array);
                Assert.assertEquals("hello", result.toString());

                target = context.asValue((GenericListConsumer) this::toString);
                array = ProxyArray.fromArray(ProxyArray.fromArray("hello"), ProxyArray.fromArray("world"), ProxyArray.fromArray());
                result = invoker.apply(target, array);
                Assert.assertEquals("hello, world", result.toString());

                target = context.asValue((GenericListConsumer) this::toString);
                array = ProxyArray.fromArray(ProxyArray.fromArray("hello"));
                result = invoker.apply(target, array);
                Assert.assertEquals("hello", result.toString());
            }
        }
    }

    @Test
    public void testPolyglotMapReproducer() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            for (var invoker : List.<BiFunction<Value, Object, Value>> of(
                            (target, array) -> target.invokeMember("call", array),
                            (target, array) -> target.execute(array))) {
                Value target;
                Value result;
                Object map;

                target = context.asValue((GenericMapConsumer) this::toString);
                map = new MapBasedTO(Map.of("hello", new ListBasedTO(List.of("world"))));
                result = invoker.apply(target, map);
                Assert.assertEquals("hello, world", result.toString());

                target = context.asValue((GenericMapConsumer) this::toString);
                map = new MapBasedTO(Map.of("hi", new ListBasedTO(List.of("there"))));
                result = invoker.apply(target, map);
                Assert.assertEquals("hi, there", result.toString());

                target = context.asValue((GenericMapConsumer) this::toString);
                map = ProxyHashMap.from(Map.of("hello", ProxyArray.fromList(List.of("world"))));
                result = invoker.apply(target, map);
                Assert.assertEquals("hello, world", result.toString());

                target = context.asValue((GenericMapConsumer) this::toString);
                map = ProxyHashMap.from(Map.of("hi", ProxyArray.fromList(List.of("there"))));
                result = invoker.apply(target, map);
                Assert.assertEquals("hi, there", result.toString());
            }
        }
    }
}
