/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.util.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.graalvm.collections.Equivalence;
import org.junit.Test;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;

/**
 * Tests the {@link EconomicHashMap}.
 */
public class EconomicHashMapTest {
    @Test
    public void testNewHashMap() {
        Map<String, Integer> map = new EconomicHashMap<>();
        assertNotNull(map);
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    public void testNewHashMapWithInitialCapacity() {
        Map<String, Integer> map = new EconomicHashMap<>(10);
        assertNotNull(map);
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    public void testNewHashMapWithStrategy() {
        Map<String, Integer> map = new EconomicHashMap<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        map.put(new String("test"), 0);
        map.put(new String("test"), 1);
        assertEquals(2, map.size());
    }

    @Test
    public void testNewHashMapWithStrategyAndCapacity() {
        Map<String, Integer> map = new EconomicHashMap<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE, 10);
        map.put(new String("test"), 0);
        map.put(new String("test"), 1);
        assertEquals(2, map.size());
    }

    @Test
    public void testNewHashMapWithOtherMap() {
        Map<String, Integer> originalMap = new EconomicHashMap<>();
        originalMap.put("one", 1);
        originalMap.put("two", 2);
        Map<String, Integer> map = new EconomicHashMap<>(originalMap);
        assertNotNull(map);
        assertFalse(map.isEmpty());
        assertEquals(2, map.size());
        assertEquals((Object) 1, map.get("one"));
        assertEquals((Object) 2, map.get("two"));
    }

    @Test
    public void testNewIdentityHashMap() {
        Map<String, Integer> map = EconomicHashMap.newIdentityMap();
        String key1 = new String("one");
        String key2 = new String("one");
        map.put(key1, 1);
        assertNull(map.get(key2)); // Because key1 and key2 are different objects.
        map.put(key2, 2);
        assertEquals(2, map.size());
        assertEquals((Object) 1, map.get(key1));
        assertEquals((Object) 2, map.get(key2));
    }

    @Test
    public void testMapOfEntries() {
        Map.Entry<String, Integer> entry1 = Map.entry("one", 1);
        Map.Entry<String, Integer> entry2 = Map.entry("two", 2);
        Map<String, Integer> map = CollectionsUtil.mapOfEntries(entry1, entry2);
        assertNotNull(map);
        assertFalse(map.isEmpty());
        assertEquals(2, map.size());
        assertEquals((Object) 1, map.get("one"));
        assertEquals((Object) 2, map.get("two"));
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable
    }

    @SafeVarargs
    private <K> void checkKeys(Map<K, ?> map, K... keys) {
        assertEquals(keys.length, map.size());
        Iterator<K> iterator = map.keySet().iterator();
        for (K key : keys) {
            assertTrue(iterator.hasNext());
            K actual = iterator.next();
            assertEquals(key, actual);
        }
    }

    @SafeVarargs
    private <V> void checkValues(Map<?, V> map, V... values) {
        assertEquals(values.length, map.size());
        Iterator<V> iterator = map.values().iterator();
        for (V value : values) {
            assertTrue(iterator.hasNext());
            V actual = iterator.next();
            assertEquals(value, actual);
        }
    }

    @Test
    public void testMapOf() {
        Map<String, Integer> map = CollectionsUtil.mapOf("one", 1);
        assertNotNull(map);
        checkKeys(map, "one");
        checkValues(map, 1);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable

        map = CollectionsUtil.mapOf("one", 1, "two", 2);
        assertNotNull(map);
        checkKeys(map, "one", "two");
        checkValues(map, 1, 2);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable

        map = CollectionsUtil.mapOf("one", 1, "two", 2, "three", 3);
        assertNotNull(map);
        checkKeys(map, "one", "two", "three");
        checkValues(map, 1, 2, 3);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable

        map = CollectionsUtil.mapOf("one", 1, "two", 2, "three", 3, "four", 4);
        assertNotNull(map);
        checkKeys(map, "one", "two", "three", "four");
        checkValues(map, 1, 2, 3, 4);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable

        map = CollectionsUtil.mapOf("one", 1, "two", 2, "three", 3, "four", 4, "five", 5);
        assertNotNull(map);
        checkKeys(map, "one", "two", "three", "four", "five");
        checkValues(map, 1, 2, 3, 4, 5);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable

        map = CollectionsUtil.mapOf("one", 1, "two", 2, "three", 3, "four", 4, "five", 5, "six", 6);
        assertNotNull(map);
        checkKeys(map, "one", "two", "three", "four", "five", "six");
        checkValues(map, 1, 2, 3, 4, 5, 6);
        assertThrows(UnsupportedOperationException.class, map::clear); // unmodifiable
    }

    @Test
    public void testPutAndGet() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        assertEquals((Object) 1, map.get("one"));
        assertEquals((Object) 2, map.get("two"));
        assertNull(map.get("three"));
    }

