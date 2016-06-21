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

import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Layout.ImplicitCast;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class ShapeTest {

    @Test
    public void testToString() {
        Layout layout = new DefaultLayoutFactory().createLayout(Layout.newLayout().addAllowedImplicitCast(ImplicitCast.IntToLong));

        Shape rootShape = layout.createShape(new ObjectType());
        Assert.assertEquals(id(rootShape) + "{}", rootShape.toString());

        Shape aInt = rootShape.defineProperty("a", 1, 0);
        Assert.assertEquals(id(aInt) + "{\"a\":(int)long@0,Object@0" + "\n}", aInt.toString());

        Shape aObj = aInt.defineProperty("a", new Object(), 0);
        Assert.assertEquals(id(aObj) + "{\"a\":Object@0,long@0" + "\n}", aObj.toString());

        Shape aIntBObj = aInt.defineProperty("b", new Object(), 0);
        Assert.assertEquals(id(aIntBObj) + "{" +
                        "\"b\":Object@1,long@1,\n" +
                        "\"a\":(int)long@0,Object@0" +
                        "\n}", aIntBObj.toString());

        Shape bool = rootShape.addProperty(Property.create("b", rootShape.allocator().locationForType(boolean.class), 0));
        Assert.assertEquals(id(bool) + "{\"b\":boolean@0\n}", bool.toString());

        Shape str = rootShape.addProperty(Property.create("str", rootShape.allocator().locationForType(String.class), 0));
        Assert.assertEquals(id(str) + "{\"str\":Object@0\n}", str.toString());

        Shape aIntBObjCBoolDDouble = aIntBObj.defineProperty("c", true, 0).defineProperty("d", 3.14, 0);
        Assert.assertEquals(id(aIntBObjCBoolDDouble) + "{" +
                        "\"d\":(double)long[0],Object@3,\n" +
                        "\"c\":(boolean)long@2,Object@2,\n" +
                        "\"b\":Object@1,long@1,\n" +
                        "\"a\":(int)long@0,Object@0" +
                        "\n}", aIntBObjCBoolDDouble.toString());
    }

    private static String id(Shape shape) {
        return "@" + Integer.toHexString(shape.hashCode());
    }

}
