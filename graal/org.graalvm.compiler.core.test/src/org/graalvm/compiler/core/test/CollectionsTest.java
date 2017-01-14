/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.Iterator;
import java.util.Objects;
import java.util.Random;

import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.core.common.CompareStrategy;
import org.graalvm.compiler.core.common.EconomicMap;
import org.graalvm.compiler.core.common.ImmutableMapCursor;
import org.graalvm.compiler.core.common.MapCursor;
import org.junit.Assert;
import org.junit.Test;

public class CollectionsTest {

    private static int[] createRandomRange(Random random, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; ++i) {
            int range = random.nextInt(20);
            if (range == 0 || range > 10) {
                range = Integer.MAX_VALUE;
            } else if (range == 10) {
                range = 100;
            }
            result[i] = range;
        }
        return result;
    }

    private static final class BadHashClass {
        private int value;

        BadHashClass(int randomInt) {
            this.value = randomInt;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof BadHashClass) {
                BadHashClass badHashClass = (BadHashClass) other;
                return badHashClass.value == value;
            }
            return false;
        }
    }

    interface MapAction {
        Object perform(EconomicMap<Object, Object> map, int randomInt);
    }

    static final Object EXISTING_VALUE = new Object();

    static MapAction[] ACTIONS = new MapAction[]{
                    (map, randomInt) -> map.removeKey(randomInt),
                    (map, randomInt) -> map.put(randomInt, "value"),
                    (map, randomInt) -> map.put(randomInt, null),
                    (map, randomInt) -> map.put(EXISTING_VALUE, randomInt),
                    (map, randomInt) -> {
                        if (randomInt == 0) {
                            map.clear();
                        }
                        return map.isEmpty();
                    },
                    (map, randomInt) -> map.containsKey(randomInt),
                    (map, randomInt) -> map.get(randomInt),
                    (map, randomInt) -> map.put(new BadHashClass(randomInt), "unique"),
                    (map, randomInt) -> {
                        if (randomInt == 0) {
                            map.replaceAll((key, value) -> Objects.toString(value) + "!");
                        }
                        return map.isEmpty();
                    }

    };

    @Test
    public void testAdd() {
        EconomicMap<Object, Object> map = CollectionsFactory.newMap(CompareStrategy.EQUALS);
        EconomicMap<Object, Object> referenceMap = CollectionsFactory.debugNewMap();

        for (int seed = 0; seed < 10; ++seed) {
            Random random = new Random(seed);
            int[] ranges = createRandomRange(random, ACTIONS.length);
            int value = random.nextInt(1000);
            for (int i = 0; i < value; ++i) {

                for (int j = 0; j < ACTIONS.length; ++j) {
                    if (random.nextInt(ranges[j]) == 0) {
                        int nextInt = random.nextInt(100);
                        MapAction action = ACTIONS[j];
                        Object result = action.perform(map, nextInt);
                        Object referenceResult = action.perform(referenceMap, nextInt);
                        Assert.assertEquals(result, referenceResult);
                        checkEquality(map, referenceMap);
                    }
                }

                if (random.nextInt(20) == 0) {
                    removeElement(random.nextInt(100), map, referenceMap);
                }
            }
        }
    }

    private static void removeElement(int index, EconomicMap<?, ?> map, EconomicMap<?, ?> referenceMap) {
        Assert.assertEquals(referenceMap.size(), map.size());
        MapCursor<?, ?> cursor = map.getEntries();
        MapCursor<?, ?> referenceCursor = referenceMap.getEntries();
        int z = 0;
        while (cursor.advance()) {
            Assert.assertTrue(referenceCursor.advance());
            Assert.assertEquals(referenceCursor.getKey(), cursor.getKey());
            Assert.assertEquals(referenceCursor.getValue(), cursor.getValue());
            if (index == z) {
                cursor.remove();
                referenceCursor.remove();
            }
            ++z;
        }

        Assert.assertFalse(referenceCursor.advance());
    }

    private static void checkEquality(EconomicMap<?, ?> map, EconomicMap<?, ?> referenceMap) {
        Assert.assertEquals(referenceMap.size(), map.size());

        // Check entries.
        ImmutableMapCursor<?, ?> cursor = map.getEntries();
        ImmutableMapCursor<?, ?> referenceCursor = referenceMap.getEntries();
        while (cursor.advance()) {
            Assert.assertTrue(referenceCursor.advance());
            Assert.assertEquals(referenceCursor.getKey(), cursor.getKey());
            Assert.assertEquals(referenceCursor.getValue(), cursor.getValue());
        }

        // Check keys.
        Iterator<?> iterator = map.getKeys().iterator();
        Iterator<?> referenceIterator = referenceMap.getKeys().iterator();
        while (iterator.hasNext()) {
            Assert.assertTrue(referenceIterator.hasNext());
            Assert.assertEquals(iterator.next(), referenceIterator.next());
        }

        // Check values.
        iterator = map.getValues().iterator();
        referenceIterator = referenceMap.getValues().iterator();
        while (iterator.hasNext()) {
            Assert.assertTrue(referenceIterator.hasNext());
            Assert.assertEquals(iterator.next(), referenceIterator.next());
        }
        Assert.assertFalse(referenceIterator.hasNext());
    }

}
