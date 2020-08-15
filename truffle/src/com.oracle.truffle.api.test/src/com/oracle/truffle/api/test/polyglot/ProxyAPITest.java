/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.tck.tests.ValueAssert.assertUnsupported;
import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.PROXY_OBJECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.tck.tests.ValueAssert.Trait;

/**
 * Testing the behavior of proxies API and proxy interfaces.
 */
public class ProxyAPITest {

    private Context context;

    @Before
    public void setUp() {
        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testProxy() {
        Proxy proxy = new Proxy() {
        };
        Value value = context.asValue(proxy);
        assertTrue(value.isProxyObject());
        assertSame(proxy, value.asProxyObject());
        assertUnsupported(value, PROXY_OBJECT);
    }

    static class ProxyArrayTest implements ProxyArray {

        Function<Long, Object> get;
        BiFunction<Long, Value, Void> set;
        Supplier<Long> getSize;

        int getCounter;
        int setCounter;
        int getSizeCounter;

        public Object get(long index) {
            getCounter++;
            return get.apply(index);
        }

        public void set(long index, Value value) {
            setCounter++;
            set.apply(index, value);
        }

        public long getSize() {
            getSizeCounter++;
            return getSize.get();
        }

    }

    @Test
    public void testProxyArray() {
        ProxyArrayTest proxy = new ProxyArrayTest();

        Value value = context.asValue(proxy);

        assertTrue(value.isProxyObject());
        assertSame(proxy, value.asProxyObject());

        assertTrue(value.hasArrayElements());

        assertEquals(0, proxy.getCounter);
        assertEquals(0, proxy.setCounter);
        assertEquals(0, proxy.getSizeCounter);

        proxy.get = (index) -> 42;
        proxy.getSize = () -> 43L;
        assertEquals(42, value.getArrayElement(42).asInt());
        assertEquals(1, proxy.getCounter);
        assertEquals(0, proxy.setCounter);
        proxy.getCounter = 0;
        proxy.get = null;

        Object setObject = new Object();
        proxy.set = (index, v) -> {
            assertSame(setObject, v.asHostObject());
            return null;
        };
        value.setArrayElement(42, setObject);
        assertEquals(0, proxy.getCounter);
        assertEquals(1, proxy.setCounter);
        proxy.setCounter = 0;
        proxy.set = null;

        proxy.getSize = () -> 42L;
        assertEquals(42L, value.getArraySize());
        assertEquals(0, proxy.getCounter);
        assertEquals(0, proxy.setCounter);
        proxy.getSize = null;
        proxy.getCounter = 0;

        RuntimeException ex = new RuntimeException();

        proxy.getSize = () -> {
            return 0L;
        };
        proxy.get = (index) -> {
            throw ex;
        };

        // test errors
        assertFails(() -> value.getArrayElement(42), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertSame(ex, e.asHostException());
            assertFalse(e.isInternalError());
        });
        proxy.get = null;

        proxy.set = (index, v) -> {
            throw ex;
        };
        assertFails(() -> value.setArrayElement(42, null), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertSame(ex, e.asHostException());
            assertFalse(e.isInternalError());
        });
        proxy.set = null;

