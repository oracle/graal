/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

/**
 * Property location.
 *
 * Planned to be deprecated.
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
    @SuppressWarnings("deprecation")
    public void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
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
