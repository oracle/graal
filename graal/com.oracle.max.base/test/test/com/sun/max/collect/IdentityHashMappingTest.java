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
 * Tests for {@link IdentityHashMapping}.
 */
public class IdentityHashMappingTest extends MaxTestCase {

    public IdentityHashMappingTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(IdentityHashMappingTest.class);
    }

    private static final class Key {

        private final int id;

        private Key(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            final Key key = (Key) other;
            return id == key.id;
        }
    }

    private static final class Value {

        private final int id;

        private Value(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }
    }

    public void test_basic() {
        final int num = 100000;
        final IdentityHashMapping<Key, Value> map = new IdentityHashMapping<Key, Value>();
        final Key[] keys = new Key[num];
        final Value[] values = new Value[num];
        final Value[] values2 = new Value[num];
        for (int i = 0; i < num; i++) {
            keys[i] = new Key(i);
            values[i] = new Value(i);
            values2[i] = new Value(i * 2);
            map.put(keys[i], values[i]);
            assertTrue(map.containsKey(keys[i]));
            assertEquals(i + 1, map.keys().size());
        }
        for (int i = 0; i < num; i++) {
            assertTrue(map.containsKey(keys[i]));
            assertSame(map.get(keys[i]), values[i]);
        }
        assertFalse(map.containsKey(new Key(-1)));
        for (int i = 0; i < num; i++) {
            map.put(keys[i], values[i]);
            assertSame(map.get(keys[i]), values[i]);
        }
        for (int i = 0; i < num; i++) {
            if ((i % 3) == 0) {
                map.put(keys[i], values2[i]);
            }
        }
        for (int i = 0; i < num; i++) {
            if ((i % 3) == 0) {
                assertSame(map.get(keys[i]), values2[i]);
            } else {
                assertSame(map.get(keys[i]), values[i]);
            }
        }
    }
}
