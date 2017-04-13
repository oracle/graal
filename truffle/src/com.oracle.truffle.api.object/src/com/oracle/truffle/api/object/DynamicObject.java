/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Represents an object members of which can be dynamically added and removed at run time.
 *
 * @see Shape
 * @since 0.8 or earlier
 */
@SuppressWarnings("deprecation")
public abstract class DynamicObject implements com.oracle.truffle.api.TypedObject, TruffleObject {

    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected DynamicObject() {
    }

    /**
     * Get the object's current shape.
     *
     * @since 0.8 or earlier
     */
    public abstract Shape getShape();

    /**
     * Get property value.
     *
     * @param key property identifier
     * @return property value or {@code null} if object has no such property
     * @since 0.8 or earlier
     */
    public final Object get(Object key) {
        return get(key, null);
    }

    /**
     * Get property value.
     *
     * @param key property identifier
     * @param defaultValue return value if property is not found
     * @return property value or defaultValue if object has no such property
     * @since 0.8 or earlier
     */
    public abstract Object get(Object key, Object defaultValue);

    /**
     * Set value of existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @return {@code true} if successful or {@code false} if property not found
     * @since 0.8 or earlier
     */
    public abstract boolean set(Object key, Object value);

    /**
     * Returns {@code true} if this object contains a property with the given key.
     *
     * @since 0.8 or earlier
     */
    public final boolean containsKey(Object key) {
        return getShape().getProperty(key) != null;
    }

    /**
     * Define new property or redefine existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @since 0.8 or earlier
     */
    public final void define(Object key, Object value) {
        define(key, value, 0);
    }

    /**
     * Define new property or redefine existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @param flags flags to be set
     * @since 0.8 or earlier
     */
    public abstract void define(Object key, Object value, int flags);

    /**
     * Define new property with a static location or change existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @param flags flags to be set
     * @param locationFactory factory function that creates a location for a given shape and value
     * @since 0.8 or earlier
     */
    public abstract void define(Object key, Object value, int flags, LocationFactory locationFactory);

    /**
     * Delete property.
     *
     * @param key property identifier
     * @return {@code true} if successful or {@code false} if property not found
     * @since 0.8 or earlier
     */
    public abstract boolean delete(Object key);

    /**
     * Returns the number of properties in this object.
     *
     * @since 0.8 or earlier
     */
    public abstract int size();

    /**
     * Returns {@code true} if this object contains no properties.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isEmpty();

    /**
     * Set object shape and grow storage if necessary.
     *
     * @param oldShape the object's current shape (must equal {@link #getShape()})
     * @param newShape the new shape to be set
     * @since 0.8 or earlier
     */
    public abstract void setShapeAndGrow(Shape oldShape, Shape newShape);

    /**
     * Set object shape and resize storage if necessary.
     *
     * @param oldShape the object's current shape (must equal {@link #getShape()})
     * @param newShape the new shape to be set
     * @since 0.8 or earlier
     */
    public abstract void setShapeAndResize(Shape oldShape, Shape newShape);

    /**
     * Ensure object shape is up-to-date.
     *
     * @return {@code true} if shape has changed
     * @since 0.8 or earlier
     */
    public abstract boolean updateShape();

    /**
     * Create a shallow copy of this object.
     *
     * @param currentShape the object's current shape (must equal {@link #getShape()})
     * @since 0.8 or earlier
     */
    public abstract DynamicObject copy(Shape currentShape);
}
