/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.ExtLocations.AbstractPrimitiveLocation;
import com.oracle.truffle.api.object.ExtLocations.ConstantLocation;
import com.oracle.truffle.api.object.ExtLocations.DoubleLocation;
import com.oracle.truffle.api.object.ExtLocations.IntLocation;
import com.oracle.truffle.api.object.ExtLocations.LongLocation;
import com.oracle.truffle.api.object.ExtLocations.ObjectLocation;

import sun.misc.Unsafe;

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
public abstract sealed class Location permits ExtLocations.InstanceLocation, ExtLocations.ValueLocation {

    final int index;
    final FieldInfo field;

    @CompilationFinal volatile AbstractAssumption finalAssumption;

    private static final AtomicReferenceFieldUpdater<Location, AbstractAssumption> FINAL_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                    Location.class, AbstractAssumption.class, "finalAssumption");

    /**
     * Constructor for instance location.
     *
     * @param index array index or field index
     * @param finalAssumption initial value of final assumption field
     */
    Location(int index, FieldInfo field, AbstractAssumption finalAssumption) {
        this.index = index;
        this.field = field;
        this.finalAssumption = finalAssumption;
        assert isValidIndex(index) : index;
    }

    /**
     * Constructor for constant value location.
     */
    Location() {
        this.index = -1;
        this.field = null;
        this.finalAssumption = (AbstractAssumption) Assumption.NEVER_VALID;
        assert this instanceof ExtLocations.ValueLocation;
    }

    static boolean isValidIndex(int index) {
        return index >= 0;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated(since = "22.2")
    protected static IncompatibleLocationException incompatibleLocation() throws IncompatibleLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw IncompatibleLocationException.instance();
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated(since = "22.2")
    protected static FinalLocationException finalLocation() throws FinalLocationException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw FinalLocationException.instance();
    }

    /**
     * Get object value as object at this location in store.
     *
     * @param shape the current shape of the object, which must contain this location
     * @since 0.8 or earlier
     */
    @Deprecated(since = "22.2")
    public final Object get(DynamicObject store, Shape shape) {
        return get(store, store.getShape() == shape);
    }

    /**
     * Get object value as object at this location in store. For internal use only and subject to
     * change, use {@link #get(DynamicObject, Shape)} instead.
     *
     * @param store storage object
     * @param condition the result of a shape check or {@code false}
     * @see #get(DynamicObject, Shape)
     * @since 0.8 or earlier
     */
    @Deprecated(since = "22.2")
    public Object get(DynamicObject store, boolean condition) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Get object value as object at this location in store.
     *
     * @since 0.8 or earlier
     */
    @Deprecated(since = "22.2")
    public final Object get(DynamicObject store) {
        return get(store, false);
    }

    /**
     * Gets this location's value as int.
     *
     * @param store storage object
     * @param guard the result of a shape check or {@code false}
     * @throws UnexpectedResultException if the location does not contain an int value.
     * @since 22.2
     */
    protected int getInt(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectInteger(get(store, guard));
    }

    /**
     * Gets this location's value as long.
     *
     * @param store storage object
     * @param guard the result of a shape check or {@code false}
     * @throws UnexpectedResultException if the location does not contain a long value.
     * @since 22.2
     */
    protected long getLong(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectLong(get(store, guard));
    }

    /**
     * Gets this location's value as double.
     *
     * @param store storage object
     * @param guard the result of a shape check or {@code false}
     * @throws UnexpectedResultException if the location does not contain a double value.
     * @since 22.2
     */
    protected double getDouble(DynamicObject store, boolean guard) throws UnexpectedResultException {
        return expectDouble(get(store, guard));
    }

    /**
     * Gets this location's value from store.
     *
     * @param store storage object
     * @param expectedShape the expected object shape; must be a shape that contains this location
     * @param guard the result of the shape check or {@code false}
     * @return the read value
     */
    final Object getInternal(DynamicObject store, Shape expectedShape, boolean guard) {
        if (this instanceof ObjectLocation objectLocation) {
            Object base;
            long offset;
            Object value;
            if (field == null) {
                base = getObjectArray(store, guard);
                offset = getObjectArrayOffset();
                value = UnsafeAccess.unsafeGetObject(base, offset, guard, this);
            } else {
                field.receiverCheck(store);
                base = store;
                offset = getFieldOffset();
                value = UnsafeAccess.unsafeGetObject(base, offset, guard, this);
            }
            return CompilerDirectives.inInterpreter() ? value : objectLocation.assumedTypeCast(value, guard);
        } else {
            if (field == null) {
                Object array = getPrimitiveArray(store, guard);
                long offset = getPrimitiveArrayOffset();
                if (isIntLocation()) {
                    return UnsafeAccess.unsafeGetInt(array, offset, guard, this);
                } else if (isLongLocation()) {
                    return UnsafeAccess.unsafeGetLong(array, offset, guard, this);
                } else if (isDoubleLocation()) {
                    return UnsafeAccess.unsafeGetDouble(array, offset, guard, this);
                } else {
                    return ((ConstantLocation) this).get(store, guard);
                }
            } else {
                field.receiverCheck(store);
                long longValue = UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
                if (this instanceof IntLocation) {
                    return (int) longValue;
                } else if (this instanceof LongLocation) {
                    return longValue;
                } else {
                    assert isDoubleLocation();
                    return Double.longBitsToDouble(longValue);
                }
            }
        }
    }

    /**
     * @see #getInternal(DynamicObject, Shape, boolean)
     */
    final int getIntInternal(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        if (isIntLocation()) {
            if (field == null) {
                Object array = getPrimitiveArray(store, guard);
                long offset = getPrimitiveArrayOffset();
                return UnsafeAccess.unsafeGetInt(array, offset, guard, this);
            } else {
                field.receiverCheck(store);
                long longValue = UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
                return (int) longValue;
            }
        } else if (this instanceof ObjectLocation objectLocation) {
            Object value;
            if (field == null) {
                Object array = getObjectArray(store, guard);
                long offset = getObjectArrayOffset();
                value = UnsafeAccess.unsafeGetObject(array, offset, guard, this);
            } else {
                if (UseVarHandle) {
                    value = field.varHandle().get(store);
                } else {
                    field.receiverCheck(store);
                    value = UnsafeAccess.unsafeGetObject(store, getFieldOffset(), guard, this);
                }
            }
            return expectInteger(CompilerDirectives.inInterpreter() ? value : objectLocation.assumedTypeCast(value, guard));
        }
        return getIntUnexpected(store, expectedShape, guard);
    }

    /**
     * Slow path of {@link #getIntInternal(DynamicObject, Shape, boolean)} that handles constant
     * locations and other primitive locations that always throw {@link UnexpectedResultException}.
     */
    private int getIntUnexpected(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(getInternal(store, expectedShape, guard));
    }

    /**
     * @see #getInternal(DynamicObject, Shape, boolean)
     */
    final long getLongInternal(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        if (isLongLocation()) {
            if (field == null) {
                Object array = getPrimitiveArray(store, guard);
                long offset = getPrimitiveArrayOffset();
                return UnsafeAccess.unsafeGetLong(array, offset, guard, this);
            } else {
                field.receiverCheck(store);
                return UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
            }
        } else if (this instanceof ObjectLocation objectLocation) {
            Object value;
            if (field == null) {
                Object array = getObjectArray(store, guard);
                long offset = getObjectArrayOffset();
                value = UnsafeAccess.unsafeGetObject(array, offset, guard, this);
            } else {
                if (UseVarHandle) {
                    value = field.varHandle().get(store);
                } else {
                    field.receiverCheck(store);
                    value = UnsafeAccess.unsafeGetObject(store, getFieldOffset(), guard, this);
                }
            }
            return expectLong(CompilerDirectives.inInterpreter() ? value : objectLocation.assumedTypeCast(value, guard));
        }
        return getLongUnexpected(store, expectedShape, guard);
    }

    /**
     * Slow path of {@link #getLongInternal(DynamicObject, Shape, boolean)} that handles constant
     * locations and other primitive locations that always throw {@link UnexpectedResultException}.
     */
    private long getLongUnexpected(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(getInternal(store, expectedShape, guard));
    }

    /**
     * @see #getInternal(DynamicObject, Shape, boolean)
     */
    final double getDoubleInternal(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        if (isDoubleLocation()) {
            if (field == null) {
                Object array = getPrimitiveArray(store, guard);
                long offset = getPrimitiveArrayOffset();
                return UnsafeAccess.unsafeGetDouble(array, offset, guard, this);
            } else {
                field.receiverCheck(store);
                long longValue = UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
                return Double.longBitsToDouble(longValue);
            }
        } else if (this instanceof ObjectLocation objectLocation) {
            Object value;
            if (field == null) {
                Object array = getObjectArray(store, guard);
                long offset = getObjectArrayOffset();
                value = UnsafeAccess.unsafeGetObject(array, offset, guard, this);
            } else {
                if (UseVarHandle) {
                    value = field.varHandle().get(store);
                } else {
                    field.receiverCheck(store);
                    value = UnsafeAccess.unsafeGetObject(store, getFieldOffset(), guard, this);
                }
            }
            return expectDouble(CompilerDirectives.inInterpreter() ? value : objectLocation.assumedTypeCast(value, guard));
        }
        return getDoubleUnexpected(store, expectedShape, guard);
    }

    /**
     * Slow path of {@link #getDoubleInternal(DynamicObject, Shape, boolean)} that handles constant
     * locations and other primitive locations that always throw {@link UnexpectedResultException}.
     */
    private double getDoubleUnexpected(DynamicObject store, Shape expectedShape, boolean guard) throws UnexpectedResultException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnexpectedResultException(getInternal(store, expectedShape, guard));
    }

    /**
     * Set object value at this location in store.
     *
     * @param store storage object
     * @param value the value to set
     * @param shape the current shape of the storage object
     * @throws IncompatibleLocationException for storage type invalidations
     * @throws FinalLocationException for effectively final fields
     * @since 0.8 or earlier
     */
    @SuppressWarnings({"unused", "deprecation"})
    @Deprecated(since = "22.2")
    public void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        try {
            set(store, value, checkShape(store, shape), false);
        } catch (UncheckedIncompatibleLocationException e) {
            throw incompatibleLocation();
        }
    }

    /**
     * Set object value at this location in store and update shape.
     *
     * @param oldShape the shape before the transition
     * @param newShape new shape after the transition
     * @throws IncompatibleLocationException if value is of non-assignable type
     * @since 0.8 or earlier
     */
    @Deprecated(since = "22.2")
    @SuppressWarnings({"unused", "deprecation"})
    public void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        if (canStore(value)) {
            boolean guard = checkShape(store, oldShape);
            DynamicObjectSupport.grow(store, oldShape, newShape);
            setSafe(store, value, guard, true);
            DynamicObjectSupport.setShapeWithStoreFence(store, newShape);
        } else {
            throw incompatibleLocation();
        }
    }

    /**
     * Sets the value of this property storage location.
     *
     * @param store the {@link DynamicObject} that holds this storage location.
     * @param value the value to be stored.
     * @param guard the result of the shape check guarding this property write or {@code false}.
     * @param init if true, this is the initial assignment of a property location; ignore final.
     * @throws UncheckedIncompatibleLocationException if the value cannot be stored in this storage
     *             location.
     * @see #setSafe(DynamicObject, Object, boolean, boolean)
     */
    abstract void set(DynamicObject store, Object value, boolean guard, boolean init);

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setIntSafe(DynamicObject, int, boolean, boolean)
     */
    void setInt(DynamicObject store, int value, boolean guard, boolean init) {
        set(store, value, guard, init);
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setLongSafe(DynamicObject, long, boolean, boolean)
     */
    void setLong(DynamicObject store, long value, boolean guard, boolean init) {
        set(store, value, guard, init);
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     * @see #setDoubleSafe(DynamicObject, double, boolean, boolean)
     */
    void setDouble(DynamicObject store, double value, boolean guard, boolean init) {
        set(store, value, guard, init);
    }

    /**
     * @see #set(DynamicObject, Object, boolean, boolean)
     */
    final void setSafe(DynamicObject store, Object value, boolean guard, boolean init) {
        set(store, value, guard, init);
    }

    /**
     * @see #setInt(DynamicObject, int, boolean, boolean)
     */
    final void setIntSafe(DynamicObject store, int value, boolean guard, boolean init) {
        setInt(store, value, guard, init);
    }

    /**
     * @see #setLong(DynamicObject, long, boolean, boolean)
     */
    final void setLongSafe(DynamicObject store, long value, boolean guard, boolean init) {
        setLong(store, value, guard, init);
    }

    /**
     * @see #setDouble(DynamicObject, double, boolean, boolean)
     */
    final void setDoubleSafe(DynamicObject store, double value, boolean guard, boolean init) {
        setDouble(store, value, guard, init);
    }

    /**
     * Stores a value in this location. Grows the object if necessary. It is the caller's
     * responsibility to check that the value is compatible with this location first.
     *
     * @param receiver storage object
     * @param value the value to be stored
     * @param guard the result of the shape check guarding this property write or {@code false}
     * @param oldShape the expected shape before the set
     * @param newShape the expected shape after the set
     * @see #canStoreValue(Object).
     */
    final void setInternal(DynamicObject receiver, Object value, boolean guard, Shape oldShape, Shape newShape) {
        assert canStoreValue(value) : value;
        boolean init = newShape != oldShape;
        if (init) {
            DynamicObjectSupport.grow(receiver, oldShape, newShape);
        } else {
            AbstractAssumption assumption = getFinalAssumptionField();
            if (assumption == null || assumption.isValid()) {
                invalidateFinalAssumption(assumption);
            }
        }
        if (this instanceof ObjectLocation objectLocation) {
            if (!objectLocation.canStoreInternal(value)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectLocation.invalidateTypeAssumption(value);
            }
            Object base;
            long offset;
            if (field == null) {
                base = getObjectArray(receiver, guard);
                offset = getObjectArrayOffset();
                UnsafeAccess.unsafePutObject(base, offset, value, this);
            } else {
                field.receiverCheck(receiver);
                base = receiver;
                offset = getFieldOffset();
                UnsafeAccess.unsafePutObject(base, offset, value, this);
            }
        } else { // primitive location
            long longValue;
            if (isIntLocation()) {
                int intValue = (int) value;
                if (field == null) {
                    Object array = getPrimitiveArray(receiver, guard);
                    long offset = getPrimitiveArrayOffset();
                    UnsafeAccess.unsafePutInt(array, offset, intValue, this);
                    return;
                } else {
                    longValue = Integer.toUnsignedLong(intValue);
                }
            } else if (this instanceof LongLocation longLocation) {
                if (value instanceof Long l) {
                    longValue = l;
                } else if (longLocation.isImplicitCastIntToLong() && value instanceof Integer i) {
                    longValue = i;
                } else {
                    return;
                }
                if (field == null) {
                    Object array = getPrimitiveArray(receiver, guard);
                    long offset = getPrimitiveArrayOffset();
                    UnsafeAccess.unsafePutLong(array, offset, longValue, this);
                    return;
                }
            } else if (this instanceof DoubleLocation doubleLocation) {
                double doubleValue;
                if (value instanceof Double d) {
                    doubleValue = d;
                } else if (doubleLocation.isImplicitCastIntToDouble() && value instanceof Integer i) {
                    doubleValue = i;
                } else {
                    return;
                }
                if (field == null) {
                    Object array = getPrimitiveArray(receiver, guard);
                    long offset = getPrimitiveArrayOffset();
                    UnsafeAccess.unsafePutDouble(array, offset, doubleValue, this);
                    return;
                } else {
                    longValue = Double.doubleToRawLongBits(doubleValue);
                }
            } else {
                assert isConstantLocation() : this;
                return;
            }
            field.receiverCheck(receiver);
            long offset = getFieldOffset();
            UnsafeAccess.unsafePutLong(receiver, offset, longValue, this);
        }
    }

    final boolean canStoreValue(Object value) {
        if (isObjectLocation()) {
            return true;
        } else if (isIntLocation()) {
            return value instanceof Integer;
        } else if (this instanceof LongLocation longLocation) {
            return value instanceof Long || (longLocation.isImplicitCastIntToLong() && value instanceof Integer);
        } else if (this instanceof DoubleLocation doubleLocation) {
            return value instanceof Double || (doubleLocation.isImplicitCastIntToDouble() && value instanceof Integer);
        } else {
            return canStoreConstant(value);
        }
    }

    @HostCompilerDirectives.InliningCutoff
    final boolean canStoreConstant(Object value) {
        return ((ConstantLocation) this).canStore(value);
    }

    /**
     * Equivalent to {@link Shape#check(DynamicObject)}.
     */
    static boolean checkShape(DynamicObject store, Shape shape) {
        return store.getShape() == shape;
    }

    /**
     * Returns {@code true} if the location can be set to the value.
     *
     * @param value the value in question
     * @since 0.8 or earlier
     * @deprecated Equivalent to {@link #canStore(Object)}.
     */
    @Deprecated(since = "22.2")
    public boolean canSet(Object value) {
        return canStore(value);
    }

    /**
     * Returns {@code true} if the location is compatible with the type of the value.
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
     * @see #isAssumedFinal()
     * @since 0.8 or earlier
     * @deprecated Use {@link #isAssumedFinal()} instead or replace by {@code false}.
     */
    @Deprecated(since = "25.1")
    public boolean isFinal() {
        return false;
    }

    /**
     * Returns {@code true} if this is a constant value location.
     *
     * @since 0.8 or earlier
     */
    public boolean isConstant() {
        return isConstantLocation();
    }

    /**
     * Must be overridden in subclasses.
     *
     * @since 0.8 or earlier
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int hash = getClass().hashCode();
        hash = hash * prime + Integer.hashCode(index);
        hash = hash * prime + Objects.hashCode(field);
        return hash;
    }

    /**
     * Must be overridden in subclasses.
     *
     * @since 0.8 or earlier
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        return getClass() == obj.getClass() && obj instanceof Location that &&
                        this.index == that.index &&
                        this.field == that.field;
    }

    /**
     * @since 0.8 or earlier
     */
    @Override
    public String toString() {
        return Objects.requireNonNullElse(getType(), Object.class).getSimpleName();
    }

    /**
     * Get the number of in-object {@link Object} fields this location requires. Used reflectively
     * by tests.
     */
    int objectFieldCount() {
        return 0;
    }

    /**
     * Get the number of in-object primitive fields this location requires. Used reflectively by
     * tests.
     */
    int primitiveFieldCount() {
        return 0;
    }

    /**
     * Get the number of primitive array elements this location requires.
     */
    int primitiveArrayCount() {
        return 0;
    }

    /**
     * Accept a visitor for location allocation for this and every nested location.
     *
     * @param locationVisitor visitor to be notified of every allocated slot in use by this location
     */
    abstract void accept(LocationVisitor locationVisitor);

    static boolean isSameLocation(Location loc1, Location loc2) {
        return loc1 == loc2 || loc1.equals(loc2);
    }

    final boolean isFieldLocation() {
        return field != null;
    }

    final boolean isArrayLocation() {
        return !isFieldLocation() && !isConstantLocation();
    }

    final boolean isConstantLocation() {
        return this instanceof ConstantLocation;
    }

    final boolean isObjectLocation() {
        return this instanceof ObjectLocation;
    }

    final boolean isIntLocation() {
        return this instanceof IntLocation;
    }

    final boolean isDoubleLocation() {
        return this instanceof DoubleLocation;
    }

    final boolean isLongLocation() {
        return this instanceof LongLocation;
    }

    boolean isImplicitCastIntToLong() {
        return false;
    }

    boolean isImplicitCastIntToDouble() {
        return false;
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

    Class<?> getType() {
        return null;
    }

    abstract void clear(DynamicObject store);

    final int getOrdinal() {
        assert !isConstantLocation() : this;
        boolean isPrimitive = this instanceof AbstractPrimitiveLocation;
        int ordinal = (isPrimitive ? -Integer.MAX_VALUE : 0) + getIndex();
        if (isArrayLocation()) {
            ordinal += ExtLocations.MAX_DYNAMIC_FIELDS;
        }
        return ordinal;
    }

    final int getIndex() {
        return index;
    }

    final long getFieldOffset() {
        return field.offset();
    }

    final long getObjectArrayOffset() {
        return Integer.toUnsignedLong(index) * Unsafe.ARRAY_OBJECT_INDEX_SCALE + Unsafe.ARRAY_OBJECT_BASE_OFFSET;
    }

    final long getPrimitiveArrayOffset() {
        return Integer.toUnsignedLong(index) * Unsafe.ARRAY_INT_INDEX_SCALE + Unsafe.ARRAY_INT_BASE_OFFSET;
    }

    static Object getObjectArray(DynamicObject store, boolean condition) {
        return UnsafeAccess.unsafeCast(store.getObjectStore(), Object[].class, condition, true, true);
    }

    static Object getPrimitiveArray(DynamicObject store, boolean condition) {
        return UnsafeAccess.unsafeCast(store.getPrimitiveStore(), int[].class, condition, true, true);
    }

    static RuntimeException incompatibleLocationException() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UncheckedIncompatibleLocationException.instance();
    }

    /**
     * Returns {@code true} if this is a declared value location.
     *
     * @since 0.18
     * @deprecated No longer needed. Declared locations can only be created with deprecated APIs.
     */
    @Deprecated(since = "22.2")
    public boolean isDeclared() {
        return false;
    }

    /**
     * Returns {@code true} if this is a value location.
     *
     * @see #isConstant()
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
        var assumption = getFinalAssumptionField();
        return assumption == null || assumption.isValid();
    }

    /**
     * Returns the assumption that this location is final.
     *
     * @see #isAssumedFinal()
     * @since 0.18
     */
    public Assumption getFinalAssumption() {
        return getFinalAssumptionInternal();
    }

    /**
     * Returns {@code true} if this location can only store primitive types and cannot contain any
     * object references.
     *
     * @since 22.2
     */
    @SuppressWarnings("deprecation")
    public boolean isPrimitive() {
        return this instanceof AbstractPrimitiveLocation;
    }

    /**
     * If this is a constant location, returns the constant value bound to this location. Otherwise,
     * returns null.
     *
     * @since 22.2
     */
    @SuppressWarnings("deprecation")
    public Object getConstantValue() {
        if (isConstant()) {
            return get(null);
        } else {
            return null;
        }
    }

    /**
     * This exception is thrown on an attempt to assign an incompatible value to a location.
     */
    @SuppressWarnings("serial")
    static final class UncheckedIncompatibleLocationException extends RuntimeException {
        private static final UncheckedIncompatibleLocationException INSTANCE = new UncheckedIncompatibleLocationException();

        private UncheckedIncompatibleLocationException() {
            super(null, null, false, false);
        }

        static UncheckedIncompatibleLocationException instance() {
            return INSTANCE;
        }
    }

    interface LocationVisitor {
        void visitObjectField(int index, int count);

        void visitObjectArray(int index, int count);

        void visitPrimitiveField(int index, int count);

        void visitPrimitiveArray(int index, int count);
    }

    // final assumption

    final AbstractAssumption getFinalAssumptionField() {
        return finalAssumption;
    }

    static AbstractAssumption createFinalAssumption() {
        DebugCounters.assumedFinalLocationAssumptionCount.inc();
        return (AbstractAssumption) Assumption.create("final location");
    }

    final void maybeInvalidateFinalAssumption() {
        var assumption = getFinalAssumptionField();
        if (assumption == null || assumption.isValid()) {
            invalidateFinalAssumption(assumption);
        }
    }

    @SuppressWarnings("unchecked")
    private void invalidateFinalAssumption(AbstractAssumption lastAssumption) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        var updater = FINAL_ASSUMPTION_UPDATER;
        DebugCounters.assumedFinalLocationAssumptionInvalidationCount.inc();
        AbstractAssumption assumption = lastAssumption;
        if (assumption == null) {
            while (!updater.compareAndSet(this, assumption, (AbstractAssumption) Assumption.NEVER_VALID)) {
                assumption = updater.get(this);
                if (assumption == Assumption.NEVER_VALID) {
                    break;
                }
                assumption.invalidate();
            }
        } else if (assumption.isValid()) {
            assumption.invalidate();
            updater.set(this, (AbstractAssumption) Assumption.NEVER_VALID);
        }
    }

    final AbstractAssumption getFinalAssumptionInternal() {
        var assumption = getFinalAssumptionField();
        if (assumption != null) {
            return assumption;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return initializeFinalAssumption();
    }

    @SuppressWarnings("unchecked")
    private AbstractAssumption initializeFinalAssumption() {
        CompilerAsserts.neverPartOfCompilation();
        var updater = FINAL_ASSUMPTION_UPDATER;
        AbstractAssumption newAssumption = createFinalAssumption();
        if (updater.compareAndSet(this, null, newAssumption)) {
            return newAssumption;
        } else {
            // if CAS failed, assumption is already initialized; cannot be null after that.
            return Objects.requireNonNull(updater.get(this));
        }
    }
}
