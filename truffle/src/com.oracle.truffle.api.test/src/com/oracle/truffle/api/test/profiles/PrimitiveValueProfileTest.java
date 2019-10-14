/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.profiles;

import static com.oracle.truffle.api.test.ReflectionUtils.getStaticField;
import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import java.util.Objects;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.profiles.PrimitiveValueProfile;

@RunWith(Theories.class)
@SuppressWarnings("deprecation")
public class PrimitiveValueProfileTest {

    @DataPoint public static final String O1 = new String();
    @DataPoint public static final String O2 = O1;
    @DataPoint public static final Object O3 = new Object();
    @DataPoint public static final Object O4 = null;

    @DataPoint public static final byte B1 = Byte.MIN_VALUE;
    @DataPoint public static final byte B2 = 0;
    @DataPoint public static final byte B3 = 14;
    @DataPoint public static final byte B4 = Byte.MAX_VALUE;

    @DataPoint public static final short S1 = Short.MIN_VALUE;
    @DataPoint public static final short S2 = 0;
    @DataPoint public static final short S3 = 14;
    @DataPoint public static final short S4 = Short.MAX_VALUE;

    @DataPoint public static final int I1 = Integer.MIN_VALUE;
    @DataPoint public static final int I2 = 0;
    @DataPoint public static final int I3 = 14;
    @DataPoint public static final int I4 = Integer.MAX_VALUE;

    @DataPoint public static final long L1 = Long.MIN_VALUE;
    @DataPoint public static final long L2 = 0;
    @DataPoint public static final long L3 = 14;
    @DataPoint public static final long L4 = Long.MAX_VALUE;

    @DataPoint public static final float F1 = Float.MIN_VALUE;
    @DataPoint public static final float F2 = -0.0f;
    @DataPoint public static final float F3 = +0.0f;
    @DataPoint public static final float F4 = 14.5f;
    @DataPoint public static final float F5 = Float.MAX_VALUE;

    @DataPoint public static final double D1 = Double.MIN_VALUE;
    @DataPoint public static final double D2 = -0.0;
    @DataPoint public static final double D3 = +0.0;
    @DataPoint public static final double D4 = 14.5;
    @DataPoint public static final double D5 = Double.MAX_VALUE;

    @DataPoint public static final boolean T1 = false;
    @DataPoint public static final boolean T2 = true;

    @DataPoint public static final char C1 = Character.MIN_VALUE;
    @DataPoint public static final char C2 = 0;
    @DataPoint public static final char C3 = 14;
    @DataPoint public static final char C4 = Character.MAX_VALUE;

    private static final float FLOAT_DELTA = 0.00001f;
    private static final double DOUBLE_DELTA = 0.00001;

    private PrimitiveValueProfile profile;

    @Before
    public void create() {
        profile = (PrimitiveValueProfile) invokeStatic(loadRelative(PrimitiveValueProfileTest.class, "PrimitiveValueProfile$Enabled"), "create");
    }

    private static boolean isGeneric(PrimitiveValueProfile profile) {
        return (boolean) invoke(profile, "isGeneric");
    }

    private static boolean isUninitialized(PrimitiveValueProfile profile) {
        return (boolean) invoke(profile, "isUninitialized");
    }

    private static Object getCachedValue(PrimitiveValueProfile profile) {
        return invoke(profile, "getCachedValue");
    }

