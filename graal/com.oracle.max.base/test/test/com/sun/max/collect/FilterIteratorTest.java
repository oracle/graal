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

import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.ide.*;
import com.sun.max.util.*;

/**
 * Tests for {@link FilterIterator}.
 */

public class FilterIteratorTest extends MaxTestCase {

    public FilterIteratorTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(FilterIteratorTest.class);
    }

    private final Predicate<Integer> evenPred = new Predicate<Integer>() {
        public boolean evaluate(Integer i) {
            return i % 2 == 0;
        }
    };

    public void test_FilterIterator() {
        final Integer[] array = new Integer[10];
        for (int i = 0; i < array.length; i++) {
            array[i] = new Integer(i);
        }
        final LinkedList<Integer> list = new LinkedList<Integer>(java.util.Arrays.asList(array));
        FilterIterator<Integer> iter = new FilterIterator<Integer>(list.iterator(), evenPred);
        int i = 0;
        Integer elem;
        while (iter.hasNext()) {
            elem = iter.next();
            assertEquals(elem.intValue(), i);
            i += 2;
        }
        assertEquals(i, 10);
        assertEquals(list.size(), 10);
        try {
            iter = new FilterIterator<Integer>(list.iterator(), evenPred);
            while (iter.hasNext()) {
                iter.remove();
            }
            fail("FilterIterator.remove() should have thrown IllegalStateException");
        } catch (IllegalStateException illegalStateException) {
        }
    }
}
