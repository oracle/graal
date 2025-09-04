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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

public abstract class DOTestAsserts {

    public static <T> T invokeGetter(String methodName, Object receiver) {
        return invokeMethod(methodName, receiver);
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(String methodName, Object receiver, Object... args) {
        try {
            Method method = Stream.concat(Arrays.stream(receiver.getClass().getMethods()), Arrays.stream(receiver.getClass().getDeclaredMethods())).filter(
                            m -> m.getName().equals(methodName)).findFirst().orElseThrow(
                                            () -> new NoSuchElementException("Method " + methodName + " not found in " + receiver.getClass()));
            method.setAccessible(true);
            return (T) method.invoke(receiver, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    public static void assertLocationFields(Location location, int prims, int objects) {
        int primitiveFieldCount = invokeGetter("primitiveFieldCount", location);
        int objectFieldCount = invokeGetter("objectFieldCount", location);
        Assert.assertEquals(prims, primitiveFieldCount);
        Assert.assertEquals(objects, objectFieldCount);
    }

    public static void assertShapeFields(DynamicObject object, int prims, int objects) {
        Shape shape = object.getShape();
        Assert.assertEquals(objects, (int) invokeGetter("getObjectFieldSize", shape));
        Assert.assertEquals(prims, (int) invokeGetter("getPrimitiveFieldSize", shape));
        Assert.assertEquals(0, (int) invokeGetter("getObjectArraySize", shape));
        Assert.assertEquals(0, (int) invokeGetter("getPrimitiveArraySize", shape));
    }

    public static void assertSameLocation(Location location1, Location location2) {
        Assert.assertSame(getInternalLocation(location1), getInternalLocation(location2));
    }

    public static void assertNotSameLocation(Location location1, Location location2) {
        Assert.assertNotSame(getInternalLocation(location1), getInternalLocation(location2));
    }

    public static void assertSameUnderlyingLocation(Location location1, Location location2) {
        Assert.assertEquals("Expected location at the same index (" + location1 + ", " + location2 + ")",
                        getLocationIndexString(location1), getLocationIndexString(location2));
    }

    private static String getLocationIndexString(Location location) {
        Matcher matcher = Pattern.compile("\\[\\d+\\]|@\\d+").matcher(location.toString());
        if (matcher.find()) {
            return matcher.group(0);
        } else {
            throw new AssertionError("Unexpected location: " + location);
        }
    }

    private static Location getInternalLocation(Location location) {
        try {
            Class<?> locations = Class.forName("com.oracle.truffle.api.object.LocationImpl");
            Method getInternalLocationMethod = Arrays.stream(locations.getDeclaredMethods()).filter(
                            m -> m.getName().equals("getInternalLocation")).findFirst().get();
            getInternalLocationMethod.setAccessible(true);
            return (Location) getInternalLocationMethod.invoke(location);
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    public static Class<?> getLocationType(Location location) {
        try {
            Class<?> locations = Class.forName("com.oracle.truffle.api.object.LocationImpl");
            Method getInternalLocationMethod = Arrays.stream(locations.getDeclaredMethods()).filter(
                            m -> m.getName().equals("getType")).findFirst().get();
            getInternalLocationMethod.setAccessible(true);
            return (Class<?>) getInternalLocationMethod.invoke(location);
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    public static void assertShape(String[] fields, Shape shape) {
        String shapeString = shapeLocationsToString(shape);
        // ignore trailing extra info in location strings.
        String regex = Arrays.stream(fields).map(Pattern::quote).collect(Collectors.joining("[^\"]*?", "\\{", "[^\"]*?\\}"));
        assertTrue("expected " + Arrays.stream(fields).collect(Collectors.joining(", ", "{", "}")) + ", actual: " + shapeString + ", regex: " + regex,
                        Pattern.matches(regex, shapeString));
    }

    private static String shapeLocationsToString(Shape shape) {
        StringBuilder sb = new StringBuilder();
        if (!shape.isValid()) {
            sb.append('!');
        }

        sb.append("{");
        for (Iterator<Property> iterator = shape.getPropertyListInternal(false).iterator(); iterator.hasNext();) {
            Property p = iterator.next();
            sb.append("\"").append(p.getKey()).append("\":").append(p.getLocation());
            if (iterator.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");

        return sb.toString();
    }

    public static Map<Object, Object> archive(DynamicObject object) {
        DynamicObjectLibrary lib = DynamicObjectLibrary.getFactory().getUncached(object);
        Map<Object, Object> archive = new HashMap<>();
        for (Property property : lib.getPropertyArray(object)) {
            archive.put(property.getKey(), lib.getOrDefault(object, property.getKey(), null));
        }
        return archive;
    }

    public static boolean verifyValues(DynamicObject object, Map<Object, Object> archive) {
        DynamicObjectLibrary lib = DynamicObjectLibrary.getFactory().getUncached(object);
        for (Property property : lib.getPropertyArray(object)) {
            Object key = property.getKey();
            Object before = archive.get(key);
            Object after = lib.getOrDefault(object, key, null);
            assertEquals("before != after for key: " + key, after, before);
        }
        return true;
    }

    public static void assertObjectLocation(Location location) {
        assertLocationType(Object.class, location);
    }

    public static void assertPrimitiveLocation(Class<?> type, Location location) {
        assertTrue(type.getTypeName(), type.isPrimitive());
        assertLocationType(type, location);
    }

    private static void assertLocationType(Class<?> type, Location location) {
        Assert.assertFalse(location.isValue());
        Assert.assertSame(type, getLocationType(location));
    }

    public static Object getTypeAssumptionRecord(Location location) {
        return invokeGetter("getTypeAssumption", location);
    }

    public static Assumption getTypeAssumption(Location location) {
        return invokeGetter("getAssumption", getTypeAssumptionRecord(location));
    }

    public static Location locationForValue(Shape shape, Object value) {
        return invokeMethod("locationForValue", invokeGetter("allocator", shape), value);
    }
}
