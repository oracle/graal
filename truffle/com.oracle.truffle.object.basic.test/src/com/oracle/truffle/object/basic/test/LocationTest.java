/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.Locations.DualLocation;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class LocationTest {

    final Layout layout = new DefaultLayoutFactory().createLayout(Layout.newLayout());
    final Shape rootShape = layout.createShape(new ObjectType());

    @Test
    public void testOnlyObjectLocationForObject() {
        DynamicObject object = rootShape.newInstance();
        object.define("obj", new Object());
        Location location = object.getShape().getProperty("obj").getLocation();
        Assert.assertTrue(location instanceof ObjectLocation);
        Assert.assertFalse(location instanceof DualLocation);
    }

    @Test
    public void testDualLocationForPrimitive() {
        DynamicObject object = rootShape.newInstance();
        object.define("prim", 42);
        Location location = object.getShape().getProperty("prim").getLocation();
        Assert.assertTrue(location instanceof DualLocation);
    }

}
