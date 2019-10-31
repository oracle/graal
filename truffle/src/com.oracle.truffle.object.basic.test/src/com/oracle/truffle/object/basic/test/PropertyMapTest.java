/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

    @SuppressWarnings("deprecation")
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
