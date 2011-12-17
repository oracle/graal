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
 * Tests for {@link Integers}.
 */
public class IntegersTest extends MaxTestCase {

    public IntegersTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(IntegersTest.class);
    }

    public void test_roundUp() {
        assertTrue(Ints.roundUp(11, 4) == 12);
        assertTrue(Ints.roundUp(0, 4) == 0);
        assertTrue(Ints.roundUp(3, 1) == 3);
        assertTrue(Ints.roundUp(-1, 3) == 0);
        try {
            assertTrue(Ints.roundUp(1, 0) == 0);
            fail();
        } catch (ArithmeticException arithmeticException) {
        }
    }

    public void test_contains() {
        final int[] array = new int[10000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        assertTrue(!Ints.contains(array, -1));
        assertTrue(Ints.contains(array, 0));
        assertTrue(Ints.contains(array, 9999));
        assertTrue(!Ints.contains(array, 10000));
        assertTrue(!Ints.contains(array, Integer.MAX_VALUE));
    }

    public void test_append() {
        int[] array = new int[0];
        for (int i = 0; i < array.length; i++) {
            array = Ints.append(array, i);
        }
        for (int i = 0; i < array.length; i++) {
            assertTrue(array[i] == i);
        }
    }

    public void test_createRange() {
        final int[] array = Ints.createRange(0, 1000);
        assertTrue(array.length == 1001);
        assertTrue(array[0] == 0);
        assertTrue(array[array.length - 1] == 1000);
        for (int i = 0; i < array.length; i++) {
            assertTrue(array[i] == i);
        }

        final int[] array2 = Ints.createRange(-1000, 1000);
        assertTrue(array2.length == 2001);
        assertTrue(array2[0] == -1000);
        assertTrue(array2[array2.length - 1] == 1000);
        for (int i = 0; i < array2.length; i++) {
            assertTrue(array2[i] == i - 1000);
        }

        final int[] array3 = Ints.createRange(100, 1000);
        assertTrue(array3.length == 901);
        assertTrue(array3[0] == 100);
        assertTrue(array3[array3.length - 1] == 1000);
        for (int i = 0; i < array3.length; i++) {
            assertTrue(array3[i] == i + 100);
        }

        final int[] array4 = Ints.createRange(-1000, -100);
        assertTrue(array4.length == 901);
        assertTrue(array4[0] == -1000);
        assertTrue(array4[array4.length - 1] == -100);
        for (int i = 0; i < array4.length; i++) {
            assertTrue(array4[i] == i - 1000);
        }

        try {
            Ints.createRange(100, 0);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        final int[] array6 = Ints.createRange(1, 1);
        assertTrue(array6.length == 1 && array6[0] == 1);
    }
}
