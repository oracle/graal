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
package com.oracle.truffle.object.basic.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Layout.Builder;
import com.oracle.truffle.api.object.Layout.ImplicitCast;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class SharedShapeTest {

    final Builder builder = Layout.newLayout().addAllowedImplicitCast(ImplicitCast.IntToLong);
    final Layout layout = new DefaultLayoutFactory().createLayout(builder);
    final Shape rootShape = layout.createShape(new ObjectType());
    final Shape sharedShape = rootShape.makeSharedShape();

    private static Class<?> getLocationType(Location location) {
        return ((com.oracle.truffle.api.object.TypedLocation) location).getType();
    }

    @Test
    public void testDifferentLocationsImplicitCast() {
        DynamicObject object = sharedShape.newInstance();
        object.define("a", 1);
        Location location1 = object.getShape().getProperty("a").getLocation();
        object.define("a", 2L);
        Location location2 = object.getShape().getProperty("a").getLocation();

        DOTestAsserts.assertNotSameLocation(location1, location2);
        Assert.assertEquals(Object.class, getLocationType(location2));

        // The old location can still be read
        Assert.assertEquals(1, location1.get(object));
        Assert.assertEquals(2L, location2.get(object));
    }

    @Test
    public void testNoReuseOfPreviousLocation() {
        DynamicObject object = sharedShape.newInstance();
        object.define("a", 1);
        Location location1 = object.getShape().getProperty("a").getLocation();
        object.define("a", 2L);
        Location location2 = object.getShape().getProperty("a").getLocation();

        DOTestAsserts.assertNotSameLocation(location1, location2);
        Assert.assertEquals(Object.class, getLocationType(location2));

        object.define("b", 3);
        Location locationB = object.getShape().getProperty("b").getLocation();

        DOTestAsserts.assertShape("{" +
                        "\"b\":int@1,\n" +
                        "\"a\":Object@0" +
                        "\n}", object.getShape());
        DOTestAsserts.assertShapeFields(object, 2, 1);

        // The old location can still be read
        Assert.assertEquals(1, location1.get(object));
        Assert.assertEquals(2L, location2.get(object));
        Assert.assertEquals(3, locationB.get(object));
    }

    @Test
    public void testCanReuseLocationsUntilShared() {
        DynamicObject object = rootShape.newInstance();
        object.define("a", 1);
        Location locationA1 = object.getShape().getProperty("a").getLocation();
        object.define("a", 2L);
        Location locationA2 = object.getShape().getProperty("a").getLocation();

        DOTestAsserts.assertSameLocation(locationA1, locationA2);
        Assert.assertEquals(long.class, getLocationType(locationA2));
        DOTestAsserts.assertShape("{\"a\":long@0\n}", object.getShape());
        DOTestAsserts.assertShapeFields(object, 1, 0);

        // Share object
        object.setShapeAndGrow(object.getShape(), object.getShape().makeSharedShape());

        object.define("b", 3);
        Location locationB1 = object.getShape().getProperty("b").getLocation();
        object.define("b", 4L);
        Location locationB2 = object.getShape().getProperty("b").getLocation();

        object.define("c", 5);

        DOTestAsserts.assertNotSameLocation(locationB1, locationB2);
        Assert.assertEquals(Object.class, getLocationType(locationB2));
        DOTestAsserts.assertShape("{" +
                        "\"c\":int@2,\n" +
                        "\"b\":Object@0,\n" +
                        "\"a\":long@0\n" +
                        "}", object.getShape());
        DOTestAsserts.assertShapeFields(object, 3, 1);

        Assert.assertEquals(2L, locationA2.get(object));
        // The old location can still be read
        Assert.assertEquals(3, locationB1.get(object));
        Assert.assertEquals(4L, locationB2.get(object));
        Assert.assertEquals(5, object.get("c"));
    }

    @Test
    public void testShapeIsSharedAndIdentity() {
        DynamicObject object = rootShape.newInstance();
        Assert.assertEquals(false, rootShape.isShared());
        Assert.assertSame(sharedShape, rootShape.makeSharedShape());
        Assert.assertEquals(true, sharedShape.isShared());
        object.setShapeAndGrow(rootShape, sharedShape);

        object.define("a", 1);
        final Shape sharedShapeWithA = object.getShape();
        Assert.assertEquals(true, sharedShapeWithA.isShared());

        DynamicObject object2 = rootShape.newInstance();
        object2.setShapeAndGrow(rootShape, sharedShape);
        object2.define("a", 1);
        Assert.assertSame(sharedShapeWithA, object2.getShape());

        // Currently, sharing is a transition and transitions do not commute magically
        DynamicObject object3 = rootShape.newInstance();
        object3.define("a", 1);
        object3.setShapeAndGrow(object3.getShape(), object3.getShape().makeSharedShape());
        Assert.assertNotSame(sharedShapeWithA, object3.getShape());
    }

    @Test
    public void testReuseReplaceProperty() {
        DynamicObject object = sharedShape.newInstance();
        object.define("a", 1);
        Location location1 = object.getShape().getProperty("a").getLocation();
        object.define("a", 2, 42);
        Location location2 = object.getShape().getProperty("a").getLocation();
        DOTestAsserts.assertSameLocation(location1, location2);
    }

    @Test
    public void testCannotDeleteFromSharedShape() {
        DynamicObject object = sharedShape.newInstance();
        object.define("a", 1);
        try {
            object.delete("a");
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(1, object.get("a"));
        }
    }
}
