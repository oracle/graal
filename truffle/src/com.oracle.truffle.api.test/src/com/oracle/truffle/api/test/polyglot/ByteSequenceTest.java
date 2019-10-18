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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Test;

public class ByteSequenceTest {

    private static class ByteSequenceImpl implements ByteSequence {

        public int length() {
            return 4;
        }

        public byte byteAt(int index) {
            if (index < 0) {
                throw new IndexOutOfBoundsException();
            }
            if (index >= 4) {
                throw new IndexOutOfBoundsException();
            }
            return (byte) (index + 1);
        }

    }

    @Test
    public void testByteArraySequence() {
        ByteSequence sequence = ByteSequence.create(new byte[]{1, 2, 3, 4});
        assertTrue(sequence.bytes().allMatch((e) -> e > 0 && e <= 4));
        assertEquals(1, sequence.byteAt(0));
        assertEquals(2, sequence.byteAt(1));
        assertEquals(3, sequence.byteAt(2));
        assertEquals(4, sequence.byteAt(3));
        assertEquals(4, sequence.length());
        sequence.hashCode(); // not failing

        assertEquals(sequence, sequence);
        assertEquals(sequence, ByteSequence.create(new byte[]{1, 2, 3, 4}));
        assertNotNull(sequence.toString());

        assertArrayEquals(new byte[0], sequence.subSequence(0, 0).toByteArray());
        assertArrayEquals(new byte[]{1}, sequence.subSequence(0, 1).toByteArray());
        assertArrayEquals(new byte[]{4}, sequence.subSequence(3, 4).toByteArray());
        assertArrayEquals(new byte[]{2, 3, 4}, sequence.subSequence(1, 4).toByteArray());
        assertArrayEquals(new byte[]{1, 2, 3}, sequence.subSequence(0, 3).toByteArray());
        assertArrayEquals(new byte[0], sequence.subSequence(4, 4).toByteArray());

        assertEquals(sequence, sequence.subSequence(0, 4));
        assertEquals(sequence.subSequence(0, 1), sequence.subSequence(0, 1));
        assertNotEquals(sequence.subSequence(0, 1), sequence.subSequence(0, 2));
        assertNotEquals(sequence.subSequence(0, 1), sequence.subSequence(1, 2));

        ByteSequence other = ByteSequence.create(new byte[]{1, 2, 3, 4});
        assertEquals(sequence, other);
        assertEquals(sequence.subSequence(0, 1), other.subSequence(0, 1));
        assertNotEquals(sequence.subSequence(0, 1), other.subSequence(0, 2));
        assertNotEquals(sequence.subSequence(0, 1), other.subSequence(1, 2));

        ByteSequence otherClass = new ByteSequenceImpl();
        assertEquals(sequence, otherClass);
        assertEquals(sequence.subSequence(0, 1), otherClass.subSequence(0, 1));
        assertNotEquals(sequence.subSequence(0, 1), otherClass.subSequence(0, 2));
        assertNotEquals(sequence.subSequence(0, 1), otherClass.subSequence(1, 2));

        assertNotEquals(sequence, null);
        assertNotEquals(sequence, new Object());

        assertFails(() -> sequence.subSequence(1, 0), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.subSequence(-1, 1), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.subSequence(3, 5), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.byteAt(-1), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.byteAt(4), IndexOutOfBoundsException.class);
    }

    @Test
    public void testByteArraySubSequence() {
        ByteSequence sequence = ByteSequence.create(new byte[]{1, 2, 3, 4});
        assertArrayEquals(new byte[]{1, 2, 3, 4}, sequence.toByteArray());
        ByteSequence subSequence = sequence.subSequence(1, 4);
        assertArrayEquals(new byte[]{2, 3, 4}, subSequence.toByteArray());
        ByteSequence subSubSequence = subSequence.subSequence(1, 3);
        assertArrayEquals(new byte[]{3, 4}, subSubSequence.toByteArray());
    }

    @Test
    public void testEmptySequence() {
        ByteSequence sequence = ByteSequence.create(new byte[]{});
        assertEquals(0, sequence.length());
        assertArrayEquals(new byte[0], sequence.toByteArray());
        assertArrayEquals(new byte[0], sequence.subSequence(0, 0).toByteArray());
    }

    @Test
    public void testCustomByteSequence() {
        ByteSequence sequence = new ByteSequenceImpl();
        assertTrue(sequence.bytes().allMatch((e) -> e > 0 && e <= 4));
        assertEquals(1, sequence.byteAt(0));
        assertEquals(2, sequence.byteAt(1));
        assertEquals(3, sequence.byteAt(2));
        assertEquals(4, sequence.byteAt(3));
        assertEquals(4, sequence.length());
        sequence.hashCode(); // not failing

        assertNotEquals(sequence, ByteSequence.create(new byte[]{1, 2, 3, 4}));
        assertNotNull(sequence.toString());

        assertArrayEquals(new byte[0], sequence.subSequence(0, 0).toByteArray());
        assertArrayEquals(new byte[]{1}, sequence.subSequence(0, 1).toByteArray());
        assertArrayEquals(new byte[]{4}, sequence.subSequence(3, 4).toByteArray());
        assertArrayEquals(new byte[]{2, 3, 4}, sequence.subSequence(1, 4).toByteArray());
        assertArrayEquals(new byte[]{1, 2, 3}, sequence.subSequence(0, 3).toByteArray());
        assertArrayEquals(new byte[0], sequence.subSequence(4, 4).toByteArray());

        assertNotEquals(sequence, sequence.subSequence(0, 4));
        assertNotEquals(sequence.subSequence(0, 1), sequence.subSequence(0, 1));
        assertNotEquals(sequence.subSequence(0, 1), sequence.subSequence(0, 2));
        assertNotEquals(sequence.subSequence(0, 1), sequence.subSequence(1, 2));

        ByteSequence other = ByteSequence.create(new byte[]{1, 2, 3, 4});
        assertNotEquals(sequence, other);
        assertNotEquals(sequence.subSequence(0, 1), other.subSequence(0, 1));
        assertNotEquals(sequence.subSequence(0, 1), other.subSequence(0, 2));
        assertNotEquals(sequence.subSequence(0, 1), other.subSequence(1, 2));

        assertFails(() -> sequence.subSequence(1, 0), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.subSequence(-1, 1), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.subSequence(3, 5), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.byteAt(-1), IndexOutOfBoundsException.class);
        assertFails(() -> sequence.byteAt(4), IndexOutOfBoundsException.class);
    }

    private static void assertFails(Runnable r, Class<? extends Exception> type) {
        try {
            r.run();
            fail();
        } catch (Exception e) {
            assertTrue(e.getClass().getName(), type.isInstance(e));
        }

    }
}
