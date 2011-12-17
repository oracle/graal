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

/**
 * Tests for {@link Pool}.
 */
public class PoolTest extends MaxTestCase {

    public PoolTest(String name) {
        super(name);
        for (int i = 0; i < nElems; i++) {
            elems[i] = new TestElement(i);
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(PoolTest.class);
    }

    private static class TestElement implements PoolObject {
        private int serial;
        public TestElement(int n) {
            serial = n;
        }
        public int serial() {
            return serial;
        }
    }

    public void test_empty() {
        Pool<TestElement> pool = new ArrayPool<TestElement>(new TestElement[0]);
        assertEquals(pool.length(), 0);
        try {
            final TestElement elem = pool.get(0);
            fail(elem + " should not be in empty collection");
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
        }
    }

    private final int nElems = 100;
    TestElement[] elems = new TestElement[nElems];

    private void check_pool(Pool<TestElement> pool, int n) {
        assertEquals(pool.length(), n);
        final Iterator<TestElement> iterator = pool.iterator();
        for (int i = 0; i < n; i++) {
            assertTrue(iterator.hasNext());
            final TestElement element = pool.get(i);
            assertEquals(element.serial(), i);
            assertSame(element, elems[i]);
            assertSame(element, iterator.next());
        }
        assertFalse(iterator.hasNext());
    }

    public void test_pool() {
        Pool<TestElement> pool = new ArrayPool<TestElement>(elems);
        check_pool(pool, nElems);
    }
}
