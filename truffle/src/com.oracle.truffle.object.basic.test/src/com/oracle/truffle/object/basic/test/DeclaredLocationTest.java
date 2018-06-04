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
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class DeclaredLocationTest {

    final Layout layout = new DefaultLayoutFactory().createLayout(Layout.newLayout());
    final Shape rootShape = layout.createShape(new ObjectType());
    final Object value = new Object();
    final Location declaredLocation = rootShape.allocator().declaredLocation(value);
    final Shape shapeWithDeclared = rootShape.addProperty(Property.create("declared", declaredLocation, 0));

    @Test
    public void testDeclaredLocation() {
        DynamicObject object = shapeWithDeclared.newInstance();
        Assert.assertSame(value, object.get("declared"));

        object.set("declared", value);
        Assert.assertSame(shapeWithDeclared, object.getShape());

        Property property = object.getShape().getProperty("declared");
        Assert.assertEquals(true, property.getLocation().canStore(value));
        Assert.assertEquals(true, property.getLocation().canSet(value));
        try {
            property.set(object, value, shapeWithDeclared);
        } catch (IncompatibleLocationException | FinalLocationException e) {
            Assert.fail(e.getMessage());
        }

        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        Assert.assertEquals(false, property.getLocation().canSet(newValue));
        try {
            property.set(object, newValue, shapeWithDeclared);
            Assert.fail();
        } catch (FinalLocationException | IncompatibleLocationException e) {
            Assert.assertTrue(e instanceof FinalLocationException);
        }

        Assert.assertSame(value, object.get("declared"));
    }

    @Test
    public void testMigrateDeclaredLocation() {
        DynamicObject object = shapeWithDeclared.newInstance();
        Assert.assertSame(shapeWithDeclared, object.getShape());
        Assert.assertSame(value, object.get("declared"));

        Object newValue = new Object();
        object.set("declared", newValue);
        Assert.assertNotSame(shapeWithDeclared, object.getShape());
        Assert.assertSame(newValue, object.get("declared"));
    }

    @Test
    public void testAddDeclaredLocation() {
        Property property = shapeWithDeclared.getProperty("declared");

        DynamicObject object = rootShape.newInstance();
        property.setSafe(object, value, rootShape, shapeWithDeclared);
        Assert.assertSame(shapeWithDeclared, object.getShape());
        Assert.assertSame(value, object.get("declared"));

        DynamicObject object2 = rootShape.newInstance();
        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        Assert.assertEquals(false, property.getLocation().canSet(newValue));
        try {
            property.set(object2, newValue, rootShape, shapeWithDeclared);
            Assert.fail();
        } catch (IncompatibleLocationException e) {
            // Expected
        }
        Assert.assertSame(rootShape, object2.getShape());
        Assert.assertEquals(false, object2.containsKey("declared"));
    }

}
