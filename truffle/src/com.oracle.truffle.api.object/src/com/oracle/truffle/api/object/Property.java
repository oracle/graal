/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Property objects represent the mapping between property identifiers (keys) and storage locations.
 * Optionally, properties may have metadata attached to them.
 *
 * @since 0.8 or earlier
 */
public abstract class Property {
    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Property() {
    }

    /**
     * Create a new property.
     *
     * @param key the key of the property
     * @param location location of the property
     * @param flags for language-specific use
     * @return new instance of the property
     * @since 0.8 or earlier
     */
    public static Property create(Object key, Location location, int flags) {
        return Layout.getFactory().createProperty(key, location, flags);
    }

    /**
     * Get property identifier.
     *
     * @since 0.8 or earlier
     */
    public abstract Object getKey();

    /**
     * Get property flags, which are free for language-specific use.
     *
     * @since 0.8 or earlier
     */
    public abstract int getFlags();

    /**
     * Change the property's location.
     *
     * @return a Property with the new location (or {@code this} if the location is unchanged).
     * @since 0.8 or earlier
     */
    public abstract Property relocate(Location newLocation);

    /**
     * Gets the value of this property of the object.
     *
     * @param store the store that this property resides in
     * @param shape the current shape of the object, which must contain this location
     * @see DynamicObject#get(Object, Object)
     * @since 0.8 or earlier
     */
    public abstract Object get(DynamicObject store, Shape shape);

    /**
     * Gets the value of this property of the object.
     *
     * @param store the store that this property resides in
     * @param condition the result of a shape check or {@code false}
     * @see DynamicObject#get(Object, Object)
     * @see #get(DynamicObject, Shape)
     * @since 0.8 or earlier
     */
    public abstract Object get(DynamicObject store, boolean condition);

    /**
     * Assigns value to this property of the object.
     *
     * Throws an exception if the value cannot be assigned to the property's current location.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param shape the current shape of the object or {@code null}
     * @throws IncompatibleLocationException if the value is incompatible with the property location
     * @throws FinalLocationException if the location is final and values differ
     * @see DynamicObject#set(Object, Object)
     * @since 0.8 or earlier
     */
    public abstract void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException;

    /**
     * Assigns value to this property of the object.
     *
     * Automatically relocates the property if the value cannot be assigned to its current location.
     *
     * @param shape the current shape of the object or {@code null}
     * @since 0.8 or earlier
     */
    public abstract void setGeneric(DynamicObject store, Object value, Shape shape);

    /**
     * Like {@link #set(DynamicObject, Object, Shape)}, but throws an {@link IllegalStateException}
     * instead.
     *
     * @since 0.8 or earlier
     */
    public abstract void setSafe(DynamicObject store, Object value, Shape shape);

    /**
     * Like {@link #setSafe}, but ignores the finalness of the property. For internal use only.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @since 0.8 or earlier
     */
    public abstract void setInternal(DynamicObject store, Object value);

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@link DynamicObject#setShapeAndGrow(Shape, Shape)} and
     * {@link #set(DynamicObject, Object, Shape)} to an atomic operation.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param oldShape the shape before the transition
     * @param newShape the shape after the transition
     * @throws IncompatibleLocationException if the value is incompatible with the property location
     * @since 0.8 or earlier
     */
    public abstract void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException;

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@link DynamicObject#setShapeAndGrow(Shape, Shape)} and
     * {@link #setGeneric(DynamicObject, Object, Shape)} to an atomic operation.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param oldShape the shape before the transition
     * @param newShape the shape after the transition
     * @since 0.8 or earlier
     */
    public abstract void setGeneric(DynamicObject store, Object value, Shape oldShape, Shape newShape);

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@link DynamicObject#setShapeAndGrow(Shape, Shape)} and
     * {@link #setSafe(DynamicObject, Object, Shape)} to an atomic operation.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param oldShape the shape before the transition
     * @param newShape the shape after the transition
     * @since 0.8 or earlier
     */
    public abstract void setSafe(DynamicObject store, Object value, Shape oldShape, Shape newShape);

    /**
     * Returns {@code true} if this property and some other property have the same key and flags.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isSame(Property other);

    /**
     * Get the property location.
     *
     * @since 0.8 or earlier
     */
    public abstract Location getLocation();

    /**
     * Is this property hidden from iteration.
     *
     * @see HiddenKey
     * @since 0.8 or earlier
     */
    public abstract boolean isHidden();

    /**
     * Create a copy of the property with the given flags.
     *
     * @since 0.8 or earlier
     */
    public abstract Property copyWithFlags(int newFlags);

    /** @since 0.8 or earlier */
    public abstract Property copyWithRelocatable(boolean newRelocatable);
}
