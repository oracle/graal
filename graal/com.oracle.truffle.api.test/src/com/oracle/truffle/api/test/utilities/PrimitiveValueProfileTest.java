/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.utilities;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.*;
import org.junit.experimental.theories.*;
import org.junit.runner.*;

import com.oracle.truffle.api.utilities.*;

@RunWith(Theories.class)
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
        profile = ValueProfile.createPrimitiveProfile();
    }

    @Test
    public void testInitial() {
        assertThat(profile.isGeneric(), is(false));
        assertThat(profile.isUninitialized(), is(true));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneObject(Object value) {
        Object result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoObject(Object value0, Object value1) {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));

        if (value0 == value1) {
            assertThat(profile.getCachedValue(), is(value0));
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileThreeObject(Object value0, Object value1, Object value2) {
        Object result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);
        Object result2 = profile.profile(value2);

        assertThat(result0, is(value0));
        assertThat(result1, is(value1));
        assertThat(result2, is(value2));

        if (value0 == value1 && value1 == value2) {
            assertThat(profile.getCachedValue(), is(value0));
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneByte(byte value) {
        byte result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoByte(byte value0, byte value1) {
        byte result0 = profile.profile(value0);
        byte result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Byte);
            assertEquals((byte) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Byte);
            assertEquals((byte) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneShort(short value) {
        short result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoShort(short value0, short value1) {
        short result0 = profile.profile(value0);
        short result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Short);
            assertEquals((short) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Short);
            assertEquals((short) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneInteger(int value) {
        int result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoInteger(int value0, int value1) {
        int result0 = profile.profile(value0);
        int result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Integer);
            assertEquals((int) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Integer);
            assertEquals((int) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneLong(long value) {
        long result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoLong(long value0, long value1) {
        long result0 = profile.profile(value0);
        long result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Long);
            assertEquals((long) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Long);
            assertEquals((long) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneFloat(float value) {
        float result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoFloat(float value0, float value1) {
        float result0 = profile.profile(value0);
        float result1 = profile.profile(value1);

        assertEquals(result0, value0, FLOAT_DELTA);
        assertEquals(result1, value1, FLOAT_DELTA);

        if (PrimitiveValueProfile.exactCompare(value0, value1)) {
            assertTrue(profile.getCachedValue() instanceof Float);
            assertEquals((float) profile.getCachedValue(), value0, FLOAT_DELTA);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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

        if (PrimitiveValueProfile.exactCompare(value0, value1) && PrimitiveValueProfile.exactCompare(value1, value2)) {
            assertTrue(profile.getCachedValue() instanceof Float);
            assertEquals((float) profile.getCachedValue(), value0, FLOAT_DELTA);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneDouble(double value) {
        double result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoDouble(double value0, double value1) {
        double result0 = profile.profile(value0);
        double result1 = profile.profile(value1);

        assertEquals(result0, value0, DOUBLE_DELTA);
        assertEquals(result1, value1, DOUBLE_DELTA);

        if (PrimitiveValueProfile.exactCompare(value0, value1)) {
            assertTrue(profile.getCachedValue() instanceof Double);
            assertEquals((double) profile.getCachedValue(), value0, DOUBLE_DELTA);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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

        if (PrimitiveValueProfile.exactCompare(value0, value1) && PrimitiveValueProfile.exactCompare(value1, value2)) {
            assertTrue(profile.getCachedValue() instanceof Double);
            assertEquals((double) profile.getCachedValue(), value0, DOUBLE_DELTA);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneBoolean(boolean value) {
        boolean result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoBoolean(boolean value0, boolean value1) {
        boolean result0 = profile.profile(value0);
        boolean result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Boolean);
            assertEquals((boolean) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Boolean);
            assertEquals((boolean) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileOneChar(char value) {
        char result = profile.profile(value);

        assertThat(result, is(value));
        assertEquals(profile.getCachedValue(), value);
        assertThat(profile.isUninitialized(), is(false));
        profile.toString(); // test that it is not crashing
    }

    @Theory
    public void testProfileTwoChar(char value0, char value1) {
        char result0 = profile.profile(value0);
        char result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertEquals(result1, value1);

        if (value0 == value1) {
            assertTrue(profile.getCachedValue() instanceof Character);
            assertEquals((char) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
            assertTrue(profile.getCachedValue() instanceof Character);
            assertEquals((char) profile.getCachedValue(), value0);
            assertThat(profile.isGeneric(), is(false));
        } else {
            assertThat(profile.isGeneric(), is(true));
        }
        assertThat(profile.isUninitialized(), is(false));
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
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedByte(byte value) {
        byte result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Byte);
        assertEquals((byte) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedByte(byte value) {
        Object result0 = profile.profile((Object) value);
        byte result1 = profile.profile(value);

        assertTrue(result0 instanceof Byte);
        assertEquals((byte) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedShort(short value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Short);
        assertEquals((short) result0, value);
        assertTrue(result1 instanceof Short);
        assertEquals((short) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedShort(short value) {
        short result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Short);
        assertEquals((short) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedShort(short value) {
        Object result0 = profile.profile((Object) value);
        short result1 = profile.profile(value);

        assertTrue(result0 instanceof Short);
        assertEquals((short) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedInt(int value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Integer);
        assertEquals((int) result0, value);
        assertTrue(result1 instanceof Integer);
        assertEquals((int) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedInt(int value) {
        int result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Integer);
        assertEquals((int) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedInt(int value) {
        Object result0 = profile.profile((Object) value);
        int result1 = profile.profile(value);

        assertTrue(result0 instanceof Integer);
        assertEquals((int) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedLong(long value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Long);
        assertEquals((long) result0, value);
        assertTrue(result1 instanceof Long);
        assertEquals((long) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedLong(long value) {
        long result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Long);
        assertEquals((long) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedLong(long value) {
        Object result0 = profile.profile((Object) value);
        long result1 = profile.profile(value);

        assertTrue(result0 instanceof Long);
        assertEquals((long) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedFloat(float value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Float);
        assertTrue(PrimitiveValueProfile.exactCompare((float) result0, value));
        assertTrue(result1 instanceof Float);
        assertTrue(PrimitiveValueProfile.exactCompare((float) result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedFloat(float value) {
        float result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertTrue(PrimitiveValueProfile.exactCompare(result0, value));
        assertTrue(result1 instanceof Float);
        assertTrue(PrimitiveValueProfile.exactCompare((float) result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedFloat(float value) {
        Object result0 = profile.profile((Object) value);
        float result1 = profile.profile(value);

        assertTrue(result0 instanceof Float);
        assertTrue(PrimitiveValueProfile.exactCompare((float) result0, value));
        assertTrue(PrimitiveValueProfile.exactCompare(result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedDouble(double value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Double);
        assertTrue(PrimitiveValueProfile.exactCompare((double) result0, value));
        assertTrue(result1 instanceof Double);
        assertTrue(PrimitiveValueProfile.exactCompare((double) result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedDouble(double value) {
        double result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertTrue(PrimitiveValueProfile.exactCompare(result0, value));
        assertTrue(result1 instanceof Double);
        assertTrue(PrimitiveValueProfile.exactCompare((double) result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedDouble(double value) {
        Object result0 = profile.profile((Object) value);
        double result1 = profile.profile(value);

        assertTrue(result0 instanceof Double);
        assertTrue(PrimitiveValueProfile.exactCompare((double) result0, value));
        assertTrue(PrimitiveValueProfile.exactCompare(result1, value));
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedBoolean(boolean value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Boolean);
        assertEquals((boolean) result0, value);
        assertTrue(result1 instanceof Boolean);
        assertEquals((boolean) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedBoolean(boolean value) {
        boolean result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Boolean);
        assertEquals((boolean) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedBoolean(boolean value) {
        Object result0 = profile.profile((Object) value);
        boolean result1 = profile.profile(value);

        assertTrue(result0 instanceof Boolean);
        assertEquals((boolean) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedBoxedChar(char value) {
        Object result0 = profile.profile((Object) value);
        Object result1 = profile.profile((Object) value);

        assertTrue(result0 instanceof Character);
        assertEquals((char) result0, value);
        assertTrue(result1 instanceof Character);
        assertEquals((char) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithUnboxedBoxedChar(char value) {
        char result0 = profile.profile(value);
        Object result1 = profile.profile((Object) value);

        assertEquals(result0, value);
        assertTrue(result1 instanceof Character);
        assertEquals((char) result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithBoxedUnboxedCharacter(char value) {
        Object result0 = profile.profile((Object) value);
        char result1 = profile.profile(value);

        assertTrue(result0 instanceof Character);
        assertEquals((char) result0, value);
        assertEquals(result1, value);
        assertFalse(profile.isUninitialized());
        assertFalse(profile.isGeneric());
    }

    @Theory
    public void testWithByteThenObject(byte value0, Object value1) {
        byte result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithShortThenObject(short value0, Object value1) {
        short result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithIntThenObject(int value0, Object value1) {
        int result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithLongThenObject(long value0, Object value1) {
        long result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithFloatThenObject(float value0, Object value1) {
        float result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertTrue(PrimitiveValueProfile.exactCompare(result0, value0));
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithDoubleThenObject(double value0, Object value1) {
        double result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertTrue(PrimitiveValueProfile.exactCompare(result0, value0));
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithBooleanThenObject(boolean value0, Object value1) {
        boolean result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Theory
    public void testWithCharThenObject(char value0, Object value1) {
        char result0 = profile.profile(value0);
        Object result1 = profile.profile(value1);

        assertEquals(result0, value0);
        assertSame(result1, value1);
        assertFalse(profile.isUninitialized());
        assertTrue(profile.isGeneric());
    }

    @Test
    public void testNegativeZeroFloat() {
        profile.profile(-0.0f);
        profile.profile(+0.0f);
        assertThat(profile.isGeneric(), is(true));
    }

    @Test
    public void testNegativeZeroDouble() {
        profile.profile(-0.0);
        profile.profile(+0.0);
        assertThat(profile.isGeneric(), is(true));
    }

}
