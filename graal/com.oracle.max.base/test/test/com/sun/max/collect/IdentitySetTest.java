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
package test.com.sun.max.collect;

import com.sun.max.collect.*;
import com.sun.max.ide.*;

/**
 * Tests for {@link IdentitySet}.
 */
public class IdentitySetTest extends MaxTestCase {

    public IdentitySetTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(IdentitySetTest.class);
    }

    private Integer[] makeIntegerArray(int nElements) {
        final Integer[] array = new Integer[nElements];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        return array;
    }

    private IdentitySet<Integer> makeIntegerIdentitySet(int nElements) {
        final IdentitySet<Integer> result = new IdentitySet<Integer>();
        for (int i = 0; i < nElements; i++) {
            result.add(i);
        }
        return result;
    }

    public void test_numberOfElements() {
        final IdentitySet<Integer> set = new IdentitySet<Integer>();
        assertEquals(set.numberOfElements(), 0);
        try {
            set.add(null);
            fail();
        } catch (IllegalArgumentException illegalArgumentException) {
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals(set.numberOfElements(), i);
            set.add(i);
        }
    }

    private void check_add(IdentitySet<Integer> set) {
        assertEquals(set.numberOfElements(), 0);
        try {
            set.add(null);
            fail();
        } catch (IllegalArgumentException illegalArgumentException) {
        }
        assertEquals(set.numberOfElements(), 0);
        set.add(0);
        assertEquals(set.numberOfElements(), 1);
        set.add(1);
        assertEquals(set.numberOfElements(), 2);
    }

    public void test_add() {
        check_add(new IdentitySet<Integer>());
        check_add(new IdentitySet<Integer>(0));
        check_add(new IdentitySet<Integer>(1));
        check_add(new IdentitySet<Integer>(10000));
    }

    public void test_contains() {
        final IdentitySet<Integer> set = new IdentitySet<Integer>();
        final Integer[] ints = makeIntegerArray(1000);
        for (int i = 0; i < 1000; i++) {
            set.add(ints[i]);
        }
        assertEquals(set.numberOfElements(), 1000);
        for (int i = 0; i < 1000; i++) {
            assertTrue(set.contains(ints[i]));
        }
        assertFalse(set.contains(new Integer(0)));
    }

    public void test_iterator() {
        final IdentitySet<Integer> set = new IdentitySet<Integer>();
        final Integer[] ints = makeIntegerArray(1000);
        for (int i = 0; i < 1000; i++) {
            set.add(ints[i]);
        }
        assertEquals(set.numberOfElements(), 1000);
        final IdentitySet<Integer> newSet = new IdentitySet<Integer>();
        assertEquals(newSet.numberOfElements(), 0);
        for (Integer theInt : set) {
            assertNotNull(theInt);
            assertTrue(set.contains(theInt));
            newSet.add(theInt);
        }
        assertEquals(newSet.numberOfElements(), 1000);
    }

}
