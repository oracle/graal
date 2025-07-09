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
import java.util.NoSuchElementException;
import java.util.Set;

import org.graalvm.collections.Equivalence;
import org.junit.Test;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashSet;

/**
 * Tests the {@link EconomicHashSet}.
 */
public class EconomicHashSetTest {
    @Test
    public void testNewHashSet() {
        Set<String> set = new EconomicHashSet<>();
        assertNotNull(set);
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void testNewHashSetWithInitialCapacity() {
        Set<String> set = new EconomicHashSet<>(10);
        assertNotNull(set);
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void testNewHashSetWithStrategy() {
        Set<String> set = new EconomicHashSet<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        set.add(new String("test"));
        set.add(new String("test"));
        assertEquals(2, set.size());
    }

    @Test
    public void testNewHashSetWithStrategyAndCapacity() {
        Set<String> set = new EconomicHashSet<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE, 10);
        set.add(new String("test"));
        set.add(new String("test"));
        assertEquals(2, set.size());
    }

    @Test
    public void testNewHashSetWithOtherCollection() {
        Set<String> originalSet = new EconomicHashSet<>();
        originalSet.add("element1");
        originalSet.add("element2");
        Set<String> set = new EconomicHashSet<>(originalSet);
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertEquals(2, set.size());
        assertTrue(set.contains("element1"));
        assertTrue(set.contains("element2"));
    }

    @Test
    public void testSetOfEmpty() {
        Set<String> set = CollectionsUtil.setOf();
        assertTrue(set.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> set.add("one")); // unmodifiable
    }

    @Test
    public void testSetOf() {
        Set<String> set = CollectionsUtil.setOf("element1", "element2");
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertEquals(2, set.size());
        assertTrue(set.contains("element1"));
        assertTrue(set.contains("element2"));
        assertThrows(UnsupportedOperationException.class, set::clear); // unmodifiable
    }

    @Test
    public void testSetCopyOf() {
        Set<String> originalSet = new EconomicHashSet<>();
        originalSet.add("element1");
        originalSet.add("element2");
        Set<String> set = CollectionsUtil.setCopyOf(originalSet);
        assertNotNull(set);
        assertFalse(set.isEmpty());
        assertEquals(2, set.size());
        assertTrue(set.contains("element1"));
        assertTrue(set.contains("element2"));
        assertThrows(UnsupportedOperationException.class, set::clear); // unmodifiable
    }

    @Test
    public void testAddElement() {
        Set<String> set = new EconomicHashSet<>();
        assertTrue(set.add("element"));
        assertFalse(set.isEmpty());
        assertEquals(1, set.size());
        assertTrue(set.contains("element"));
    }

    @Test
    public void testAddDuplicateElement() {
        Set<String> set = new EconomicHashSet<>();
        assertTrue(set.add("element"));
        assertFalse(set.add("element"));
        assertEquals(1, set.size());
    }

    @Test
    public void testRemoveElement() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element");
        assertTrue(set.remove("element"));
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveNonExistentElement() {
        Set<String> set = new EconomicHashSet<>();
        assertFalse(set.remove("element"));
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void testClear() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        set.clear();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void testIterator() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        var iterator = set.iterator();
        assertTrue(iterator.hasNext());
        assertEquals("element1", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("element2", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testIteratorRemove() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        var iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertEquals(1, set.size());
        assertFalse(set.contains("element1"));
    }

    @Test
    public void testIteratorRemoveTwice() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        var iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    public void testConcurrentRemove() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        var iterator = set.iterator();
        iterator.next();
        set.remove("element1"); // structural modification
        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    public void testConcurrentAdd() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        var iterator = set.iterator();
        iterator.next();
        set.add("element3"); // structural modification
        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    public void testConcurrentClear() {
        Set<String> set = new EconomicHashSet<>();
        set.add("element1");
        set.add("element2");
        var iterator = set.iterator();
        iterator.next();
        set.clear(); // structural modification
        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    public void testNullElement() {
        Set<String> set = new EconomicHashSet<>();
        assertTrue(set.add(null));
        assertFalse(set.add(null));
        assertEquals(1, set.size());
        assertTrue(set.contains(null));
        assertTrue(set.remove(null));
        assertFalse(set.remove(null));
        assertTrue(set.isEmpty());

        set.add(null);
        set.clear();
        assertFalse(set.contains(null));

        set.add(null);
        var iterator = set.iterator();
        assertTrue(iterator.hasNext());
        assertNull(iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);

        set.add(null);
        iterator = set.iterator();
        iterator.next();
        iterator.remove();
        assertTrue(set.isEmpty());
    }
}
