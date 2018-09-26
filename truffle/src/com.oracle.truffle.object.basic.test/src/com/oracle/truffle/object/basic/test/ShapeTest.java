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
