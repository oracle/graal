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
        DOTestAsserts.assertShape("{}", rootShape);

        Shape aInt = rootShape.defineProperty("a", 1, 0);
        DOTestAsserts.assertShape("{\"a\":int@0" + "\n}", aInt);

        Shape aObj = aInt.defineProperty("a", new Object(), 0);
        DOTestAsserts.assertShape("{\"a\":Object@0" + "\n}", aObj);

        Shape aObjBInt = aObj.defineProperty("b", 2, 0);
        DOTestAsserts.assertShape("{" +
                        "\"b\":int@1,\n" +
                        "\"a\":Object@0" +
                        "\n}", aObjBInt);

        Shape aIntBObj = aInt.defineProperty("b", new Object(), 0);
        DOTestAsserts.assertShape("{" +
                        "\"b\":Object@0,\n" +
                        "\"a\":int@0" +
                        "\n}", aIntBObj);

        Shape bool = rootShape.addProperty(Property.create("bool", rootShape.allocator().locationForType(boolean.class), 0));
        DOTestAsserts.assertShape("{\"bool\":boolean@0\n}", bool);

        Shape str = rootShape.addProperty(Property.create("str", rootShape.allocator().locationForType(String.class), 0));
        DOTestAsserts.assertShape("{\"str\":Object@0\n}", str);

        Shape shapeWithExtArray = aIntBObj.defineProperty("c", true, 0).defineProperty("d", 3.14, 0).defineProperty("e", 1L << 44, 0);
        DOTestAsserts.assertShape("{" +
                        "\"e\":long[0],\n" +
                        "\"d\":double@2,\n" +
                        "\"c\":boolean@1,\n" +
                        "\"b\":Object@0,\n" +
                        "\"a\":int@0" +
                        "\n}", shapeWithExtArray);
    }
}
