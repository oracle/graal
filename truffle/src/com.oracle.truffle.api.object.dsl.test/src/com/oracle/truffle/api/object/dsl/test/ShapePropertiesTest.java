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
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.dsl.Layout;

import org.junit.Assert;

public class ShapePropertiesTest {

    @Layout
    public interface ShapePropertiesTestLayout {

        DynamicObjectFactory createShapePropertiesTestShape(int shapeProperty);

        DynamicObject createShapePropertiesTest(DynamicObjectFactory factory, int instanceProperty);

        int getShapeProperty(DynamicObjectFactory factory);

        DynamicObjectFactory setShapeProperty(DynamicObjectFactory factory, int value);

        int getShapeProperty(DynamicObject object);

        void setShapeProperty(DynamicObject object, int value);

        int getShapeProperty(ObjectType objectType);

        int getInstanceProperty(DynamicObject object);

        void setInstanceProperty(DynamicObject object, int value);

    }

    private static final ShapePropertiesTestLayout LAYOUT = ShapePropertiesTestLayoutImpl.INSTANCE;

    @Test
    public void testCreate() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        Assert.assertNotNull(LAYOUT.createShapePropertiesTest(factory, 22));
    }

    @Test
    public void testFactoryGetter() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(factory));
    }

    @Test
    public void testObjectTypeGetter() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        final DynamicObject object = LAYOUT.createShapePropertiesTest(factory, 22);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(object.getShape().getObjectType()));
    }

    @Test
    public void testObjectGetter() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        final DynamicObject object = LAYOUT.createShapePropertiesTest(factory, 22);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(object));
    }

    @Test
    public void testFactorySetter() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(factory));
        final DynamicObject object = LAYOUT.createShapePropertiesTest(factory, 22);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(object));
        final DynamicObjectFactory newFactory = LAYOUT.setShapeProperty(factory, 44);
        Assert.assertEquals(44, LAYOUT.getShapeProperty(newFactory));
        final DynamicObject newObject = LAYOUT.createShapePropertiesTest(newFactory, 22);
        Assert.assertEquals(44, LAYOUT.getShapeProperty(newObject));
    }

    @Test
    public void testObjectSetter() {
        final DynamicObjectFactory factory = LAYOUT.createShapePropertiesTestShape(14);
        final DynamicObject object = LAYOUT.createShapePropertiesTest(factory, 22);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(object));
        LAYOUT.setShapeProperty(object, 44);
        Assert.assertEquals(44, LAYOUT.getShapeProperty(object));
        final DynamicObject newObject = LAYOUT.createShapePropertiesTest(factory, 22);
        Assert.assertEquals(14, LAYOUT.getShapeProperty(newObject));
    }

}
