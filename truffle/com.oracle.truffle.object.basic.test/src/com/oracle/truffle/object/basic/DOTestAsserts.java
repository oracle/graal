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
package com.oracle.truffle.object.basic;

import org.junit.Assert;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.object.LocationImpl;
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.basic.BasicLocations.PrimitiveLocationDecorator;

public abstract class DOTestAsserts {

    public static void assertLocationFields(Location location, int prims, int objects) {
        LocationImpl locationImpl = (LocationImpl) location;
        Assert.assertEquals(prims, locationImpl.primitiveFieldCount());
        Assert.assertEquals(objects, locationImpl.objectFieldCount());
    }

    public static void assertShapeFields(DynamicObject object, int prims, int objects) {
        ShapeImpl shape = (ShapeImpl) object.getShape();
        Assert.assertEquals(objects, shape.getObjectFieldSize());
        Assert.assertEquals(prims, shape.getPrimitiveFieldSize());
        Assert.assertEquals(0, shape.getObjectArraySize());
        Assert.assertEquals(0, shape.getPrimitiveArraySize());
    }

    public static void assertSameLocation(Location location1, Location location2) {
        Assert.assertEquals(
                        ((PrimitiveLocationDecorator) location1).getInternalLocation(),
                        ((PrimitiveLocationDecorator) location2).getInternalLocation());
    }
}
