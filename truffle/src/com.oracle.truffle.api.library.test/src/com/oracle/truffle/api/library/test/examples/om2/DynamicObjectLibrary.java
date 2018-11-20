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

import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@GenerateLibrary
@DefaultExport(DefaultDynamicObject.class)
public abstract class DynamicObjectLibrary extends Library {

    /**
     * Creates a new {@link DynamicObjectLibrary} node for cached access with a constant key. The
     * resulting node caches the object shape. All property accesses use the constant key.
     *
     * @param key the constant key to be used for all property accesses
     * @return a new cache node
     * @since 1.0
     */
// @TruffleBoundary
// public static DynamicObjectLibrary createCached(Object key) {
// return Layout.getFactory().createAccessNode(key, null, null);
// }
//
// /**
// * Creates a new {@link DynamicObjectLibrary} node for cached access with a specific object
// * shape and an optional constant key. This method is provided for nesting into an outer cache
// * node that already dispatches on the shape.
// *
// * @param key the constant key to be used for all property accesses; may be null
// * @param shape the cache shape
// * @return a new cache node
// * @since 1.0
// */
// @TruffleBoundary
// public static DynamicObjectLibrary createCached(Object key, Shape shape) {
// return Layout.getFactory().createAccessNode(key, shape, null);
// }
//
// /**
// * Creates a new {@link DynamicObjectLibrary} node for cached access with a specific constant
// * object and an optional constant key. All access methods must be passed the same object and
// * property key (if provided). This allows the cache node to take assumptions about the object's
// * properties regardless of its current shape.
// *
// * @param key the constant key to be used for all property accesses; may be null
// * @param object the constant object
// * @return a new cache node
// * @since 1.0
// */
// @TruffleBoundary
// public static DynamicObjectLibrary createCached(Object key, DynamicObject object) {
// return Layout.getFactory().createAccessNode(key, null, object);
// }

    public abstract Object getOrDefault(DynamicObject object, Object key, Object defaultValue);

    public abstract int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

    public abstract double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

    public abstract long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

    public abstract boolean getBooleanOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

    public abstract boolean put(DynamicObject object, Object key, Object value, PutConfig config);

    public abstract boolean putInt(DynamicObject object, Object key, int value, PutConfig config);

    public abstract boolean putDouble(DynamicObject object, Object key, double value, PutConfig config);

    public abstract boolean putLong(DynamicObject object, Object key, long value, PutConfig config);

    public abstract boolean putBoolean(DynamicObject object, Object key, boolean value, PutConfig config);

    public abstract boolean removeKey(DynamicObject object, Object key);

    public abstract boolean setTypeId(DynamicObject object, Object type);

    public abstract Object getTypeId(DynamicObject object);

    public abstract boolean containsKey(DynamicObject object, Object key);

    public abstract int getShapeFlags(DynamicObject object);

    public abstract boolean setShapeFlags(DynamicObject object, int flags);

    public boolean updateShapeFlags(DynamicObject object, IntUnaryOperator updateFunction) {
        int oldFlags = getShapeFlags(object);
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return setShapeFlags(object, newFlags);
    }

    public abstract Property getProperty(DynamicObject object, Object key);

    public int getPropertyFlagsOrDefault(DynamicObject object, Object key, int defaultValue) {
        Property property = getProperty(object, key);
        return property != null ? property.getFlags() : defaultValue;
    }

    public abstract boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags);

    public boolean updatePropertyFlags(DynamicObject object, Object key, IntUnaryOperator updateFunction) {
        Property property = getProperty(object, key);
        if (property == null) {
            return false;
        }
        int oldFlags = property.getFlags();
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return setPropertyFlags(object, key, newFlags);
    }

    public abstract boolean allPropertiesMatch(DynamicObject object, Predicate<Property> predicate);

    public abstract int getPropertyCount(DynamicObject object);

    public abstract Iterable<Object> getKeys(DynamicObject object);

    public abstract Object[] getKeyArray(DynamicObject object);

    public abstract Iterable<Property> getProperties(DynamicObject object);

    public abstract Property[] getPropertyArray(DynamicObject object);

    public abstract void makeShared(DynamicObject object);

    public abstract boolean isShared(DynamicObject object);

    public abstract boolean updateShape(DynamicObject object);

    public abstract boolean resetShape(DynamicObject object, Shape otherShape);

// public boolean isSupportedKey(Object key) {
// return true;
// }
//
// public Object getCachedKey() {
// return null;
// }
//
// public Shape getCachedShape() {
// return null;
// }
//
// public boolean isSupported(DynamicObject object) {
// return true;
// }
//
// public boolean isSupported(DynamicObject object, Object key) {
// return isSupported(object) && isSupportedKey(key);
// }
}
