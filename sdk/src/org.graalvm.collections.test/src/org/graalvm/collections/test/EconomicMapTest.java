/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedHashMap;

import org.graalvm.collections.EconomicMap;
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
    public void testMapPutAll() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        EconomicMap<Integer, Integer> newMap = EconomicMap.wrapMap(new LinkedHashMap<>());
        newMap.put(1, 1);
        newMap.put(2, 4);
        map.putAll(newMap);
        Assert.assertEquals(map.size(), 2);

        UnmodifiableEconomicMap<Integer, Integer> unmodifiableEconomicMap = EconomicMap.create(newMap);

        map.removeKey(1);
        map.put(2, 2);
        map.put(3, 9);

        map.putAll(unmodifiableEconomicMap);
        Assert.assertEquals(map.size(), 3);
        Assert.assertEquals(map.get(2), Integer.valueOf(4));
    }

    @Test
    public void testToString() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        Assert.assertEquals(map.toString(), "map(size=2, {(0,0),(1,1)})");
    }

    @Test
    public void testPutIfAbsent() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        Assert.assertNull(map.putIfAbsent(1, 2));
        Assert.assertEquals(Integer.valueOf(2), map.get(1));
        Assert.assertEquals(Integer.valueOf(2), map.putIfAbsent(1, 4));
        Assert.assertEquals(map.toString(), "map(size=1, {(1,2)})");
        map.removeKey(1);
        Assert.assertNull(map.putIfAbsent(1, 5));
        Assert.assertEquals(Integer.valueOf(5), map.get(1));
    }

}
