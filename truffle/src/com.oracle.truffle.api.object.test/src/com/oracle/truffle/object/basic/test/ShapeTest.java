/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.object.basic.test.DOTestAsserts.invokeMethod;
import static com.oracle.truffle.object.basic.test.DOTestAsserts.locationForValue;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class ShapeTest {

    @Parameters(name = "{0}")
    public static List<Boolean> data() {
        return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }

    @Parameter public boolean useLookup;

    private Shape makeRootShape() {
        if (useLookup) {
            return Shape.newBuilder().layout(TestDynamicObjectDefault.class, MethodHandles.lookup()).allowImplicitCastIntToLong(true).build();
        } else {
            return Shape.newBuilder().layout(TestDynamicObjectDefault.class).allowImplicitCastIntToLong(true).build();
        }
    }

    @Test
    public void testToString() {
        Shape rootShape = makeRootShape();
        DOTestAsserts.assertShape(new String[]{}, rootShape);

        Shape aInt = rootShape.defineProperty("a", 1, 0);
        DOTestAsserts.assertShape(new String[]{"\"a\":int@0"}, aInt);

        Shape aObj = aInt.defineProperty("a", new Object(), 0);
        DOTestAsserts.assertShape(new String[]{"\"a\":Object@0"}, aObj);

        Shape aObjBInt = aObj.defineProperty("b", 2, 0);
        DOTestAsserts.assertShape(new String[]{
                        "\"b\":int@0",
                        "\"a\":Object@0"}, aObjBInt);

        Shape aIntBObj = aInt.defineProperty("b", new Object(), 0);
        DOTestAsserts.assertShape(new String[]{
                        "\"b\":Object@1",
                        "\"a\":Object@0"}, aIntBObj);

        Location boolLocation = locationForValue(rootShape, true);
        Shape bool = invokeMethod("addProperty", rootShape, Property.create("bool", boolLocation, 0));
        DOTestAsserts.assertShape(new String[]{"\"bool\":Object@0"}, bool);

        Location strLocation = locationForValue(rootShape, "");
        Shape str = invokeMethod("addProperty", rootShape, Property.create("str", strLocation, 0));
        DOTestAsserts.assertShape(new String[]{"\"str\":Object@0"}, str);

        Shape shapeWithManyFields = aIntBObj.//
                        defineProperty("c", true, 0).//
                        defineProperty("d", 3.14, 0).//
                        defineProperty("e", 1L << 44, 0).//
                        defineProperty("f", 9001, 0);
        DOTestAsserts.assertShape(new String[]{
                        "\"f\":int@2",
                        "\"e\":long@1",
                        "\"d\":double@0",
                        "\"c\":Object@2",
                        "\"b\":Object@1",
                        "\"a\":Object@0"}, shapeWithManyFields);

        Shape shapeWithExtArray = makeRootShape().//
                        defineProperty("a", 1, 0).//
                        defineProperty("b", new Object(), 0).//
                        defineProperty("c", true, 0).//
                        defineProperty("d", 3.14, 0).//
                        defineProperty("e", 1L << 33, 0).//
                        defineProperty("f", 1L << 44, 0);
        DOTestAsserts.assertShape(new String[]{
                        "\"f\":long[0]",
                        "\"e\":long@2",
                        "\"d\":double@1",
                        "\"c\":Object@1",
                        "\"b\":Object@0",
                        "\"a\":int@0"}, shapeWithExtArray);
    }
}
