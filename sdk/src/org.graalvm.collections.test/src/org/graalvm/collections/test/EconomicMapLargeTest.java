/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.collections.test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Random;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EconomicMapLargeTest {

    @Parameter(value = 0) public EconomicMap<Object, Object> testMap;
    @Parameter(value = 1) public EconomicMap<Object, Object> referenceMap;
    @Parameter(value = 2) public String name;

    @Parameters(name = "{2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[]{EconomicMap.create(Equivalence.DEFAULT), EconomicMap.create(Equivalence.DEFAULT), "EconomicMap"},
                        new Object[]{EconomicMap.create(Equivalence.IDENTITY), EconomicMap.create(Equivalence.IDENTITY), "EconomicMap(IDENTITY)"},
                        new Object[]{EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE), EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE),
                                        "EconomicMap(IDENTITY_WITH_SYSTEM_HASHCODE)"},
                        new Object[]{EconomicMap.create(Equivalence.DEFAULT), EconomicMap.wrapMap(new LinkedHashMap<>()), "EconomicMap<->wrapMap"},
                        new Object[]{EconomicMap.wrapMap(new LinkedHashMap<>()), EconomicMap.wrapMap(new LinkedHashMap<>()), "wrapMap"});
    }

    private static int[] createRandomRange(Random random, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; ++i) {
            int range = random.nextInt(14);
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

    static final MapAction[] INCREASE_ACTIONS = new MapAction[]{
                    (map, randomInt) -> map.put(randomInt, "value"),
                    (map, randomInt) -> map.get(randomInt)
    };

    static final MapAction[] ACTIONS = new MapAction[]{
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
    public void testVeryLarge() {
        testMap.clear();
        referenceMap.clear();

        Random random = new Random(0);
        for (int i = 0; i < 200000; ++i) {
            for (int j = 0; j < INCREASE_ACTIONS.length; ++j) {
                int nextInt = random.nextInt(10000000);
                MapAction action = INCREASE_ACTIONS[j];
                Object result = action.perform(testMap, nextInt);
                Object referenceResult = action.perform(referenceMap, nextInt);
                Assert.assertEquals(result, referenceResult);
            }
        }
    }

    /**
     * Tests a sequence of random operations on the map.
     */
    @Test
    public void testAddRemove() {
        testMap.clear();
        referenceMap.clear();

        for (int seed = 0; seed < 10; ++seed) {
            Random random = new Random(seed);
            int[] ranges = createRandomRange(random, ACTIONS.length);
            int value = random.nextInt(10000);
            for (int i = 0; i < value; ++i) {
                for (int j = 0; j < ACTIONS.length; ++j) {
                    if (random.nextInt(ranges[j]) == 0) {
                        int nextInt = random.nextInt(100);
                        MapAction action = ACTIONS[j];
                        Object result = action.perform(testMap, nextInt);
                        Object referenceResult = action.perform(referenceMap, nextInt);
                        Assert.assertEquals(result, referenceResult);
                        if (j % 100 == 0) {
                            checkEquality(testMap, referenceMap);
                        }
                    }
                }

                if (random.nextInt(20) == 0) {
                    removeElement(random.nextInt(100), testMap, referenceMap);
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
        UnmodifiableMapCursor<?, ?> cursor = map.getEntries();
        UnmodifiableMapCursor<?, ?> referenceCursor = referenceMap.getEntries();
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
