/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test.examples.om2;

import java.util.function.Predicate;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = DynamicObjectLibrary.class, receiverClass = DynamicObject.class)
public class DefaultDynamicObject {

    @ExportMessage
    static boolean accepts(DynamicObject receiver,
                    @Cached(value = "receiver.getShape()", uncached = "receiver.getShape()") Shape cachedShape) {
        return receiver.getShape() == cachedShape;
    }

    @ExportMessage
    static Object getOrDefault(DynamicObject receiver, Object key, Object defaultValue,
                    @Cached(value = "receiver.getShape()", uncached = "receiver.getShape()") Shape cachedShape) {
        return null;
    }

    @ExportMessage
    static int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) {
        return 0;
    }

    @ExportMessage
    static double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) {
        return 0.0D;
    }

    @ExportMessage
    static long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) {
        return 0L;
    }

    @ExportMessage
    static boolean getBooleanOrDefault(DynamicObject object, Object key, Object defaultValue) {
        return false;
    }

    @ExportMessage
    static boolean put(DynamicObject object, Object key, Object value, PutConfig config) {
        return false;
    }

    @ExportMessage
    static boolean putInt(DynamicObject object, Object key, int value, PutConfig config) {
        return false;
    }

    @ExportMessage
    static boolean putDouble(DynamicObject object, Object key, double value, PutConfig config) {
        return false;
    }

    @ExportMessage
    static boolean putLong(DynamicObject object, Object key, long value, PutConfig config) {
        return false;
    }

    @ExportMessage
    static boolean putBoolean(DynamicObject object, Object key, boolean value, PutConfig config) {
        return false;
    }

    @ExportMessage
    static boolean removeKey(DynamicObject object, Object key) {
        return false;
    }

    @ExportMessage
    static boolean setTypeId(DynamicObject object, Object type) {
        return false;
    }

    @ExportMessage
    static Object getTypeId(DynamicObject object) {
        return null;
    }

    @ExportMessage
    static boolean containsKey(DynamicObject object, Object key) {
        return false;
    }

    @ExportMessage
    static int getShapeFlags(DynamicObject object) {
        return 0;
    }

    @ExportMessage
    static boolean setShapeFlags(DynamicObject object, int flags) {
        return false;
    }

    @ExportMessage
    static Property getProperty(DynamicObject object, Object key) {
        return null;
    }

    @ExportMessage
    static boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags) {
        return false;
    }

    @ExportMessage
    static boolean allPropertiesMatch(DynamicObject object, Predicate<Property> predicate) {
        return false;
    }

    @ExportMessage
    static int getPropertyCount(DynamicObject object) {
        return 0;
    }

    @ExportMessage
    static Iterable<Object> getKeys(DynamicObject object) {
        return null;
    }

    @ExportMessage
    static Object[] getKeyArray(DynamicObject object) {
        return null;
    }

    @ExportMessage
    static Iterable<Property> getProperties(DynamicObject object) {
        return null;
    }

    @ExportMessage
    static Property[] getPropertyArray(DynamicObject object) {
        return null;
    }

    @ExportMessage
    static void makeShared(DynamicObject object) {
    }

    @ExportMessage
    static boolean isShared(DynamicObject object) {
        return false;
    }

    @ExportMessage
    static boolean updateShape(DynamicObject object) {
        return false;
    }

    @ExportMessage
    static boolean resetShape(DynamicObject object, Shape otherShape) {
        return false;
    }

}
