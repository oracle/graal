/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

@SuppressWarnings("deprecation")
public class InteropDefaultsTest extends InteropLibraryBaseTest {

    public static class TestInterop1 {
    }

    @Test
    public void testBooleanDefault() throws InteropException {
        assertBoolean(true, true);
        assertBoolean(false, false);
    }

    private void assertBoolean(Object v, boolean expected) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isBoolean(v));
        assertEquals(expected, library.asBoolean(v));

        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoString(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

    }

    @Test
    public void testByteDefault() throws InteropException {
        assertNumber(Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((byte) 0, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE, true, true, true, true, true, true);
    }

    @Test
    public void testShortDefault() throws InteropException {
        assertNumber(Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((short) (Byte.MIN_VALUE - 1), false, true, true, true, true, true);
        assertNumber((short) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((short) 0, true, true, true, true, true, true);
        assertNumber((short) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((short) (Byte.MAX_VALUE + 1), false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE, false, true, true, true, true, true);
    }

    @Test
    public void testIntDefault() throws InteropException {
        assertNumber(Integer.MIN_VALUE, false, false, true, true, false, true);
        assertNumber(Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((int) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber(Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((int) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0, true, true, true, true, true, true);
        assertNumber((int) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((int) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber(Integer.MAX_VALUE, false, false, true, true, false, true);
    }

    @Test
    public void testLongDefault() throws InteropException {
        assertNumber(Long.MIN_VALUE, false, false, false, true, false, false);
        assertNumber((long) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((long) Integer.MIN_VALUE, false, false, true, true, false, true);
        assertNumber((long) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0L, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((long) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber(Long.MAX_VALUE, false, false, false, true, false, false);
    }

    @Test
    public void testFloatDefault() throws InteropException {
        assertNumber(Float.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((float) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE - 1, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((float) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0f, false, false, false, false, true, true);
        assertNumber(0.0f, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((float) Integer.MAX_VALUE, false, false, false, false, true, true);
        assertNumber((float) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Float.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Float.NaN, false, false, false, false, true, true);
        assertNumber(Float.MIN_VALUE, false, false, false, false, true, true);
        assertNumber(Float.MIN_NORMAL, false, false, false, false, true, true);
        assertNumber(Float.MAX_VALUE, false, false, false, false, true, true);
    }

    @Test
    public void testDoubleDefault() throws InteropException {
        assertNumber(Double.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((double) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((double) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((double) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0d, false, false, false, false, true, true);
        assertNumber(0.0d, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((double) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber((double) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Double.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Double.NaN, false, false, false, false, true, true);
        assertNumber(Double.MIN_VALUE, false, false, false, false, false, true);
        assertNumber(Double.MIN_NORMAL, false, false, false, false, false, true);
        assertNumber(Double.MAX_VALUE, false, false, false, false, false, true);
    }

    @Test
    public void testStringDefaults() throws InteropException {
        assertString("foo", "foo");
        assertString("bar", "bar");
    }

    @Test
    public void testCharacterDefaults() throws InteropException {
        assertString('a', "a");
        assertString('b', "b");
    }

    private void assertString(Object v, String expectedString) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isString(v));
        assertEquals(expectedString, library.asString(v));

        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

    }

    private void assertNumber(Object v, boolean supportsByte, boolean supportsShort,
                    boolean supportsInt, boolean supportsLong, boolean supportsFloat, boolean supportsDouble) throws InteropException {

        Object expectedValue = v;

        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        assertTrue(l.isNumber(v));

        assertEquals(supportsByte, l.fitsInByte(v));
        assertEquals(supportsShort, l.fitsInShort(v));
        assertEquals(supportsInt, l.fitsInInt(v));
        assertEquals(supportsLong, l.fitsInLong(v));
        assertEquals(supportsFloat, l.fitsInFloat(v));
        assertEquals(supportsDouble, l.fitsInDouble(v));

        if (supportsByte) {
            assertEquals(((Number) expectedValue).byteValue(), l.asByte(v));
        } else {
            assertUnsupported(() -> l.asByte(v));
        }
        if (supportsShort) {
            assertEquals(((Number) expectedValue).shortValue(), l.asShort(v));
        } else {
            assertUnsupported(() -> l.asShort(v));
        }
        if (supportsInt) {
            assertEquals(((Number) expectedValue).intValue(), l.asInt(v));
        } else {
            assertUnsupported(() -> l.asInt(v));
        }
        if (supportsLong) {
            assertEquals(((Number) expectedValue).longValue(), l.asLong(v));
        } else {
            assertUnsupported(() -> l.asLong(v));
        }
        if (supportsFloat) {
            assertEquals(((Number) expectedValue).floatValue(), l.asFloat(v), 0);
        } else {
            assertUnsupported(() -> l.asFloat(v));
        }
        if (supportsDouble) {
            assertEquals(((Number) expectedValue).doubleValue(), l.asDouble(v), 0);
        } else {
            assertUnsupported(() -> l.asDouble(v));
        }

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoString(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

    }

}
