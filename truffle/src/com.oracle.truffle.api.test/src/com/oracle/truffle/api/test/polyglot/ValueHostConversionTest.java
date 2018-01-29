/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertUnsupported;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertValue;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.HOST_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.MEMBERS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NULL;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.PROXY_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.ValueAssert.Trait;

/**
 * Tests class for {@link Context#asValue(Object)}.
 */
public class ValueHostConversionTest {

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
    public void testNull() {
        Value v = context.asValue(null);
        assertTrue(v.isNull());
        assertTrue(v.isHostObject());

        assertUnsupported(v, NULL, HOST_OBJECT);
    }

    @Test
    public void testPolyglotValue() {
        Value value = context.asValue(42);
        assertSame(value, context.asValue(value));
    }

    @Test
    public void testNumbers() {
        assertOnlyNumber(context.asValue((byte) 42));
        assertOnlyNumber(context.asValue((short) 42));
        assertOnlyNumber(context.asValue(42));
        assertOnlyNumber(context.asValue(42L));
        assertOnlyNumber(context.asValue(42f));
        assertOnlyNumber(context.asValue(42d));
    }

    private static void assertOnlyNumber(Value v) {
        assertTrue(v.isNumber());
        assertUnsupported(v, NUMBER);
    }

    @Test
    public void testStrings() {
        assertOnlyString(context.asValue(""));
        assertOnlyString(context.asValue("a"));
        assertOnlyString(context.asValue('a'));
        assertOnlyString(context.asValue("foio"));
    }

    private static void assertOnlyString(Value v) {
        assertTrue(v.isString());
        assertUnsupported(v, STRING);
    }

    @Test
    public void testBooleans() {
        assertOnlyBoolean(context.asValue(false));
        assertOnlyBoolean(context.asValue(true));
    }

    private static void assertOnlyBoolean(Value v) {
        assertTrue(v.isBoolean());
        assertUnsupported(v, BOOLEAN);
    }

    @Test
    public void testProxyIdentityRestore() {
        Proxy proxy = new Proxy() {
        };
        Value value = context.asValue(proxy);
        assertUnsupported(value, PROXY_OBJECT);
        assertSame(proxy, context.asValue(value.as(Object.class)).asProxyObject());
    }

    @Test
    public void testHostObjectIdentityRestore() {
        Object obj = new Object();
        Value value = context.asValue(obj);

        assertTrue(value.isHostObject());
        assertSame(obj, context.asValue(value.as(Object.class)).asHostObject());
        assertUnsupported(value, HOST_OBJECT, MEMBERS);
    }

    /**
     * Tests basic examples from {@link Context#asValue(Object)}.
     */
    @Test
    public void testBasicExamples() {
        assertTrue(context.asValue(null).isNull());
        assertTrue(context.asValue(42).isNumber());
        assertTrue(context.asValue("42").isString());
        assertTrue(context.asValue('c').isString());
        assertTrue(context.asValue(new String[0]).hasArrayElements());
        assertTrue(context.asValue(new ArrayList<>()).isHostObject());
        assertTrue(context.asValue(new ArrayList<>()).hasArrayElements());
        assertTrue(context.asValue((Supplier<Integer>) () -> 42).execute().asInt() == 42);
    }

    public static class JavaRecord {
        public int x = 42;
        public double y = 42.0;

        public String name() {
            return "foo";
        }
    }

    /**
     * Tests advanced examples from {@link Context#asValue(Object)}.
     */
    @Test
    public void testAdvancedExamples() {
        Value record = context.asValue(new JavaRecord());
        assertTrue(record.getMember("x").asInt() == 42);
        assertTrue(record.getMember("y").asDouble() == 42.0d);
        assertTrue(record.getMember("name").execute().asString().equals("foo"));
    }

