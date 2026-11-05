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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.junit.Assert;
import org.junit.Test;

public class EconomicMapTest {

    @Test
    public void testMapGetDefault() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 1);
        Assert.assertEquals(map.get(0, 2), Integer.valueOf(1));
        Assert.assertEquals(map.get(1, 2), Integer.valueOf(2));
    }

    @Test
    public void testClear() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        map.clear();
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void testRemoveKey() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        Assert.assertEquals(Integer.valueOf(0), map.removeKey(0));
        Assert.assertNull(map.get(0));
    }

    @Test
    public void testReplaceAll() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        map.replaceAll((k, v) -> v + 1);
        Assert.assertEquals(Integer.valueOf(1), map.get(0));
        Assert.assertEquals(Integer.valueOf(2), map.get(1));
    }

    @Test
    public void testCreateWithInitialCapacity() {
        EconomicMap<Integer, Integer> map = EconomicMap.create(10);
        for (int i = 0; i < 10; i++) {
            map.put(i, i);
        }
        Assert.assertEquals(10, map.size());
    }

    @Test
    public void testCreateWithEquivalence() {
        EconomicMap<Integer, Integer> map = EconomicMap.create(Equivalence.DEFAULT);
        map.put(0, 0);
        Assert.assertEquals(Integer.valueOf(0), map.get(0));
    }

    @Test
    public void testWrapMap() {
        Map<Integer, Integer> javaMap = new HashMap<>();
        javaMap.put(0, 0);
        EconomicMap<Integer, Integer> map = EconomicMap.wrapMap(javaMap);
        Assert.assertEquals(Integer.valueOf(0), map.get(0));
    }

    @Test
    public void testEmptyCursor() {
        MapCursor<Integer, Integer> cursor = EconomicMap.emptyCursor();
        Assert.assertFalse(cursor.advance());
    }

    @Test
    public void testEmptyMap() {
        EconomicMap<Integer, Integer> map = EconomicMap.emptyMap();
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void testOf() {
        EconomicMap<Integer, Integer> map = EconomicMap.of(0, 0);
        Assert.assertEquals(Integer.valueOf(0), map.get(0));
    }

    @Test
    public void testOfTwoElements() {
        EconomicMap<Integer, Integer> map = EconomicMap.of(0, 0, 1, 1);
        Assert.assertEquals(Integer.valueOf(0), map.get(0));
        Assert.assertEquals(Integer.valueOf(1), map.get(1));
    }

    @Test
    public void testComputeIfAbsent() {
        for (EconomicMap<Integer, Integer> map : computeIfAbsentMaps()) {
            Assert.assertEquals(Integer.valueOf(0), map.computeIfAbsent(0, k -> 0));
            Assert.assertEquals(Integer.valueOf(0), map.get(0));
            Assert.assertEquals(Integer.valueOf(0), map.computeIfAbsent(0, k -> {
                throw new AssertionError("Existing non-null value must bypass the mapping function");
            }));
        }
    }

    /**
     * Verifies that a null computation leaves the key absent and preserves unrelated mappings.
     * Repeated calls retry the computation, and a later non-null result creates the mapping.
     */
    @Test
    public void testComputeIfAbsentNullResult() {
        for (EconomicMap<Integer, Integer> map : computeIfAbsentMaps()) {
            map.put(1, 1);
            int[] calls = {0};
            for (int i = 0; i < 2; i++) {
                Assert.assertNull(map.computeIfAbsent(0, k -> {
                    Assert.assertEquals(Integer.valueOf(0), k);
                    calls[0]++;
                    return null;
                }));
                Assert.assertFalse(map.containsKey(0));
                Assert.assertEquals(1, map.size());
                Assert.assertEquals(Integer.valueOf(1), map.get(1));
            }
            Assert.assertEquals(2, calls[0]);
            Assert.assertEquals(Integer.valueOf(0), map.computeIfAbsent(0, k -> 0));
            Assert.assertEquals(Integer.valueOf(0), map.get(0));
            Assert.assertEquals(2, map.size());
        }
    }

    /**
     * Checks that an existing null-valued mapping invokes the function, remains present after a null
     * result, and is replaced by a later non-null result without changing the map size.
     */
    @Test
    public void testComputeIfAbsentNullMapping() {
        for (EconomicMap<Integer, Integer> map : computeIfAbsentMaps()) {
            map.put(0, null);
            int[] calls = {0};
            Assert.assertNull(map.computeIfAbsent(0, k -> {
                calls[0]++;
                return null;
            }));
            Assert.assertEquals(1, calls[0]);
            Assert.assertTrue(map.containsKey(0));
            Assert.assertNull(map.get(0));
            Assert.assertEquals(1, map.size());
            Assert.assertEquals(Integer.valueOf(2), map.computeIfAbsent(0, k -> 2));
            Assert.assertEquals(Integer.valueOf(2), map.get(0));
            Assert.assertEquals(1, map.size());
        }
    }

    /**
     * Verifies that a mapping-function exception is propagated unchanged and does not create an
     * absent mapping or alter an existing null-valued mapping.
     */
    @Test
    public void testComputeIfAbsentException() {
        for (EconomicMap<Integer, Integer> map : computeIfAbsentMaps()) {
            for (boolean present : new boolean[]{false, true}) {
                if (present) {
                    map.put(0, null);
                }
                IllegalStateException failure = new IllegalStateException("Mapping failed");
                Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> map.computeIfAbsent(0, k -> {
                    throw failure;
                })));
                Assert.assertEquals(present, map.containsKey(0));
                Assert.assertNull(map.get(0));
                Assert.assertEquals(present ? 1 : 0, map.size());
            }
        }
    }

    /**
     * Checks that every wrapped-map computation delegates to the backing map with the original
     * function, including cache hits and null results. The backing map controls function invocation
     * and whether a computed value is stored.
     */
    @Test
    public void testWrapComputeIfAbsentDelegates() {
        int[] computations = {0};
        int[] delegations = {0};
        Function<Integer, Integer> mappingFunction = k -> {
            computations[0]++;
            return k == 0 ? 42 : null;
        };
        Map<Integer, Integer> javaMap = new HashMap<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Integer computeIfAbsent(Integer key, Function<? super Integer, ? extends Integer> function) {
                Assert.assertSame(mappingFunction, function);
                delegations[0]++;
                return super.computeIfAbsent(key, function);
            }
        };
        EconomicMap<Integer, Integer> map = EconomicMap.wrapMap(javaMap);
        Assert.assertEquals(Integer.valueOf(42), map.computeIfAbsent(0, mappingFunction));
        Assert.assertEquals(Integer.valueOf(42), map.computeIfAbsent(0, mappingFunction));
        Assert.assertNull(map.computeIfAbsent(1, mappingFunction));
        Assert.assertNull(map.computeIfAbsent(1, mappingFunction));
        Assert.assertEquals(4, delegations[0]);
        Assert.assertEquals(3, computations[0]);
        Assert.assertEquals(Integer.valueOf(42), javaMap.get(0));
        Assert.assertFalse(javaMap.containsKey(1));
        Assert.assertEquals(1, javaMap.size());
    }

    private static List<EconomicMap<Integer, Integer>> computeIfAbsentMaps() {
        return List.of(EconomicMap.create(), EconomicMap.wrapMap(new HashMap<>()));
    }

    @Test
    public void testMapPutAll() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        EconomicMap<Integer, Integer> newMap = EconomicMap.wrapMap(new LinkedHashMap<>());
        newMap.put(1, 1);
        newMap.put(2, 4);
        map.putAll(newMap);
        Assert.assertEquals(2, map.size());

        UnmodifiableEconomicMap<Integer, Integer> unmodifiableEconomicMap = EconomicMap.create(newMap);

        map.removeKey(1);
        map.put(2, 2);
        map.put(3, 9);

        map.putAll(unmodifiableEconomicMap);
        Assert.assertEquals(3, map.size());
        Assert.assertEquals(map.get(2), Integer.valueOf(4));
    }

    @Test
    public void testToString() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        Assert.assertEquals("(size=2, {(0,0),(1,1)})", map.toString());
    }

    @Test
    public void testPutIfAbsent() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        Assert.assertNull(map.putIfAbsent(1, 2));
        Assert.assertEquals(Integer.valueOf(2), map.get(1));
        Assert.assertEquals(Integer.valueOf(2), map.putIfAbsent(1, 4));
        Assert.assertEquals("(size=1, {(1,2)})", map.toString());
        map.removeKey(1);
        Assert.assertNull(map.putIfAbsent(1, 5));
        Assert.assertEquals(Integer.valueOf(5), map.get(1));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutNullKey() {
        EconomicMap.create(0).put(null, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContainsNullKey() {
        EconomicMap.create(0).containsKey(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetNullKey() {
        EconomicMap.create(0).get(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveNullKey() {
        EconomicMap.create(0).removeKey(null);
    }
}