        proxy.getSize = () -> {
            throw ex;
        };
        assertFails(() -> value.getArraySize(), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertSame(ex, e.asHostException());
            assertFalse(e.isInternalError());
        });
        proxy.getSize = null;
    }

    static class ProxyExecutableTest implements ProxyExecutable {
        Function<Value[], Object> execute;
        int executeCounter;

        public Object execute(Value... arguments) {
            executeCounter++;
            return execute.apply(arguments);
        }

    }

    @Test
    public void testProxyExecutable() {
        ProxyExecutableTest proxy = new ProxyExecutableTest();

        Value value = context.asValue(proxy);

        assertTrue(value.canExecute());
        assertSame(proxy, value.asProxyObject());

        assertEquals(0, proxy.executeCounter);

        proxy.execute = (args) -> {
            assertEquals(2, args.length);
            assertEquals("a", args[0].asString());
            assertEquals('a', args[0].as(Object.class));
            assertValue(args[0], Trait.STRING);

            assertTrue(args[1].isNumber());
            assertEquals((byte) 42, args[1].asByte());
            assertEquals((short) 42, args[1].asShort());
            assertEquals(42, args[1].asInt());
            assertEquals(42L, args[1].asLong());
            assertEquals(42, args[1].as(Object.class));
            assertValue(args[1], Trait.NUMBER);
            return 42;
        };

        assertEquals(42, value.execute('a', 42).asInt());
        assertEquals(1, proxy.executeCounter);

        final RuntimeException ex = new RuntimeException();
        proxy.execute = (args) -> {
            throw ex;
        };

        try {
            value.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertSame(ex, e.asHostException());
            assertEquals(2, proxy.executeCounter);
        }
        assertValue(value, Trait.PROXY_OBJECT, Trait.EXECUTABLE);
    }

    static class ProxyInstantiableTest implements ProxyInstantiable {

        Function<Value[], Object> newInstance;
        int newInstanceCounter;

        public Object newInstance(Value... arguments) {
            newInstanceCounter++;
            return newInstance.apply(arguments);
        }

    }

    @Test
    public void testProxyInstantiable() {
        ProxyInstantiableTest proxy = new ProxyInstantiableTest();

        Value value = context.asValue(proxy);

        assertTrue(value.canInstantiate());
        assertSame(proxy, value.asProxyObject());

        assertEquals(0, proxy.newInstanceCounter);

        proxy.newInstance = (args) -> {
            assertEquals(2, args.length);
            assertEquals("a", args[0].asString());
            assertEquals('a', args[0].as(Object.class));
            assertValue(args[0], Trait.STRING);

            assertTrue(args[1].isNumber());
            assertEquals((byte) 42, args[1].asByte());
            assertEquals((short) 42, args[1].asShort());
            assertEquals(42, args[1].asInt());
            assertEquals(42L, args[1].asLong());
            assertEquals(42, args[1].as(Object.class));
            assertValue(args[1], Trait.NUMBER);
            return 42;
        };

        assertEquals(42, value.newInstance('a', 42).asInt());
        assertEquals(1, proxy.newInstanceCounter);

        final RuntimeException ex = new RuntimeException();
        proxy.newInstance = (args) -> {
            throw ex;
        };

        try {
            value.newInstance();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertSame(ex, e.asHostException());
            assertEquals(2, proxy.newInstanceCounter);
        }
        assertValue(value, Trait.PROXY_OBJECT, Trait.INSTANTIABLE);
    }

    static class ProxyObjectTest implements ProxyObject {

        Function<String, Object> getMember;
        int getMemberCounter;

        Supplier<Object> getMemberKeys;
        int getMemberKeysCounter;

        Function<String, Boolean> hasMember;
        int hasMemberCounter;

        BiFunction<String, Value, Void> putMember;
        int putMemberCounter;

        public Object getMember(String key) {
            getMemberCounter++;
            return getMember.apply(key);
        }

        public Object getMemberKeys() {
            getMemberKeysCounter++;
            return getMemberKeys.get();
        }

        public boolean hasMember(String key) {
            hasMemberCounter++;
            return hasMember.apply(key);
        }

        public void putMember(String key, Value value) {
            putMemberCounter++;
            putMember.apply(key, value);
        }
    }

    @Test
    public void testProxyObject() {
        ProxyObjectTest proxy = new ProxyObjectTest();

        Value value = context.asValue(proxy);

        assertTrue(value.hasMembers());
        assertSame(proxy, value.asProxyObject());

        proxy.hasMember = (key) -> {
            assertEquals("foo", key);
            return true;
        };
        proxy.putMember = (key, v) -> {
            assertEquals("foo", key);
            assertEquals(42, v.asInt());
            assertValue(v, Trait.NUMBER);
            return null;
        };
        value.putMember("foo", 42);
        assertEquals(1, proxy.putMemberCounter);

        proxy.hasMember = (key) -> {
            assertEquals("foo", key);
            return true;
        };
        proxy.hasMemberCounter = 0;
        assertTrue(value.hasMember("foo"));
        assertTrue(proxy.hasMemberCounter > 0);

        proxy.getMember = (key) -> {
            assertEquals("foo", key);
            return 42;
        };
        assertEquals(42, value.getMember("foo").asInt());
        assertEquals(1, proxy.getMemberCounter);

        List<String> testKeys = Arrays.asList("a", "b", "c");
        proxy.hasMember = (key) -> {
            return testKeys.contains(key);
        };
        proxy.getMemberKeys = () -> {
            return testKeys;
        };

        assertEquals(new HashSet<>(testKeys), value.getMemberKeys());
        assertEquals(1, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return testKeys.toArray();
        };

        assertEquals(new HashSet<>(testKeys), value.getMemberKeys());
        assertEquals(2, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return ProxyArray.fromArray(testKeys.toArray());
        };

        assertEquals(new HashSet<>(testKeys), value.getMemberKeys());
        assertEquals(3, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return null;
        };

        assertEquals(new HashSet<>(), value.getMemberKeys());
        assertEquals(4, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return new Object[]{"a", 'c'};
        };

        assertEquals(new HashSet<>(Arrays.asList("a", "c")), value.getMemberKeys());
        assertEquals(5, proxy.getMemberKeysCounter);

        proxy.hasMember = (k) -> {
            return true;
        };

        proxy.getMemberKeys = () -> {
            return new Object[]{42};
        };

        assertNull(value.getMemberKeys().iterator().next());
        assertEquals(6, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return new Object();
        };
        assertFails(() -> value.getMemberKeys(), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof IllegalStateException);
        });
        assertEquals(7, proxy.getMemberKeysCounter);

        proxy.getMemberKeys = () -> {
            return testKeys.toArray();
        };
        proxy.hasMember = (key) -> {
            return testKeys.contains(key);
        };
        proxy.getMember = (key) -> {
            return 42;
        };
        proxy.putMember = (key, v) -> {
            return null;
        };
        assertValue(value, Trait.PROXY_OBJECT, Trait.MEMBERS);
    }

    @Test
    public void testExceptionFrames() {
        Value innerInner = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw new RuntimeException("foobar");
            }
        });

        Value inner = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return innerInner.execute();
            }
        });

        Value outer = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return inner.execute();
            }
        });

        try {
            outer.execute();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof RuntimeException);
            assertEquals("foobar", e.getMessage());
            Iterator<StackFrame> frameIterator = e.getPolyglotStackTrace().iterator();
            StackFrame frame;
            for (int i = 0; i < 6; i++) {
                frame = frameIterator.next();
                assertTrue(frame.isHostFrame());
                assertEquals("execute", frame.toHostFrame().getMethodName());
            }

            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("testExceptionFrames", frame.toHostFrame().getMethodName());
        }
    }

    static class DummyObject {

    }

    /*
     * Tests that if different incompatible argument array types are used, they are properly copied.
     */
    @Test
    public void testInvalidArrayCopying() {
        Value executable = context.asValue(new ProxyExecutable() {
            @Override
            public Object execute(Value... args) {
                return args[0];
            }
        });
        Value instantiable = context.asValue(new ProxyInstantiable() {
            @Override
            public Object newInstance(Value... args) {
                return args[0];
            }
        });
        DummyObject[] arg0 = new DummyObject[]{new DummyObject()};
        DummyObject[] arg1 = new DummyObject[]{new DummyObject(), new DummyObject()};
        assertSame(arg0[0], executable.execute((Object[]) arg0).asHostObject());
        assertSame(arg1[0], executable.execute((Object[]) arg1).asHostObject());

        assertSame(arg0[0], instantiable.newInstance((Object[]) arg0).asHostObject());
        assertSame(arg1[0], instantiable.newInstance((Object[]) arg1).asHostObject());
    }

    @Test
    public void testInvalidReturnValue() {
        Value date = context.asValue(new ProxyDate() {
            public LocalDate asDate() {
                return null;
            }
        });
        assertFails(() -> date.asDate(), PolyglotException.class, (e) -> assertTrue(e.asHostException() instanceof AssertionError));

        Value time = context.asValue(new ProxyTime() {
            public LocalTime asTime() {
                return null;
            }
        });
        assertFails(() -> time.asTime(), PolyglotException.class, (e) -> assertTrue(e.asHostException() instanceof AssertionError));

        Value timeZone = context.asValue(new ProxyTimeZone() {
            public ZoneId asTimeZone() {
                return null;
            }
        });
        assertFails(() -> timeZone.asTimeZone(), PolyglotException.class, (e) -> assertTrue(e.asHostException() instanceof AssertionError));

        Value instant = context.asValue(new ProxyInstant() {
            public Instant asInstant() {
                return null;
            }
        });
        assertFails(() -> instant.asInstant(), PolyglotException.class, (e) -> assertTrue(e.asHostException() instanceof AssertionError));

        Value duration = context.asValue(new ProxyDuration() {
            public Duration asDuration() {
                return null;
            }
        });
        assertFails(() -> duration.asDuration(), PolyglotException.class, (e) -> assertTrue(e.asHostException() instanceof AssertionError));
    }

    static final class P0 implements Proxy {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof P0 || obj instanceof P1;
        }

        @Override
        public int hashCode() {
            return 0;
        }

    }

    static final class P1 implements Proxy {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof P0 || obj instanceof P1;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    @Test
    public void testProxyEquality() {
        Value p0 = context.asValue(new P0());
        Value p1 = context.asValue(new P1());
        assertEquals(p0, p0);
        assertEquals(p1, p1);
        assertNotEquals(p0, p1);
        assertNotEquals(p1, p0);
    }

}
