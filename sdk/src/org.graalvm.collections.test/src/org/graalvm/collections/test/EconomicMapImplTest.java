/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
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
}
