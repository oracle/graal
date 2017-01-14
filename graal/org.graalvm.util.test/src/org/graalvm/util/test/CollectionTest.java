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
package org.graalvm.util.test;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;

import org.graalvm.util.CollectionFactory;
import org.graalvm.util.CompareStrategy;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.ImmutableMapCursor;
import org.graalvm.util.MapCursor;
import org.graalvm.util.ObjectSizeEstimate;
import org.junit.Assert;
import org.junit.Test;

public class CollectionTest {

    @Test
    public void testSize() {
        EconomicMap<Object, Object> map = CollectionFactory.newMap(CompareStrategy.IDENTITY);
        assertEquals(48, ObjectSizeEstimate.forObject(map).getTotalBytes());

        Integer value = 1;
        map.put(value, value);
        assertEquals(152, ObjectSizeEstimate.forObject(map).getTotalBytes());

        Integer secondValue = 2;
        map.put(secondValue, secondValue);
        assertEquals(152 + 20, ObjectSizeEstimate.forObject(map).getTotalBytes());
    }

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
        EconomicMap<Object, Object> map = CollectionFactory.newMap(CompareStrategy.EQUALS);
        EconomicMap<Object, Object> referenceMap = createDebugMap();

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

    public static <K, V> EconomicMap<K, V> createDebugMap() {
        final LinkedHashMap<K, V> linkedMap = new LinkedHashMap<>();
        final EconomicMap<K, V> sparseMap = CollectionFactory.newMap(CompareStrategy.EQUALS);
        return new EconomicMap<K, V>() {

            @Override
            public V get(K key) {
                V result = linkedMap.get(key);
                V sparseResult = sparseMap.get(key);
                assert Objects.equals(result, sparseResult);
                return result;
            }

            @Override
            public V put(K key, V value) {
                V result = linkedMap.put(key, value);
                assert Objects.equals(result, sparseMap.put(key, value));
                return result;
            }

            @Override
            public int size() {
                int result = linkedMap.size();
                assert result == sparseMap.size();
                return result;
            }

            @Override
            public boolean containsKey(K key) {
                boolean result = linkedMap.containsKey(key);
                assert result == sparseMap.containsKey(key);
                return result;
            }

            @Override
            public void clear() {
                linkedMap.clear();
                sparseMap.clear();
            }

            @Override
            public V removeKey(K key) {
                V result = linkedMap.remove(key);
                assert Objects.equals(result, sparseMap.removeKey(key));
                return result;
            }

            @Override
            public Iterable<V> getValues() {

                Iterator<V> iterator = linkedMap.values().iterator();
                Iterator<V> sparseIterator = sparseMap.getValues().iterator();
                return new Iterable<V>() {

                    @Override
                    public Iterator<V> iterator() {
                        return new Iterator<V>() {

                            @Override
                            public boolean hasNext() {
                                boolean result = iterator.hasNext();
                                boolean otherResult = sparseIterator.hasNext();
                                assert result == otherResult;
                                return result;
                            }

                            @Override
                            public V next() {
                                V sparseNext = sparseIterator.next();
                                V next = iterator.next();
                                assert Objects.equals(sparseNext, next);
                                return next;
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                sparseIterator.remove();
                            }
                        };
                    }

                };
            }

            @Override
            public Iterable<K> getKeys() {

                Iterator<K> iterator = linkedMap.keySet().iterator();
                Iterator<K> sparseIterator = sparseMap.getKeys().iterator();
                return new Iterable<K>() {

                    @Override
                    public Iterator<K> iterator() {
                        return new Iterator<K>() {

                            @Override
                            public boolean hasNext() {
                                boolean result = iterator.hasNext();
                                boolean otherResult = sparseIterator.hasNext();
                                assert result == otherResult;
                                return result;
                            }

                            @Override
                            public K next() {
                                K sparseNext = sparseIterator.next();
                                K next = iterator.next();
                                assert Objects.equals(sparseNext, next);
                                return next;
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                sparseIterator.remove();
                            }
                        };
                    }

                };
            }

            @Override
            public boolean isEmpty() {
                boolean result = linkedMap.isEmpty();
                assert result == sparseMap.isEmpty();
                return result;
            }

            @Override
            public MapCursor<K, V> getEntries() {
                Iterator<java.util.Map.Entry<K, V>> iterator = linkedMap.entrySet().iterator();
                MapCursor<K, V> cursor = sparseMap.getEntries();
                return new MapCursor<K, V>() {

                    private Map.Entry<K, V> current;

                    @Override
                    public boolean advance() {
                        boolean result = iterator.hasNext();
                        boolean otherResult = cursor.advance();
                        assert result == otherResult;
                        if (result) {
                            current = iterator.next();
                        }

                        return result;
                    }

                    @Override
                    public K getKey() {
                        K key = current.getKey();
                        assert key == cursor.getKey();
                        return key;
                    }

                    @Override
                    public V getValue() {
                        V value = current.getValue();
                        assert Objects.equals(value, cursor.getValue());
                        return value;
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                        cursor.remove();
                    }
                };
            }

            @Override
            public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
                linkedMap.replaceAll(function);
                sparseMap.replaceAll(function);
            }

        };
    }
}
