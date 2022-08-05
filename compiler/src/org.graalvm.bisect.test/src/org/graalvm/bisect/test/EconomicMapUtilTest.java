/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.test;

import org.graalvm.bisect.util.EconomicMapUtil;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
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
}
