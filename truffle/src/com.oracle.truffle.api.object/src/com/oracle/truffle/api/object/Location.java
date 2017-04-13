/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 * @see DynamicObject
 * @since 0.8 or earlier
 */
public abstract class Location {
    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Location() {
    }

    /** @since 0.8 or earlier */
    protected static IncompatibleLocationException incompatibleLocation() throws IncompatibleLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IncompatibleLocationException();
    }

    /** @since 0.8 or earlier */
    protected static FinalLocationException finalLocation() throws FinalLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new FinalLocationException();
    }

    /**
     * Get object value as object at this location in store.
     *
     * @param shape the current shape of the object, which must contain this location
     * @since 0.8 or earlier
     */
    public final Object get(DynamicObject store, Shape shape) {
        return get(store, checkShape(store, shape));
    }

    /**
     * Get object value as object at this location in store. For internal use only and subject to
     * change, use {@link #get(DynamicObject, Shape)} instead.
     *
     * @param condition the result of a shape check or {@code false}
     * @see #get(DynamicObject, Shape)
     * @since 0.8 or earlier
     */
    public Object get(DynamicObject store, boolean condition) {
        return getInternal(store);
    }

    /**
     * Get object value as object at this location in store.
     *
     * @since 0.8 or earlier
     */
    public final Object get(DynamicObject store) {
        return get(store, false);
    }

    /**
     * Set object value at this location in store.
     *
     * @param shape the current shape of the storage object
     * @throws IncompatibleLocationException for storage type invalidations
     * @throws FinalLocationException for effectively final fields
     * @since 0.8 or earlier
     */
    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        setInternal(store, value);
    }

    /**
     * Set object value at this location in store and update shape.
     *
     * @param oldShape the shape before the transition
     * @param newShape new shape after the transition
     * @throws IncompatibleLocationException if value is of non-assignable type
     * @since 0.8 or earlier
     */
    public final void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        if (canStore(value)) {
            store.setShapeAndGrow(oldShape, newShape);
            try {
                setInternal(store, value);
            } catch (IncompatibleLocationException ex) {
                throw new IllegalStateException();
            }
        } else {
            throw incompatibleLocation();
        }
    }

    /**
     * Set object value at this location in store.
     *
     * @throws IncompatibleLocationException for storage type invalidations
     * @throws FinalLocationException for effectively final fields
     * @since 0.8 or earlier
     */
    public final void set(DynamicObject store, Object value) throws IncompatibleLocationException, FinalLocationException {
        set(store, value, null);
    }

    /** @since 0.8 or earlier */
    protected abstract Object getInternal(DynamicObject store);

    /**
     * Like {@link #set(DynamicObject, Object, Shape)}, but does not invalidate final locations. For
     * internal use only and subject to change, use {@link DynamicObjectFactory} to create objects
     * with predefined properties.
     *
     * @throws IncompatibleLocationException if value is of non-assignable type
     * @since 0.8 or earlier
     */
    protected abstract void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException;

    /**
     * Returns {@code true} if the location can be set to the given value.
     *
     * @param store the receiver object
     * @param value the value in question
     * @since 0.8 or earlier
     */
    public boolean canSet(DynamicObject store, Object value) {
        return canStore(value);
    }

    /**
     * Returns {@code true} if the location can be set to the value.
     *
     * @param value the value in question
     * @since 0.8 or earlier
     */
    public boolean canSet(Object value) {
        return canStore(value);
    }

    /**
     * Returns {@code true} if the location is compatible with the type of the value.
     *
     * The actual value may still be rejected if {@link #canSet(DynamicObject, Object)} returns
     * false.
     *
     * @param value the value in question
     * @since 0.8 or earlier
     */
    public boolean canStore(Object value) {
        return true;
    }

    /**
     * Returns {@code true} if this is a final location, i.e. readonly once set.
     *
     * @since 0.8 or earlier
     */
    public boolean isFinal() {
        return false;
    }

    /**
     * Returns {@code true} if this is a constant value location.
     *
     * @since 0.8 or earlier
     */
    public boolean isConstant() {
        return false;
    }

    /**
     * Abstract to force overriding.
     *
     * @since 0.8 or earlier
     */
    @Override
    public abstract int hashCode();

    /**
     * Abstract to force overriding.
     *
     * @since 0.8 or earlier
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Returns {@code true} if this is a declared value location.
     *
     * @since 0.18
     */
    public boolean isDeclared() {
        return false;
    }

    /**
     * Returns {@code true} if this is a value location.
     *
     * @see #isConstant()
     * @see #isDeclared()
     * @since 0.18
     */
    public boolean isValue() {
        return false;
    }

    /**
     * Returns {@code true} if this location is assumed to be final.
     *
     * @see #getFinalAssumption()
     * @since 0.18
     */
    public boolean isAssumedFinal() {
        return false;
    }

    /**
     * Returns the assumption that this location is final.
     *
     * @see #isAssumedFinal()
     * @since 0.18
     */
    public Assumption getFinalAssumption() {
        return NeverValidAssumption.INSTANCE;
    }

    /**
     * Equivalent to {@link Shape#check(DynamicObject)}.
     *
     * @since 0.8 or earlier
     */
    protected static boolean checkShape(DynamicObject store, Shape shape) {
        return store.getShape() == shape;
    }
}