    @Test
    public void testContainsKey() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        assertTrue(map.containsKey("one"));
        assertFalse(map.containsKey("two"));
    }

    @Test
    public void testContainsValue() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        assertTrue(map.containsValue(1));
        assertFalse(map.containsValue(2));
    }

    @Test
    public void testRemove() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        assertEquals((Object) 1, map.remove("one"));
        assertNull(map.remove("one"));
        assertTrue(map.isEmpty());
    }

    @Test
    public void testClear() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void testEntrySetIterator() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        assertEquals(2, map.entrySet().size());
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        assertNotNull(entry);
        assertEquals("one", entry.getKey());
        assertEquals((Object) 1, entry.getValue());
        entry = iterator.next();
        assertNotNull(entry);
        assertEquals("two", entry.getKey());
        assertEquals((Object) 2, entry.getValue());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testConcurrentPut() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        map.put("two", 2); // structural modification
        assertEquals("one", entry.getKey());
        assertThrows(ConcurrentModificationException.class, entry::getValue);
        assertThrows(ConcurrentModificationException.class, iterator::next);
        assertThrows(ConcurrentModificationException.class, () -> entry.setValue(null));
        assertThrows(ConcurrentModificationException.class, iterator::remove);
    }

    @Test
    public void testConcurrentRemove() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        map.remove("two"); // structural modification
        assertEquals("one", entry.getKey());
        assertThrows(ConcurrentModificationException.class, entry::getValue);
        assertThrows(ConcurrentModificationException.class, iterator::next);
        assertThrows(ConcurrentModificationException.class, () -> entry.setValue(null));
        assertThrows(ConcurrentModificationException.class, iterator::remove);
    }

    @Test
    public void testConcurrentClear() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        map.clear(); // structural modification
        assertEquals("one", entry.getKey());
        assertThrows(ConcurrentModificationException.class, entry::getValue);
        assertThrows(ConcurrentModificationException.class, iterator::next);
        assertThrows(ConcurrentModificationException.class, () -> entry.setValue(null));
        assertThrows(ConcurrentModificationException.class, iterator::remove);
    }

    @Test
    public void testIteratorRemoveAll() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        assertEquals("one", entry.getKey());
        iterator.remove();
        assertEquals(1, map.size());
        assertFalse(map.containsKey("one"));
        entry = iterator.next();
        assertEquals("two", entry.getKey());
        assertEquals((Object) 2, entry.getValue());
        iterator.remove();
        assertTrue(map.isEmpty());
        assertFalse(iterator.hasNext());
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    public void testIteratorSetValue() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        var iterator = map.entrySet().iterator();
        var entry1 = iterator.next();
        entry1.setValue(10);
        var entry2 = iterator.next();
        entry2.setValue(20);
        assertThrows(UnsupportedOperationException.class, () -> entry1.setValue(30));
        assertEquals((Integer) 10, entry1.getValue());
        assertEquals((Integer) 20, entry2.getValue());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testMultipleIteratorsWithPut() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Iterator<Map.Entry<String, Integer>> iterator1 = map.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> iterator2 = map.entrySet().iterator();
        assertTrue(iterator1.hasNext());
        assertTrue(iterator2.hasNext());
        Map.Entry<String, Integer> entry1 = iterator1.next();
        Map.Entry<String, Integer> entry2 = iterator2.next();
        assertNotNull(entry1);
        assertNotNull(entry2);
        map.put("three", 3); // structural modification
        assertThrows(ConcurrentModificationException.class, iterator1::hasNext);
        assertThrows(ConcurrentModificationException.class, iterator2::hasNext);
    }

    @Test
    public void testMultipleIteratorsWithSetValue() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Iterator<Map.Entry<String, Integer>> iterator1 = map.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> iterator2 = map.entrySet().iterator();
        Map.Entry<String, Integer> entry1 = iterator1.next();
        Map.Entry<String, Integer> entry2 = iterator2.next();
        entry1.setValue(10);
        assertEquals((Object) 10, entry1.getValue());
        assertEquals("one", entry2.getKey());
        assertThrows(ConcurrentModificationException.class, entry2::getValue);
        assertThrows(ConcurrentModificationException.class, iterator2::next);
        assertEquals((Object) 10, map.get("one"));
    }

    @Test
    public void testMultipleIteratorsWithRemove() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Iterator<Map.Entry<String, Integer>> iterator1 = map.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> iterator2 = map.entrySet().iterator();
        Map.Entry<String, Integer> entry1 = iterator1.next();
        Map.Entry<String, Integer> entry2 = iterator2.next();
        iterator1.remove();
        assertNotNull(iterator1.next());
        assertEquals("one", entry1.getKey());
        assertEquals("one", entry2.getKey());
        assertThrows(ConcurrentModificationException.class, entry2::getValue);
        assertThrows(ConcurrentModificationException.class, iterator2::next);
        assertFalse(map.containsKey("one"));
        assertEquals((Object) 2, map.get("two"));
    }

    @Test
    public void testEntrySetRemove() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        assertFalse(map.entrySet().remove(Map.entry("one", 2)));
        assertFalse(map.entrySet().remove(Map.entry("two", 1)));
        assertTrue(map.entrySet().remove(Map.entry("one", 1)));
        assertFalse(map.entrySet().remove(Map.entry("one", 1)));
        assertFalse(map.entrySet().remove("one"));
    }

    @Test
    public void testEntrySetContains() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        assertFalse(map.entrySet().contains(Map.entry("one", 2)));
        assertFalse(map.entrySet().contains(Map.entry("two", 1)));
        assertTrue(map.entrySet().contains(Map.entry("one", 1)));
        assertFalse(map.entrySet().contains("one"));
    }

    @Test
    public void testEntrySetAdd() {
        Map<String, Integer> map = new EconomicHashMap<>();
        Map.Entry<String, Integer> entry = Map.entry("one", 1);
        assertTrue(map.entrySet().add(entry));
        assertFalse(map.entrySet().add(entry));
    }

    @Test
    public void testEntrySetClear() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.entrySet().clear();
        assertTrue(map.isEmpty());
        assertTrue(map.entrySet().isEmpty());
    }

    @Test
    public void testEntryToString() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        assertEquals(Map.entry("one", 1).toString(), entry.toString());
    }

    @Test
    public void testEntryHashCode() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        assertEquals(Map.entry("one", 1).hashCode(), entry.hashCode());
    }

    @Test
    public void testEntryEquals() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put("one", 1);
        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = iterator.next();
        assertTrue(entry.equals(Map.entry("one", 1)));
        assertFalse(entry.equals(Map.entry("one", 2)));
        assertFalse(entry.equals(Map.entry("two", 1)));
        assertTrue(entry.equals(entry));
        assertFalse(entry.equals("one"));
    }

    @Test
    public void testNullKey() {
        Map<String, Integer> map = new EconomicHashMap<>();
        assertNull(map.put(null, 1));
        assertTrue(map.containsKey(null));
        assertEquals((Object) 1, map.get(null));
        assertEquals((Object) 1, map.remove(null));
        assertFalse(map.containsKey(null));
    }

    private record NullableEntry(String key, Integer value) implements Map.Entry<String, Integer> {
        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public Integer setValue(Integer value) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testNullKeyInEntrySet() {
        Map<String, Integer> map = new EconomicHashMap<>();
        map.put(null, 1);
        assertTrue(map.entrySet().contains(new NullableEntry(null, 1)));
        assertTrue(map.entrySet().remove(new NullableEntry(null, 1)));
        assertFalse(map.entrySet().contains(new NullableEntry(null, 1)));

        map.put(null, 1);
        map.entrySet().clear();
        assertFalse(map.containsKey(null));
    }
}
