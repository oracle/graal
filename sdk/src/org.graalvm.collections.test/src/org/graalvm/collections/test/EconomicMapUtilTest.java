/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.junit.Assert;
import org.junit.Test;

public class EconomicMapUtilTest {
    private static final class Box {
        private final int number;

        Box(int number) {
            this.number = number;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Box)) {
                return false;
            }

            Box box = (Box) object;
            return number == box.number;
        }

        @Override
        public int hashCode() {
            return number;
        }
    }

    @Test
    public void simpleEquality() {
        EconomicMap<String, Box> map1 = EconomicMap.create();
        map1.put("foo", new Box(1));
        map1.put("bar", new Box(2));
        EconomicMap<String, Box> map2 = EconomicMap.create();
        map2.put("bar", new Box(2));
        map2.put("foo", new Box(1));
        Assert.assertTrue(EconomicMapUtil.equals(map1, map2));
        Assert.assertTrue(EconomicMapUtil.equals(map2, map1));
        Assert.assertEquals(EconomicMapUtil.hashCode(map1), EconomicMapUtil.hashCode(map2));
    }

    @Test
    public void differentSizesNotEqual() {
        EconomicMap<String, Integer> map1 = EconomicMap.create();
        EconomicMap<String, Integer> map2 = EconomicMap.create();
        map2.put("foo", 1);
        Assert.assertFalse(EconomicMapUtil.equals(map1, map2));
        Assert.assertFalse(EconomicMapUtil.equals(map2, map1));
    }

    @Test
    public void differentKeysNotEqual() {
        EconomicMap<String, Integer> map1 = EconomicMap.create();
        map1.put("foo", 1);
        EconomicMap<String, Integer> map2 = EconomicMap.create();
        map2.put("bar", 1);
        Assert.assertFalse(EconomicMapUtil.equals(map1, map2));
        Assert.assertFalse(EconomicMapUtil.equals(map2, map1));
    }

    @Test
    public void differentStrategiesNotEqual() {
        EconomicMap<Integer, String> map1 = EconomicMap.create();
        map1.put(0, "foo");
        EconomicMap<Integer, String> map2 = EconomicMap.create(Equivalence.IDENTITY);
        map2.put(0, "foo");
        Assert.assertFalse(EconomicMapUtil.equals(map1, map2));
        Assert.assertFalse(EconomicMapUtil.equals(map2, map1));
    }

    @Test
    public void lexicographicalComparison() {
        Comparator<UnmodifiableEconomicMap<String, Integer>> comparator = EconomicMapUtil.lexicographicalComparator(
                        Comparator.nullsFirst(String::compareTo), Integer::compareTo);
        List<EconomicMap<String, Integer>> expectedOrder = List.of(
                        EconomicMap.emptyMap(),
                        EconomicMap.of("a", 0, "b", 0),
                        EconomicMap.of("a", 1),
                        EconomicMap.of("a", 1, "b", 0),
                        EconomicMap.of("a", 2),
                        EconomicMap.of("a", 2, "b", 0),
                        EconomicMap.of("a", 2, "b", 1),
                        EconomicMap.of("b", 0));
        List<EconomicMap<String, Integer>> sorted = Stream.of(
                        expectedOrder.get(3),
                        expectedOrder.get(6),
                        expectedOrder.get(2),
                        expectedOrder.get(5),
                        expectedOrder.get(0),
                        expectedOrder.get(4),
                        expectedOrder.get(7),
                        expectedOrder.get(1)).sorted(comparator).collect(Collectors.toList());
        Assert.assertEquals(expectedOrder, sorted);
    }
}
