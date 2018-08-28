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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;

public class PrimitiveTypeTest extends ProxyLanguageEnvTest {
    private TruffleObject boxedNumber;
    private TruffleObject boxedChar;
    private TruffleObject boxedString;
    private TruffleObject boxedBoolean;
    private TruffleObject boxedObject;

    @Before
    public void initObjects() {
        boxedNumber = asTruffleObject(42);
        boxedChar = asTruffleObject('A');
        boxedString = asTruffleObject("Ahoj");
        boxedBoolean = asTruffleObject(true);
        boxedObject = asTruffleObject(new Object());
    }

    @Test
    public void convertToString() {
        String value = asJavaObject(String.class, boxedString);
        assertEquals("Ahoj", value);
    }

    @Test
    public void convertToBoolean() {
        boolean value = asJavaObject(boolean.class, boxedBoolean);
        assertEquals(true, value);
    }

    @Test
    public void convertToBooleanType() {
        Boolean value = asJavaObject(Boolean.class, boxedBoolean);
        assertEquals(true, value);
    }

    @Test
    public void convertToChar() {
        char value = asJavaObject(char.class, boxedChar);
        assertEquals('A', value);
    }

    @Test
    public void convertToCharacterType() {
        Character value = asJavaObject(Character.class, boxedChar);
        assertEquals('A', value.charValue());
    }

    @Test
    public void convertToByte() {
        byte value = asJavaObject(byte.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToByteType() {
        Byte value = asJavaObject(Byte.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToShort() {
        short value = asJavaObject(short.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToShortType() {
        Short value = asJavaObject(Short.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToInt() {
        int value = asJavaObject(int.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToIntegerType() {
        Integer value = asJavaObject(Integer.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToLong() {
        long value = asJavaObject(long.class, boxedNumber);
        assertEquals(42, value);
    }

    @Test
    public void convertToLongType() {
        Long value = asJavaObject(Long.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test
    public void convertToDouble() {
        double value = asJavaObject(double.class, boxedNumber);
        assertEquals(42.0, value, 0.1);
    }

    @Test
    public void convertToDoubleType() {
        Double value = asJavaObject(Double.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42.0, value, 0.1);
    }

    @Test
    public void convertToFloat() {
        float value = asJavaObject(float.class, boxedNumber);
        assertEquals(42.0f, value, 0.1f);
    }

    @Test
    public void convertToFloatType() {
        Float value = asJavaObject(Float.class, boxedNumber);
        assertNotNull("Some value computed", value);
        assertEquals(42.0f, value, 0.1f);
    }

    @Test(expected = ClassCastException.class)
    public void convertObjectToNumberDoesntWork() {
        Number value = asJavaObject(Number.class, boxedObject);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

    @Test(expected = ClassCastException.class)
    public void convertObjectToIntegerDoesntWork() {
        Integer value = asJavaObject(Integer.class, boxedObject);
        assertNotNull("Some value computed", value);
        assertEquals(42, value.intValue());
    }

}
