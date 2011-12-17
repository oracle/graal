/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.lang;

import com.sun.max.ide.*;
import com.sun.max.lang.*;

/**
 * Tests for {@link Longs}.
 */
public class LongsTest extends MaxTestCase {

    public LongsTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(LongsTest.class);
    }

    public void test_numberOfEffectiveUnsignedBits() {
        assertTrue(Longs.numberOfEffectiveUnsignedBits(0L) == 0);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(1L) == 1);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(2L) == 2);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(3L) == 2);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(4L) == 3);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(126L) == 7);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(127L) == 7);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(129L) == 8);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(254L) == 8);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(255L) == 8);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(256L) == 9);
        assertTrue(Longs.numberOfEffectiveUnsignedBits(257L) == 9);
    }

    public void test_numberOfEffectiveSignedBits() {
        for (long i = 0; i < 257L; i++) {
            assertTrue(Longs.numberOfEffectiveSignedBits(i) == Longs.numberOfEffectiveUnsignedBits(i) + 1L);
        }
        assertTrue(Longs.numberOfEffectiveSignedBits(0L) == 1);
        assertTrue(Longs.numberOfEffectiveSignedBits(-1L) == 1);
        assertTrue(Longs.numberOfEffectiveSignedBits(-2L) == 2);
        assertTrue(Longs.numberOfEffectiveSignedBits(-3L) == 3);
        assertTrue(Longs.numberOfEffectiveSignedBits(-4L) == 3);
        assertTrue(Longs.numberOfEffectiveSignedBits(-5L) == 4);
        assertTrue(Longs.numberOfEffectiveSignedBits(-6L) == 4);
        assertTrue(Longs.numberOfEffectiveSignedBits(-7L) == 4);
        assertTrue(Longs.numberOfEffectiveSignedBits(-8L) == 4);
        assertTrue(Longs.numberOfEffectiveSignedBits(-9L) == 5);
        assertTrue(Longs.numberOfEffectiveSignedBits(-126L) == 8);
        assertTrue(Longs.numberOfEffectiveSignedBits(-127L) == 8);
        assertTrue(Longs.numberOfEffectiveSignedBits(-128L) == 8);
        assertTrue(Longs.numberOfEffectiveSignedBits(-129L) == 9);
        assertTrue(Longs.numberOfEffectiveSignedBits(-254L) == 9);
        assertTrue(Longs.numberOfEffectiveSignedBits(-255L) == 9);
        assertTrue(Longs.numberOfEffectiveSignedBits(-256L) == 9);
        assertTrue(Longs.numberOfEffectiveSignedBits(-257L) == 10);
    }
}
