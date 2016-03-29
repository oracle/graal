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
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;

import org.junit.Assert;

public class InheritedShapePropertiesTest {

    @Layout
    public interface ShapeBaseLayout {

        DynamicObjectFactory createShapeBaseShape(int a);

        DynamicObject createShapeBase(DynamicObjectFactory factory);

        int getA(DynamicObject object);

    }

    @Layout
    public interface ShapeTopLayout extends ShapeBaseLayout {

        DynamicObjectFactory createShapeTopShape(int a, int b);

        DynamicObject createShapeTop(DynamicObjectFactory factory);

        int getB(DynamicObject object);

    }

    private static final ShapeBaseLayout BASE_LAYOUT = ShapeBaseLayoutImpl.INSTANCE;
    private static final ShapeTopLayout TOP_LAYOUT = ShapeTopLayoutImpl.INSTANCE;

    @Test
    public void testBase() {
        final DynamicObjectFactory factory = BASE_LAYOUT.createShapeBaseShape(14);
        final DynamicObject object = BASE_LAYOUT.createShapeBase(factory);
        Assert.assertEquals(14, BASE_LAYOUT.getA(object));
    }

    @Test
    public void testTop() {
        final DynamicObjectFactory factory = TOP_LAYOUT.createShapeTopShape(14, 2);
        final DynamicObject object = TOP_LAYOUT.createShapeTop(factory);
        Assert.assertEquals(14, TOP_LAYOUT.getA(object));
        Assert.assertEquals(2, TOP_LAYOUT.getB(object));
    }

}
