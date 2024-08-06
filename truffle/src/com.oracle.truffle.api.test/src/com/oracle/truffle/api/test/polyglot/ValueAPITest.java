/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.ARRAY_ELEMENTS;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.BUFFER_ELEMENTS;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.DATE;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.DURATION;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.EXCEPTION;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.EXECUTABLE;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.HASH;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.HOST_OBJECT;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.INSTANTIABLE;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.ITERABLE;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.MEMBERS;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.META;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.NULL;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.PROXY_OBJECT;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.STRING;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.TIME;
import static com.oracle.truffle.tck.tests.ValueAssert.Trait.TIMEZONE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.Implementable;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class ValueAPITest {

    private static Context context;
    private static Context secondaryContext;

    @BeforeClass
    public static void setUp() {
        context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        secondaryContext = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
    }

    @AfterClass
    public static void tearDown() {
        context.close();
        secondaryContext.close();
    }

    @Test
    public void testGetContext() {
        assertNull(Value.asValue(null).getContext());
        assertNull(Value.asValue("").getContext());
        assertNull(Value.asValue(42).getContext());
        assertNull(Value.asValue(Instant.now()).getContext());
        assertNull(Value.asValue(new EmptyObject()).getContext());
        assertNull(Value.asValue(new Members()).getContext());

        Context creator = Context.create();
        creator.enter();
        Context current = Context.getCurrent();
        creator.leave();
        assertNotSame(creator, current);
        assertEquals(creator, current);

        assertSame(current, creator.asValue(null).getContext());
        assertSame(current, creator.asValue("").getContext());
        assertSame(current, creator.asValue(42).getContext());
        assertSame(current, creator.asValue(Instant.now()).getContext());
        assertSame(current, creator.asValue(new EmptyObject()).getContext());
        assertSame(current, creator.asValue(new Members()).getContext());

    }

    @Test
    public void testInstantiate() {
        Value classValue = context.asValue(java.util.Date.class);
        Value dateInstance = classValue.newInstance();
        Date date = dateInstance.asHostObject();
        assertNotNull(date);

        long ms = 1_000_000_000L;
        dateInstance = classValue.newInstance(ms);
        date = dateInstance.asHostObject();
        assertEquals(ms, date.getTime());
    }

    private static final Object[] STRINGS = new Object[]{
                    "", 'a', "a", "foo",
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testString() {
        for (Object string : STRINGS) {
            assertValueInContexts(context.asValue(string), STRING);
            assertValueInContexts(context.asValue(new StringWrapper(string.toString())), STRING);
        }
    }

    @Test
    public void testDatesTimesZonesAndDuration() {
        assertValueInContexts(context.asValue(LocalDate.now()), HOST_OBJECT, MEMBERS, DATE, EXECUTABLE);
        assertValueInContexts(context.asValue(LocalTime.now()), HOST_OBJECT, MEMBERS, TIME, EXECUTABLE);
        assertValueInContexts(context.asValue(LocalDateTime.now()), HOST_OBJECT, MEMBERS, DATE, TIME, EXECUTABLE);
        assertValueInContexts(context.asValue(Instant.now()), HOST_OBJECT, MEMBERS, DATE, TIME, TIMEZONE, EXECUTABLE);
        assertValueInContexts(context.asValue(ZonedDateTime.now()), HOST_OBJECT, MEMBERS, DATE, TIME, TIMEZONE);
        assertValueInContexts(context.asValue(ZoneId.of("UTC")), HOST_OBJECT, MEMBERS, TIMEZONE);
        assertValueInContexts(context.asValue(Date.from(Instant.now())), HOST_OBJECT, MEMBERS, DATE, TIME, TIMEZONE);
        assertValueInContexts(context.asValue(Duration.ofMillis(100)), HOST_OBJECT, MEMBERS, DURATION);

        assertValueInContexts(context.asValue(ProxyDate.from(LocalDate.now())), DATE, PROXY_OBJECT);
        assertValueInContexts(context.asValue(ProxyTime.from(LocalTime.now())), TIME, PROXY_OBJECT);
        assertValueInContexts(context.asValue(ProxyInstant.from(Instant.now())), DATE, TIME, TIMEZONE, PROXY_OBJECT);
        assertValueInContexts(context.asValue(ProxyTimeZone.from(ZoneId.of("UTC"))), TIMEZONE, PROXY_OBJECT);
        assertValueInContexts(context.asValue(ProxyDuration.from(Duration.ofMillis(100))), DURATION, PROXY_OBJECT);

    }

    private static void assertValueInContexts(Value value) {
        assertValue(value);
        assertValue(secondaryContext.asValue(value));
    }

    private static void assertValueInContexts(Value value, Trait... expectedTypes) {
        assertValue(value, expectedTypes);
        assertValue(secondaryContext.asValue(value), expectedTypes);
    }

    private static final Number[] NUMBERS = new Number[]{
                    (byte) 0, (byte) 1, Byte.MAX_VALUE, Byte.MIN_VALUE,
                    (short) 0, (short) 1, Short.MAX_VALUE, Short.MIN_VALUE,
                    0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE,
                    0f, 0.24f, -0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL,
                    0d, 0.24d, -0d, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL,
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testNumbers() {
        for (Number number : NUMBERS) {
            assertValueInContexts(context.asValue(number), NUMBER);
            assertValueInContexts(context.asValue(new NumberWrapper(number)), NUMBER);
        }
    }

    private static final boolean[] BOOLEANS = new boolean[]{
                    false, true,
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testBooleans() {
        for (boolean bool : BOOLEANS) {
            assertValueInContexts(context.asValue(bool), BOOLEAN);
            assertValueInContexts(context.asValue(new BooleanWrapper(bool)), BOOLEAN);
        }
    }

    @Test
    public void testNull() {
        assertValueInContexts(context.asValue(null), HOST_OBJECT, NULL);
    }

    private static class BigIntegerSubClass extends BigInteger {

        private static final long serialVersionUID = -8639948598957064947L;

        BigIntegerSubClass(String val) {
            super(val);
        }
    }

    private static final Object[] HOST_OBJECTS = new Object[]{
                    new ArrayList<>(),
                    new HashMap<>(),
                    new EmptyObject(),
                    new PrivateObject(),
                    new FieldAccess(),
                    new JavaSuperClass(),
                    new BigInteger("42"),
                    new BigIntegerSubClass("42"),
                    new BigDecimal("42"),
                    new Function<>() {
                        public Object apply(Object t) {
                            return t;
                        }
                    }, new Supplier<String>() {
                        public String get() {
                            return "foobar";
                        }
                    }, BigDecimal.class, Class.class, Proxy.newProxyInstance(ValueAPITest.class.getClassLoader(), new Class<?>[]{ProxyInterface.class}, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            switch (method.getName()) {
                                case "foobar":
                                    return args[0];
                                case "toString":
                                    return "Proxy";
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        }
                    }), ByteBuffer.wrap(new byte[0])};

    @Test
    public void testBigIntegerNumberAccess() {
        try (Context ctx = Context.newBuilder().allowHostAccess(HostAccess.newBuilder().allowPublicAccess(true).allowBigIntegerNumberAccess(false).build()).build()) {
            Value val = ctx.asValue(new BigInteger("42"));
            assertFalse(val.fitsInByte());
            assertFalse(val.fitsInShort());
            assertFalse(val.fitsInInt());
            assertFalse(val.fitsInLong());
            assertFalse(val.fitsInBigInteger());
            assertFalse(val.fitsInFloat());
            assertFalse(val.fitsInDouble());
            assertFalse(val.isNumber());
        }
        try (Context ctx = Context.newBuilder().allowHostAccess(HostAccess.newBuilder().allowPublicAccess(true).build()).build()) {
            Value val = ctx.asValue(new BigInteger("42"));
            assertTrue(val.fitsInByte());
            assertTrue(val.fitsInShort());
            assertTrue(val.fitsInInt());
            assertTrue(val.fitsInLong());
            assertTrue(val.fitsInBigInteger());
            assertTrue(val.fitsInFloat());
            assertTrue(val.fitsInDouble());
            assertTrue(val.isNumber());
        }
    }

    @Test
    public void testHostObject() {
        assertTrue(context.asValue(new EmptyObject()).getMemberKeys().isEmpty());
        assertTrue(context.asValue(new PrivateObject()).getMemberKeys().isEmpty());

        for (Object value : HOST_OBJECTS) {
            List<Trait> expectedTraits = new ArrayList<>();
            expectedTraits.add(MEMBERS);
            expectedTraits.add(HOST_OBJECT);

            if (value.getClass() == BigInteger.class) {
                expectedTraits.add(NUMBER);
            }

            if (value instanceof Supplier || value instanceof Function) {
                expectedTraits.add(EXECUTABLE);
            }

            if (value instanceof List) {
                expectedTraits.add(ARRAY_ELEMENTS);
                expectedTraits.add(ITERABLE);
            }

            if (value instanceof ByteBuffer) {
                expectedTraits.add(BUFFER_ELEMENTS);
            }

            if (value instanceof Class && value != Class.class) {
                expectedTraits.add(INSTANTIABLE);
            }

            if (value instanceof Class) {
                expectedTraits.add(META);
            }

            if (value instanceof Map) {
                expectedTraits.add(HASH);
            }

            assertValueInContexts(context.asValue(value), expectedTraits.toArray(new Trait[0]));
        }
    }

    private static final Object[] ARRAYS = new Object[]{
                    new boolean[]{true, false, true},
                    new char[]{'a', 'b', 'c'},
                    new byte[]{42, 42, 42},
                    new short[]{42, 42, 42},
                    new int[]{42, 42, 42},
                    new long[]{42, 42, 42},
                    new float[]{42, 42, 42},
                    new double[]{42, 42, 42},
                    new Boolean[]{true, false, true},
                    new Character[]{'a', 'b', 'c'},
                    new Byte[]{42, 42, 42},
                    new Short[]{42, 42, 42},
                    new Integer[]{42, 42, 42},
                    new Long[]{42L, 42L, 42L},
                    new Float[]{42f, 42f, 42f},
                    new Double[]{42d, 42d, 42d},
                    new Object[]{true, 'a', "ab", (byte) 42, (short) 42, 42, 42L, 42f, 42d},
                    new String[]{"a", "b", "c"},
                    new CharSequence[]{"a"},
                    new ArrayList<>(Arrays.asList("a", 42)),
                    new DummyList()

    };

    @Test
    public void testArrays() {
        for (Object array : ARRAYS) {
            assertValueInContexts(context.asValue(array), ARRAY_ELEMENTS, ITERABLE, HOST_OBJECT, MEMBERS);
        }
    }

    @Test
    public void testListRemove() {
        List<Object> list = new ArrayList<>(Arrays.asList("a", "b", 42, 43));
        Value vlist = context.asValue(list);
        assertEquals(4, vlist.getArraySize());
        boolean success = vlist.removeArrayElement(1);
        assertTrue(success);
        assertEquals(3, list.size());
        assertEquals(3, vlist.getArraySize());
    }

    // region Buffer tests

    /**
     * Returns an array of test {@link ByteBuffer}s with different implementation.
     * <p>
     * All test buffers contains bytes [1, 2, 3, 4, 5, 6, 7, 8].
     */
    public static ByteBuffer[] makeTestBuffers() {
        final List<ByteBuffer> list = new ArrayList<>();

        // Heap buffers
        list.add(fillTestBuffer(ByteBuffer.allocate(8))); // HeapByteBuffer
        list.add(fillTestBuffer(ByteBuffer.allocate(8)).order(ByteOrder.BIG_ENDIAN)); // HeapByteBuffer
        list.add(fillTestBuffer(ByteBuffer.allocate(8)).order(ByteOrder.LITTLE_ENDIAN)); // HeapByteBuffer
        list.add(fillTestBuffer(ByteBuffer.wrap(new byte[8]))); // HeapByteBuffer
        list.add(fillTestBuffer(sliceTestBuffer(ByteBuffer.allocate(10)))); // HeapByteBuffer
        list.add(fillTestBuffer(ByteBuffer.allocate(8)).asReadOnlyBuffer()); // HeapByteBufferR

        // Direct buffers
        list.add(fillTestBuffer(ByteBuffer.allocateDirect(8))); // DirectHeapBuffer
        list.add(fillTestBuffer(ByteBuffer.allocateDirect(8)).order(ByteOrder.BIG_ENDIAN)); // DirectHeapBuffer
        list.add(fillTestBuffer(ByteBuffer.allocateDirect(8)).order(ByteOrder.LITTLE_ENDIAN)); // DirectHeapBuffer
        list.add(fillTestBuffer(sliceTestBuffer(ByteBuffer.allocateDirect(10)))); // DirectHeapBuffer
        list.add(fillTestBuffer(ByteBuffer.allocateDirect(8)).asReadOnlyBuffer()); // DirectHeapBufferR

        return list.toArray(new ByteBuffer[0]);
    }

    /*
     * testBuffers field cannot be static, otherwise the test doesn't run on SVM unless ValueAPITest
     * is initialized at runtime, which we want to avoid.
     */
    private final ByteBuffer[] testBuffers = makeTestBuffers();

    private static ByteBuffer fillTestBuffer(ByteBuffer buffer) {
        return buffer.put(0, (byte) 1).put(1, (byte) 2).put(2, (byte) 3).put(3, (byte) 4).put(4, (byte) 5).put(5, (byte) 6).put(6, (byte) 7).put(7, (byte) 8);
    }

    private static ByteBuffer sliceTestBuffer(ByteBuffer buffer) {
        buffer.position(1); // not chainable (returns Buffer)
        buffer.limit(9); // not chainable (returns Buffer)
        return buffer.slice();
    }

    @Test
    public void testBuffers() {
        for (final ByteBuffer buffer : testBuffers) {
            final Value value = context.asValue(buffer);
            assertValueInContexts(value, BUFFER_ELEMENTS, HOST_OBJECT, MEMBERS);
        }
    }

    @Test
    public void testBuffersModifiable() {
        for (final ByteBuffer buffer : makeTestBuffers()) {
            Assert.assertEquals(!buffer.isReadOnly(), context.asValue(buffer).isBufferWritable());
        }
    }

    @Test
    public void testBuffersSize() {
        for (final ByteBuffer buffer : makeTestBuffers()) {
            Assert.assertEquals(8, context.asValue(buffer).getBufferSize());
        }
    }

    @Test
    public void testBuffersRead() {
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            for (byte i = 0; i < value.getBufferSize(); ++i) {
                Assert.assertEquals(buffer.get(i), value.readBufferByte(i));
                Assert.assertEquals("Side effect: readBufferByte should not modify wrapped buffer's order", order, buffer.order());
            }
            for (byte i = 0; i < value.getBufferSize() - 1; ++i) {
                Assert.assertEquals(buffer.getShort(i), value.readBufferShort(order, i));
                Assert.assertEquals("Side effect: readBufferShort should not modify wrapped buffer's order", order, buffer.order());
            }
            for (byte i = 0; i < value.getBufferSize() - 3; ++i) {
                Assert.assertEquals(buffer.getInt(i), value.readBufferInt(order, i));
                Assert.assertEquals("Side effect: readBufferInt should not modify wrapped buffer's order", order, buffer.order());
                Assert.assertEquals(buffer.getFloat(i), value.readBufferFloat(order, i), 0);
                Assert.assertEquals("Side effect: readBufferFloat should not modify wrapped buffer's order", order, buffer.order());
            }
            for (byte i = 0; i < value.getBufferSize() - 7; ++i) {
                Assert.assertEquals(buffer.getLong(i), value.readBufferLong(order, i));
                Assert.assertEquals("Side effect: readBufferLong should not modify wrapped buffer's order", order, buffer.order());
                Assert.assertEquals(buffer.getDouble(i), value.readBufferDouble(order, i), 0);
                Assert.assertEquals("Side effect: readBufferDouble should not modify wrapped buffer's order", order, buffer.order());
            }
        }
    }

    @Test
    public void testBuffersWrite() {
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize(); ++i) {
                    value.writeBufferByte(i, Byte.MAX_VALUE);
                    Assert.assertEquals(Byte.MAX_VALUE, buffer.get(i));
                    Assert.assertEquals("Side effect: writeBufferByte should not modify wrapped buffer's order", order, buffer.order());
                }
            }
        }
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize() - 1; ++i) {
                    value.writeBufferShort(order, i, Short.MAX_VALUE);
                    Assert.assertEquals(Short.MAX_VALUE, buffer.getShort(i));
                }
            }
        }
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize() - 3; ++i) {
                    value.writeBufferInt(order, i, Integer.MAX_VALUE);
                    Assert.assertEquals(Integer.MAX_VALUE, buffer.getInt(i));
                }
            }
        }
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize() - 3; ++i) {
                    value.writeBufferFloat(order, i, Float.MAX_VALUE);
                    Assert.assertEquals(Float.MAX_VALUE, buffer.getFloat(i), 0);
                }
            }
        }
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize() - 7; ++i) {
                    value.writeBufferLong(order, i, Long.MAX_VALUE);
                    Assert.assertEquals(Long.MAX_VALUE, buffer.getLong(i));
                }
            }
        }
        for (final ByteBuffer buffer : makeTestBuffers()) {
            final Value value = context.asValue(buffer);
            final ByteOrder order = buffer.order();
            if (value.isBufferWritable()) {
                for (int i = 0; i < value.getBufferSize() - 7; ++i) {
                    value.writeBufferDouble(order, i, Double.MAX_VALUE);
                    Assert.assertEquals(Double.MAX_VALUE, buffer.getDouble(i), 0);
                }
            }
        }

    }

    @Test
    public void testBuffersErrors() {
        for (final ByteBuffer buffer : testBuffers) {
            final Value value = context.asValue(buffer);
            final String className = buffer.getClass().getName();
            final ByteOrder order = buffer.order();
            if (buffer.isReadOnly()) {
                assertFails(() -> value.writeBufferByte(0, Byte.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferByte() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
                assertFails(() -> value.writeBufferShort(order, 0, Short.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferShort() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
                assertFails(() -> value.writeBufferInt(order, 0, Integer.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferInt() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
                assertFails(() -> value.writeBufferLong(order, 0, Long.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferLong() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
                assertFails(() -> value.writeBufferFloat(order, 0, Float.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferFloat() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
                assertFails(() -> value.writeBufferDouble(order, 0, Double.MAX_VALUE), UnsupportedOperationException.class,
                                "Unsupported operation Value.writeBufferDouble() for '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className +
                                                "). You can ensure that the operation is supported using Value.isBufferWritable().");
            } else {
                assertFails(() -> value.readBufferByte(-1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 1 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferByte(value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 1 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferByte(-1, Byte.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 1 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferByte(value.getBufferSize(), Byte.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 1 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");

                assertFails(() -> value.readBufferShort(order, -1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 2 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferShort(order, value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 2 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferShort(order, -1, Short.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 2 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferShort(order, value.getBufferSize(), Short.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 2 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");

                assertFails(() -> value.readBufferInt(order, -1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferInt(order, value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferInt(order, -1, Integer.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferInt(order, value.getBufferSize(), Integer.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");

                assertFails(() -> value.readBufferLong(order, -1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferLong(order, value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferLong(order, -1, Long.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferLong(order, value.getBufferSize(), Long.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");

                assertFails(() -> value.readBufferFloat(order, -1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferFloat(order, value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferFloat(order, -1, Float.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferFloat(order, value.getBufferSize(), Float.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 4 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");

                assertFails(() -> value.readBufferDouble(order, -1), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.readBufferDouble(order, value.getBufferSize()), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferDouble(order, -1, Double.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset -1 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
                assertFails(() -> value.writeBufferDouble(order, value.getBufferSize(), Double.MAX_VALUE), IndexOutOfBoundsException.class,
                                "Invalid buffer access of length 8 at byte offset 8 for buffer '" + className + "[pos=0 lim=8 cap=8]'(language: Java, type: " + className + ").");
            }
        }
    }

    // endregion

    @Test
    public void testComplexGenericCoercion() {
        TypeLiteral<List<Map<Integer, Map<String, Object[]>>>> literal = new TypeLiteral<>() {
        };
        Map<String, Object> map = new HashMap<>();
        map.put("foobar", new Object[]{"baz"});
        Object[] array = new Object[]{ProxyArray.fromArray(ProxyObject.fromMap(map))};

        List<Map<Integer, Map<String, Object[]>>> value = context.asValue(array).as(literal);
        assertEquals("baz", value.get(0).get(0).get("foobar")[0]);

    }

    @Test
    public void testComplexGenericCoercion2() {
        TypeLiteral<List<Map<String, Map<String, Object[]>>>> literal = new TypeLiteral<>() {
        };
        Object[] array = new Object[]{ProxyObject.fromMap(Collections.singletonMap("foo", ProxyObject.fromMap(Collections.singletonMap("bar", new Object[]{"baz"}))))};

        List<Map<String, Map<String, Object[]>>> value = context.asValue(array).as(literal);
        assertEquals("baz", value.get(0).get("foo").get("bar")[0]);
    }

    public interface JavaInterface {

    }

    @FunctionalInterface
    public interface JavaFunctionalInterface {

        void foo();

    }

    public static class JavaSuperClass {

    }

    public static class JavaClass extends JavaSuperClass implements JavaInterface, JavaFunctionalInterface {

        public void foo() {
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectCoercion() {
        objectCoercionTest(null, Object.class, null);
        objectCoercionTest(null, String.class, null);
        objectCoercionTest(null, Boolean.class, null);
        objectCoercionTest(null, Boolean.class, null);

        objectCoercionTest(true, Boolean.class, null);
        objectCoercionTest("foo", String.class, null);
        objectCoercionTest('c', Character.class, null);
        objectCoercionTest((byte) 42, Byte.class, null);
        objectCoercionTest((short) 42, Short.class, null);
        objectCoercionTest(42, Integer.class, null);
        objectCoercionTest((long) 42, Long.class, null);
        objectCoercionTest(42.5d, Double.class, null);
        objectCoercionTest(42.5f, Float.class, null);

        objectCoercionTest(new JavaClass(), JavaClass.class, null);
        objectCoercionTest(new JavaClass(), JavaSuperClass.class, null);
        objectCoercionTest(new JavaClass(), JavaInterface.class, null);
        objectCoercionTest(new JavaClass(), JavaFunctionalInterface.class, null);
        objectCoercionTest(new JavaClass(), Object.class, null);

        Map<String, Object> map = new HashMap<>();
        map.put("foobar", "baz");
        map.put("foobar2", "baz2");
        objectCoercionTest(ProxyObject.fromMap(map), Map.class,
                        (v) -> assertEquals("baz", v.get("foobar")));
        objectCoercionTest(ProxyObject.fromMap(map), Map.class, (v) -> {
            assertNull(v.remove("notAMember"));
            assertEquals("baz", v.remove("foobar"));
            assertFalse(v.remove("foobar2", "baz"));
            assertFalse(v.remove("foobar", "baz2"));
            assertTrue(v.remove("foobar2", "baz2"));
            assertTrue(map.isEmpty());
            map.put("foobar", "baz");
            map.put("foobar2", "baz2");
        });

        ProxyArray array = ProxyArray.fromArray(42, 42, 42);
        objectCoercionTest(array, List.class,
                        (v) -> assertEquals(42, v.get(2)));
        List<Object> arrayList = new ArrayList<>();
        ProxyArray list = ProxyArray.fromList(arrayList);
        objectCoercionTest(list, List.class, (v) -> {
            arrayList.addAll(Arrays.asList(41, 42));
            assertEquals(42, v.remove(1));
            assertTrue(v.remove((Object) 41));
            assertFalse(v.remove((Object) 14));
        });

        ArrayElements arrayElements = new ArrayElements();
        arrayElements.array.add(42);

        objectCoercionTest(arrayElements, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertFalse(v instanceof Function);

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertFalse(value.canInstantiate());
            assertFalse(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArray mapAndArray = new MembersAndArray();
        mapAndArray.map.put("foo", "bar");
        mapAndArray.array.add(42);

        objectCoercionTest(mapAndArray, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertFalse(v instanceof Function);

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndInstantiable membersAndInstantiable = new MembersAndInstantiable();
        membersAndInstantiable.map.put("foo", "bar");
        membersAndInstantiable.instantiableResult = "foobarbaz";

        objectCoercionTest(membersAndInstantiable, Map.class, (v) -> {
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

        MembersAndExecutable membersAndExecutable = new MembersAndExecutable();
        membersAndExecutable.map.put("foo", "bar");
        membersAndExecutable.executableResult = "foobarbaz";

        objectCoercionTest(membersAndExecutable, Map.class, (v) -> {
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

        MembersAndArrayAndExecutable mapAndArrayAndExecutable = new MembersAndArrayAndExecutable();
        mapAndArrayAndExecutable.map.put("foo", "bar");
        mapAndArrayAndExecutable.array.add(42);
        mapAndArrayAndExecutable.executableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndExecutable, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArrayAndInstantiable mapAndArrayAndInstantiable = new MembersAndArrayAndInstantiable();
        mapAndArrayAndInstantiable.map.put("foo", "bar");
        mapAndArrayAndInstantiable.array.add(42);
        mapAndArrayAndInstantiable.instantiableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndInstantiable, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArrayAndExecutableAndInstantiable mapAndArrayAndExecutableAndInstantiable = new MembersAndArrayAndExecutableAndInstantiable();
        mapAndArrayAndExecutableAndInstantiable.map.put("foo", "bar");
        mapAndArrayAndExecutableAndInstantiable.array.add(42);
        mapAndArrayAndExecutableAndInstantiable.executableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndExecutableAndInstantiable, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));
            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        Executable exectable = new Executable();
        exectable.executableResult = "foobarbaz";

        objectCoercionTest(exectable, Function.class, (v) -> {
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));
            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertFalse(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

        MembersAndInvocable invocable = new MembersAndInvocable();
        invocable.invokeMember = "foo";
        invocable.invocableResult = "foobarbaz";

        objectCoercionTest(invocable, Map.class, (v) -> {
            Value value = context.asValue(v);
            assertTrue(value.canInvokeMember("foo"));
            assertEquals("foobarbaz", value.invokeMember("foo").asString());
        }, false);

        HashEntries hashEntries = new HashEntries();
        hashEntries.hashEntries.put("foo", "foobarbaz");

        objectCoercionTest(hashEntries, Map.class, (v) -> {
            assertEquals(1, v.size());
            assertEquals("foobarbaz", v.get("foo"));
            Value value = context.asValue(v);
            assertTrue(value.hasHashEntries());
            assertFalse(value.hasMembers());
        });

        HashEntriesAndArray hashEntriesAndArray = new HashEntriesAndArray();
        hashEntries.hashEntries.put("foo", "foobarbaz");
        hashEntriesAndArray.array.add(42);
        hashEntriesAndArray.array.add(43);

        objectCoercionTest(hashEntriesAndArray, List.class, (v) -> {
            assertEquals(2, v.size());
            assertEquals(42, v.get(0));
            assertEquals(43, v.get(1));
            Value value = context.asValue(v);
            assertTrue(value.hasHashEntries());
            assertTrue(value.hasArrayElements());
            assertFalse(value.hasMembers());
        });

        HashEntriesAndMembers hashEntriesAndMembers = new HashEntriesAndMembers();
        hashEntriesAndMembers.hashEntries.put("foo", "foobarbaz");
        hashEntriesAndMembers.members.put("member1", "whatever");
        hashEntriesAndMembers.members.put("member2", "whatever");

        objectCoercionTest(hashEntriesAndMembers, Map.class, (v) -> {
            assertEquals(1, v.size());
            assertEquals("foobarbaz", v.get("foo"));
            Value value = context.asValue(v);
            assertTrue(value.hasHashEntries());
            assertTrue(value.hasMembers());
        });

        HashEntriesAndArrayAndMembers hashEntriesAndArrayAndMembers = new HashEntriesAndArrayAndMembers();
        hashEntriesAndArrayAndMembers.hashEntries.put("foo", "foobarbaz");
        hashEntriesAndArrayAndMembers.members.put("member1", "whatever");
        hashEntriesAndArrayAndMembers.members.put("member2", "whatever");
        hashEntriesAndArrayAndMembers.array.addAll(Arrays.asList(42, 43, 44));

        objectCoercionTest(hashEntriesAndArrayAndMembers, List.class, (v) -> {
            assertEquals(3, v.size());
            assertEquals(42, v.get(0));
            assertEquals(43, v.get(1));
            assertEquals(44, v.get(2));
            Value value = context.asValue(v);
            assertTrue(value.hasHashEntries());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });
    }

    private static <T> void objectCoercionTest(Object value, Class<T> expectedType, Consumer<T> validator) {
        objectCoercionTest(value, expectedType, validator, true);
    }

    @SuppressWarnings({"unchecked"})
    private static <T> void objectCoercionTest(Object value, Class<T> expectedType, Consumer<T> validator, boolean valueTest) {
        Value coerce = context.asValue(new CoerceObject()).getMember("coerce");
        T result = (T) context.asValue(value).as(Object.class);
        if (result != null) {
            assertTrue("expected " + expectedType + " but was " + result.getClass(), expectedType.isInstance(result));
        } else if (value != null) {
            fail("expected " + expectedType + " but was null");
        }

        if (validator == null) {
            assertEquals(value, result);
        } else {
            validator.accept(result);
            coerce.execute(value, validator);
        }

        if (valueTest) {
            assertValueInContexts(context.asValue(value));
            assertValueInContexts(context.asValue(result));
        }
    }

    private static class DummyList extends DummyCollection implements List<Object> {

        public boolean addAll(int index, Collection<? extends Object> c) {
            return values.addAll(index, c);
        }

        public Object get(int index) {
            return values.get(index);
        }

        public Object set(int index, Object element) {
            return values.set(index, element);
        }

        public void add(int index, Object element) {
            values.add(index, element);
        }

        public Object remove(int index) {
            return values.remove(index);
        }

        public int indexOf(Object o) {
            return values.indexOf(o);
        }

        public int lastIndexOf(Object o) {
            return values.lastIndexOf(o);
        }

        public ListIterator<Object> listIterator() {
            return values.listIterator();
        }

        public ListIterator<Object> listIterator(int index) {
            return values.listIterator(index);
        }

        public List<Object> subList(int fromIndex, int toIndex) {
            return values.subList(fromIndex, toIndex);
        }

    }

    private static class DummyCollection implements Collection<Object> {
        List<Object> values = Arrays.asList("a", 42);

        public java.util.Iterator<Object> iterator() {
            return values.iterator();
        }

        public int size() {
            return values.size();
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public boolean contains(Object o) {
            return values.contains(o);
        }

        public Object[] toArray() {
            return values.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return values.toArray(a);
        }

        public boolean add(Object e) {
            return values.add(e);
        }

        public boolean remove(Object o) {
            return values.remove(o);
        }

        public boolean containsAll(Collection<?> c) {
            return values.containsAll(c);
        }

        public boolean addAll(Collection<? extends Object> c) {
            return values.addAll(c);
        }

        public boolean removeAll(Collection<?> c) {
            return values.removeAll(c);
        }

        public boolean retainAll(Collection<?> c) {
            return values.retainAll(c);
        }

        public void clear() {
            values.clear();
        }
    }

    public static class CoerceObject {

        public <T> Object coerce(T value, Consumer<T> o) {
            o.accept(value);
            return value;
        }

    }

    public static class EmptyObject {
    }

    private static class PrivateObject {
    }

    private static class FieldAccess {
        @SuppressWarnings("unused") public EmptyObject member;
    }

    private interface ProxyInterface {

        Object foobar(Object... args);

    }

    static class Members implements ProxyObject {

        final Map<String, Object> map = new HashMap<>();

        public Object getMember(String key) {
            return map.get(key);
        }

        public ProxyArray getMemberKeys() {
            return ProxyArray.fromArray(map.keySet().toArray());
        }

        public boolean hasMember(String key) {
            return map.containsKey(key);
        }

        public void putMember(String key, Value value) {
            map.put(key, value);
        }

    }

    static class ArrayElements implements ProxyArray {

        final List<Object> array = new ArrayList<>();

        public Object get(long index) {
            return array.get((int) index);
        }

        public void set(long index, Value value) {
            array.set((int) index, value);
        }

        public long getSize() {
            return array.size();
        }
    }

    static class HashEntries implements ProxyHashMap {

        final Map<Object, Object> hashEntries;
        private final ProxyHashMap delegate;

        HashEntries() {
            this.hashEntries = new LinkedHashMap<>();
            this.delegate = ProxyHashMap.from(hashEntries);
        }

        @Override
        public long getHashSize() {
            return delegate.getHashSize();
        }

        @Override
        public boolean hasHashEntry(Value key) {
            return delegate.hasHashEntry(key);
        }

        @Override
        public Object getHashValue(Value key) {
            return delegate.getHashValue(key);
        }

        @Override
        public void putHashEntry(Value key, Value value) {
            delegate.putHashEntry(key, value);
        }

        @Override
        public boolean removeHashEntry(Value key) {
            return delegate.removeHashEntry(key);
        }

        @Override
        public Object getHashEntriesIterator() {
            return delegate.getHashEntriesIterator();
        }
    }

    static class HashEntriesAndArray extends HashEntries implements ProxyArray {

        final List<Object> array = new ArrayList<>();

        @Override
        public Object get(long index) {
            return array.get((int) index);
        }

        @Override
        public void set(long index, Value value) {
            array.set((int) index, value);
        }

        @Override
        public long getSize() {
            return array.size();
        }
    }

    static class HashEntriesAndMembers extends HashEntries implements ProxyObject {
        final Map<String, Object> members = new HashMap<>();

        @Override
        public Object getMember(String key) {
            return members.get(key);
        }

        @Override
        public ProxyArray getMemberKeys() {
            return ProxyArray.fromArray(members.keySet().toArray());
        }

        @Override
        public boolean hasMember(String key) {
            return members.containsKey(key);
        }

        @Override
        public void putMember(String key, Value value) {
            members.put(key, value);
        }
    }

    static class HashEntriesAndArrayAndMembers extends HashEntriesAndArray implements ProxyObject {
        final Map<String, Object> members = new HashMap<>();

        @Override
        public Object getMember(String key) {
            return members.get(key);
        }

        @Override
        public ProxyArray getMemberKeys() {
            return ProxyArray.fromArray(members.keySet().toArray());
        }

        @Override
        public boolean hasMember(String key) {
            return members.containsKey(key);
        }

        @Override
        public void putMember(String key, Value value) {
            members.put(key, value);
        }
    }

    static class MembersAndArray extends Members implements ProxyObject, ProxyArray {

        final List<Object> array = new ArrayList<>();

        public Object get(long index) {
            return array.get((int) index);
        }

        public void set(long index, Value value) {
            array.set((int) index, value);
        }

        public long getSize() {
            return array.size();
        }

    }

    private static class MembersAndExecutable extends Members implements ProxyObject, ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }
    }

    private static class MembersAndInstantiable extends Members implements ProxyObject, ProxyInstantiable {

        Object instantiableResult;

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class MembersAndArrayAndExecutable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

    }

    private static class MembersAndArrayAndInstantiable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyInstantiable {

        Object instantiableResult;

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class MembersAndArrayAndExecutableAndInstantiable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyInstantiable, ProxyExecutable {

        Object executableResult;
        Object instantiableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class Executable implements ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class MembersAndInvocable implements TruffleObject {

        String invokeMember;
        Object invocableResult;

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean internal) {
            return new KeysArray(new String[]{invokeMember});
        }

        @ExportMessage
        Object invokeMember(String member, @SuppressWarnings("unused") Object[] arguments) throws UnknownIdentifierException {
            if (member.equals(invokeMember)) {
                return invocableResult;
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        boolean isMemberInvocable(String member) {
            return invokeMember.equals(member);
        }

        @ExportLibrary(InteropLibrary.class)
        static final class KeysArray implements TruffleObject {

            private final String[] keys;

            KeysArray(String[] keys) {
                this.keys = keys;
            }

            @SuppressWarnings("static-method")
            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return index >= 0 && index < keys.length;
            }

            @ExportMessage
            long getArraySize() {
                return keys.length;
            }

            @ExportMessage
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                try {
                    return keys[(int) index];
                } catch (IndexOutOfBoundsException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw InvalidArrayIndexException.create(index);
                }
            }
        }
    }

    @Test
    public void testNullCoercionErrors() {
        Value nullValue = context.asValue(null);
        assertFails(() -> nullValue.asInt(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int' using Value.asInt(). " +
                                        "You can ensure that the operation is supported using Value.fitsInInt().");
        assertFails(() -> nullValue.as(int.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");
        assertFails(() -> nullValue.asByte(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'byte' using Value.asByte(). " +
                                        "You can ensure that the operation is supported using Value.fitsInByte().");
        assertFails(() -> nullValue.as(byte.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'byte'.");
        assertFails(() -> nullValue.asShort(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'short' using Value.asShort(). " +
                                        "You can ensure that the operation is supported using Value.fitsInShort().");
        assertFails(() -> nullValue.as(short.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'short'.");
        assertFails(() -> nullValue.asLong(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'long' using Value.asLong(). " +
                                        "You can ensure that the operation is supported using Value.fitsInLong().");
        assertFails(() -> nullValue.asBigInteger(), ClassCastException.class,
                        "Cannot convert 'null'(language: Java) to Java type 'java.math.BigInteger' using Value.asBigInteger(): Invalid or lossy coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInBigInteger().");
        assertFails(() -> nullValue.as(long.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'long'.");
        assertFails(() -> nullValue.asFloat(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'float' using Value.asFloat(). " +
                                        "You can ensure that the operation is supported using Value.fitsInFloat().");
        assertFails(() -> nullValue.as(float.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'float'.");
        assertFails(() -> nullValue.asDouble(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'double' using Value.asDouble(). " +
                                        "You can ensure that the operation is supported using Value.fitsInDouble().");
        assertFails(() -> nullValue.as(double.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'double'.");
        assertFails(() -> nullValue.as(char.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'char'.");
        assertFails(() -> nullValue.asBoolean(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'boolean' using Value.asBoolean(). " +
                                        "You can ensure that the operation is supported using Value.isBoolean().");
        assertFails(() -> nullValue.as(boolean.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'boolean'.");
    }

    @Test
    public void testPrimitiveCoercionErrors() {
        Value bigNumber = context.asValue(Long.MAX_VALUE);

        assertFails(() -> bigNumber.asByte(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'byte' using Value.asByte(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInByte().");
        assertFails(() -> bigNumber.as(byte.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'byte': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Byte.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Byte': Invalid or lossy primitive coercion.");

        assertFails(() -> bigNumber.asShort(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'short' using Value.asShort(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInShort().");
        assertFails(() -> bigNumber.as(short.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'short': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Short.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Short': Invalid or lossy primitive coercion.");

        assertFails(() -> bigNumber.asInt(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'int' using Value.asInt(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInInt().");
        assertFails(() -> bigNumber.as(int.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Integer.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Integer': Invalid or lossy primitive coercion.");

        Value nan = context.asValue(Double.NaN);

        assertFails(() -> nan.asLong(), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'long' using Value.asLong(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInLong().");
        assertFails(() -> nan.as(long.class), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'long': Invalid or lossy primitive coercion.");
        assertFails(() -> nan.as(Long.class), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'java.lang.Long': Invalid or lossy primitive coercion.");

        assertFails(() -> nan.asBigInteger(), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'java.math.BigInteger' using Value.asBigInteger(): Invalid or lossy coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInBigInteger().");

        Value nofloat = context.asValue(Double.MAX_VALUE);

        assertFails(() -> nofloat.asFloat(), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'float' using Value.asFloat(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInFloat().");
        assertFails(() -> nofloat.as(float.class), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'float': Invalid or lossy primitive coercion.");
        assertFails(() -> nofloat.as(Float.class), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'java.lang.Float': Invalid or lossy primitive coercion.");

        Value nodouble = context.asValue(9007199254740993L);

        assertFails(() -> nodouble.asDouble(), ClassCastException.class,
                        "Cannot convert '9007199254740993'(language: Java, type: java.lang.Long) to Java type 'double' using Value.asDouble(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInDouble().");
        assertFails(() -> nodouble.as(double.class), ClassCastException.class,
                        "Cannot convert '9007199254740993'(language: Java, type: java.lang.Long) to Java type 'double': Invalid or lossy primitive coercion.");
        assertFails(() -> nodouble.as(Double.class), ClassCastException.class,
                        "Cannot convert '9007199254740993'(language: Java, type: java.lang.Long) to Java type 'java.lang.Double': Invalid or lossy primitive coercion.");

        Value noString = context.asValue(false);

        assertFails(() -> noString.asString(), ClassCastException.class,
                        "Cannot convert 'false'(language: Java, type: java.lang.Boolean) to Java type 'java.lang.String' using Value.asString(): Invalid coercion. You can ensure that the value can be converted using Value.isString().");
        assertFails(() -> noString.as(char.class), ClassCastException.class,
                        "Cannot convert 'false'(language: Java, type: java.lang.Boolean) to Java type 'char': Invalid or lossy primitive coercion.");
        assertFails(() -> noString.as(String.class), ClassCastException.class,
                        "Cannot convert 'false'(language: Java, type: java.lang.Boolean) to Java type 'java.lang.String': Invalid or lossy primitive coercion.");

        Value noBoolean = context.asValue("foobar");

        assertFails(() -> noBoolean.asBoolean(), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'boolean' using Value.asBoolean(): Invalid or lossy primitive coercion. You can ensure that the value can be converted using Value.isBoolean().");
        assertFails(() -> noBoolean.as(boolean.class), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'boolean': Invalid or lossy primitive coercion.");
        assertFails(() -> noBoolean.as(Boolean.class), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'java.lang.Boolean': Invalid or lossy primitive coercion.");

    }

    private static class EmptyProxy implements org.graalvm.polyglot.proxy.Proxy {

        @Override
        public String toString() {
            return "proxy";
        }

    }

    @Test
    public void testTypeCoercionError() {
        Value pipe = context.asValue("not a pipe");

        assertFails(() -> pipe.as(List.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.List': Unsupported target type.");
        assertFails(() -> pipe.as(Map.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.Map': Unsupported target type.");
        assertFails(() -> pipe.as(Function.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.function.Function': Unsupported target type.");
        assertFails(() -> pipe.as(JavaInterface.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'com.oracle.truffle.api.test.polyglot.ValueAPITest$JavaInterface': Unsupported target type.");
        assertFails(() -> pipe.as(PolyglotException.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'org.graalvm.polyglot.PolyglotException': Unsupported target type.");

        Value other = context.asValue(new EmptyProxy());

        assertFails(() -> other.as(List.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.List': Value must have array elements.");
        assertFails(() -> other.as(Map.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.Map': Value must have members, array elements or hash entries.");
        assertFails(() -> other.as(Function.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.function.Function': Value must be executable or instantiable.");
        assertFails(() -> other.as(JavaInterface.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'com.oracle.truffle.api.test.polyglot.ValueAPITest$JavaInterface': Value must have members.");
        assertFails(() -> other.as(PolyglotException.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'org.graalvm.polyglot.PolyglotException': Value must be an exception.");

    }

    @Test
    public void testUnsupportedError() {
        Value pipe = context.asValue("not a pipe");

        assertFails(() -> pipe.getMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.getMember(String) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> pipe.putMember("", null), UnsupportedOperationException.class,
                        "Unsupported operation Value.putMember(String, Object) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> pipe.getArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArrayElement(long) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> pipe.setArrayElement(0, null), UnsupportedOperationException.class,
                        "Unsupported operation Value.setArrayElement(long, Object) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> pipe.getArraySize(), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArraySize() for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");

    }

    private static final TypeLiteral<List<String>> STRING_LIST = new TypeLiteral<>() {
    };
    private static final TypeLiteral<List<Integer>> INTEGER_LIST = new TypeLiteral<>() {
    };

    @Test
    @SuppressWarnings("unchecked")
    public void testArrayErrors() {
        String[] array = new String[]{"asdf"};
        Value v = context.asValue(array);

        assertFails(() -> v.getArrayElement(1L), IndexOutOfBoundsException.class,
                        "Invalid array index 1 for array '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> v.setArrayElement(1L, null), IndexOutOfBoundsException.class,
                        "Invalid array index 1 for array '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> v.removeArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.removeArrayElement(long, Object) for '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFalse(v.removeMember("a"));

        List<Object> list = v.as(List.class);
        assertFails(() -> list.get(1), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> list.set(1, null), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> list.remove(0), UnsupportedOperationException.class,
                        "Unsupported operation remove for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");

        List<?> stringList = v.as(STRING_LIST);
        assertFails(() -> stringList.get(1), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<java.lang.String> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> stringList.set(1, null), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<java.lang.String> '[asdf]'(language: Java, type: java.lang.String[]).");

        ((List<Object>) stringList).set(0, "42");
        assertEquals("42", stringList.get(0));
        ((List<Object>) stringList).set(0, context.asValue("42"));
        assertEquals("42", stringList.get(0));

        // just to make sure this works
        ((List<Object>) stringList).set(0, context.asValue("foo"));

        List<Integer> integerList = v.as(INTEGER_LIST);
        assertFails(() -> integerList.get(0), ClassCastException.class,
                        "Cannot convert 'foo'(language: Java, type: java.lang.String) to Java type 'java.lang.Integer': Invalid or lossy primitive coercion.");

        Value notAnArray = context.asValue("");
        assertFails(() -> notAnArray.getArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArrayElement(long) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> notAnArray.setArrayElement(0, null), UnsupportedOperationException.class,
                        "Unsupported operation Value.setArrayElement(long, Object) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> notAnArray.getArraySize(), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArraySize() for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");

        Value rv = context.asValue(new ArrayList<>(Arrays.asList(new String[]{"a", "b", "c"})));
        assertFails(() -> rv.removeArrayElement(3), IndexOutOfBoundsException.class,
                        "Invalid array index 3 for array '[a, b, c]'(language: Java, type: java.util.ArrayList).");
        assertFalse(v.removeMember("a"));
    }

    private static final TypeLiteral<Map<String, String>> STRING_MAP = new TypeLiteral<>() {
    };

    public static class MemberErrorTest {

        public int value = 43;
        public final int finalValue = 42;

        @Override
        public String toString() {
            return "MemberErrorTest";
        }

    }

    @Test
    public void testMemberErrors() {
        Value noMembers = context.asValue("");
        assertFails(() -> noMembers.getMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.getMember(String) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> noMembers.putMember("", null), UnsupportedOperationException.class,
                        "Unsupported operation Value.putMember(String, Object) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> noMembers.removeMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.removeMember(String, Object) for ''(language: Java, type: java.lang.String).");

        assertEquals(0, noMembers.getMemberKeys().size());
        assertFalse(noMembers.hasMembers());
        assertFalse(noMembers.hasMember(""));
        assertFalse(noMembers.getMemberKeys().contains(""));

        MemberErrorTest test = new MemberErrorTest();
        Value v = context.asValue(test);
        assertEquals(43, v.getMember("value").asInt());
        v.putMember("value", 42);
        assertEquals(42, v.getMember("value").asInt());

        assertFails(() -> v.putMember("value", ""), IllegalArgumentException.class,
                        "Invalid member value ''(language: Java, type: java.lang.String) for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and member key 'value'.");

        assertFails(() -> v.putMember("finalValue", 42), UnsupportedOperationException.class,
                        "Non writable or non-existent member key 'finalValue' for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> v.putMember("notAMember", ""), UnsupportedOperationException.class,
                        "Non writable or non-existent member key 'notAMember' for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertNull(v.getMember("notAMember"));

        assertFalse(v.removeMember("notAMember"));

        @SuppressWarnings("unchecked")
        Map<Object, Object> map = v.as(Map.class);

        // maps behave slightly differently for not existing keys
        assertNull(map.get("notAMember"));

        map.put("value", 43);
        assertEquals(43, map.get("value"));

        assertFails(() -> map.put(new Object(), ""), IllegalArgumentException.class,
                        "Illegal identifier type 'java.lang.Object' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        Map<String, String> stringMap = v.as(STRING_MAP);
        assertFails(() -> stringMap.get("value"), ClassCastException.class,
                        "Cannot convert '43'(language: Java, type: java.lang.Integer) to Java type 'java.lang.String': Invalid or lossy primitive coercion.");

        assertFails(() -> map.put("value", ""), ClassCastException.class,
                        "Invalid value ''(language: Java, type: java.lang.String) for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and identifier 'value'.");

        assertFails(() -> map.put("value", "42"), ClassCastException.class,
                        "Invalid value '42'(language: Java, type: java.lang.String) for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and identifier 'value'.");

        assertFails(() -> map.put("value", 4.2), ClassCastException.class,
                        "Invalid value '4.2'(language: Java, type: java.lang.Double) for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and identifier 'value'.");

        assertFails(() -> map.put("finalValue", 42), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'finalValue' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> map.put("finalValue", "42"), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'finalValue' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> map.put("finalValue", 4.2), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'finalValue' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> map.put("notAMember", ""), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'notAMember' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        Map<Object, Object> rmap = new HashMap<>();
        rmap.put("value", 43);
        Value rv = context.asValue(rmap);
        assertFalse(rv.removeMember("notAMember"));
    }

    @Test
    public void testNullError() {
        Value members = context.asValue(new MembersAndArrayAndExecutableAndInstantiable());
        assertFails(() -> members.putMember(null, "value"), NullPointerException.class, "identifier");
        assertFails(() -> members.getMember(null), NullPointerException.class, "identifier");
        assertFails(() -> members.hasMember(null), NullPointerException.class, "identifier");
        assertFails(() -> members.removeMember(null), NullPointerException.class, "identifier");

        assertFails(() -> members.execute((Object[]) null), NullPointerException.class, null);
        assertFails(() -> members.executeVoid((Object[]) null), NullPointerException.class, null);
        assertFails(() -> members.newInstance((Object[]) null), NullPointerException.class, null);
        assertFails(() -> members.as((Class<?>) null), NullPointerException.class, null);
        assertFails(() -> members.as((TypeLiteral<?>) null), NullPointerException.class, null);
    }

    @FunctionalInterface
    public interface ExecutableInterface {

        String execute(String argument);

    }

    /*
     * Referenced in proxy-config.json
     */
    @FunctionalInterface
    public interface OtherInterface0 {

        Object execute();

    }

    /*
     * Referenced in proxy-config.json
     */
    @FunctionalInterface
    public interface OtherInterface1 {

        Object execute(Object s);

    }

    /*
     * Referenced in proxy-config.json
     */
    @FunctionalInterface
    public interface OtherInterface2 {

        Object execute(String s, String s2);

    }

    public abstract static class AbstractClass1<T> extends AbstractList<T> implements OtherInterface0, OtherInterface1 {

    }

    @SuppressWarnings("unused")
    public static class AmbiguousType {

        public String f(int a, byte b) {
            return "1";
        }

        public String f(byte a, int b) {
            return "2";
        }
    }

    static class TestExecutable implements ExecutableInterface {

        public String execute(String argument) {
            return argument;
        }

        @Override
        public String toString() {
            return "testExecutable";
        }
    }

    @Test
    public void testExecutableErrors() {
        ExecutableInterface executable = new TestExecutable();

        Value v = context.asValue(executable);

        assertTrue(v.canExecute());
        assertEquals("", v.execute("").as(Object.class));
        assertEquals("", v.execute("").asString());

        String className = executable.getClass().getName();
        assertFails(() -> v.execute("", ""), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") " +
                                        "with arguments [''(language: Java, type: java.lang.String), ''(language: Java, type: java.lang.String)]. Expected 1 argument(s) but got 2.");

        assertFails(() -> v.execute(), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") with arguments []." +
                                        " Expected 1 argument(s) but got 0.");

        assertTrue(v.execute("42").isString());
        assertEquals("42", v.execute("42").asString());

        assertFails(() -> context.asValue("").execute(), UnsupportedOperationException.class,
                        "Unsupported operation Value.execute(Object...) for ''(language: Java, type: java.lang.String). You can ensure that the operation " +
                                        "is supported using Value.canExecute().");

        assertFails(() -> v.as(OtherInterface0.class).execute(), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") " +
                                        "with arguments []. Expected 1 argument(s) but got 0.");

        assertEquals("", v.as(OtherInterface1.class).execute(""));

        assertEquals("42", v.as(OtherInterface1.class).execute("42"));

        assertFails(() -> v.as(OtherInterface2.class).execute("", ""), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, " +
                                        "type: " + className + ") with arguments [''(language: Java, type: java.lang.String), " +
                                        "''(language: Java, type: java.lang.String)]. Expected 1 argument(s) but got 2.");

        assertSame(executable, v.as(ExecutableInterface.class));

        Value value = context.asValue(new AmbiguousType());
        assertFails(() -> value.getMember("f").execute(1, 2), IllegalArgumentException.class,
                        "Invalid argument when executing 'com.oracle.truffle.api.test.polyglot.ValueAPITest$AmbiguousType." +
                                        "f'(language: Java, type: Unknown). Multiple applicable overloads found for method name f " +
                                        "(candidates: [Method[public java.lang.String com.oracle.truffle.api.test.polyglot.ValueAPITest$AmbiguousType." +
                                        "f(int,byte)], Method[public java.lang.String com.oracle.truffle.api.test.polyglot.ValueAPITest$AmbiguousType." +
                                        "f(byte,int)]], arguments: [1 (Integer), 2 (Integer)]) Provided arguments: " +
                                        "['1'(language: Java, type: java.lang.Integer), '2'(language: Java, type: java.lang.Integer)].");
    }

    public static class InvocableType {

        @SuppressWarnings("unused")
        public String f(int a, byte b) {
            return "1";
        }

        @Override
        public String toString() {
            return getClass().getCanonicalName();
        }
    }

    @Test
    public void testInvokableErrors() {
        Value value = context.asValue(new InvocableType());
        assertTrue(value.canInvokeMember("f"));

        assertFails(() -> value.invokeMember(""), UnsupportedOperationException.class,
                        "Non readable or non-existent member key '' for object 'com.oracle.truffle.api.test.polyglot.ValueAPITest.InvocableType'" +
                                        "(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$InvocableType).");
        assertFails(() -> value.invokeMember("f", 2), IllegalArgumentException.class,
                        "Invalid argument count when invoking 'f' on 'com.oracle.truffle.api.test.polyglot.ValueAPITest.InvocableType'" +
                                        "(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$InvocableType) " +
                                        "with arguments ['2'(language: Java, type: java.lang.Integer)]. Expected 2 argument(s) but got 1.");
        assertFails(() -> value.invokeMember("f", "2", "128"), IllegalArgumentException.class,
                        "Invalid argument when invoking 'f' on 'com.oracle.truffle.api.test.polyglot.ValueAPITest." +
                                        "InvocableType'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$InvocableType). " +
                                        "Cannot convert '2'(language: Java, type: java.lang.String) to Java type 'int': Invalid or lossy primitive coercion." +
                                        "Provided arguments: ['2'(language: Java, type: java.lang.String), '128'(language: Java, type: java.lang.String)].");
        assertEquals("1", value.invokeMember("f", 2, 3).asString());

        Value primitiveValue = context.asValue(42);
        assertFails(() -> primitiveValue.invokeMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.invoke(, Object...) for '42'(language: Java, type: java.lang.Integer)." +
                                        " You can ensure that the operation is supported using Value.canInvoke(String).");
    }

    private static void assertFails(Runnable r, Class<?> hostExceptionType, String message) {
        try {
            r.run();
            Assert.fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                throw new AssertionError(e.getClass().getName() + ":" + e.getMessage(), e);
            }
            if (message != null && !message.equals(e.getMessage())) {
                ComparisonFailure f = new ComparisonFailure(null, message, e.getMessage());
                f.initCause(e);
                throw f;
            }
        }
    }

    @Test
    public void testList() {
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        Value v = context.asValue(list);

        assertTrue(v.hasArrayElements());
        assertTrue(v.hasMembers());
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("bar", v.getArrayElement(1).asString());

        AbstractPolyglotTest.assertFails(() -> v.getArrayElement(2), ArrayIndexOutOfBoundsException.class);
        // append to the list
        v.setArrayElement(2, "baz");
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("bar", v.getArrayElement(1).asString());
        assertEquals("baz", v.getArrayElement(2).asString());

        assertTrue(v.removeArrayElement(1));
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("baz", v.getArrayElement(1).asString());

        assertTrue(v.removeArrayElement(0));
        assertEquals("baz", v.getArrayElement(0).asString());

        assertTrue(v.removeArrayElement(0));
        assertTrue(v.getArraySize() == 0);
    }

    @Test
    public void testRecursiveList() {
        Object[] o1 = new Object[1];
        Object[] o2 = new Object[]{o1};
        o1[0] = o2;

        Value v1 = context.asValue(o1);
        Value v2 = context.asValue(o2);

        assertEquals(v1.as(List.class), v1.as(List.class));
        assertEquals(v2.as(List.class), v2.as(List.class));
        assertNotEquals(v1.as(List.class), (v2.as(List.class)));
        assertNotEquals(v1, v2);
        assertEquals(v1, v1);
        assertEquals(v2, v2);

        assertValueInContexts(v1);
        assertValueInContexts(v2);
    }

    public static class RecursiveObject {
        @HostAccess.Export public RecursiveObject rec;

    }

    @Test
    public void testRecursiveObject() {
        RecursiveObject o1 = new RecursiveObject();
        RecursiveObject o2 = new RecursiveObject();
        o1.rec = o2;
        o2.rec = o1;

        Value v1 = context.asValue(o1);
        Value v2 = context.asValue(o2);

        assertEquals(v1.as(Map.class), v1.as(Map.class));
        assertEquals(v2.as(Map.class), v2.as(Map.class));
        assertNotEquals(v1.as(Map.class), v2.as(Map.class));
        assertNotEquals(v1, v2);
        assertEquals(v1, v1);
        assertEquals(v2, v2);

        assertValueInContexts(v1);
        assertValueInContexts(v2);
    }

    @Test
    public void testPolyglotMapRemoveEntry() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", ProxyObject.fromMap(Collections.singletonMap("field", "value")));
        Value mapValue = context.asValue(ProxyObject.fromMap(map));
        Map<String, Object> membersMap = mapValue.as(new TypeLiteral<Map<String, Object>>() {
        });
        assertEquals(1, membersMap.size());
        Map.Entry<String, Object> membersEntry = membersMap.entrySet().iterator().next();
        membersMap.entrySet().remove(membersEntry);
        assertEquals(0, membersMap.size());

        List<Object> list = new ArrayList<>();
        list.add(Collections.singletonMap("field", "value"));
        Value arrayValue = context.asValue(ProxyArray.fromList(list));
        Map<Integer, Object> arrayMap = arrayValue.as(new TypeLiteral<Map<Integer, Object>>() {
        });
        assertEquals(1, arrayMap.size());
        Map.Entry<Integer, Object> arrayEntry = arrayMap.entrySet().iterator().next();
        arrayMap.entrySet().remove(arrayEntry);
        assertEquals(0, arrayMap.size());
    }

    /*
     * Referenced in proxy-config.json
     */
    @Implementable
    public interface EmptyInterface {

        void foo();

        void bar();

    }

    /*
     * Referenced in proxy-config.json
     */
    @FunctionalInterface
    public interface EmptyFunctionalInterface {

        void noop();

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static final class TestObject implements TruffleObject {

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        long getArraySize() {
            return 0L;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return false;
        }

    }

    @Test
    public void testAsValue() {
        for (Object number : NUMBERS) {
            assertValueInContexts(Value.asValue(number), Trait.NUMBER);
        }
        for (Object string : STRINGS) {
            assertValueInContexts(Value.asValue(string), Trait.STRING);
        }
        for (Object b : BOOLEANS) {
            assertValueInContexts(Value.asValue(b), Trait.BOOLEAN);
        }
        for (Object b : HOST_OBJECTS) {
            Value v = Value.asValue(b);
            assertTrue(v.isHostObject());
            assertValueInContexts(v);
        }
        Object o = new Object();
        Value v = Value.asValue(o);
        ProxyExecutable executable = new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return arguments[0].invokeMember("hashCode");
            }
        };
        assertEquals(o.hashCode(), context.asValue(executable).execute(v).asInt());

        Function<Object, Object> f = (a) -> Value.asValue(a).invokeMember("hashCode");
        assertEquals(o.hashCode(), context.asValue(f).execute(v).asInt());

        assertTrue(Value.asValue(null).isNull());
        assertNull(Value.asValue(null).as(Object.class));
        assertNull(Value.asValue(null).as(String.class));
        assertNull(Value.asValue(null).as(Integer.class));

        ProxyExecutable executableProxy = new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return null;
            }
        };
        assertTrue(Value.asValue(executableProxy).isProxyObject());
        assertSame(executableProxy, Value.asValue(executableProxy).asProxyObject());
        assertFalse(Value.asValue(executableProxy).isHostObject());
        // proxy executables (and others) created without a context cannot normally function
        assertFalse(Value.asValue(executableProxy).canExecute());

        Object hostWrapper = context.asValue(executableProxy).as(Function.class);
        assertTrue(Value.asValue(hostWrapper).canExecute());

        ProxyObject objectProxy = ProxyObject.fromMap(new HashMap<>());
        hostWrapper = context.asValue(objectProxy).as(Map.class);
        assertTrue(Value.asValue(hostWrapper).hasMembers());

        ProxyArray arrayProxy = ProxyArray.fromArray("");
        hostWrapper = context.asValue(arrayProxy).as(List.class);
        assertTrue(Value.asValue(hostWrapper).hasArrayElements());

        // but when migrated they can
        assertTrue(context.asValue(Value.asValue(executableProxy)).canExecute());
        context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                assertTrue(arguments[0].canExecute());
                return null;
            }
        }).execute(Value.asValue(executableProxy));

    }

    @Test
    public void testGuestObjectSharable() {
        Context context1 = context;
        Context context2 = Context.create();
        List<Object> nonSharables = new ArrayList<>();
        Object interopObject = new TestObject();
        nonSharables.add(interopObject);
        Value v = context1.asValue(interopObject);
        nonSharables.add(v.as(Map.class));
        nonSharables.add(v.as(EmptyInterface.class));
        nonSharables.add(v.as(List.class));
        nonSharables.add(v.as(Function.class));
        nonSharables.add(v.as(EmptyFunctionalInterface.class));
        nonSharables.add(v);
        nonSharables.add(Value.asValue(v));

        for (Object object : nonSharables) {
            Object nonSharableObject = object;
            if (nonSharableObject instanceof TruffleObject) {
                nonSharableObject = context1.asValue(nonSharableObject);
            }
            context2.getPolyglotBindings().putMember("foo", nonSharableObject);

            ProxyExecutable executable = new ProxyExecutable() {
                public Object execute(Value... arguments) {
                    return 42;
                }
            };
            assertEquals(42, context1.asValue(executable).execute(nonSharableObject).asInt());
            context2.asValue(executable).execute(nonSharableObject);
            nonSharableObject.toString(); // does not fails
            assertTrue(nonSharableObject.equals(nonSharableObject));
            assertTrue(nonSharableObject.hashCode() == nonSharableObject.hashCode());
        }
        context2.close();
    }

    @Test
    public void testHostObjectsAndPrimitivesSharable() {
        Context context1 = context;
        Context context2 = Context.create();

        List<Object> sharableObjects = new ArrayList<>();
        sharableObjects.addAll(Arrays.asList(HOST_OBJECTS));
        sharableObjects.addAll(Arrays.asList(NUMBERS));
        sharableObjects.addAll(Arrays.asList(BOOLEANS));
        sharableObjects.addAll(Arrays.asList(STRINGS));
        sharableObjects.addAll(Arrays.asList(ARRAYS));
        sharableObjects.addAll(Arrays.asList(testBuffers));

        expandObjectVariants(context1, sharableObjects);
        for (Object object : sharableObjects) {
            List<Object> variants = new ArrayList<>();
            variants.add(context1.asValue(object));
            variants.add(object);
            variants.add(Value.asValue(object));

            ProxyExecutable executable = new ProxyExecutable() {
                public Object execute(Value... arguments) {
                    return 42;
                }
            };

            for (Object variant : sharableObjects) {
                context2.getPolyglotBindings().putMember("foo", variant);
                assertEquals(42, context2.asValue(executable).execute(variant).asInt());
            }
            object.toString(); // does not fail
            assertTrue(object.equals(object));
            assertTrue(object.hashCode() == object.hashCode());
        }

        // special case for context less TruffleObject
        Value contextLessValue = Value.asValue(new TestObject());
        context1.getPolyglotBindings().putMember("foo", contextLessValue);
        context2.getPolyglotBindings().putMember("foo", contextLessValue);

        context2.close();

    }

    private static void expandObjectVariants(Context sourceContext, List<Object> objects) {
        for (Object object : objects.toArray()) {
            Value v = sourceContext.asValue(object);
            if (v.hasMembers()) {
                objects.add(v.as(Map.class));
                objects.add(v.as(EmptyInterface.class));
            }
            if (v.hasArrayElements()) {
                objects.add(v.as(List.class));
                objects.add(v.as(Object[].class));
            }
            if (v.canExecute()) {
                objects.add(v.as(Function.class));
                objects.add(v.as(EmptyFunctionalInterface.class));
            }

            // add the value itself
            objects.add(v);
            objects.add(Value.asValue(v)); // migrate from Value
            objects.add(Value.asValue(object)); // directly from object
        }
    }

    @Test
    public void testHostException() {
        Value exceptionValue = context.asValue(new RuntimeException("expected"));
        assertValueInContexts(exceptionValue, HOST_OBJECT, MEMBERS, EXCEPTION);
        try {
            exceptionValue.throwException();
            fail("should have thrown");
        } catch (PolyglotException expected) {
            // caught expected exception
            assertThat(expected.getMessage(), containsString("expected"));
            assertTrue("expected a host exception", expected.isHostException());
        } catch (UnsupportedOperationException unsupported) {
            throw new AssertionError(unsupported);
        }
        PolyglotException polyglotException = exceptionValue.as(PolyglotException.class);
        assertNotNull(polyglotException);
        assertThat(polyglotException.getMessage(), containsString("expected"));
    }

    @Test
    public void testGuestException() {
        Value exceptionValue = context.asValue(new ExceptionWrapper(new LanguageException("expected")));
        assertValueInContexts(exceptionValue, EXCEPTION);
        try {
            exceptionValue.throwException();
            fail("should have thrown");
        } catch (PolyglotException expected) {
            // caught expected exception
            assertThat(expected.getMessage(), containsString("expected"));
            assertTrue("expected a guest exception", expected.isGuestException());
        } catch (UnsupportedOperationException unsupported) {
            throw new AssertionError(unsupported);
        }
        PolyglotException polyglotException = exceptionValue.as(PolyglotException.class);
        assertNotNull(polyglotException);
        assertThat(polyglotException.getMessage(), containsString("expected"));
    }

    @Test
    public void testMetaObject() {
        Value v = context.asValue(OtherInterface0.class);
        assertTrue(v.isMetaObject());
        assertEquals(OtherInterface0.class.getTypeName(), v.getMetaQualifiedName());
        assertEquals(OtherInterface0.class.getSimpleName(), v.getMetaSimpleName());
        assertTrue(v.isMetaInstance(new OtherInterface0() {
            @Override
            public Object execute() {
                return null;
            }
        }));
        assertFalse(v.isMetaInstance(new OtherInterface1() {
            @Override
            public Object execute(Object s) {
                return null;
            }
        }));
    }

    @Test
    public void testMetaParents() {
        Value v = context.asValue(AbstractClass1.class);
        assertTrue(v.isMetaObject());
        assertTrue(v.hasMetaParents());
        Class<?>[] superTypes = new Class<?>[3];
        superTypes[0] = AbstractClass1.class.getSuperclass();
        superTypes[1] = AbstractClass1.class.getInterfaces()[0];
        superTypes[2] = AbstractClass1.class.getInterfaces()[1];

        Value metaParents = v.getMetaParents();
        assertTrue(metaParents.hasArrayElements());
        assertEquals(superTypes.length, metaParents.getArraySize());
        for (int i = 0; i < superTypes.length; i++) {
            assertEquals(superTypes[i].getSimpleName(), metaParents.getArrayElement(i).getMetaSimpleName());
            assertEquals(superTypes[i].getTypeName(), metaParents.getArrayElement(i).getMetaQualifiedName());
        }
    }

    @Test
    public void testMetaObjectNull() {
        Value nullValue = context.asValue(null);
        assertFalse(context.asValue(nullValue).isMetaObject());
        assertNull(context.asValue(nullValue).getMetaObject());

        assertFalse(context.asValue(null).isMetaObject());
        assertNull(context.asValue(null).getMetaObject());
    }

    @Test
    public void testIsMetaInstanceNull() {
        Value nullValue = context.asValue(null);
        assertFalse(context.asValue(Object.class).isMetaInstance(nullValue));
        assertFalse(context.asValue(Void.class).isMetaInstance(nullValue));

        assertFalse(context.asValue(Object.class).isMetaInstance(null));
        assertFalse(context.asValue(Void.class).isMetaInstance(null));
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StringWrapper implements TruffleObject {

        final String string;

        StringWrapper(String string) {
            this.string = string;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString() {
            return string;
        }

    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "number")
    static final class NumberWrapper implements TruffleObject {

        final Number number;

        NumberWrapper(Number number) {
            this.number = number;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class BooleanWrapper implements TruffleObject {

        final boolean delegate;

        BooleanWrapper(boolean delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isBoolean() {
            return true;
        }

        @ExportMessage
        boolean asBoolean() {
            return delegate;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExceptionWrapper implements TruffleObject {

        final RuntimeException delegate;

        ExceptionWrapper(RuntimeException delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isException() {
            return true;
        }

        @ExportMessage
        RuntimeException throwException() {
            throw delegate;
        }

    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static final class BooleanAndDelegate implements TruffleObject {

        final Object delegate;
        boolean booleanValue;

        BooleanAndDelegate(Object delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isBoolean() {
            return true;
        }

        @ExportMessage
        boolean asBoolean() {
            return booleanValue;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final String[] members;

        TestArray(String[] members) {
            this.members = members;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < members.length;
        }

        @ExportMessage
        String readArrayElement(long idx,
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter(node);
                throw InvalidArrayIndexException.create(idx);
            }
            return members[(int) idx];
        }
    }

    @SuppressWarnings("serial")
    private static final class LanguageException extends AbstractTruffleException {

        LanguageException(String message) {
            super(message);
        }
    }

    @Test
    public void testPrimitiveAndObject() {
        BooleanAndDelegate o = new BooleanAndDelegate(new TestArray(new String[0]));
        Value v = context.asValue(o);
        assertValueInContexts(v, ARRAY_ELEMENTS, ITERABLE, BOOLEAN);
    }

    @Test
    public void testBadNarrowingConversions() {
        final long[] badIndices = {-1L << 32, -1L << 32 + 1, 1L << 32, 1L << 32 + 1, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long index : badIndices) {
            Value list = context.asValue(new ArrayList<>(Arrays.asList(1, 2, 3)));
            AbstractPolyglotTest.assertFails(() -> list.getArrayElement(index), ArrayIndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> list.setArrayElement(index, 42), ArrayIndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> list.removeArrayElement(index), ArrayIndexOutOfBoundsException.class);

            Value array = context.asValue(new int[]{1, 2, 3});
            AbstractPolyglotTest.assertFails(() -> array.getArrayElement(index), ArrayIndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> array.setArrayElement(index, 42), ArrayIndexOutOfBoundsException.class);

            final Value buffer = context.asValue(ByteBuffer.wrap(new byte[]{1, 2, 3}));
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferByte(index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferByte(index, (byte) 42), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferShort(ByteOrder.LITTLE_ENDIAN, index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferShort(ByteOrder.LITTLE_ENDIAN, index, (short) 42), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferInt(ByteOrder.LITTLE_ENDIAN, index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferInt(ByteOrder.LITTLE_ENDIAN, index, 2), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferLong(ByteOrder.LITTLE_ENDIAN, index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferLong(ByteOrder.LITTLE_ENDIAN, index, 42), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferFloat(ByteOrder.LITTLE_ENDIAN, index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferFloat(ByteOrder.LITTLE_ENDIAN, index, 42), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.readBufferDouble(ByteOrder.LITTLE_ENDIAN, index), IndexOutOfBoundsException.class);
            AbstractPolyglotTest.assertFails(() -> buffer.writeBufferDouble(ByteOrder.LITTLE_ENDIAN, index, 42), IndexOutOfBoundsException.class);
        }
    }

    @Test
    public void testToString() {
        // test null context
        assertNotNull(Value.asValue("").toString());

        Context c = Context.create();

        Object[] values = new Object[]{
                        c.asValue(""), // bound value,
                        c.asValue(new Members()).as(Map.class),
                        c.asValue(new ArrayElements()).as(List.class),
                        c.asValue(new Executable()).as(Function.class),
                        c.asValue(new HashEntries()).as(Map.class),
        };

        for (Object v : values) {
            assertNotNull(v.toString());
        }

        c.close();

        for (Object v : values) {
            assertEquals("Error in toString(): Context is invalid or closed.", v.toString());
        }

    }

    @Test
    public void testProxyErrorInToString() {
        try (Context c = Context.create()) {

            Value v = c.asValue(new org.graalvm.polyglot.proxy.Proxy() {

                @Override
                public String toString() {
                    throw new UnsupportedOperationException("test message");
                }

            });

            AbstractPolyglotTest.assertFails(() -> v.toString(), PolyglotException.class, (e) -> {
                assertEquals("test message", e.getMessage());
                assertTrue(e.isHostException());
                assertTrue(e.asHostException() instanceof UnsupportedOperationException);
            });

        }
    }

    @Test
    public void testDisconnectedBigInteger() {
        Value val = Value.asValue(BigInteger.ONE);
        assertTrue(val.isNumber());
        assertTrue(val.fitsInByte());
        assertTrue(val.fitsInShort());
        assertTrue(val.fitsInInt());
        assertTrue(val.fitsInInt());
        assertTrue(val.fitsInBigInteger());
        assertTrue(val.fitsInFloat());
        assertTrue(val.fitsInDouble());
        assertEquals(1, val.asByte());
        assertEquals(1, val.asShort());
        assertEquals(1, val.asInt());
        assertEquals(1, val.asLong());
        assertEquals(BigInteger.ONE, val.asBigInteger());
        assertEquals(1.0f, val.asFloat(), 0.0000001f);
        assertEquals(1.0d, val.asDouble(), 0.000000000d);
        assertEquals((Byte) (byte) 1, val.as(byte.class));
        assertEquals((Byte) (byte) 1, val.as(Byte.class));
        assertEquals((Short) (short) 1, val.as(short.class));
        assertEquals((Short) (short) 1, val.as(Short.class));
        assertEquals((Integer) 1, val.as(int.class));
        assertEquals((Integer) 1, val.as(Integer.class));
        assertEquals((Long) 1L, val.as(long.class));
        assertEquals((Long) 1L, val.as(Long.class));
        assertEquals((Float) 1.0f, val.as(float.class));
        assertEquals((Float) 1.0f, val.as(Float.class));
        assertEquals((Double) 1.0d, val.as(double.class));
        assertEquals((Double) 1.0d, val.as(Double.class));
        assertEquals(BigInteger.ONE, val.as(BigInteger.class));
        assertEquals(BigInteger.ONE, val.as(Number.class));
        assertEquals((Character) (char) 1, val.as(char.class));
        assertEquals((Character) (char) 1, val.as(Character.class));
    }

}
