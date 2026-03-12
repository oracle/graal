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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.junit.Assert;
import org.junit.Test;

public class EconomicSetTest {

    @Test
    public void testUtilities() {
        EconomicSet<Integer> set = EconomicSet.create(0);
        set.add(0);
        Assert.assertTrue(set.add(1));
        Assert.assertEquals(2, set.size());
        Assert.assertFalse(set.add(1));
        Assert.assertEquals(2, set.size());
        set.remove(1);
        Assert.assertEquals(1, set.size());
        set.remove(2);
        Assert.assertEquals(1, set.size());
        Assert.assertTrue(set.add(1));
        set.clear();
        Assert.assertEquals(0, set.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContainsNull() {
        Assert.assertFalse(EconomicSet.create(0).contains(null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddNull() {
        Assert.assertFalse(EconomicSet.create(0).add(null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveNull() {
        EconomicSet.create(0).remove(null);
    }

    @Test
    public void testAddAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1, 0));
        Assert.assertEquals(2, set.size());

        EconomicSet<Integer> newSet = EconomicSet.create();
        newSet.addAll(Arrays.asList(1, 2));
        Assert.assertEquals(2, newSet.size());
        newSet.addAll(set);
        Assert.assertEquals(3, newSet.size());
    }

    @Test
    public void testRemoveAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));

        set.removeAll(Arrays.asList(1, 2));
        Assert.assertEquals(1, set.size());

        set.removeAll(EconomicSet.create(set));
        Assert.assertEquals(0, set.size());
    }

    @Test
    public void testRetainAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1, 2));

        EconomicSet<Integer> newSet = EconomicSet.create();
        newSet.addAll(Arrays.asList(2, 3));

        set.retainAll(newSet);
        Assert.assertEquals(1, set.size());
    }

    @Test
    public void testToArray() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));
        Assert.assertArrayEquals(new Integer[]{0, 1}, set.toArray(new Integer[set.size()]));
    }

    @Test
    public void testToString() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));
        Assert.assertEquals("(size=2, {0,1})", set.toString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testToUnalignedArray() {
        Assert.assertArrayEquals(new Integer[0], EconomicSet.create().toArray(new Integer[2]));
    }

    @Test
    public void testSetRemoval() {
        ArrayList<Integer> initialList = new ArrayList<>();
        ArrayList<Integer> removalList = new ArrayList<>();
        ArrayList<Integer> finalList = new ArrayList<>();
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY);
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(6);
        set.add(7);
        set.add(8);
        set.add(9);
        for (Integer integer : set) {
            initialList.add(integer);
        }
        int size = 0;
        Iterator<Integer> i2 = set.iterator();
        while (i2.hasNext()) {
            Integer elem = i2.next();
            if (size++ < 8) {
                i2.remove();
            }
            removalList.add(elem);
        }
        for (Integer integer : set) {
            finalList.add(integer);
        }
        Assert.assertEquals(initialList, removalList);
        Assert.assertEquals(1, finalList.size());
        Assert.assertEquals(newInteger(9), finalList.get(0));
    }

    @SuppressWarnings({"deprecation"})
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

    @Test
    public void testCreateWithEquivalenceAndCapacity() {
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY, 1);
        Integer a1 = newInteger(1);
        Integer a2 = newInteger(1);
        Assert.assertTrue(set.add(a1));
        Assert.assertFalse(set.add(a1));
        Assert.assertFalse("identity equivalence shouldn't match equal value", set.contains(a2));
        Assert.assertEquals(1, set.size());
    }

    @Test
    public void testCreateWithEquivalenceAndUnmodifiableSet() {
        EconomicSet<Integer> source = EconomicSet.create();
        Integer a1 = newInteger(1);
        Integer a1dup = newInteger(1);
        source.add(a1);
        source.add(a1dup); // deduped by default equivalence
        EconomicSet<Integer> idSet = EconomicSet.create(Equivalence.IDENTITY, source);
        Assert.assertEquals(1, idSet.size());
        Assert.assertTrue(idSet.contains(a1));
        Assert.assertFalse(idSet.contains(newInteger(1)));
    }

    @Test
    public void testCreateFromIterable() {
        EconomicSet<Integer> set = EconomicSet.create(Arrays.asList(0, 1, 0));
        Assert.assertEquals(2, set.size());
        Assert.assertTrue(set.contains(0));
        Assert.assertTrue(set.contains(1));
    }

    @Test
    public void testAddAllIterator() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.add(0);
        set.addAll(Arrays.asList(1, 2, 1).iterator());
        Assert.assertEquals(3, set.size());
        Assert.assertTrue(set.contains(0));
        Assert.assertTrue(set.contains(1));
        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAllIterator() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1, 2, 3));
        set.removeAll(Arrays.asList(1, 3, 4).iterator());
        Assert.assertEquals(2, set.size());
        Assert.assertTrue(set.contains(0));
        Assert.assertTrue(set.contains(2));
        Assert.assertFalse(set.contains(1));
        Assert.assertFalse(set.contains(3));
    }

    @Test
    public void testIdentityEquivalenceContains() {
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY);
        Integer a1 = newInteger(42);
        Integer a2 = newInteger(42);
        set.add(a1);
        Assert.assertTrue(set.contains(a1));
        Assert.assertFalse(set.contains(a2));
    }

    @Test
    public void testEmptySetBasics() {
        EconomicSet<Integer> empty = EconomicSet.emptySet();
        Assert.assertTrue(empty.isEmpty());
        Assert.assertEquals(0, empty.size());
        Assert.assertFalse(empty.contains(123));
        Integer[] arr = empty.toArray(new Integer[0]);
        Assert.assertEquals(0, arr.length);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEmptySetContainsNull() {
        EconomicSet.emptySet().contains(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySetAdd() {
        EconomicSet.emptySet().add(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySetRemove() {
        EconomicSet.emptySet().remove(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySetClear() {
        EconomicSet.emptySet().clear();
    }

}
