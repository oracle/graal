/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.object.PropertyMap;

public class PropertyMapTest {

    @Test
    public void testPropertyMap() {
        PropertyMap map = PropertyMap.empty();
        Map<Object, Property> referenceMap = new LinkedHashMap<>();

        Random rnd = new Random();
        final int size = 1000;
        int[] randomSequence = rnd.ints().limit(size).toArray();
        int[] shuffledSequence = randomSequence.clone();
        shuffle(shuffledSequence, rnd);

        Layout layout = Layout.newLayout().build();
        // fill the map
        for (int i = 0; i < size; i++) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            Property value = Property.create(key, newLocation(layout, id), 0);
            map = (PropertyMap) map.copyAndPut(key, value);
            referenceMap.put(key, value);
            assertEqualsOrdered(referenceMap, map);
        }

        // put the same values again, should not modify the map
        PropertyMap initial = map;
        for (int i = 0; i < size; i++) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            Property value = Property.create(key, newLocation(layout, id), 0);
            map = (PropertyMap) map.copyAndPut(key, value);
            assertSame(initial, map);
        }
        assertEqualsOrdered(referenceMap, map);

        // update existing values
        for (int i = 0; i < size; i++) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            Property value = Property.create(key, newLocation(layout, (double) id), 0);
            map = (PropertyMap) map.copyAndPut(key, value);
            referenceMap.put(key, value);
        }
        assertEqualsOrdered(referenceMap, map);
        for (int i = size - 1; i >= 0; i--) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            Property value = Property.create(key, newLocation(layout, (double) id), 0);
            map = (PropertyMap) map.copyAndPut(key, value);
            referenceMap.put(key, value);
        }
        assertEqualsOrdered(referenceMap, map);

        // update existing values, in random order
        for (int i = 0; i < size; i++) {
            int id = shuffledSequence[i];
            String key = String.valueOf(id);
            Property value = Property.create(key, newLocation(layout, (long) id), 0);
            map = (PropertyMap) map.copyAndPut(key, value);
            referenceMap.put(key, value);
        }
        assertEqualsOrdered(referenceMap, map);

        // remove keys
        for (int i = size - 10; i < size; i++) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            map = (PropertyMap) map.copyAndRemove(key);
            referenceMap.remove(key);
            assertEqualsOrdered(referenceMap, map);
        }
        for (int i = 10; i >= 0; i--) {
            int id = randomSequence[i];
            String key = String.valueOf(id);
            map = (PropertyMap) map.copyAndRemove(key);
            referenceMap.remove(key);
            assertEqualsOrdered(referenceMap, map);
        }
        for (int i = 0; i < size; i++) {
            int id = shuffledSequence[i];
            String key = String.valueOf(id);
            map = (PropertyMap) map.copyAndRemove(key);
            referenceMap.remove(key);
            assertEqualsOrdered(referenceMap, map);
        }
    }

    private static Location newLocation(Layout layout, Object id) {
        return layout.createAllocator().locationForValue(id);
    }

    void assertEqualsOrdered(Map<Object, Property> referenceMap, PropertyMap map) {
        assertEquals(referenceMap, map);
        for (Iterator<Map.Entry<Object, Property>> it1 = referenceMap.entrySet().iterator(), it2 = map.entrySet().iterator(); it1.hasNext() && it2.hasNext();) {
            Map.Entry<Object, Property> e1 = it1.next();
            Map.Entry<Object, Property> e2 = it2.next();
            assertEquals(e1.getKey(), e2.getKey());
            assertEquals(e1.getValue(), e2.getValue());
            assertEquals(it1.hasNext(), it2.hasNext());
        }
        for (Iterator<Object> it1 = new ArrayList<>(referenceMap.keySet()).listIterator(referenceMap.size()), it2 = map.reverseOrderedKeyIterator(); it1.hasNext() && it2.hasNext();) {
            assertEquals(it1.next(), it2.next());
            assertEquals(it1.hasNext(), it2.hasNext());
        }
        for (Iterator<Property> it1 = new ArrayList<>(referenceMap.values()).listIterator(referenceMap.size()), it2 = map.reverseOrderedValueIterator(); it1.hasNext() && it2.hasNext();) {
            assertEquals(it1.next(), it2.next());
            assertEquals(it1.hasNext(), it2.hasNext());
        }
        for (Iterator<Object> it1 = referenceMap.keySet().iterator(), it2 = map.keySet().iterator(); it1.hasNext() && it2.hasNext();) {
            assertEquals(it1.next(), it2.next());
            assertEquals(it1.hasNext(), it2.hasNext());
        }
        for (Iterator<Property> it1 = referenceMap.values().iterator(), it2 = map.values().iterator(); it1.hasNext() && it2.hasNext();) {
            assertEquals(it1.next(), it2.next());
            assertEquals(it1.hasNext(), it2.hasNext());
        }
    }

    private static void shuffle(int[] array, Random rnd) {
        for (int i = array.length; i > 1; i--) {
            int j = rnd.nextInt(i);
            int tmp = array[i - 1];
            array[i - 1] = array[j];
            array[j] = tmp;
        }
    }
}
