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

import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.LocationImpl;
import com.oracle.truffle.object.ShapeImpl;

public abstract class DOTestAsserts {

    public static void assertLocationFields(Location location, int prims, int objects) {
        LocationImpl locationImpl = (LocationImpl) assumeCoreLocation(location);
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
        Assert.assertSame(getInternalLocation(location1), getInternalLocation(location2));
    }

    public static void assertNotSameLocation(Location location1, Location location2) {
        Assert.assertNotSame(getInternalLocation(location1), getInternalLocation(location2));
    }

    private static Location getInternalLocation(Location location) {
        assumeCoreLocation(location);
        try {
            Class<?> locations = Class.forName("com.oracle.truffle.object.LocationImpl");
            Method getInternalLocationMethod = Arrays.stream(locations.getDeclaredMethods()).filter(
                            m -> m.getName().equals("getInternalLocation")).findFirst().get();
            getInternalLocationMethod.setAccessible(true);
            return (Location) getInternalLocationMethod.invoke(location);
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    private static Location assumeCoreLocation(Location location) {
        try {
            Class<?> locationBaseClass = Class.forName("com.oracle.truffle.object.CoreLocation");
            Assume.assumeTrue(locationBaseClass.isInstance(location));
        } catch (ClassNotFoundException | IllegalArgumentException e) {
            throw new AssertionError(e);
        }
        return location;
    }

    public static void assertShape(String[] fields, Shape shape) {
        String shapeString = shapeLocationsToString(shape);
        // ignore trailing extra info in location strings.
        String regex = Arrays.asList(fields).stream().map(Pattern::quote).collect(Collectors.joining("[^\"]*?", "\\{", "[^\"]*?\\}"));
        assertTrue("expected " + Arrays.stream(fields).collect(Collectors.joining(", ", "{", "}")) + ", actual: " + shapeString, Pattern.matches(regex, shapeString));
    }

    private static String shapeLocationsToString(Shape shape) {
        StringBuilder sb = new StringBuilder();
        if (!shape.isValid()) {
            sb.append('!');
        }

        sb.append("{");
        for (Iterator<Property> iterator = shape.getPropertyListInternal(false).iterator(); iterator.hasNext();) {
            Property p = iterator.next();
            assumeCoreLocation(p.getLocation());
            sb.append("\"").append(p.getKey()).append("\":").append(p.getLocation());
            if (iterator.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");

        return sb.toString();
    }
}