    @Test
    public void testInitial() {
        assertThat(isGeneric(profile), is(false));
        assertThat(isUninitialized(profile), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneObject(Object value) {
        Object result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    private static boolean primitiveEquals(Object value0, Object value1) {
        return Objects.equals(value0, value1);
    }

    @Theory
    public void testProfileTwoObject(Object value0, Object value1) {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        if (primitiveEquals(value0, value1)) {
            assertThat(getCachedValue(profile), is(value0));
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneByte(byte value) {
        byte result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoByte(byte value0, byte value1) {
        byte result0 = profile.profile(value0);
        byte result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Byte);
            assertEquals((byte) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeByte(byte value0, byte value1, byte value2) {
        byte result0 = profile.profile(value0);
        byte result1 = profile.profile(value1);
        byte result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Byte);
            assertEquals((byte) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneShort(short value) {
        short result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoShort(short value0, short value1) {
        short result0 = profile.profile(value0);
        short result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Short);
            assertEquals((short) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeShort(short value0, short value1, short value2) {
        short result0 = profile.profile(value0);
        short result1 = profile.profile(value1);
        short result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Short);
            assertEquals((short) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneInteger(int value) {
        int result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoInteger(int value0, int value1) {
        int result0 = profile.profile(value0);
        int result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Integer);
            assertEquals((int) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeInteger(int value0, int value1, int value2) {
        int result0 = profile.profile(value0);
        int result1 = profile.profile(value1);
        int result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Integer);
            assertEquals((int) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneLong(long value) {
        long result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoLong(long value0, long value1) {
        long result0 = profile.profile(value0);
        long result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Long);
            assertEquals((long) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeLong(long value0, long value1, long value2) {
        long result0 = profile.profile(value0);
        long result1 = profile.profile(value1);
        long result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Long);
            assertEquals((long) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneFloat(float value) {
        float result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoFloat(float value0, float value1) {
        float result0 = profile.profile(value0);
        float result1 = profile.profile(value1);

        assertEquals(result0, value0, FLOAT_DELTA);
        assertEquals(result1, value1, FLOAT_DELTA);

        if (exactCompare(value0, value1)) {
            assertTrue(getCachedValue(profile) instanceof Float);
            assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeFloat(float value0, float value1, float value2) {
        float result0 = profile.profile(value0);
        float result1 = profile.profile(value1);
        float result2 = profile.profile(value2);

        assertEquals(result0, value0, FLOAT_DELTA);
        assertEquals(result1, value1, FLOAT_DELTA);
        assertEquals(result2, value2, FLOAT_DELTA);

        if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
            assertTrue(getCachedValue(profile) instanceof Float);
            assertEquals((float) getCachedValue(profile), value0, FLOAT_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneDouble(double value) {
        double result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoDouble(double value0, double value1) {
        double result0 = profile.profile(value0);
        double result1 = profile.profile(value1);

        assertEquals(result0, value0, DOUBLE_DELTA);
        assertEquals(result1, value1, DOUBLE_DELTA);

        if (exactCompare(value0, value1)) {
            assertTrue(getCachedValue(profile) instanceof Double);
            assertEquals((double) getCachedValue(profile), value0, DOUBLE_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeDouble(double value0, double value1, double value2) {
        double result0 = profile.profile(value0);
        double result1 = profile.profile(value1);
        double result2 = profile.profile(value2);

        assertEquals(result0, value0, DOUBLE_DELTA);
        assertEquals(result1, value1, DOUBLE_DELTA);
        assertEquals(result2, value2, DOUBLE_DELTA);

        if (exactCompare(value0, value1) && exactCompare(value1, value2)) {
            assertTrue(getCachedValue(profile) instanceof Double);
            assertEquals((double) getCachedValue(profile), value0, DOUBLE_DELTA);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneBoolean(boolean value) {
        boolean result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoBoolean(boolean value0, boolean value1) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Boolean);
            assertEquals((boolean) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeBoolean(boolean value0, boolean value1, boolean value2) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);
        boolean result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Boolean);
            assertEquals((boolean) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneChar(char value) {
        char result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(getCachedValue(profile), value);
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoChar(char value0, char value1) {
        char result0 = profile.profile(value0);
        char result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(getCachedValue(profile) instanceof Character);
            assertEquals((char) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeChar(char value0, char value1, char value2) {
        char result0 = profile.profile(value0);
        char result1 = profile.profile(value1);
        char result2 = profile.profile(value2);

        assertEquals(result0, value0);
        assertEquals(result1, value1);
        assertEquals(result2, value2);

        if (value0 == value1 && value1 == value2) {
            assertTrue(getCachedValue(profile) instanceof Character);
            assertEquals((char) getCachedValue(profile), value0);
            assertThat(isGeneric(profile), is(false));
        } else {
            assertThat(isGeneric(profile), is(true));
        }
        assertThat(isUninitialized(profile), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testWithBoxedBoxedByte(byte value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Byte);
        assertEquals((byte) result0, value);
        assertTrue(result1 instanceof Byte);
        assertEquals((byte) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedByte(byte value) {
        byte result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Byte);
        assertEquals((byte) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedByte(byte value) {
        Object result0 = profile.profile((Object) value);
        byte result1 = profile.profile(value);

        assertTrue(result0 instanceof Byte);
        assertEquals((byte) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedShort(short value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Short);
        assertEquals((short) result0, value);
        assertTrue(result1 instanceof Short);
        assertEquals((short) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedShort(short value) {
        short result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Short);
        assertEquals((short) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedShort(short value) {
        Object result0 = profile.profile((Object) value);
        short result1 = profile.profile(value);

        assertTrue(result0 instanceof Short);
        assertEquals((short) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedInt(int value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Integer);
        assertEquals((int) result0, value);
        assertTrue(result1 instanceof Integer);
        assertEquals((int) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedInt(int value) {
        int result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Integer);
        assertEquals((int) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedInt(int value) {
        Object result0 = profile.profile((Object) value);
        int result1 = profile.profile(value);

        assertTrue(result0 instanceof Integer);
        assertEquals((int) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedLong(long value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Long);
        assertEquals((long) result0, value);
        assertTrue(result1 instanceof Long);
        assertEquals((long) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedLong(long value) {
        long result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Long);
        assertEquals((long) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedLong(long value) {
        Object result0 = profile.profile((Object) value);
        long result1 = profile.profile(value);

        assertTrue(result0 instanceof Long);
        assertEquals((long) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedFloat(float value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Float);
        assertTrue(exactCompare((float) result0, value));
        assertTrue(result1 instanceof Float);
        assertTrue(exactCompare((float) result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedFloat(float value) {
        float result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertTrue(exactCompare(result0, value));
        assertTrue(result1 instanceof Float);
        assertTrue(exactCompare((float) result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedFloat(float value) {
        Object result0 = profile.profile((Object) value);
        float result1 = profile.profile(value);

        assertTrue(result0 instanceof Float);
        assertTrue(exactCompare((float) result0, value));
        assertTrue(exactCompare(result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedDouble(double value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Double);
        assertTrue(exactCompare((double) result0, value));
        assertTrue(result1 instanceof Double);
        assertTrue(exactCompare((double) result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedDouble(double value) {
        double result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertTrue(exactCompare(result0, value));
        assertTrue(result1 instanceof Double);
        assertTrue(exactCompare((double) result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedDouble(double value) {
        Object result0 = profile.profile((Object) value);
        double result1 = profile.profile(value);

        assertTrue(result0 instanceof Double);
        assertTrue(exactCompare((double) result0, value));
        assertTrue(exactCompare(result1, value));
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedBoolean(boolean value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Boolean);
        assertEquals((boolean) result0, value);
        assertTrue(result1 instanceof Boolean);
        assertEquals((boolean) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedBoolean(boolean value) {
        boolean result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Boolean);
        assertEquals((boolean) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedBoolean(boolean value) {
        Object result0 = profile.profile((Object) value);
        boolean result1 = profile.profile(value);

        assertTrue(result0 instanceof Boolean);
        assertEquals((boolean) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedBoxedChar(char value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Character);
        assertEquals((char) result0, value);
        assertTrue(result1 instanceof Character);
        assertEquals((char) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithUnboxedBoxedChar(char value) {
        char result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Character);
        assertEquals((char) result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithBoxedUnboxedCharacter(char value) {
        Object result0 = profile.profile((Object) value);
        char result1 = profile.profile(value);

        assertTrue(result0 instanceof Character);
        assertEquals((char) result0, value);
        assertEquals(result1, value);
        assertFalse(isUninitialized(profile));
        assertFalse(isGeneric(profile));
    }

    @Theory
    public void testWithByteThenObject(byte value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        byte result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithShortThenObject(short value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        short result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithIntThenObject(int value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        int result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithLongThenObject(long value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        long result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithFloatThenObject(float value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        float result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertTrue(exactCompare(result0, value0));
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithDoubleThenObject(double value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        double result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertTrue(exactCompare(result0, value0));
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithBooleanThenObject(boolean value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        boolean result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Theory
    public void testWithCharThenObject(char value0, Object value1) {
        assumeThat(value0, is(not(equalTo(value1))));

        char result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(isUninitialized(profile));
        assertTrue(isGeneric(profile));
    }

    @Test
    public void testNegativeZeroFloat() {
        profile.profile(-0.0f);
        profile.profile(+0.0f);
        assertThat(isGeneric(profile), is(true));
    }

    @Test
    public void testNegativeZeroDouble() {
        profile.profile(-0.0);
        profile.profile(+0.0);
        assertThat(isGeneric(profile), is(true));
    }

    @Test
    public void testDisabled() {
        PrimitiveValueProfile p = (PrimitiveValueProfile) getStaticField(loadRelative(PrimitiveValueProfileTest.class, "PrimitiveValueProfile$Disabled"), "INSTANCE");
        assertThat(p.profile(O1), is(O1));
        assertThat(p.profile(B1), is(B1));
        assertThat(p.profile(S1), is(S1));
        assertThat(p.profile(I1), is(I1));
        assertThat(p.profile(L1), is(L1));
        assertThat(p.profile(F1), is(F1));
        assertThat(p.profile(D1), is(D1));
        assertThat(p.profile(T1), is(T1));
        assertThat(p.profile(C1), is(C1));
        p.toString(); // test that it is not crashing
    }

    static boolean exactCompare(float a, float b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    static boolean exactCompare(double a, double b) {
        /*
         * -0.0 == 0.0, but you can tell the difference through other means, so we need to
         * differentiate.
         */
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

}
