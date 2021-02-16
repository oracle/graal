/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.utilities;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.BitSet;

import org.junit.Test;

import com.oracle.truffle.api.utilities.FinalBitSet;

public class FinalBitSetTest {

    @Test
    public void testEmpty() {
        FinalBitSet v = FinalBitSet.EMPTY;
        assertEquals(0, v.length());
        assertEquals(0, v.size());
        assertFalse(v.get(0));
        assertFalse(v.get(1));
        assertFalse(v.get(2));
        assertTrue(v.isEmpty());
        assertEquals(0, v.cardinality());
        assertEquals(-1, v.nextSetBit(0));
        assertEquals(0, v.nextClearBit(0));
        assertEquals("{}", v.toString());
        assertEquals(v.hashCode(), v.hashCode());
        assertFalse(v.equals(new Object()));
        assertArrayEquals(new long[]{}, v.toLongArray());
        assertEquals(v, v);
        assertFails(() -> v.get(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextSetBit(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextClearBit(-1), IndexOutOfBoundsException.class);

        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(BitSet.valueOf(new byte[0])));
        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(BitSet.valueOf(new byte[1])));
        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(BitSet.valueOf(new byte[2])));
        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(new long[0]));
        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(new long[1]));
        assertSame(FinalBitSet.EMPTY, FinalBitSet.valueOf(new long[2]));
    }

    @Test
    public void test1() {
        long[] array = new long[]{1};
        FinalBitSet v = FinalBitSet.valueOf(array);

        assertEquals(v, FinalBitSet.valueOf(array));
        long[] newArray = Arrays.copyOf(array, array.length);
        newArray[0]++;
        assertNotEquals(v, FinalBitSet.valueOf(newArray));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x0}));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x2}));

        assertEquals(1, v.length());
        assertEquals(64, v.size());
        assertTrue(v.get(0));
        assertFalse(v.get(1));
        assertFalse(v.get(2));
        assertFalse(v.isEmpty());
        assertEquals(1, v.cardinality());
        assertEquals(0, v.nextSetBit(0));
        assertEquals(1, v.nextClearBit(0));
        assertEquals("{0}", v.toString());
        assertEquals(v.hashCode(), v.hashCode());
        assertArrayEquals(array, v.toLongArray());
        assertEquals(v, v);
        assertFails(() -> v.get(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextSetBit(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextClearBit(-1), IndexOutOfBoundsException.class);
    }

    @Test
    public void test63() {
        long[] array = new long[]{0x8000_0000_0000_0000L};
        FinalBitSet v = FinalBitSet.valueOf(array);

        assertEquals(v, FinalBitSet.valueOf(array));
        long[] newArray = Arrays.copyOf(array, array.length);
        newArray[0]++;
        assertNotEquals(v, FinalBitSet.valueOf(newArray));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x0}));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x1}));

        assertEquals(64, v.length());
        assertEquals(64, v.size());
        assertTrue(v.get(63));
        assertFalse(v.get(62));
        assertFalse(v.get(64));
        assertFalse(v.isEmpty());
        assertEquals(1, v.cardinality());
        assertEquals(63, v.nextSetBit(0));
        assertEquals(64, v.nextClearBit(63));
        assertEquals("{63}", v.toString());
        assertEquals(v.hashCode(), v.hashCode());
        assertArrayEquals(array, v.toLongArray());
        assertEquals(v, v);
        assertFails(() -> v.get(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextSetBit(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextClearBit(-1), IndexOutOfBoundsException.class);
    }

    @Test
    public void test64() {
        long[] array = new long[]{0x0000_0000_0000_0000L, 1};
        FinalBitSet v = FinalBitSet.valueOf(array);

        assertEquals(v, FinalBitSet.valueOf(array));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x0}));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x1}));

        assertEquals(65, v.length());
        assertEquals(128, v.size());
        assertTrue(v.get(64));
        assertFalse(v.get(63));
        assertFalse(v.get(65));
        assertFalse(v.isEmpty());
        assertEquals(1, v.cardinality());
        assertEquals(64, v.nextSetBit(0));
        assertEquals(65, v.nextClearBit(64));
        assertEquals("{64}", v.toString());
        assertEquals(v.hashCode(), v.hashCode());
        assertArrayEquals(array, v.toLongArray());
        assertEquals(v, v);
        assertFails(() -> v.get(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextSetBit(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextClearBit(-1), IndexOutOfBoundsException.class);
    }

    @Test
    public void test1and63and64() {
        long[] array = new long[]{0x8000_0000_0000_0001L, 1};
        FinalBitSet v = FinalBitSet.valueOf(array);

        assertEquals(v, FinalBitSet.valueOf(array));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x0}));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x1}));
        assertNotEquals(v, FinalBitSet.valueOf(new long[]{0x0, 1}));

        assertEquals(65, v.length());
        assertEquals(128, v.size());
        assertTrue(v.get(0));
        assertFalse(v.get(1));
        assertTrue(v.get(64));
        assertTrue(v.get(63));
        assertFalse(v.get(65));
        assertFalse(v.isEmpty());
        assertEquals(3, v.cardinality());
        assertEquals(0, v.nextSetBit(0));
        assertEquals(63, v.nextSetBit(1));
        assertEquals(63, v.nextSetBit(63));
        assertEquals(64, v.nextSetBit(64));
        assertEquals(-1, v.nextSetBit(65));
        assertEquals(1, v.nextClearBit(0));
        assertEquals(65, v.nextClearBit(64));
        assertEquals("{0, 63, 64}", v.toString());
        assertEquals(v.hashCode(), v.hashCode());
        assertArrayEquals(array, v.toLongArray());
        assertEquals(v, v);
        assertFails(() -> v.get(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextSetBit(-1), IndexOutOfBoundsException.class);
        assertFails(() -> v.nextClearBit(-1), IndexOutOfBoundsException.class);
    }

}
