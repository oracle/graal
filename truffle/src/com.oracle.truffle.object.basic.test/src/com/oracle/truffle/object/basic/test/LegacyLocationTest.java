/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

@SuppressWarnings("deprecation")
public class LegacyLocationTest {

    final Layout layout = Layout.newLayout().build();
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
