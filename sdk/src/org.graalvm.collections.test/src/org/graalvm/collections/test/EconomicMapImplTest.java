/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.junit.Assert;
import org.junit.Test;

public class EconomicMapImplTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveNull() {
        EconomicMap<Integer, Integer> map = EconomicMap.create(10);
        map.removeKey(null);
    }

    @Test
    public void testInitFromHashSet() {
        UnmodifiableEconomicSet<Integer> set = new UnmodifiableEconomicSet<>() {

            @Override
            public boolean contains(Integer element) {
                return element == 0;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<>() {

                    private boolean visited = false;

                    @Override
                    public boolean hasNext() {
                        return !visited;
                    }

                    @Override
                    public Integer next() {
                        if (visited) {
                            return null;
                        } else {
                            visited = true;
                            return 1;
                        }
                    }
                };
            }
        };

        EconomicSet<Integer> newSet = EconomicSet.create(Equivalence.DEFAULT, set);
        Assert.assertEquals(newSet.size(), 1);
    }

    @Test
    public void testCopyHash() {
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY);
        set.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        EconomicSet<Integer> newSet = EconomicSet.create(Equivalence.IDENTITY, set);
        Assert.assertEquals(newSet.size(), 10);
        newSet.remove(8);
        newSet.remove(9);
        Assert.assertEquals(newSet.size(), 8);
    }

    @Test
    public void testNewEquivalence() {
        EconomicSet<Integer> set = EconomicSet.create(new Equivalence() {
            @Override
            public boolean equals(Object a, Object b) {
                return false;
            }

            @Override
            public int hashCode(Object o) {
                return 0;
            }
        });
        set.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        Assert.assertTrue(set.add(newInteger(0)));
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMapPutNull() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(null, null);
    }

    @Test
    public void testInvalidIteratorRemove() {
        EconomicMap<String, Integer> map = EconomicMap.create();
        map.put("one", 1);
        var iterator = map.getKeys().iterator();
        assertThrows(IllegalStateException.class, iterator::remove);
        assertNotNull(iterator.next());
        iterator.remove();
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    public void testRepeatedIteratorRemove() {
        for (int size : new int[]{2, 4, 8, 32, 256}) {
            for (boolean values : new boolean[]{false, true}) {
                EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
                for (int i = 0; i < size; i++) {
                    map.put(new CollidingKey(i), i == 1 ? null : i);
                }
                Iterator<?> iterator = values ? map.getValues().iterator() : map.getKeys().iterator();
                assertThrows(IllegalStateException.class, iterator::remove);
                iterator.next();
                iterator.next();
                iterator.remove();
                assertThrows(IllegalStateException.class, iterator::remove);
                assertEquals(size - 1, map.size());
                assertTrue(map.containsKey(new CollidingKey(0)));
                assertFalse(map.containsKey(new CollidingKey(1)));
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                    assertThrows(IllegalStateException.class, iterator::remove);
                }
                assertEquals(1, map.size());
                assertEquals((Object) 0, map.get(new CollidingKey(0)));
            }
        }
    }

    @Test
    public void testIteratorRemoveAfterFailedNext() {
        for (int size : new int[]{1, 2, 8, 32}) {
            for (boolean values : new boolean[]{false, true}) {
                EconomicMap<Integer, Integer> map = EconomicMap.create();
                for (int i = 0; i < size; i++) {
                    map.put(i, null);
                }
                Iterator<?> iterator = values ? map.getValues().iterator() : map.getKeys().iterator();
                while (iterator.hasNext()) {
                    iterator.next();
                }
                assertThrows(NoSuchElementException.class, iterator::next);
                iterator.remove();
                assertThrows(IllegalStateException.class, iterator::remove);
                assertEquals(size - 1, map.size());
                assertFalse(map.containsKey(size - 1));
            }
        }
    }

    @Test
    public void testRepeatedSetIteratorRemove() {
        for (int size : new int[]{2, 4, 8, 32, 256}) {
            EconomicSet<CollidingKey> set = EconomicSet.create();
            for (int i = 0; i < size; i++) {
                set.add(new CollidingKey(i));
            }
            Iterator<CollidingKey> iterator = set.iterator();
            assertThrows(IllegalStateException.class, iterator::remove);
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
                assertThrows(IllegalStateException.class, iterator::remove);
            }
            assertTrue(set.isEmpty());
        }
    }

    @Test
    public void testKeyIteratorNext() {
        EconomicMap<String, Integer> map = EconomicMap.create();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);
        map.removeKey("two");
        var iterator = map.getKeys().iterator();
        assertEquals("one", iterator.next());
        assertEquals("three", iterator.next());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testValueIteratorNext() {
        EconomicMap<String, Integer> map = EconomicMap.create();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", null);
        var iterator = map.getValues().iterator();
        assertEquals((Object) 1, iterator.next());
        assertEquals((Object) 2, iterator.next());
        assertNull(iterator.next());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testTrimToSize() {
        EconomicMap<String, Integer> map = EconomicMap.create(100);
        for (int i = 0; i < 10; i++) {
            map.put("key" + i, i);
        }
        map.removeKey("key3");
        map.removeKey("key7");

        map.trimToSize();

        assertEquals(8, map.size());
        for (int i = 0; i < 10; i++) {
            Integer expected = (i == 3 || i == 7) ? null : i;
            assertEquals(expected, map.get("key" + i));
        }
    }

    @Test
    public void testTrimToSizeAfterGrowingSingleMap() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
        for (int size = 1; size <= 64; size++) {
            assertNull(map.put(new CollidingKey(size), size));

            if (size % 3 == 0) {
                assertEquals((Object) (size - 1), map.removeKey(new CollidingKey(size - 1)));
            }
            if (size % 8 == 0) {
                map.trimToSize();
            }

            for (int i = 1; i <= size; i++) {
                Integer expected = i % 3 == 2 && i < size ? null : i;
                assertEquals(expected, map.get(new CollidingKey(i)));
            }
        }

        for (int i = 1; i <= 64; i++) {
            if (i % 3 == 2) {
                assertNull(map.put(new CollidingKey(i), -i));
            }
        }
        map.trimToSize();

        assertEquals(64, map.size());
        for (int i = 1; i <= 64; i++) {
            Integer expected = i % 3 == 2 ? -i : i;
            assertEquals(expected, map.get(new CollidingKey(i)));
        }
    }

    @Test
    public void testHashCollisions() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
        for (int i = 0; i < 32; i++) {
            assertNull(map.put(new CollidingKey(i), i));
        }

        assertEquals((Object) 17, map.put(new CollidingKey(17), 1700));
        assertEquals((Object) 1700, map.get(new CollidingKey(17)));
        assertEquals((Object) 8, map.removeKey(new CollidingKey(8)));
        assertNull(map.get(new CollidingKey(8)));
        assertNull(map.put(new CollidingKey(100), 100));

        for (int i = 0; i < 32; i++) {
            Integer expected = i == 8 ? null : i == 17 ? 1700 : i;
            assertEquals(expected, map.get(new CollidingKey(i)));
        }
        assertEquals((Object) 100, map.get(new CollidingKey(100)));
    }

    /**
     * Checks that replacements return the previous logical value, including null, without changing
     * a shallow copy's collision links. Also covers replacements through a cursor.
     */
    @Test
    public void testReplacementPreservesCopy() {
        for (int size : new int[]{2, 32}) {
            EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
            CollidingKey[] keys = new CollidingKey[size];
            for (int i = 0; i < size; i++) {
                keys[i] = new CollidingKey(i);
                assertNull(map.put(keys[i], i % 2 == 0 ? null : i));
            }
            EconomicMap<CollidingKey, Integer> copy = EconomicMap.create(map);
            for (int i = 0; i < size; i++) {
                CollidingKey equivalent = new CollidingKey(i);
                Integer expected = i % 2 == 0 ? null : i;
                assertEquals(expected, map.put(equivalent, 100 + i));
                assertEquals(expected, copy.get(equivalent));
                assertEquals((Object) (100 + i), map.put(equivalent, null));
                assertNull(map.put(equivalent, 200 + i));
            }
            MapCursor<CollidingKey, Integer> cursor = copy.getEntries();
            for (int i = 0; i < size; i++) {
                assertTrue(cursor.advance());
                assertSame(keys[i], cursor.getKey());
                Integer expected = i % 2 == 0 ? null : i;
                assertEquals(expected, cursor.setValue(-i - 1));
                assertEquals((Object) (-i - 1), cursor.getValue());
                assertEquals((Object) (200 + i), map.get(keys[i]));
            }
            assertFalse(cursor.advance());
            assertEquals(size, map.size());
            assertEquals(size, copy.size());
        }
    }

    @Test
    public void testHashTableSizeAtHalfOccupancy() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
        for (int i = 0; i < 9; i++) {
            assertNull(map.put(new CollidingKey(i), i));
        }

        for (int i = 0; i < 9; i++) {
            assertEquals((Object) i, map.get(new CollidingKey(i)));
        }
    }

    @Test
    public void testHashIteratorRemove() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
        for (int i = 0; i < 16; i++) {
            map.put(new CollidingKey(i), i);
        }

        Iterator<CollidingKey> iterator = map.getKeys().iterator();
        while (iterator.hasNext()) {
            CollidingKey key = iterator.next();
            if ((key.id & 1) == 0) {
                iterator.remove();
            }
        }

        assertEquals(8, map.size());
        for (int i = 0; i < 16; i++) {
            Integer expected = (i & 1) == 0 ? null : i;
            assertEquals(expected, map.get(new CollidingKey(i)));
        }
    }

    @Test
    public void testTrimToSizeWithHashCollisions() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create(100);
        for (int i = 0; i < 20; i++) {
            map.put(new CollidingKey(i), i);
        }
        map.removeKey(new CollidingKey(3));
        map.removeKey(new CollidingKey(7));

        map.trimToSize();

        assertEquals(18, map.size());
        for (int i = 0; i < 20; i++) {
            Integer expected = (i == 3 || i == 7) ? null : i;
            assertEquals(expected, map.get(new CollidingKey(i)));
        }
    }

    @Test
    public void testThreeByteHashIndices() {
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create(1 << 16);
        for (int i = 0; i < 20; i++) {
            assertNull(map.put(new CollidingKey(i), i));
        }

        assertEquals((Object) 11, map.removeKey(new CollidingKey(11)));
        assertNull(map.put(new CollidingKey(100), 100));

        for (int i = 0; i < 20; i++) {
            Integer expected = i == 11 ? null : i;
            assertEquals(expected, map.get(new CollidingKey(i)));
        }
        assertEquals((Object) 100, map.get(new CollidingKey(100)));
    }

    /**
     * Verifies that equivalent-key updates preserve the original key object and insertion order across
     * map sizes. Covers null-valued entries, non-null {@code putIfAbsent} hits, and ordinary {@code
     * put} replacement.
     */
    @Test
    public void testPutIfAbsentRetainsKeyAndOrder() {
        for (int size : new int[]{0, 3, 4, 8, 32}) {
            EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
            for (int i = 0; i < size; i++) {
                map.put(new CollidingKey(i), i);
            }
            CollidingKey original = new CollidingKey(-1);
            CollidingKey equivalent = new CollidingKey(-1);
            assertNull(map.putIfAbsent(original, null));
            assertNull(map.putIfAbsent(equivalent, null));
            Assert.assertTrue(map.containsKey(original));
            assertNull(map.putIfAbsent(equivalent, 1));
            assertEquals((Object) 1, map.putIfAbsent(equivalent, 2));
            assertEquals((Object) 1, map.putIfAbsent(equivalent, null));
            assertEquals((Object) 1, map.get(original));
            assertEquals((Object) 1, map.put(equivalent, 3));
            assertEquals((Object) 3, map.get(original));
            assertEquals(size + 1, map.size());
            Iterator<CollidingKey> keys = map.getKeys().iterator();
            for (int i = 0; i < size; i++) {
                assertEquals(new CollidingKey(i), keys.next());
            }
            Assert.assertSame(original, keys.next());
            Assert.assertFalse(keys.hasNext());
        }
    }

    /**
     * Checks that both identity strategies keep equal but distinct key objects separate, while
     * repeated insertion of the same object preserves its value.
     */
    @Test
    public void testPutIfAbsentIdentityEquivalence() {
        for (Equivalence equivalence : new Equivalence[]{Equivalence.IDENTITY, Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE}) {
            EconomicMap<CollidingKey, Integer> map = EconomicMap.create(equivalence);
            for (int i = 0; i < 32; i++) {
                CollidingKey first = new CollidingKey(i);
                CollidingKey equal = new CollidingKey(i);
                assertNull(map.putIfAbsent(first, i));
                assertNull(map.putIfAbsent(equal, -i));
                assertEquals((Object) i, map.putIfAbsent(first, 100));
                assertEquals((Object) (-i), map.get(equal));
            }
            assertEquals(64, map.size());
        }
    }

    /**
     * Verifies that {@code putIfAbsent} uses the configured equivalence rather than the keys' own
     * equality when deciding whether to preserve an existing value.
     */
    @Test
    public void testPutIfAbsentCustomEquivalence() {
        Equivalence equivalence = new Equivalence() {
            @Override
            public boolean equals(Object a, Object b) {
                return ((CollidingKey) a).id / 2 == ((CollidingKey) b).id / 2;
            }

            @Override
            public int hashCode(Object key) {
                return ((CollidingKey) key).id / 2;
            }
        };
        EconomicMap<CollidingKey, Integer> map = EconomicMap.create(equivalence);
        for (int i = 0; i < 64; i += 2) {
            CollidingKey key = new CollidingKey(i);
            CollidingKey equivalent = new CollidingKey(i + 1);
            assertNull(map.putIfAbsent(key, i));
            assertEquals((Object) i, map.putIfAbsent(equivalent, i + 1));
            assertEquals((Object) i, map.get(equivalent));
        }
        assertEquals(32, map.size());
    }

    /**
     * Checks that {@code putIfAbsent} rejects null keys in both empty and populated maps without
     * changing existing mappings.
     */
    @Test
    public void testPutIfAbsentRejectsNullKeys() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(null, null));
        map.put(1, 2);
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(null, 3));
        assertEquals(1, map.size());
        assertEquals((Object) 2, map.get(1));
    }

    /**
     * Checks that computing a value for an equivalent key fills a null-valued mapping without
     * replacing the stored key or changing iteration order. The function receives the caller's key,
     * not the stored key.
     */
    @Test
    public void testComputeIfAbsentRetainsKeyAndOrder() {
        for (int size : new int[]{2, 32}) {
            EconomicMap<CollidingKey, Integer> map = EconomicMap.create();
            CollidingKey first = new CollidingKey(-2);
            CollidingKey original = new CollidingKey(-1);
            CollidingKey equivalent = new CollidingKey(-1);
            map.put(first, 77);
            map.put(original, null);
            for (int i = 0; i < size; i++) {
                map.put(new CollidingKey(i), i);
            }
            assertEquals((Object) 42, map.computeIfAbsent(equivalent, k -> {
                assertSame(equivalent, k);
                return 42;
            }));
            Iterator<CollidingKey> keys = map.getKeys().iterator();
            assertSame(first, keys.next());
            assertSame(original, keys.next());
            for (int i = 0; i < size; i++) {
                assertEquals(i, keys.next().id);
                assertEquals((Object) i, map.get(new CollidingKey(i)));
            }
            assertFalse(keys.hasNext());
            assertEquals((Object) 77, map.get(first));
            assertEquals((Object) 42, map.get(original));
        }
    }

    /**
     * Verifies that {@code computeIfAbsent} rejects a null key before invoking the mapping function
     * and leaves the map empty.
     */
    @Test
    public void testComputeIfAbsentRejectsNullKey() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        Assert.assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(null, k -> {
            throw new AssertionError("Null key must be rejected before invoking the mapping function");
        }));
        assertTrue(map.isEmpty());
    }

    /**
     * Checks that duplicate colliding additions preserve set size, original key objects, and iteration
     * order. Also verifies removal and reinsertion after those duplicate additions.
     */
    @Test
    public void testDuplicateSetAddWithCollisions() {
        EconomicSet<CollidingKey> set = EconomicSet.create();
        CollidingKey[] keys = new CollidingKey[32];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new CollidingKey(i);
            assertTrue(set.add(keys[i]));
        }
        for (int i = 0; i < keys.length; i++) {
            assertFalse(set.add(keys[i]));
            assertFalse(set.add(new CollidingKey(i)));
        }
        assertEquals(keys.length, set.size());
        Iterator<CollidingKey> iterator = set.iterator();
        for (CollidingKey key : keys) {
            assertSame(key, iterator.next());
        }
        assertFalse(iterator.hasNext());
        set.remove(new CollidingKey(17));
        assertFalse(set.contains(keys[17]));
        assertEquals(keys.length - 1, set.size());
        assertTrue(set.add(new CollidingKey(17)));
        assertEquals(keys.length, set.size());
        for (CollidingKey key : keys) {
            assertTrue(set.contains(key));
        }
    }

    private static final class CollidingKey {
        private final int id;

        private CollidingKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CollidingKey other && id == other.id;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }
}
