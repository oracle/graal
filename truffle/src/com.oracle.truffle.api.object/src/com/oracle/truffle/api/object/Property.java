/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Property relocate(Location newLocation);

    /**
     * Gets the value of this property of the object.
     *
     * Planned to be deprecated.
     *
     * @param store the store that this property resides in
     * @param shape the current shape of the object, which must contain this location
     * @see DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)
     * @since 0.8 or earlier
     */
    public abstract Object get(DynamicObject store, Shape shape);

    /**
     * Gets the value of this property of the object.
     *
     * Planned to be deprecated.
     *
     * @param store the store that this property resides in
     * @param condition the result of a shape check or {@code false}
     * @see DynamicObjectLibrary#getOrDefault(DynamicObject, Object, Object)
     * @see #get(DynamicObject, Shape)
     * @since 0.8 or earlier
     */
    public abstract Object get(DynamicObject store, boolean condition);

    /**
     * Assigns value to this property of the object.
     *
     * Throws an exception if the value cannot be assigned to the property's current location.
     *
     * Planned to be deprecated.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param shape the current shape of the object or {@code null}
     * @throws IncompatibleLocationException if the value is incompatible with the property location
     * @throws FinalLocationException if the location is final and values differ
     * @see DynamicObjectLibrary#put(DynamicObject, Object, Object)
     * @since 0.8 or earlier
     */
    public abstract void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException;

    /**
     * Assigns value to this property of the object.
     *
     * Automatically relocates the property if the value cannot be assigned to its current location.
     *
     * Planned to be deprecated.
     *
     * @param shape the current shape of the object or {@code null}
     * @since 0.8 or earlier
     */
    public abstract void setGeneric(DynamicObject store, Object value, Shape shape);

    /**
     * Like {@link #set(DynamicObject, Object, Shape)}, but throws an {@link IllegalStateException}
     * instead.
     *
     * Planned to be deprecated.
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
     * @deprecated Properties can be set using {@link DynamicObjectLibrary#put}.
     */
    @Deprecated
    public abstract void setInternal(DynamicObject store, Object value);

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@code setShapeAndGrow} and {@link #set(DynamicObject, Object, Shape)} to an atomic
     * operation.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param oldShape the shape before the transition
     * @param newShape the shape after the transition
     * @throws IncompatibleLocationException if the value is incompatible with the property location
     * @since 0.8 or earlier
     * @deprecated Properties can be added, set, and changed using {@link DynamicObjectLibrary#put}
     *             and {@link DynamicObjectLibrary#putWithFlags}.
     */
    @Deprecated
    public abstract void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException;

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@code setShapeAndGrow} and {@link #setGeneric(DynamicObject, Object, Shape)} to an
     * atomic operation.
     *
     * @param store the store that this property resides in
     * @param value the value to assign
     * @param oldShape the shape before the transition
     * @param newShape the shape after the transition
     * @since 0.8 or earlier
     * @deprecated Properties can be added, set, and changed using {@link DynamicObjectLibrary#put}
     *             and {@link DynamicObjectLibrary#putWithFlags}.
     */
    @Deprecated
    public abstract void setGeneric(DynamicObject store, Object value, Shape oldShape, Shape newShape);

    /**
     * Assigns value to this property of the object, changing the object's shape.
     *
     * Combines {@code setShapeAndGrow} and {@link #setSafe(DynamicObject, Object, Shape)} to an
     * atomic operation.
     *
     * Planned to be deprecated.
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
     * @deprecated Equivalent to comparing the property's key and flags.
     */
    @Deprecated
    public abstract boolean isSame(Property other);

    /**
     * Get the property location.
     *
     * Planned to be deprecated.
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
     * @deprecated Property flags can be changed using
     *             {@link DynamicObjectLibrary#setPropertyFlags(DynamicObject, Object, int)}.
     */
    @Deprecated
    public abstract Property copyWithFlags(int newFlags);

    /**
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Property copyWithRelocatable(boolean newRelocatable);
}