    @Test
    public void testClassProperties() {
        Value recordClass = context.asValue(JavaRecord.class);
        assertTrue(recordClass.getMemberKeys().isEmpty());
        assertTrue(recordClass.canInstantiate());

        Value newInstance = recordClass.newInstance();
        assertTrue(newInstance.isHostObject());
        assertTrue(newInstance.asHostObject() instanceof JavaRecord);

        assertTrue(newInstance.hasMember("getClass"));
        assertTrue(newInstance.getMember("getClass").execute().newInstance().asHostObject() instanceof JavaRecord);
        assertTrue(newInstance.getMetaObject().newInstance().asHostObject() instanceof JavaRecord);
        assertTrue(newInstance.getMetaObject().asHostObject() == JavaRecord.class);

        assertValue(context, recordClass, Trait.INSTANTIABLE, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testObjectProperties() {
        Value record = context.asValue(new JavaRecord());

        String[] publicKeys = new String[]{"x", "y", "name"};

        assertEquals(new HashSet<>(Arrays.asList(publicKeys)), record.getMemberKeys());

        assertTrue(record.hasMember("hashCode"));
        assertTrue(record.hasMember("equals"));
        assertTrue(record.hasMember("toString"));
        assertTrue(record.hasMember("getClass"));
        assertFalse(record.hasMember("clone"));
        assertTrue(record.hasMember("notify"));
        assertTrue(record.hasMember("wait"));
        assertTrue(record.hasMember("notifyAll"));

        assertValue(context, record, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertValue(context, record.getMetaObject(), Trait.INSTANTIABLE, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testStringInstantiaion() {
        Value stringClass = context.asValue(String.class);
        assertEquals("", stringClass.newInstance().asString());
        assertEquals("foo", stringClass.newInstance("foo").asString());
    }

    @SuppressWarnings("unused")
    public static class ByteHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }
    }

    @Test
    public void testByteHierarchy() {
        Value hierarchy = context.asValue(new ByteHierarchy()).getMember("hierarchy");
        final byte minValue = Byte.MIN_VALUE;
        final byte maxValue = Byte.MAX_VALUE;

        assertEquals("byte", hierarchy.execute(minValue).asString());
        assertEquals("byte", hierarchy.execute(maxValue).asString());
        assertEquals("byte", hierarchy.execute((short) minValue).asString());
        assertEquals("byte", hierarchy.execute((short) maxValue).asString());
        assertEquals("byte", hierarchy.execute((int) minValue).asString());
        assertEquals("byte", hierarchy.execute((int) maxValue).asString());
        assertEquals("byte", hierarchy.execute((long) minValue).asString());
        assertEquals("byte", hierarchy.execute((long) maxValue).asString());
        assertEquals("byte", hierarchy.execute((float) minValue).asString());
        assertEquals("byte", hierarchy.execute((float) maxValue).asString());
        assertEquals("byte", hierarchy.execute((double) minValue).asString());
        assertEquals("byte", hierarchy.execute((double) maxValue).asString());

        assertEquals("number", hierarchy.execute((short) (maxValue + 1)).asString());
        assertEquals("number", hierarchy.execute((short) (minValue - 1)).asString());
        assertEquals("number", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Long.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class ShortHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(short a) {
            return "short";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }

    }

    @Test
    public void testShortHierarchy() {
        Value hierarchy = context.asValue(new ShortHierarchy()).getMember("hierarchy");
        final short minValue = Short.MIN_VALUE;
        final short maxValue = Short.MAX_VALUE;

        assertEquals("short", hierarchy.execute(minValue).asString());
        assertEquals("short", hierarchy.execute(maxValue).asString());
        assertEquals("short", hierarchy.execute((int) minValue).asString());
        assertEquals("short", hierarchy.execute((int) maxValue).asString());
        assertEquals("short", hierarchy.execute((long) minValue).asString());
        assertEquals("short", hierarchy.execute((long) maxValue).asString());
        assertEquals("short", hierarchy.execute((float) minValue).asString());
        assertEquals("short", hierarchy.execute((float) maxValue).asString());
        assertEquals("short", hierarchy.execute((double) minValue).asString());
        assertEquals("short", hierarchy.execute((double) maxValue).asString());

        assertEquals("byte", hierarchy.execute(Byte.MIN_VALUE).asString());
        assertEquals("byte", hierarchy.execute(Byte.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Long.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class IntHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(short a) {
            return "short";
        }

        public String hierarchy(int a) {
            return "int";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }

    }

    @Test
    public void testIntHierarchy() {
        Value hierarchy = context.asValue(new IntHierarchy()).getMember("hierarchy");

        assertEquals("int", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute((long) Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute((long) Integer.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute((double) Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute((double) Integer.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute((float) -(Math.pow(2, 24) - 1)).asString());
        assertEquals("int", hierarchy.execute((float) +(Math.pow(2, 24) - 1)).asString());

        assertEquals("byte", hierarchy.execute(Byte.MIN_VALUE).asString());
        assertEquals("byte", hierarchy.execute(Byte.MAX_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MIN_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MAX_VALUE).asString());

        assertEquals("number", hierarchy.execute(Integer.MIN_VALUE - 1L).asString());
        assertEquals("number", hierarchy.execute(Integer.MAX_VALUE + 1L).asString());
        assertEquals("number", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Long.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute((float) Integer.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute((float) Integer.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class LongHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(short a) {
            return "short";
        }

        public String hierarchy(int a) {
            return "int";
        }

        public String hierarchy(long a) {
            return "long";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }

    }

    @Test
    public void testLongHierarchy() {
        Value hierarchy = context.asValue(new LongHierarchy()).getMember("hierarchy");

        assertEquals("long", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("long", hierarchy.execute(Long.MAX_VALUE).asString());

        // double
        assertEquals("long", hierarchy.execute((double) (Integer.MIN_VALUE - 1L)).asString());
        assertEquals("long", hierarchy.execute((double) (Integer.MAX_VALUE + 1L)).asString());
        double maxSafeInteger = Math.pow(2, 53) - 1;
        assertEquals("long", hierarchy.execute(-maxSafeInteger).asString());
        assertEquals("long", hierarchy.execute(+maxSafeInteger).asString());

        // large double values cannot be safely converted to integer due to lack of precision
        assertEquals("number", hierarchy.execute(-maxSafeInteger - 1).asString());
        assertEquals("number", hierarchy.execute(+maxSafeInteger + 1).asString());
        assertEquals("number", hierarchy.execute((double) Long.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute((double) Long.MAX_VALUE).asString());

        // float
        assertEquals("int", hierarchy.execute((float) -(Math.pow(2, 24) - 1)).asString());
        assertEquals("int", hierarchy.execute((float) +(Math.pow(2, 24) - 1)).asString());

        // large float values cannot be safely converted to integer due to lack of precision
        assertEquals("number", hierarchy.execute((float) Integer.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute((float) Integer.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute((float) Long.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute((float) Long.MAX_VALUE).asString());

        assertEquals("byte", hierarchy.execute(Byte.MIN_VALUE).asString());
        assertEquals("byte", hierarchy.execute(Byte.MAX_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MIN_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Float.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class FloatHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(short a) {
            return "short";
        }

        public String hierarchy(int a) {
            return "int";
        }

        public String hierarchy(long a) {
            return "long";
        }

        public String hierarchy(float a) {
            return "float";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }

    }

    @Test
    public void testFloatHierarchy() {
        Value hierarchy = context.asValue(new FloatHierarchy()).getMember("hierarchy");
        final float minValue = Float.MIN_VALUE;
        final float maxValue = Float.MAX_VALUE;

        assertEquals("float", hierarchy.execute(minValue).asString());
        assertEquals("float", hierarchy.execute(maxValue).asString());
        assertEquals("float", hierarchy.execute(Float.NaN).asString());
        assertEquals("float", hierarchy.execute(Float.NEGATIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(Float.POSITIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(-0.0f).asString());
        assertEquals("float", hierarchy.execute((double) minValue).asString());
        assertEquals("float", hierarchy.execute((double) maxValue).asString());
        assertEquals("float", hierarchy.execute(Double.NaN).asString());
        assertEquals("float", hierarchy.execute(Double.NEGATIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(Double.POSITIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(-0.0d).asString());

        assertEquals("byte", hierarchy.execute(Byte.MIN_VALUE).asString());
        assertEquals("byte", hierarchy.execute(Byte.MAX_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MIN_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("long", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("long", hierarchy.execute(Long.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MIN_VALUE).asString());
        assertEquals("number", hierarchy.execute(Double.MAX_VALUE).asString());
        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class DoubleHierarchy {

        public String hierarchy(byte a) {
            return "byte";
        }

        public String hierarchy(short a) {
            return "short";
        }

        public String hierarchy(int a) {
            return "int";
        }

        public String hierarchy(long a) {
            return "long";
        }

        public String hierarchy(float a) {
            return "float";
        }

        public String hierarchy(double a) {
            return "double";
        }

        public String hierarchy(Number a) {
            return "number";
        }

        public String hierarchy(Object a) {
            return "object";
        }

    }

    @Test
    public void testDoubleHierarchy() {
        Value hierarchy = context.asValue(new DoubleHierarchy()).getMember("hierarchy");
        final double minValue = Double.MIN_VALUE;
        final double maxValue = Double.MAX_VALUE;

        assertEquals("double", hierarchy.execute(minValue).asString());
        assertEquals("double", hierarchy.execute(maxValue).asString());

        assertEquals("byte", hierarchy.execute(Byte.MIN_VALUE).asString());
        assertEquals("byte", hierarchy.execute(Byte.MAX_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MIN_VALUE).asString());
        assertEquals("short", hierarchy.execute(Short.MAX_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MIN_VALUE).asString());
        assertEquals("int", hierarchy.execute(Integer.MAX_VALUE).asString());
        assertEquals("long", hierarchy.execute(Long.MIN_VALUE).asString());
        assertEquals("long", hierarchy.execute(Long.MAX_VALUE).asString());
        assertEquals("float", hierarchy.execute(Float.NaN).asString());
        assertEquals("float", hierarchy.execute(Float.NEGATIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(Float.POSITIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(-0.0f).asString());
        assertEquals("float", hierarchy.execute(Double.NaN).asString());
        assertEquals("float", hierarchy.execute(Double.NEGATIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(Double.POSITIVE_INFINITY).asString());
        assertEquals("float", hierarchy.execute(-0.0d).asString());

        assertEquals("number", hierarchy.execute(new BigDecimal("1")).asString());

        assertEquals("object", hierarchy.execute("").asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class CharHierarchy {

        public String hierarchy(char a) {
            return "char";
        }

        public String hierarchy(CharSequence a) {
            return "CharSequence";
        }

        public String hierarchy(Object a) {
            return "object";
        }
    }

    @Test
    public void testCharHierarchy() {
        Value hierarchy = context.asValue(new CharHierarchy()).getMember("hierarchy");

        assertEquals("char", hierarchy.execute('a').asString());
        assertEquals("char", hierarchy.execute("a").asString());

        assertEquals("CharSequence", hierarchy.execute("").asString());
        assertEquals("CharSequence", hierarchy.execute("foo").asString());
        assertEquals("CharSequence", hierarchy.execute(new StringBuilder("a")).asString());

        assertEquals("object", hierarchy.execute(42).asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @SuppressWarnings("unused")
    public static class StringHierarchy {

        public String hierarchy(char a) {
            return "char";
        }

        public String hierarchy(String a) {
            return "string";
        }

        public String hierarchy(CharSequence a) {
            return "CharSequence";
        }

        public String hierarchy(Object a) {
            return "object";
        }
    }

    @Test
    public void testStringHierarchy() {
        Value hierarchy = context.asValue(new StringHierarchy()).getMember("hierarchy");

        assertEquals("char", hierarchy.execute('a').asString());
        assertEquals("char", hierarchy.execute("a").asString());

        assertEquals("string", hierarchy.execute("").asString());
        assertEquals("string", hierarchy.execute("foo").asString());
        assertEquals("CharSequence", hierarchy.execute(new StringBuilder("a")).asString());

        assertEquals("object", hierarchy.execute(42).asString());
        assertEquals("object", hierarchy.execute(false).asString());
    }

    @Test
    public void testExceptionFrames1() {
        Value innerInner = context.asValue(new Function<Object, Object>() {
            public Object apply(Object t) {
                throw new RuntimeException("foobar");
            }
        });

        Value inner = context.asValue(new Function<Object, Object>() {
            public Object apply(Object t) {
                return innerInner.execute(t);
            }
        });

        Value outer = context.asValue(new Function<Object, Object>() {
            public Object apply(Object t) {
                return inner.execute(t);
            }
        });

        try {
            outer.execute(1);
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof RuntimeException);
            assertEquals("foobar", e.getMessage());
            Iterator<StackFrame> frameIterator = e.getPolyglotStackTrace().iterator();
            StackFrame frame;
            for (int i = 0; i < 3; i++) {
                frame = frameIterator.next();
                assertTrue(frame.isHostFrame());
                assertEquals("apply", frame.toHostFrame().getMethodName());
                frame = frameIterator.next();
                assertTrue(frame.isHostFrame());
                assertEquals("execute", frame.toHostFrame().getMethodName());
            }
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("testExceptionFrames1", frame.toHostFrame().getMethodName());
        }
    }

    public static class TestExceptionFrames2 {

        public void foo() {
            throw new RuntimeException("foo");
        }

    }

    @Test
    public void testExceptionFrames2() {
        Value value = context.asValue(new TestExceptionFrames2());
        try {
            value.getMember("foo").execute();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof RuntimeException);
            assertEquals("foo", e.getMessage());
            Iterator<StackFrame> frameIterator = e.getPolyglotStackTrace().iterator();
            StackFrame frame;
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("foo", frame.toHostFrame().getMethodName());
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("execute", frame.toHostFrame().getMethodName());
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("testExceptionFrames2", frame.toHostFrame().getMethodName());
        }

    }

    private interface TestExceptionFrames3 {

        void foo();

    }

    @Test
    public void testExceptionFrames3() {
        Value value = context.asValue(new TestExceptionFrames2());
        TestExceptionFrames3 f = value.as(TestExceptionFrames3.class);
        try {
            f.foo();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof RuntimeException);
            assertEquals("foo", e.getMessage());
            Iterator<StackFrame> frameIterator = e.getPolyglotStackTrace().iterator();
            StackFrame frame;
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("foo", frame.toHostFrame().getMethodName());
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("invoke", frame.toHostFrame().getMethodName());
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("foo", frame.toHostFrame().getMethodName());
            frame = frameIterator.next();
            assertTrue(frame.isHostFrame());
            assertEquals("testExceptionFrames3", frame.toHostFrame().getMethodName());
        }
    }

    public static class TestIllegalArgumentInt {

        public Object foo(int i) {
            return i;
        }

    }

    @Test
    public void testIllegalArgumentInt() {
        Value value = context.asValue(new TestIllegalArgumentInt());

        // valid cases
        assertEquals(42, value.getMember("foo").execute(42).asInt());
        assertEquals(42, value.getMember("foo").execute((byte) 42).asInt());
        assertEquals(42, value.getMember("foo").execute((short) 42).asInt());
        assertEquals(42, value.getMember("foo").execute((long) 42).asInt());
        assertEquals(42, value.getMember("foo").execute((float) 42).asInt());
        assertEquals(42, value.getMember("foo").execute((double) 42).asInt());

        // TODO currently broken -> fix
        // assertHostPolyglotException(() -> value.getMember("foo").execute((Object) null),
        // NullPointerException.class, "");
        // assertHostPolyglotException(() -> value.getMember("foo").execute(""),
        // ClassCastException.class,
        // "");
        // assertHostPolyglotException(() -> value.getMember("foo").execute(42.2d),
        // ClassCastException.class, "");
        // assertHostPolyglotException(() -> value.getMember("foo").execute(42.2f),
        // ClassCastException.class, "");
        // assertHostPolyglotException(() -> value.getMember("foo").execute(Float.NaN),
        // ClassCastException.class, "");
        // assertHostPolyglotException(() -> value.getMember("foo").execute(Double.NaN),
        // ClassCastException.class, "");
    }

    // private static void assertHostPolyglotException(Runnable r, Class<?> hostExceptionType,
    // String
    // message) {
    // try {
    // r.run();
    // } catch (PolyglotException e) {
    // assertTrue(e.isHostException());
    // assertTrue(e.asHostException().getClass().getName(),
    // hostExceptionType.isInstance(e.asHostException()));
    // assertEquals(message, e.getMessage());
    // }
    // }

}
