/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

public class PrimitiveTypeTest {
    private TruffleObject boxedNumber;
    private TruffleObject boxedChar;
    private TruffleObject boxedString;
    private TruffleObject boxedBoolean;
    private TruffleObject boxedObject;

    @Before
    public void initObjects() {
        boxedNumber = JavaInterop.asTruffleObject(42);
        boxedChar = JavaInterop.asTruffleObject('A');
        boxedString = JavaInterop.asTruffleObject("Ahoj");
        boxedBoolean = JavaInterop.asTruffleObject(true);
        boxedObject = JavaInterop.asTruffleObject(new Object());
    }

    @Test
    public void convertToString() {
        String value = JavaInterop.asJavaObject(String.class, boxedString);
        assertEquals("Ahoj", value);
    }

    @Test
    public void convertToBoolean() {
        boolean value = JavaInterop.asJavaObject(boolean.class, boxedBoolean);
        assertEquals(true, value);
    }

    @Test
    public void convertToBooleanType() {
        Boolean value = JavaInterop.asJavaObject(Boolean.class, boxedBoolean);
        assertEquals(true, value);
    }

    @Test
    public void convertToChar() {
        char value = JavaInterop.asJavaObject(char.class, boxedChar);
        assertEquals('A', value);
    }

    @Test
    public void convertToCharacterType() {
        Character value = JavaInterop.asJavaObject(Character.class, boxedChar);
        assertEquals('A', value.charValue());
    }

    @Test
    public void convertToByte() {
        byte value = JavaInterop.asJavaObject(byte.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToByteType() {
        Byte value = JavaInterop.asJavaObject(Byte.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToShort() {
        short value = JavaInterop.asJavaObject(short.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToShortType() {
        Short value = JavaInterop.asJavaObject(Short.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToInt() {
        int value = JavaInterop.asJavaObject(int.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToIntegerType() {
        Integer value = JavaInterop.asJavaObject(Integer.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToLong() {
        long value = JavaInterop.asJavaObject(long.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToLongType() {
        Long value = JavaInterop.asJavaObject(Long.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToDouble() {
        double value = JavaInterop.asJavaObject(double.class, boxedNumber);
        assertEquals(42.0, value, 0.1);
    }

    @Test
    public void convertToDoubleType() {
        Double value = JavaInterop.asJavaObject(Double.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42.0, value, 0.1);
    }

    @Test
    public void convertToFloat() {
        float value = JavaInterop.asJavaObject(float.class, boxedNumber);
        assertEquals(42.0f, value, 0.1f);
    }

    @Test
    public void convertToFloatType() {
        Float value = JavaInterop.asJavaObject(Float.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42.0f, value, 0.1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertObjectToNumberDoesntWork() {
        Number value = JavaInterop.asJavaObject(Number.class, boxedObject);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertObjectToIntegerDoesntWork() {
        Integer value = JavaInterop.asJavaObject(Integer.class, boxedObject);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

}
