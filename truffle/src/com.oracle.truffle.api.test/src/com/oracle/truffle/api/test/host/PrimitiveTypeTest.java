/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
