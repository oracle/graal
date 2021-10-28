/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.Shape;

/** @since 0.17 or earlier */
@SuppressWarnings("deprecation")
public abstract class LocationImpl extends Location {
    /**
     * @since 0.17 or earlier
     */
    protected LocationImpl() {
    }

    /** @since 0.17 or earlier */
    public interface InternalLongLocation extends LongLocation {
        /** @since 0.17 or earlier */
        void setLongInternal(DynamicObject store, long value);

        /** @since 0.17 or earlier */
        String getWhereString();
    }

    /** @since 0.17 or earlier */
    public interface LocationVisitor {
        /** @since 0.17 or earlier */
        void visitObjectField(int index, int count);

        /** @since 0.17 or earlier */
        void visitObjectArray(int index, int count);

        /** @since 0.17 or earlier */
        void visitPrimitiveField(int index, int count);

        /** @since 0.17 or earlier */
        void visitPrimitiveArray(int index, int count);
    }

    /** @since 0.17 or earlier */
    @Override
    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException {
        set(store, value, checkShape(store, shape), false);
    }

    @Override
    public void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        if (canStore(value)) {
            LayoutImpl.ACCESS.grow(store, oldShape, newShape);
            try {
                setInternal(store, value);
            } catch (IncompatibleLocationException ex) {
                throw shouldNotHappen(ex);
            }
            LayoutImpl.ACCESS.setShapeWithStoreFence(store, newShape);
        } else {
            throw incompatibleLocation();
        }
    }

    /** @since 0.17 or earlier */
    @Override
    protected final Object getInternal(DynamicObject store) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get object value as object at this location in store. For internal use only.
     *
     * @param guard the result of a shape check or {@code false}
     */
    @Override
    public abstract Object get(DynamicObject store, boolean guard);

    /**
     * @see #get(DynamicObject, boolean)
     */
    protected long getLong(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectLong(get(store, guard));
    }

    /**
     * @see #get(DynamicObject, boolean)
     */
    protected int getInt(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectInteger(get(store, guard));
    }

    /**
     * @see #get(DynamicObject, boolean)
     */
    protected double getDouble(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectDouble(get(store, guard));
    }

    /**
     * @see #get(DynamicObject, boolean)
     */
    protected boolean getBoolean(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectBoolean(get(store, guard));
    }

    /**
     * Sets the value of this property storage location.
     *
     * @param store the {@link DynamicObject} that holds this storage location.
     * @param value the value to be stored.
     * @param guard the result of the shape check guarding this property write or {@code false}.
     * @param init if true, this is the initial assignment of a property location; ignore final.
     * @throws IncompatibleLocationException if the value cannot be stored in this storage location.
     * @see #setSafe(DynamicObject, Object, boolean, boolean)
     */
    protected abstract void set(DynamicObject store, Object value, boolean guard, boolean init) throws IncompatibleLocationException;

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setIntSafe(DynamicObject, int, boolean, boolean)
     */
    protected void setInt(DynamicObject store, int value, boolean guard, boolean init) throws IncompatibleLocationException {
        set(store, value, guard, init);
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setLongSafe(DynamicObject, long, boolean, boolean)
     */
    protected void setLong(DynamicObject store, long value, boolean guard, boolean init) throws IncompatibleLocationException {
        set(store, value, guard, init);
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setDoubleSafe(DynamicObject, double, boolean, boolean)
     */
    protected void setDouble(DynamicObject store, double value, boolean guard, boolean init) throws IncompatibleLocationException {
        set(store, value, guard, init);
    }

    /**
     * Equivalent to {@link Shape#check(DynamicObject)}.
     */
    protected static final boolean checkShape(DynamicObject store, Shape shape) {
        return store.getShape() == shape;
    }

    /** @since 0.17 or earlier */
    @Override
    protected final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
        set(store, value, false, true);
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean canSet(DynamicObject store, Object value) {
        return canStore(value) && canStoreFinal(store, value);
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean canSet(Object value) {
        return canSet(null, value);
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean canStore(Object value) {
        return true;
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("unused")
    protected boolean canStoreFinal(DynamicObject store, Object value) {
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean isFinal() {
        return false;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean isConstant() {
        return false;
    }

    /** @since 0.17 or earlier */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isFinal() ? 1231 : 1237);
        return result;
    }

    /** @since 0.17 or earlier */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public String toString() {
        String finalString = isFinal() ? "f" : "";
        String typeString = (this instanceof com.oracle.truffle.api.object.TypedLocation ? ((com.oracle.truffle.api.object.TypedLocation) this).getType().getSimpleName() : "Object");
        return finalString + typeString + getWhereString();
    }

    /** @since 0.17 or earlier */
    protected String getWhereString() {
        return "";
    }

    /**
     * Get the number of object array elements this location requires.
     *
     * @since 0.17 or earlier
     */
    public int objectArrayCount() {
        return 0;
    }

    /**
     * Get the number of in-object {@link Object} fields this location requires.
     *
     * @since 0.17 or earlier
     */
    public int objectFieldCount() {
        return 0;
    }

    /**
     * Get the number of in-object primitive fields this location requires.
     *
     * @since 0.17 or earlier
     */
    public int primitiveFieldCount() {
        return 0;
    }

    /**
     * Get the number of primitive array elements this location requires.
     *
     * @since 0.17 or earlier
     */
    public int primitiveArrayCount() {
        return 0;
    }

    /**
     * Accept a visitor for location allocation for this and every nested location.
     *
     * @param locationVisitor visitor to be notified of every allocated slot in use by this location
     * @since 0.17 or earlier
     */
    public abstract void accept(LocationVisitor locationVisitor);

    protected LocationImpl getInternalLocation() {
        return this;
    }

    static boolean isSameLocation(LocationImpl loc1, LocationImpl loc2) {
        return loc1 == loc2 || loc1.getInternalLocation().equals(loc2.getInternalLocation());
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     */
    protected final void setSafe(DynamicObject store, Object value, boolean guard, boolean init) {
        try {
            set(store, value, guard, init);
        } catch (IncompatibleLocationException e) {
            throw shouldNotHappen(e);
        }
    }

    /**
     * @see #setInt(DynamicObject, int, boolean, boolean)
     */
    protected final void setIntSafe(DynamicObject store, int value, boolean guard, boolean init) {
        try {
            setInt(store, value, guard, init);
        } catch (IncompatibleLocationException e) {
            throw shouldNotHappen(e);
        }
    }

    /**
     * @see #setLong(DynamicObject, long, boolean, boolean)
     */
    protected final void setLongSafe(DynamicObject store, long value, boolean guard, boolean init) {
        try {
            setLong(store, value, guard, init);
        } catch (IncompatibleLocationException e) {
            throw shouldNotHappen(e);
        }
    }

    /**
     * @see #setDouble(DynamicObject, double, boolean, boolean)
     */
    protected final void setDoubleSafe(DynamicObject store, double value, boolean guard, boolean init) {
        try {
            setDouble(store, value, guard, init);
        } catch (IncompatibleLocationException e) {
            throw shouldNotHappen(e);
        }
    }

    protected boolean isIntLocation() {
        return false;
    }

    protected boolean isLongLocation() {
        return false;
    }

    protected boolean isDoubleLocation() {
        return false;
    }

    protected boolean isImplicitCastIntToLong() {
        return false;
    }

    protected boolean isImplicitCastIntToDouble() {
        return false;
    }

    protected boolean isObjectLocation() {
        return false;
    }

    static boolean expectBoolean(Object value) throws UnexpectedResultException {
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        throw new UnexpectedResultException(value);
    }

    static int expectInteger(Object value) throws UnexpectedResultException {
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new UnexpectedResultException(value);
    }

    static double expectDouble(Object value) throws UnexpectedResultException {
        if (value instanceof Double) {
            return (double) value;
        }
        throw new UnexpectedResultException(value);
    }

    static long expectLong(Object value) throws UnexpectedResultException {
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    public Class<?> getType() {
        return null;
    }

    protected void clear(@SuppressWarnings("unused") DynamicObject store) {
    }

    @Override
    public Assumption getFinalAssumption() {
        return neverValidAssumption();
    }

    protected static RuntimeException shouldNotHappen(Exception e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException(e);
    }

    /** Not using NeverValidAssumption.INSTANCE in order not to pollute profiles. */
    protected static Assumption neverValidAssumption() {
        return NEVER_VALID_ASSUMPTION;
    }

    /** Not using AlwaysValidAssumption.INSTANCE in order not to pollute profiles. */
    protected static Assumption alwaysValidAssumption() {
        assert ALWAYS_VALID_ASSUMPTION.isValid();
        return ALWAYS_VALID_ASSUMPTION;
    }

    private static final Assumption NEVER_VALID_ASSUMPTION;
    private static final Assumption ALWAYS_VALID_ASSUMPTION;

    static {
        NEVER_VALID_ASSUMPTION = Truffle.getRuntime().createAssumption("never valid");
        NEVER_VALID_ASSUMPTION.invalidate();
        ALWAYS_VALID_ASSUMPTION = Truffle.getRuntime().createAssumption("always valid");
    }
}
