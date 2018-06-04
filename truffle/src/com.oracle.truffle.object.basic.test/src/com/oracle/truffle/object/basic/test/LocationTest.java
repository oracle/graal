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
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.Shape.Allocator;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class LocationTest {

    final Layout layout = new DefaultLayoutFactory().createLayout(Layout.newLayout());
    final Shape rootShape = layout.createShape(new ObjectType());

    static Class<?> getLocationType(Location location) {
        return ((com.oracle.truffle.api.object.TypedLocation) location).getType();
    }

    @Test
    public void testOnlyObjectLocationForObject() {
        DynamicObject object = rootShape.newInstance();
        object.define("obj", new Object());
        Location location = object.getShape().getProperty("obj").getLocation();
        Assert.assertTrue(location instanceof ObjectLocation);
        DOTestAsserts.assertLocationFields(location, 0, 1);
        DOTestAsserts.assertShapeFields(object, 0, 1);
    }

    @Test
    public void testOnlyPrimLocationForPrimitive() {
        DynamicObject object = rootShape.newInstance();
        object.define("prim", 42);
        Location location = object.getShape().getProperty("prim").getLocation();
        Assert.assertEquals(int.class, getLocationType(location));
        DOTestAsserts.assertLocationFields(location, 1, 0);
        DOTestAsserts.assertShapeFields(object, 1, 0);
    }

    @Test
    public void testPrim2Object() {
        DynamicObject object = rootShape.newInstance();
        object.define("foo", 42);
        Location location1 = object.getShape().getProperty("foo").getLocation();
        Assert.assertEquals(int.class, getLocationType(location1));
        DOTestAsserts.assertLocationFields(location1, 1, 0);
        DOTestAsserts.assertShapeFields(object, 1, 0);

        object.set("foo", new Object());
        Location location2 = object.getShape().getProperty("foo").getLocation();
        Assert.assertEquals(Object.class, getLocationType(location2));
        DOTestAsserts.assertLocationFields(location2, 0, 1);
        DOTestAsserts.assertShapeFields(object, 1, 1);
    }

    @Test
    public void testUnrelatedPrimitivesGoToObject() {
        DynamicObject object = rootShape.newInstance();
        object.define("foo", 42L);
        Location location1 = object.getShape().getProperty("foo").getLocation();
        Assert.assertEquals(long.class, getLocationType(location1));
        DOTestAsserts.assertLocationFields(location1, 1, 0);
        DOTestAsserts.assertShapeFields(object, 1, 0);

        object.set("foo", 3.14);
        Location location2 = object.getShape().getProperty("foo").getLocation();
        Assert.assertEquals(Object.class, getLocationType(location2));
        DOTestAsserts.assertLocationFields(location2, 0, 1);
        DOTestAsserts.assertShapeFields(object, 1, 1);
    }

    @Test
    public void testChangeFlagsReuseLocation() {
        DynamicObject object = rootShape.newInstance();
        object.define("foo", 42);
        Location location = object.getShape().getProperty("foo").getLocation();

        object.define("foo", 43, 111);
        Assert.assertEquals(43, object.get("foo"));
        Property newProperty = object.getShape().getProperty("foo");
        Assert.assertEquals(111, newProperty.getFlags());
        Location newLocation = newProperty.getLocation();
        Assert.assertSame(location, newLocation);
    }

    @Test
    public void testChangeFlagsChangeLocation() {
        DynamicObject object = rootShape.newInstance();
        object.define("foo", 42);
        Location location = object.getShape().getProperty("foo").getLocation();

        object.define("foo", "str", 111);
        Assert.assertEquals("str", object.get("foo"));
        Property newProperty = object.getShape().getProperty("foo");
        Assert.assertEquals(111, newProperty.getFlags());
        Location newLocation = newProperty.getLocation();
        Assert.assertNotSame(location, newLocation);
    }

    @Test
    public void testDelete() {
        DynamicObject object = rootShape.newInstance();
        object.define("a", 1);
        object.define("b", 2);
        object.delete("a");
        Assert.assertFalse(object.containsKey("a"));
        Assert.assertTrue(object.containsKey("b"));
        Assert.assertEquals(2, object.get("b"));
        object.define("a", 3);
        object.delete("b");
        Assert.assertEquals(3, object.get("a"));
    }

    @Test
    public void testLocationDecoratorEquals() {
        Allocator allocator = rootShape.allocator();
        Location intLocation1 = allocator.locationForType(int.class);
        Location intLocation2 = allocator.locationForType(int.class);
        Assert.assertEquals(intLocation1.getClass(), intLocation2.getClass());
        Assert.assertNotEquals(intLocation1, intLocation2);
    }

    @Test
    public void testDeleteDeclaredProperty() {
        DynamicObject object = rootShape.newInstance();
        object.define("a", new Object(), 0, new LocationFactory() {
            public Location createLocation(Shape shape, Object value) {
                return shape.allocator().declaredLocation(value);
            }
        });
        Assert.assertTrue(object.containsKey("a"));
        object.define("a", 42);
        Assert.assertEquals(1, object.getShape().getPropertyCount());
        object.delete("a");
        Assert.assertFalse(object.containsKey("a"));
    }
}
