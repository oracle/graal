/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.ext.test;

import static com.oracle.truffle.object.basic.test.DOTestAsserts.assertShape;
import static com.oracle.truffle.object.basic.test.DOTestAsserts.assertShapeFields;
import static com.oracle.truffle.object.basic.test.DOTestAsserts.getLocationType;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class SharedShapeTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.values());
    }

    final Shape rootShape = Shape.newBuilder().layout(CustomLayoutTest.TestDynamicObject2.class, MethodHandles.lookup()).allowImplicitCastIntToLong(true).build();
    final Shape sharedShape = rootShape.makeSharedShape();

    private DynamicObject newInstance() {
        return new CustomLayoutTest.TestDynamicObject2(rootShape);
    }

    private DynamicObject newInstanceShared() {
        return new CustomLayoutTest.TestDynamicObject2(sharedShape);
    }

    @Test
    public void testDifferentLocationsImplicitCast() {
        DynamicObject object = newInstanceShared();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 1);
        Location location1 = object.getShape().getProperty("a").getLocation();
        library.put(object, "a", 2L);
        Location location2 = object.getShape().getProperty("a").getLocation();

        assertNotSameLocation(location1, location2);
        Assert.assertEquals(Object.class, getLocationType(location2));

        // The old location can still be read
        Assert.assertEquals(1, location1.get(object));
        Assert.assertEquals(2L, location2.get(object));
    }

    @Test
    public void testNoReuseOfPreviousLocation() {
        DynamicObject object = newInstanceShared();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 0);
        library.put(object, "a", 1);

        assertShape(new String[]{
                        "\"a\":int@0"},
                        object.getShape());

        Location location1 = object.getShape().getProperty("a").getLocation();
        library.put(object, "a", 2L);
        Location location2 = object.getShape().getProperty("a").getLocation();

        assertShape(new String[]{
                        "\"a\":Object@0"},
                        object.getShape());

        assertNotSameLocation(location1, location2);
        Assert.assertEquals(Object.class, getLocationType(location2));

        library.put(object, "b", 3);
        Location locationB = object.getShape().getProperty("b").getLocation();

        assertShape(new String[]{
                        "\"b\":int@1",
                        "\"a\":Object@0"},
                        object.getShape());
        assertShapeFields(object, 2, 1);

        // The old location can still be read
        Assert.assertEquals(1, location1.get(object));
        Assert.assertEquals(2L, location2.get(object));
        Assert.assertEquals(3, locationB.get(object));
    }

    @Test
    public void testCanReuseLocationsUntilShared() {
        DynamicObject object = newInstance();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 1);
        Location locationA1 = object.getShape().getProperty("a").getLocation();
        library.put(object, "a", 2L);
        Location locationA2 = object.getShape().getProperty("a").getLocation();

        assertNotSameLocation(locationA1, locationA2);
        Assert.assertEquals(long.class, getLocationType(locationA2));
        assertShape(new String[]{
                        "\"a\":long@0"},
                        object.getShape());
        assertShapeFields(object, 1, 0);

        // Share object
        library.markShared(object);

        library.put(object, "b", 3);
        Location locationB1 = object.getShape().getProperty("b").getLocation();

        assertShape(new String[]{
                        "\"b\":int@1",
                        "\"a\":long@0"},
                        object.getShape());

        library.put(object, "b", 4L);
        Location locationB2 = object.getShape().getProperty("b").getLocation();

        assertShape(new String[]{
                        "\"b\":Object@0",
                        "\"a\":long@0"},
                        object.getShape());

        library.put(object, "c", 5);

        assertNotSameLocation(locationB1, locationB2);
        Assert.assertEquals(Object.class, getLocationType(locationB2));
        assertShape(new String[]{
                        "\"c\":int@2",
                        "\"b\":Object@0",
                        "\"a\":long@0"},
                        object.getShape());
        assertShapeFields(object, 3, 1);

        Assert.assertEquals(2L, locationA2.get(object));
        // The old location can still be read
        Assert.assertEquals(3, locationB1.get(object));
        Assert.assertEquals(4L, locationB2.get(object));
        Assert.assertEquals(5, library.getOrDefault(object, "c", null));
    }

    @Test
    public void testShapeIsSharedAndIdentity() {
        DynamicObject object = newInstance();
        Assert.assertEquals(false, rootShape.isShared());
        Assert.assertSame(sharedShape, rootShape.makeSharedShape());
        Assert.assertEquals(true, sharedShape.isShared());

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.markShared(object);
        Assert.assertSame(sharedShape, object.getShape());

        library.put(object, "a", 1);
        final Shape sharedShapeWithA = object.getShape();
        Assert.assertEquals(true, sharedShapeWithA.isShared());

        DynamicObject object2 = newInstance();
        library.markShared(object2);
        Assert.assertSame(sharedShape, object2.getShape());
        library.put(object2, "a", 1);
        Assert.assertSame(sharedShapeWithA, object2.getShape());

        // Currently, sharing is a transition and transitions do not commute magically
        DynamicObject object3 = newInstance();
        library.put(object3, "a", 1);
        library.markShared(object3);
        Assert.assertNotSame(sharedShapeWithA, object3.getShape());
    }

    @Test
    public void testReuseReplaceProperty() {
        DynamicObject object = newInstanceShared();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 0);
        library.put(object, "a", 1);
        Location location1 = object.getShape().getProperty("a").getLocation();
        library.putWithFlags(object, "a", 2, 42);
        Location location2 = object.getShape().getProperty("a").getLocation();
        assertSameLocation(location1, location2);
    }

    @Test
    public void testDeleteFromSharedShape() {
        DynamicObject object = newInstanceShared();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 1);
        Shape aShape = object.getShape();
        library.removeKey(object, "a");

        library.put(object, "a", 2);
        Assert.assertNotSame(aShape, object.getShape());
        assertNotSameLocation(aShape.getProperty("a").getLocation(), object.getShape().getProperty("a").getLocation());
        library.put(object, "b", 3);
        assertNotSameLocation(aShape.getProperty("a").getLocation(), object.getShape().getProperty("b").getLocation());
    }

    @Test
    public void testReplaceProperty() {
        DynamicObject object = newInstance();

        DynamicObjectLibrary library = createLibrary(DynamicObjectLibrary.class, object);

        library.put(object, "a", 1);
        library.markShared(object);
        library.put(object, "b", 2);
        library.setPropertyFlags(object, "a", 1);
        Assert.assertTrue(library.isShared(object));
        Assert.assertEquals(1, library.getOrDefault(object, "a", null));
        Assert.assertEquals(1, library.getProperty(object, "a").getFlags());
    }

    public static void assertSameLocation(Location location1, Location location2) {
        Assert.assertSame(location1, location2);
    }

    public static void assertNotSameLocation(Location location1, Location location2) {
        Assert.assertNotSame(location1, location2);
    }
}
