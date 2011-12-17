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
 * Tests for {@link IdentityHashMap}.
 */
public class LinkedIdentityHashMapTest extends MaxTestCase {

    public LinkedIdentityHashMapTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(LinkedIdentityHashMapTest.class);
    }

    private final int nKeys = 100;
    private String[] keys = new String[nKeys];
    private Integer[] vals = new Integer[nKeys];

    private void initialize() {
        for (int i = 0; i < nKeys; i++) {
            keys[i] = "key" + i;
            vals[i] = new Integer(i);
        }
    }

    private void check_serial(LinkedIdentityHashMap<String, Integer> table, int n) {
        int i = 0;
        for (String key : table) {
            assertSame(key, keys[i++]);
        }
        assertEquals(i, n + 1);
        assertEquals(i, table.size());
    }

    public void test_serial() {
        initialize();
        final LinkedIdentityHashMap<String, Integer> table = new LinkedIdentityHashMap<String, Integer>();
        for (int i = 0; i < nKeys; i++) {
            assertEquals(table.get(keys[i]), null);
            table.put(keys[i], vals[i]);
            check_serial(table, i);
        }
    }

    public void test_random() {
        initialize();
        final LinkedIdentityHashMap<String, Integer> table = new LinkedIdentityHashMap<String, Integer>();
        final Random random = new Random();
        final int[] keyOrder = new int[nKeys];
        for (int i = 0; i < nKeys; i++) {
            int k = 0;
            do {
                k = random.nextInt(nKeys);
            } while (table.get(keys[k]) != null);
            keyOrder[i] = k;
            table.put(keys[k], vals[k]);
        }
        int i = 0;
        for (String key : table) {
            assertSame(key, keys[keyOrder[i]]);
            assertSame(table.get(key), vals[keyOrder[i]]);
            i++;
        }
        assertEquals(i, nKeys);
    }

    public void test_equals() {
        initialize();
        final LinkedIdentityHashMap<String, Integer> table1 = new LinkedIdentityHashMap<String, Integer>();
        final LinkedIdentityHashMap<String, Integer> table2 = new LinkedIdentityHashMap<String, Integer>();
        assertTrue(table1.equals(table2));
        assertTrue(table2.equals(table1));
        for (int i = 0; i < nKeys; i++) {
            table1.put(keys[i], vals[i]);
        }
        for (int i = 0; i < nKeys; i++) {
            table2.put(keys[i], vals[i]);
        }
        assertTrue(table1.equals(table2));
        assertTrue(table2.equals(table1));
        table1.put(keys[0], new Integer(-1));
        assertFalse(table1.equals(table2));
        assertFalse(table2.equals(table1));
    }

}
