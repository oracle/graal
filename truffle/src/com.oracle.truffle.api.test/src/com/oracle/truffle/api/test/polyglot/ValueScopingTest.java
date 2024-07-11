/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.tck.tests.ValueAssert;

public class ValueScopingTest {

    public static class HostInteropTestClass {

    }

    public static class HostInteropTest {

        @HostAccess.Export
        public HostInteropTestClass valueCast(HostInteropTestClass m) {
            return m;
        }

        @HostAccess.Export
        public Proxy proxyCast(Value v) {
            assertTrue(v.isProxyObject());
            return v.asProxyObject();
        }
    }

    @Test
    public void testHostInterop() {
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.EXPLICIT).allowPublicAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            HostInteropTest o = new HostInteropTest();
            Value test = context.asValue(o);

            HostInteropTestClass c = new HostInteropTestClass();

            assertSame(c, test.invokeMember("valueCast", c).asHostObject());
            assertSame(c, test.invokeMember("valueCast", c).asHostObject());

            Proxy p = ProxyObject.fromMap(new HashMap<>());
            assertSame(p, test.invokeMember("proxyCast", p).asProxyObject());
            assertSame(p, test.invokeMember("proxyCast", p).asProxyObject());
        }
    }

    public static class StoreAndPinTest {
        private Value value;
        private Object object;
        private Map<String, Object> map;

        StoreAndPinTest() {
        }

        @HostAccess.Export
        public void storeValueAndPin(Value v) {
            value = v;
            v.pin();
        }

        @HostAccess.Export
        public void storeValue(Value v) {
            value = v;
        }

        @HostAccess.Export
        public void storeValueMemberAsObject(Value v, String member) {
            object = v.getMember(member).as(Object.class);
        }

        @HostAccess.Export
        public void storeValueMember(Value v, String member) {
            value = v.getMember(member);
        }

        @HostAccess.Export
        public void storeObject(Object o) {
            object = o;
        }

        @HostAccess.Export
        public void storeValueAndThrow(Value v) throws Exception {
            value = v;
            throw new Exception("method failed");
        }

        @HostAccess.Export
        public void storeMapAndPin(Map<String, Object> v) {
            Value asValue = Value.asValue(v);
            this.value = asValue;
            this.map = v;
            this.value.pin();
        }

        @HostAccess.Export
        public void storeMap(Map<String, Object> v) {
            this.map = v;
        }

        @HostAccess.Export
        @HostAccess.DisableMethodScoping
        public void storeDisabled(Value v) {
            value = v;
            ValueAssert.assertValue(v);
        }

        @HostAccess.Export
        public void storeMapElement(Map<String, Object> m) {
            this.value = Value.asValue(m.get("array"));
            ValueAssert.assertValue(this.value);
        }

        @HostAccess.Export
        public void storeValueElement(Value m) {
            this.value = m.getMember("array");
            ValueAssert.assertValue(this.value);
        }

        @HostAccess.Export
        public void storeInGuest(Value m) {
            Value guestObject = Context.getCurrent().asValue(new GuestObject());
            this.value = guestObject.execute(m);
            ValueAssert.assertValue(this.value);
        }

    }

    static final Consumer<IllegalStateException> SCOPE_RELEASED = (e) -> {
        if (!e.getMessage().startsWith("This scoped object has already been released.")) {
            e.printStackTrace();
        }
        assertTrue(e.getMessage(), e.getMessage().startsWith("This scoped object has already been released."));
    };

    @Test
    public void testStoreAndPin() {
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).allowPublicAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            StoreAndPinTest o = new StoreAndPinTest();
            Map<String, Object> map = new HashMap<>();
            map.put("cafe", "42");
            map.put("cafeboolean", true);
            map.put("cafebyte", (byte) 42);
            map.put("cafeshort", (short) 42);
            map.put("cafeint", 42);
            map.put("cafelong", 42L);
            map.put("cafefloat", 42.0f);
            map.put("cafedouble", 42.0d);
            map.put("cafechar", (char) 42);
            map.put("array", ProxyArray.fromArray());
            ProxyObject proxy = ProxyObject.fromMap(map);
            Value test = context.asValue(o);

            // no-op
            test.pin();

            // scoped primitive values preserve type (second level scoped objects (members of first
            // level scoped object))
            test.invokeMember("storeValueMemberAsObject", proxy, "cafeboolean");
            assertEquals(Boolean.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafebyte");
            assertEquals(Byte.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafeshort");
            assertEquals(Short.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafeint");
            assertEquals(Integer.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafelong");
            assertEquals(Long.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafefloat");
            assertEquals(Float.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafedouble");
            assertEquals(Double.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafechar");
            assertEquals(Character.class, o.object.getClass());
            test.invokeMember("storeValueMemberAsObject", proxy, "cafe");
            assertEquals(String.class, o.object.getClass());

            // primitive value accessed out of scope
            test.invokeMember("storeValueMember", proxy, "cafe");
            assertFails(() -> o.value.isString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.asString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.pin(), IllegalStateException.class, SCOPE_RELEASED);

            // value accessed out of scope
            test.invokeMember("storeValue", proxy);
            assertFails(() -> o.value.isString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.asString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.pin(), IllegalStateException.class, SCOPE_RELEASED);

            // primitive value accessed out of scope
            test.invokeMember("storeValue", "42");
            assertFails(() -> o.value.isString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.asString(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.pin(), IllegalStateException.class, SCOPE_RELEASED);

            // value passed as Object parameter accessed out of scope
            test.invokeMember("storeObject", proxy);
            assertFails(() -> ((Map<?, ?>) o.object).get("cafe"), IllegalStateException.class, SCOPE_RELEASED);

            // scoped primitive values preserve type (first level scoped)
            test.invokeMember("storeObject", true);
            assertEquals(Boolean.class, o.object.getClass());
            test.invokeMember("storeObject", (byte) 42);
            assertEquals(Byte.class, o.object.getClass());
            test.invokeMember("storeObject", (short) 42);
            assertEquals(Short.class, o.object.getClass());
            test.invokeMember("storeObject", 42);
            assertEquals(Integer.class, o.object.getClass());
            test.invokeMember("storeObject", 42L);
            assertEquals(Long.class, o.object.getClass());
            test.invokeMember("storeObject", 42.0f);
            assertEquals(Float.class, o.object.getClass());
            test.invokeMember("storeObject", 42.0d);
            assertEquals(Double.class, o.object.getClass());
            test.invokeMember("storeObject", (char) 42);
            assertEquals(Character.class, o.object.getClass());
            test.invokeMember("storeObject", "42");
            assertEquals(String.class, o.object.getClass());

            test.invokeMember("storeMap", proxy);
            assertFails(() -> o.map.get(""), IllegalStateException.class, SCOPE_RELEASED);

            // recursive references
            assertFails(() -> test.invokeMember("storeValue", o.map), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> test.invokeMember("storeValue", o.value), IllegalStateException.class, SCOPE_RELEASED);

            test.invokeMember("storeValueAndPin", proxy);
            assertTrue(o.value.isProxyObject());
            ValueAssert.assertValue(o.value);

            // host object
            test.invokeMember("storeValueAndPin", test);
            assertTrue(o.value.isHostObject());

            // host object
            test.invokeMember("storeValueAndPin", ProxyObject.fromMap(new HashMap<>()));
            assertTrue(o.value.isProxyObject());

            test.invokeMember("storeMapAndPin", proxy);
            // maps can be pinned too
            assertEquals("42", o.map.get("cafe"));
            ValueAssert.assertValue(o.value);

            // if scoping is disabled
            test.invokeMember("storeDisabled", proxy);
            // can pin afterwards
            o.value.pin();
            // and access anything
            ValueAssert.assertValue(o.value);

            // if values are accessed the scope should propagate
            test.invokeMember("storeValueElement", proxy);
            assertFails(() -> o.value.hasArrayElements(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.pin(), IllegalStateException.class, SCOPE_RELEASED);

            // if polyglot wrappers are accessed the scope should propagate
            test.invokeMember("storeMapElement", proxy);
            assertFails(() -> o.value.hasArrayElements(), IllegalStateException.class, SCOPE_RELEASED);
            assertFails(() -> o.value.pin(), IllegalStateException.class, SCOPE_RELEASED);

            // if a value is stored in guest we remove the scope
            test.invokeMember("storeInGuest", proxy);
            ValueAssert.assertValue(o.value);
            // does not fail
            o.value.pin();

            // host method execution fails
            try {
                test.invokeMember("storeValueAndThrow", proxy);
                fail("method invocation should throw an exception");
            } catch (Exception ex) {
                assertEquals("method failed", ex.getMessage());
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class GuestObject implements TruffleObject {

        @ExportMessage
        final Object execute(Object[] args, @CachedLibrary(limit = "3") InteropLibrary lib) {
            for (Object arg : args) {
                // make sure we can use it
                lib.isExecutable(arg);
                lib.hasArrayElements(arg);
                lib.hasMembers(arg);
            }
            return args[0];
        }

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

    }

    public static class TestMultiThread {
        final ExecutorService executorService = Executors.newFixedThreadPool(32);
        final List<Future<?>> futures = new ArrayList<>();
        final AtomicInteger fails = new AtomicInteger();
        final AtomicInteger successes = new AtomicInteger();

        @HostAccess.Export
        public void storeMultiThreaded(Value v, int threads) {
            for (int i = 0; i < threads; i++) {
                futures.add(executorService.submit(() -> {
                    boolean pinned = false;
                    try {
                        v.pin();
                        pinned = true;
                        successes.incrementAndGet();
                        // this may succeed
                    } catch (IllegalStateException e) {
                        // or fail if the scope happened to be closed before
                        SCOPE_RELEASED.accept(e);
                        fails.incrementAndGet();
                    }
                    if (pinned) {
                        // must succeed
                        assertEquals("42", v.getMember("cafe").asString());
                    } else {
                        try {
                            v.getMember("cafe");
                            Assert.fail("pin was not successful getMember should not work");
                        } catch (IllegalStateException e) {
                            // or fail if the scope happened to be closed before
                            SCOPE_RELEASED.accept(e);
                        }
                    }
                }));
            }
        }

    }

    @Test
    public void testMultiThreading() throws InterruptedException, ExecutionException {
        TestMultiThread o = new TestMultiThread();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).allowPublicAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("cafe", "42");
            ProxyObject proxy = ProxyObject.fromMap(map);
            Value test = context.asValue(o);

            // repeat a few times to cause races
            for (int i = 0; i < 100; i++) {
                int[] testValues = new int[]{1, 4, 8, 16, 32, 64, 128, 256};
                for (int threads : testValues) {
                    o.futures.clear();
                    o.fails.set(0);
                    o.successes.set(0);
                    test.invokeMember("storeMultiThreaded", proxy, threads);
                    assertEquals(threads, o.futures.size());
                    for (Future<?> f : o.futures) {
                        f.get();
                    }

                    assertEquals(threads, o.fails.get() + o.successes.get());

                }
            }
            o.executorService.shutdownNow();
            o.executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

}
