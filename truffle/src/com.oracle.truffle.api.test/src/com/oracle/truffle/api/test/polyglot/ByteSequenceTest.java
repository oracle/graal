/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
