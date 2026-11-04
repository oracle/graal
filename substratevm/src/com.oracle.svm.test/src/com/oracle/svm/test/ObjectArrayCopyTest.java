/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link System#arraycopy} and {@link Object#clone} on object arrays, including copies that
 * store references to young objects into old arrays. A missing GC barrier shows up as a lost or
 * corrupted element after the following collections (and as a remembered set error when the image
 * is built with {@code -H:+VerifyHeap}).
 */
public class ObjectArrayCopyTest {

    /** Covers the per-reference path, the bulk path and more than one bulk chunk. */
    private static final int[] LENGTHS = {0, 1, 2, 7, 15, 16, 17, 31, 32, 33, 64, 1000, 16 * 1024 + 3, 40 * 1024 + 5};

    private static final class Box {
        final int value;

        Box(int value) {
            this.value = value;
        }
    }

    private static Box[] boxes(int length, int base) {
        Box[] result = new Box[length];
        for (int i = 0; i < length; i++) {
            result[i] = new Box(base + i);
        }
        return result;
    }

    private static void assertBoxes(Box[] array, int from, int length, int base) {
        for (int i = 0; i < length; i++) {
            Box b = array[from + i];
            if (b == null || b.value != base + i) {
                Assert.fail("element " + (from + i) + ": expected " + (base + i) + " but was " + (b == null ? "null" : b.value));
            }
        }
    }

    /** Allocates enough garbage to trigger some incremental (young) collections. */
    private static Object churn() {
        Object last = null;
        for (int i = 0; i < 200_000; i++) {
            last = new byte[256];
        }
        return last;
    }

    @Test
    public void testForward() {
        for (int length : LENGTHS) {
            Box[] src = boxes(length, 0);
            Box[] dst = new Box[length + 10];
            System.arraycopy(src, 0, dst, 5, length);
            assertBoxes(dst, 5, length, 0);
            Assert.assertNull(dst[4]);
            Assert.assertNull(dst[length + 5]);
        }
    }

    @Test
    public void testOverlappingBackward() {
        for (int length : LENGTHS) {
            Box[] array = boxes(length + 7, 0);
            /* fromIndex < toIndex: must copy from the end. */
            System.arraycopy(array, 0, array, 7, length);
            assertBoxes(array, 7, length, 0);
            assertBoxes(array, 0, Math.min(7, length), 0);
        }
    }

    @Test
    public void testOverlappingForward() {
        for (int length : LENGTHS) {
            Box[] array = boxes(length + 7, 0);
            /* fromIndex > toIndex: must copy from the start. */
            System.arraycopy(array, 7, array, 0, length);
            assertBoxes(array, 0, length, 7);
        }
    }

    /**
     * With 4-byte references, the start address of a copy is 8-byte aligned or not depending on
     * the index parity, so every parity combination of source and destination takes a different
     * path (8-byte moves, one 4-byte move and then 8-byte moves, or 4-byte moves only).
     */
    @Test
    public void testAllStartAlignments() {
        for (int length : LENGTHS) {
            for (int fromIndex = 0; fromIndex < 4; fromIndex++) {
                for (int toIndex = 0; toIndex < 4; toIndex++) {
                    Box[] src = boxes(fromIndex + length, -fromIndex);
                    Box[] dst = new Box[toIndex + length + 1];
                    System.arraycopy(src, fromIndex, dst, toIndex, length);
                    assertBoxes(dst, toIndex, length, 0);
                    for (int i = 0; i < toIndex; i++) {
                        Assert.assertNull(dst[i]);
                    }
                    Assert.assertNull(dst[toIndex + length]);

                    /* Overlapping, both directions. */
                    Box[] array = boxes(length + 4, 0);
                    System.arraycopy(array, fromIndex, array, toIndex, length);
                    assertBoxes(array, toIndex, length, fromIndex);
                }
            }
        }
    }

    @Test
    public void testSubtypeArrays() {
        for (int length : LENGTHS) {
            Box[] src = boxes(length, 0);
            Object[] dst = new Object[length];
            System.arraycopy(src, 0, dst, 0, length);
            for (int i = 0; i < length; i++) {
                Assert.assertSame(src[i], dst[i]);
            }
        }
    }

    @Test
    public void testYoungIntoOld() {
        for (int length : LENGTHS) {
            /* Make the destination old. */
            Box[] dst = new Box[length];
            System.gc();
            System.gc();

            /* Store young objects into the old array, then collect only the young generation. */
            Box[] src = boxes(length, 1000);
            System.arraycopy(src, 0, dst, 0, length);
            src = null;
            churn();
            assertBoxes(dst, 0, length, 1000);

            System.gc();
            assertBoxes(dst, 0, length, 1000);
        }
    }

    @Test
    public void testYoungIntoOldLargeArrayPartialRange() {
        /* Large enough for an unaligned chunk; the copy dirties only part of its cards. */
        int length = 1 << 20;
        Box[] dst = new Box[length];
        System.gc();
        System.gc();

        int offset = length / 2 + 123;
        int count = 70_000;
        Box[] src = boxes(count, 5);
        System.arraycopy(src, 0, dst, offset, count);
        src = null;
        churn();
        assertBoxes(dst, offset, count, 5);
        Assert.assertNull(dst[offset - 1]);
        Assert.assertNull(dst[offset + count]);

        /* Overlapping copy inside the large old array, in both directions. */
        System.arraycopy(dst, offset, dst, offset + 1000, count);
        churn();
        assertBoxes(dst, offset + 1000, count, 5);
        System.arraycopy(dst, offset + 1000, dst, offset - 2000, count);
        churn();
        assertBoxes(dst, offset - 2000, count, 5);

        System.gc();
        assertBoxes(dst, offset - 2000, count, 5);
    }

    @Test
    public void testClone() {
        for (int length : LENGTHS) {
            Box[] src = boxes(length, 3);
            Box[] copy = src.clone();
            src = null;
            churn();
            assertBoxes(copy, 0, length, 3);
        }
    }

    @Test
    public void testCopyOf() {
        for (int length : LENGTHS) {
            Box[] src = boxes(length, 9);
            Box[] copy = Arrays.copyOf(src, length + 3);
            churn();
            assertBoxes(copy, 0, length, 9);
            Assert.assertNull(copy[length]);
        }
    }

    /** Short and long copies must throw the same exceptions, after copying the same elements. */
    @Test
    public void testExceptions() {
        for (int length : LENGTHS) {
            /* Store check fails at the last element: everything before it is copied. */
            Object[] src = new Object[length];
            for (int i = 0; i < length; i++) {
                src[i] = i == length - 1 ? Integer.valueOf(i) : new Box(i);
            }
            Box[] dst = new Box[length];
            if (length > 0) {
                Assert.assertThrows(ArrayStoreException.class, () -> System.arraycopy(src, 0, dst, 0, length));
                assertBoxes(dst, 0, length - 1, 0);
                Assert.assertNull(dst[length - 1]);
            }
            Assert.assertThrows(ArrayStoreException.class, () -> System.arraycopy(src, 0, new int[length], 0, length));
            Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> System.arraycopy(src, 1, new Object[length], 0, length));
            Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> System.arraycopy(src, 0, new Object[length], 1, length));
            Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> System.arraycopy(src, 0, src, 0, -1 - length));
            /* Type mismatch is reported before the bounds. */
            Assert.assertThrows(ArrayStoreException.class, () -> System.arraycopy(src, 1, new int[length], 0, length));
        }
    }
}
