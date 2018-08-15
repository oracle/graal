/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertFails;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertUnsupported;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertValue;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.PROXY_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.ValueAssert.Trait;

/**
 * Testing the behavior of proxies API and proxy interfaces.
 */
public class ProxyAPITest {

    private Context context;

    @Before
    public void setUp() {
        context = Context.create();
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

    @SuppressWarnings("deprecation")
    static class ProxyPrimitiveTest implements org.graalvm.polyglot.proxy.ProxyPrimitive {

        Object primitive;
        int invocationCounter = 0;

        public Object asPrimitive() {
            invocationCounter++;
            return primitive;
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testProxyPrimitive() {
        ProxyPrimitiveTest proxy = new ProxyPrimitiveTest();

        Value value = context.asValue(proxy);

        assertTrue(value.isProxyObject());
        assertSame(proxy, value.asProxyObject());

        // no need to invoke asPrimitive yet.
        assertEquals(0, proxy.invocationCounter);

        assertProxyPrimitive(proxy, value, false, Boolean.class, BOOLEAN, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, "a", String.class, STRING, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, 'a', Character.class, STRING, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, (byte) 42, Byte.class, NUMBER, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, (short) 42, Short.class, NUMBER, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, 42, Integer.class, NUMBER, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, 42L, Long.class, NUMBER, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, 42.0f, Float.class, NUMBER, PROXY_OBJECT);
        assertProxyPrimitive(proxy, value, 42.0d, Double.class, NUMBER, PROXY_OBJECT);

        // test errors
        proxy.primitive = null;

        try {
            // force to unbox the primitive
            value.isNumber();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof IllegalStateException);
        }

        RuntimeException e = new RuntimeException();
        try {
            value = context.asValue(new org.graalvm.polyglot.proxy.ProxyPrimitive() {
                public Object asPrimitive() {
                    throw e;
                }
            });
            // force to unbox the primitive
            value.isNumber();
        } catch (PolyglotException ex) {
            assertTrue(ex.isHostException());
            assertSame(e, ex.asHostException());
            assertFalse(ex.isInternalError());
        }

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
        assertEquals(42, value.getArrayElement(42).asInt());
        assertEquals(1, proxy.getCounter);
        assertEquals(0, proxy.setCounter);
        assertEquals(0, proxy.getSizeCounter);
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
        assertEquals(0, proxy.getSizeCounter);
        proxy.setCounter = 0;
        proxy.set = null;

        proxy.getSize = () -> 42L;
        assertEquals(42L, value.getArraySize());
        assertEquals(0, proxy.getCounter);
        assertEquals(0, proxy.setCounter);
        assertEquals(1, proxy.getSizeCounter);
        proxy.getSize = null;
        proxy.getCounter = 0;

        RuntimeException ex = new RuntimeException();
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

    private void assertProxyPrimitive(ProxyPrimitiveTest proxy, Value value, Object primitiveValue, Class<?> primitiveType, Trait... traits) {
        proxy.primitive = primitiveValue;
        proxy.invocationCounter = 0;
        assertEquals(0, proxy.invocationCounter);
        assertEquals(proxy.primitive, value.as(primitiveType));
        assertEquals(proxy.primitive, value.as(Object.class));
        assertEquals(2, proxy.invocationCounter);
        proxy.invocationCounter = 0;
        assertValue(context, value, traits);
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
            ValueAssert.assertValue(context, args[0], Trait.STRING);

            assertTrue(args[1].isNumber());
            assertEquals((byte) 42, args[1].asByte());
            assertEquals((short) 42, args[1].asShort());
            assertEquals(42, args[1].asInt());
            assertEquals(42L, args[1].asLong());
            assertEquals(42, args[1].as(Object.class));
            ValueAssert.assertValue(context, args[1], Trait.NUMBER);
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
        assertValue(context, value, Trait.PROXY_OBJECT, Trait.EXECUTABLE);
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
            ValueAssert.assertValue(context, args[0], Trait.STRING);

            assertTrue(args[1].isNumber());
            assertEquals((byte) 42, args[1].asByte());
            assertEquals((short) 42, args[1].asShort());
            assertEquals(42, args[1].asInt());
            assertEquals(42L, args[1].asLong());
            assertEquals(42, args[1].as(Object.class));
            ValueAssert.assertValue(context, args[1], Trait.NUMBER);
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
        assertValue(context, value, Trait.PROXY_OBJECT, Trait.INSTANTIABLE);
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

        proxy.putMember = (key, v) -> {
            assertEquals("foo", key);
            assertEquals(42, v.asInt());
            ValueAssert.assertValue(context, v, Trait.NUMBER);
            return null;
        };
        value.putMember("foo", 42);
        assertEquals(1, proxy.putMemberCounter);

        proxy.hasMember = (key) -> {
            assertEquals("foo", key);
            return true;
        };
        assertTrue(value.hasMember("foo"));
        assertEquals(1, proxy.hasMemberCounter);

        proxy.getMember = (key) -> {
            assertEquals("foo", key);
            return 42;
        };
        assertEquals(42, value.getMember("foo").asInt());
        assertEquals(1, proxy.getMemberCounter);

        List<String> testKeys = Arrays.asList("a", "b", "c");

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

        proxy.getMemberKeys = () -> {
            return new Object[]{42};
        };

        assertFails(() -> value.getMemberKeys().iterator().next(), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof ClassCastException);
        });
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
        assertValue(context, value, Trait.PROXY_OBJECT, Trait.MEMBERS);
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

}
