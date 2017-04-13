/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.dsl.test;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Volatile;

import org.junit.Assert;

public class VolatileTest {

    @Layout
    public interface VolatileTestLayout {

        DynamicObject createVolatileTest(@Volatile int volatileValue);

        int getVolatileValue(DynamicObject object);

        void setVolatileValue(DynamicObject object, int value);

        boolean compareAndSetVolatileValue(DynamicObject object, int expectedValue, int value);

        int getAndSetVolatileValue(DynamicObject object, int value);

    }

    private static final VolatileTestLayout LAYOUT = VolatileTestLayoutImpl.INSTANCE;

    @Test
    public void testGetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14);
        Assert.assertEquals(14, LAYOUT.getVolatileValue(object));
    }

    @Test
    public void testSetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14);
        Assert.assertEquals(14, LAYOUT.getVolatileValue(object));
        LAYOUT.setVolatileValue(object, 22);
        Assert.assertEquals(22, LAYOUT.getVolatileValue(object));
    }

    @Test
    public void testCompareAndSetSuccess() {
        final DynamicObject object = LAYOUT.createVolatileTest(14);
        Assert.assertEquals(14, LAYOUT.getVolatileValue(object));
        Assert.assertTrue(LAYOUT.compareAndSetVolatileValue(object, 14, 22));
        Assert.assertEquals(22, LAYOUT.getVolatileValue(object));
    }

    @Test
    public void testCompareAndSetFailure() {
        final DynamicObject object = LAYOUT.createVolatileTest(14);
        Assert.assertEquals(14, LAYOUT.getVolatileValue(object));
        Assert.assertFalse(LAYOUT.compareAndSetVolatileValue(object, 44, 22));
    }

    @Test
    public void testGetAndSetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14);
        Assert.assertEquals(14, LAYOUT.getVolatileValue(object));
        Assert.assertEquals(14, LAYOUT.getAndSetVolatileValue(object, 22));
        Assert.assertEquals(22, LAYOUT.getVolatileValue(object));
    }

}
