/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
