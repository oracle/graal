/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.object.LocationImpl;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class ConstantLocationTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.values());
    }

    final Shape rootShape = Shape.newBuilder().build();
    final Object value = new Object();
    final Shape shapeWithConstant = Shape.newBuilder(rootShape).addConstantProperty("constant", value, 0).build();

    private DynamicObject newInstance() {
        return new TestDynamicObjectDefault(rootShape);
    }

    private DynamicObject newInstanceWithConstant() {
        return new TestDynamicObjectDefault(shapeWithConstant);
    }

    @Test
    public void testConstantLocation() {
        DynamicObject object = newInstanceWithConstant();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        Assert.assertSame(value, library.getOrDefault(object, "constant", null));

        library.putIfPresent(object, "constant", value);
        Assert.assertSame(shapeWithConstant, object.getShape());

        Property property = object.getShape().getProperty("constant");
        Assert.assertEquals(true, property.getLocation().canStore(value));
        try {
            property.getLocation().set(object, value, shapeWithConstant);
        } catch (com.oracle.truffle.api.object.IncompatibleLocationException | com.oracle.truffle.api.object.FinalLocationException e) {
            Assert.fail(e.getMessage());
        }

        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        try {
            property.getLocation().set(object, newValue, shapeWithConstant);
            Assert.fail();
        } catch (com.oracle.truffle.api.object.IncompatibleLocationException | com.oracle.truffle.api.object.FinalLocationException e) {
            MatcherAssert.assertThat(e, CoreMatchers.instanceOf(com.oracle.truffle.api.object.IncompatibleLocationException.class));
        }

        Assert.assertSame(value, library.getOrDefault(object, "constant", null));
    }

    @Test
    public void testMigrateConstantLocation() {
        DynamicObject object = newInstanceWithConstant();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        Assert.assertSame(shapeWithConstant, object.getShape());
        Assert.assertSame(value, library.getOrDefault(object, "constant", null));

        Object newValue = new Object();
        library.putIfPresent(object, "constant", newValue);
        Assert.assertNotSame(shapeWithConstant, object.getShape());
        Assert.assertSame(newValue, library.getOrDefault(object, "constant", null));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAddConstantLocation() throws com.oracle.truffle.api.object.IncompatibleLocationException {
        Property property = shapeWithConstant.getProperty("constant");

        DynamicObject object = newInstance();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        ((LocationImpl) property.getLocation()).set(object, value, rootShape, shapeWithConstant);
        Assert.assertSame(shapeWithConstant, object.getShape());
        Assert.assertSame(value, library.getOrDefault(object, "constant", null));

        DynamicObject object2 = newInstance();
        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        try {
            ((LocationImpl) property.getLocation()).set(object2, newValue, rootShape, shapeWithConstant);
            Assert.fail();
        } catch (com.oracle.truffle.api.object.IncompatibleLocationException e) {
            // expected
        }
        Assert.assertSame(rootShape, object2.getShape());
        Assert.assertEquals(false, library.containsKey(object2, "constant"));
    }

    @Test
    public void testGetConstantValue() {
        Property property = shapeWithConstant.getProperty("constant");
        Assert.assertTrue(property.getLocation().isConstant());
        Assert.assertSame(value, property.getLocation().getConstantValue());

        DynamicObject object = newInstance();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);
        library.put(object, "other", "otherValue");

        Property otherProperty = object.getShape().getProperty("other");
        Assert.assertFalse(otherProperty.getLocation().isConstant());
        Assert.assertNull(otherProperty.getLocation().getConstantValue());
    }
}
