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
import com.oracle.truffle.api.object.dsl.Nullable;
import com.oracle.truffle.api.object.dsl.Volatile;

import org.junit.Assert;

public class VolatileTest {

    @Layout
    public interface VolatileTestLayout {

        DynamicObject createVolatileTest(
                        @Volatile int volatileInt,
                        @Nullable @Volatile Thread volatileThread);

        int getVolatileInt(DynamicObject object);

        Thread getVolatileThread(DynamicObject object);

        void setVolatileInt(DynamicObject object, int value);

        void setVolatileThread(DynamicObject object, Thread value);

        boolean compareAndSetVolatileInt(DynamicObject object, int expectedValue, int value);

        boolean compareAndSetVolatileThread(DynamicObject object, Thread expectedValue, Thread value);

        int getAndSetVolatileInt(DynamicObject object, int value);

        Thread getAndSetVolatileThread(DynamicObject object, Thread value);

    }

    private static final VolatileTestLayout LAYOUT = VolatileTestLayoutImpl.INSTANCE;

    @Test
    public void testGetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14, null);
        Assert.assertEquals(14, LAYOUT.getVolatileInt(object));
        Assert.assertNull(LAYOUT.getVolatileThread(object));
    }

    @Test
    public void testSetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14, null);
        Assert.assertEquals(14, LAYOUT.getVolatileInt(object));
        LAYOUT.setVolatileInt(object, 22);
        Assert.assertEquals(22, LAYOUT.getVolatileInt(object));

        Assert.assertNull(LAYOUT.getVolatileThread(object));
        LAYOUT.setVolatileThread(object, Thread.currentThread());
        Assert.assertEquals(Thread.currentThread(), LAYOUT.getVolatileThread(object));
    }

    @Test
    public void testCompareAndSetSuccess() {
        final DynamicObject object = LAYOUT.createVolatileTest(14, null);
        Assert.assertEquals(14, LAYOUT.getVolatileInt(object));
        Assert.assertTrue(LAYOUT.compareAndSetVolatileInt(object, 14, 22));
        Assert.assertEquals(22, LAYOUT.getVolatileInt(object));

        Assert.assertNull(LAYOUT.getVolatileThread(object));
        Assert.assertTrue(LAYOUT.compareAndSetVolatileThread(object, null, Thread.currentThread()));
        Assert.assertEquals(Thread.currentThread(), LAYOUT.getVolatileThread(object));
    }

    @Test
    public void testCompareAndSetFailure() {
        final DynamicObject object = LAYOUT.createVolatileTest(14, null);
        Assert.assertEquals(14, LAYOUT.getVolatileInt(object));
        Assert.assertFalse(LAYOUT.compareAndSetVolatileInt(object, 44, 22));

        Assert.assertNull(LAYOUT.getVolatileThread(object));
        Assert.assertFalse(LAYOUT.compareAndSetVolatileThread(object, Thread.currentThread(), Thread.currentThread()));
        Assert.assertNull(LAYOUT.getVolatileThread(object));
    }

    @Test
    public void testGetAndSetVolatile() {
        final DynamicObject object = LAYOUT.createVolatileTest(14, null);
        Assert.assertEquals(14, LAYOUT.getVolatileInt(object));
        Assert.assertEquals(14, LAYOUT.getAndSetVolatileInt(object, 22));
        Assert.assertEquals(22, LAYOUT.getVolatileInt(object));

        Assert.assertNull(LAYOUT.getVolatileThread(object));
        Assert.assertEquals(null, LAYOUT.getAndSetVolatileThread(object, Thread.currentThread()));
        Assert.assertEquals(Thread.currentThread(), LAYOUT.getVolatileThread(object));
    }

}
