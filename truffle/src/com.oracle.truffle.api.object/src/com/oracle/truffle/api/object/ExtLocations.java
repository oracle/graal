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

import static com.oracle.truffle.api.object.ObjectStorageOptions.UseVarHandle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.AbstractAssumption;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 * @see DynamicObject
 */
@SuppressWarnings({"deprecation", "cast"})
abstract class ExtLocations {
    static final int INT_ARRAY_SLOT_SIZE = 1;
    static final int DOUBLE_ARRAY_SLOT_SIZE = 2;
    static final int LONG_ARRAY_SLOT_SIZE = 2;
    static final int OBJECT_SLOT_SIZE = 1;
    static final int MAX_DYNAMIC_FIELDS = 1000;

    abstract static sealed class ValueLocation extends Location {

        final Object value;

        ValueLocation(Object value) {
            super();
            assert !(value instanceof Location);
            this.value = value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && Objects.equals(value, ((ValueLocation) obj).value);
        }

        @Override
        public final Object get(DynamicObject store, boolean guard) {
            return value;
        }

        @Override
        public final boolean canStore(Object val) {
            return valueEquals(this.value, val);
        }

        @Override
        public final void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (!canStore(value)) {
                if (init) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                } else {
                    throw incompatibleLocationException();
                }
            }
        }

        @Override
        void clear(DynamicObject store) {
        }

        @Override
        public String toString() {
            return "=" + value;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
        }

        @Override
        public final boolean isValue() {
            return true;
        }

        /**
         * Boxed values need to be compared by value not by reference.
         *
         * The first parameter should be the one with the more precise type information.
         *
         * For sets to final locations, otherValue.equals(thisValue) seems more beneficial, since we
         * usually know more about the value to be set.
         */
        static boolean valueEquals(Object val1, Object val2) {
            return val1 == val2 || (val1 != null && equalsBoundary(val1, val2));
        }

        @TruffleBoundary // Object.equals is a boundary
        private static boolean equalsBoundary(Object val1, Object val2) {
            return val1.equals(val2);
        }
    }

    static final class ConstantLocation extends ValueLocation {

        ConstantLocation(Object value) {
            super(value);
        }
    }

    /**
     * Array or field location.
     */
    abstract static sealed class InstanceLocation extends Location {

        static final boolean LAZY_FINAL_ASSUMPTION = true;

        protected InstanceLocation(int index, FieldInfo field, AbstractAssumption finalAssumption) {
            super(index, field, finalAssumption);
        }

        @Override
        public String toString() {
            return super.toString() + (isArrayLocation() ? ("[" + getIndex() + "]") : ("@" + getIndex())) + ("[final=" + isAssumedFinal() + "]");
        }
    }

    /**
     * Object array or field location with assumption-based type speculation.
     */
    static final class ObjectLocation extends InstanceLocation {

        @CompilationFinal volatile TypeAssumption typeAssumption;

        private static final AtomicReferenceFieldUpdater<ObjectLocation, TypeAssumption> TYPE_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                        ObjectLocation.class, TypeAssumption.class, "typeAssumption");

        static final boolean LAZY_TYPE_ASSUMPTION = false;

        /*
         * @param typeAssumption initial value of type assumption field
         */
        private ObjectLocation(int index, AbstractAssumption finalAssumption, TypeAssumption typeAssumption) {
            super(index, null, finalAssumption);
            this.typeAssumption = typeAssumption;
        }

        /*
         * @param typeAssumption initial value of type assumption field
         */
        private ObjectLocation(int index, FieldInfo field, AbstractAssumption finalAssumption, TypeAssumption typeAssumption) {
            super(index, Objects.requireNonNull(field), finalAssumption);
            this.typeAssumption = typeAssumption;
        }

        static ObjectLocation createObjectArrayLocation(int index, AbstractAssumption finalAssumption, TypeAssumption typeAssumption) {
            return new ObjectLocation(index, finalAssumption, typeAssumption);
        }

        static ObjectLocation createObjectFieldLocation(int index, FieldInfo field, AbstractAssumption finalAssumption, TypeAssumption typeAssumption) {
            return new ObjectLocation(index, field, finalAssumption, typeAssumption);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            Object value;
            if (field == null) {
                value = getObjectArrayInternal(store, guard);
            } else {
                value = getObjectFieldInternal(store, guard);
            }
            return CompilerDirectives.inInterpreter() ? value : assumedTypeCast(value, guard);
        }

        private Object getObjectArrayInternal(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetObject(getObjectArray(store, guard), getObjectArrayOffset(), guard, this);
        }

        private Object getObjectFieldInternal(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetObject(store, getFieldOffset(), guard, this);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            maybeInvalidateTypeAssumption(value);
            if (field == null) {
                setObjectArrayInternal(store, value, guard);
            } else {
                setObjectFieldInternal(store, value);
            }
        }

        private void setObjectArrayInternal(DynamicObject store, Object value, boolean guard) {
            UnsafeAccess.unsafePutObject(getObjectArray(store, guard), getObjectArrayOffset(), value, this);
        }

        private void setObjectFieldInternal(DynamicObject store, Object value) {
            if (UseVarHandle) {
                field.varHandle().set(store, value);
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutObject(store, getFieldOffset(), value, this);
        }

        @Override
        protected void clear(DynamicObject store) {
            if (field == null) {
                setObjectArrayInternal(store, null, false);
            } else {
                setObjectFieldInternal(store, null);
            }
        }

        @Override
        public boolean canStore(Object value) {
            return true;
        }

        @Override
        Class<? extends Object> getType() {
            return Object.class;
        }

        TypeAssumption getTypeAssumption() {
            return typeAssumption;
        }

        boolean canStoreInternal(Object value) {
            TypeAssumption curr = getTypeAssumption();
            if (curr == TypeAssumption.ANY) {
                return true;
            } else if (curr != null && curr.getAssumption().isValid()) {
                if (value == null) {
                    return !curr.nonNull;
                }
                Class<? extends Object> type = curr.type;
                return type == Object.class || value.getClass() == type || type.isInstance(value);
            } else {
                return false;
            }
        }

        Object assumedTypeCast(Object value, boolean condition) {
            assert CompilerDirectives.inCompiledCode();
            TypeAssumption curr = getTypeAssumption();
            if (curr != null && curr != TypeAssumption.ANY && curr.getAssumption().isValid()) {
                Class<? extends Object> type = curr.type;
                boolean nonNull = curr.nonNull;
                return UnsafeAccess.unsafeCast(value, type, condition, nonNull, false);
            } else {
                return value;
            }
        }

        Class<? extends Object> getAssumedType() {
            TypeAssumption curr = getTypeAssumption();
            if (curr != null && curr.getAssumption().isValid()) {
                return curr.type;
            } else {
                return Object.class;
            }
        }

        boolean isAssumedNonNull() {
            TypeAssumption curr = getTypeAssumption();
            if (curr != null && curr.getAssumption().isValid()) {
                return curr.nonNull;
            } else {
                return false;
            }
        }

        static TypeAssumption createTypeAssumption(Class<? extends Object> type, boolean nonNull) {
            if (type == Object.class && !nonNull) {
                return TypeAssumption.ANY;
            }
            DebugCounters.assumedTypeLocationAssumptionCount.inc();
            return new TypeAssumption((AbstractAssumption) Assumption.create("typed object location"), type, nonNull);
        }

        static TypeAssumption createTypeAssumptionFromValue(Object value) {
            boolean nonNull = value != null;
            Class<? extends Object> type = nonNull ? value.getClass() : Object.class;
            return createTypeAssumption(type, nonNull);
        }

        void maybeInvalidateTypeAssumption(Object value) {
            if (canStoreInternal(value)) {
                return;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            invalidateTypeAssumption(value);
        }

        void invalidateTypeAssumption(Object value) {
            CompilerAsserts.neverPartOfCompilation();
            var updater = TYPE_ASSUMPTION_UPDATER;
            for (;;) {  // TERMINATION ARGUMENT: loop will terminate once CAS succeeds
                TypeAssumption curr = getTypeAssumption();
                if (curr == null) {
                    TypeAssumption next = createTypeAssumptionFromValue(value);
                    if (updater.compareAndSet(this, curr, next)) {
                        break;
                    } else {
                        continue;
                    }
                }
                boolean nonNull = curr.nonNull;
                boolean changed = false;
                if (nonNull && value == null) {
                    nonNull = false;
                    changed = true;
                }
                Class<? extends Object> type = curr.type;
                if (value != null && !type.isInstance(value)) {
                    type = ExtAllocator.getCommonSuperclassForValue(curr.type, value);
                    changed = true;
                }
                if (!changed) {
                    /*
                     * The value is still assignable to the currently assumed type, so the
                     * assumption should be valid.
                     *
                     * But we also need to account for the case where the assumption is (was) shared
                     * with another location because a location has been moved and the assumption
                     * copied over to the new location, so the other location might have invalidated
                     * the type assumption, and we need to create a new, valid one for ourself.
                     */
                    if (curr.getAssumption().isValid()) {
                        break;
                    } else {
                        DebugCounters.assumedTypeLocationAssumptionRenewCount.inc();
                    }
                } else {
                    curr.getAssumption().invalidate("generalizing object type " +
                                    TypeAssumption.toString(curr.type, curr.nonNull) + " => " +
                                    TypeAssumption.toString(type, nonNull) + " " + this);
                    DebugCounters.assumedTypeLocationAssumptionInvalidationCount.inc();
                }
                TypeAssumption next = createTypeAssumption(type, nonNull);
                if (updater.compareAndSet(this, curr, next)) {
                    break;
                }
            }
        }

        void mergeTypeAssumption(TypeAssumption other) {
            CompilerAsserts.neverPartOfCompilation();
            var updater = TYPE_ASSUMPTION_UPDATER;
            for (;;) {  // TERMINATION ARGUMENT: loop will terminate once CAS succeeds
                TypeAssumption curr = getTypeAssumption();
                if (curr == null) {
                    TypeAssumption next = other;
                    if (updater.compareAndSet(this, curr, next)) {
                        break;
                    } else {
                        continue;
                    }
                }
                boolean nonNull = curr.nonNull;
                boolean changed = false;
                if (nonNull && !other.nonNull) {
                    nonNull = false;
                    changed = true;
                }
                Class<? extends Object> type = curr.type;
                if (!type.isAssignableFrom(other.type)) {
                    type = ExtAllocator.getCommonSuperclass(curr.type, other.type);
                    changed = true;
                }
                if (!changed) {
                    // Type assumption should be valid, but see invalidateTypeAssumption().
                    if (curr.getAssumption().isValid()) {
                        break;
                    } else {
                        DebugCounters.assumedTypeLocationAssumptionRenewCount.inc();
                    }
                } else {
                    curr.getAssumption().invalidate("generalizing object type " +
                                    TypeAssumption.toString(curr.type, curr.nonNull) + " => " +
                                    TypeAssumption.toString(type, nonNull) + " " + this);
                    DebugCounters.assumedTypeLocationAssumptionInvalidationCount.inc();
                }
                TypeAssumption next = createTypeAssumption(type, nonNull);
                if (updater.compareAndSet(this, curr, next)) {
                    break;
                }
            }
        }

        @Override
        public int objectFieldCount() {
            return isFieldLocation() ? OBJECT_SLOT_SIZE : 0;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            if (field == null) {
                locationVisitor.visitObjectArray(getIndex(), OBJECT_SLOT_SIZE);
            } else {
                locationVisitor.visitObjectField(getIndex(), OBJECT_SLOT_SIZE);
            }
        }

        @Override
        public String toString() {
            TypeAssumption assumed = getTypeAssumption();
            return super.toString() + ("[type=" + TypeAssumption.toString(assumed.type, assumed.nonNull) + "]");
        }
    }

    /**
     * Non-sealed because there used to be BooleanFieldLocation and Graal.js still uses
     * {@link com.oracle.truffle.api.object.BooleanLocation}. If sealed it would cause a javac
     * error:
     *
     * <pre>
     * .../PropertySetNode.java:577: error: incompatible types: Location cannot be converted to BooleanLocation
     *             this.location = (com.oracle.truffle.api.object.BooleanLocation) property.getLocation();
     * </pre>
     */
    abstract static non-sealed class AbstractPrimitiveLocation extends InstanceLocation {

        AbstractPrimitiveLocation(int index, AbstractAssumption finalAssumption) {
            super(index, null, finalAssumption);
        }

        AbstractPrimitiveLocation(int index, FieldInfo field, AbstractAssumption finalAssumption) {
            super(index, Objects.requireNonNull(field), finalAssumption);
            assert field.type() == long.class : field;
        }

        @Override
        final int primitiveFieldCount() {
            return isFieldLocation() ? 1 : 0;
        }
    }

    static final class IntLocation extends AbstractPrimitiveLocation implements com.oracle.truffle.api.object.IntLocation {

        private IntLocation(int index, AbstractAssumption finalAssumption) {
            super(index, finalAssumption);
        }

        private IntLocation(int index, FieldInfo field, AbstractAssumption finalAssumption) {
            super(index, Objects.requireNonNull(field), finalAssumption);
        }

        static IntLocation createIntArrayLocation(int index, AbstractAssumption finalAssumption) {
            return new IntLocation(index, finalAssumption);
        }

        static IntLocation createIntFieldLocation(int index, FieldInfo field, AbstractAssumption finalAssumption) {
            return new IntLocation(index, field, finalAssumption);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getInt(store, guard);
        }

        @Override
        public int getInt(DynamicObject store, boolean guard) {
            if (field == null) {
                return getIntArray(store, guard);
            } else {
                return getIntField(store, guard);
            }
        }

        private int getIntArray(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetInt(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), guard, this);
        }

        private int getIntField(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return (int) (long) field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return (int) UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (canStore(value)) {
                setInt(store, (int) value, guard, init);
            } else {
                throw incompatibleLocationException();
            }
        }

        @Override
        public void setInt(DynamicObject store, int value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            if (field == null) {
                setIntArrayInternal(store, value, guard);
            } else {
                setIntFieldInternal(store, value);
            }
        }

        private void setIntArrayInternal(DynamicObject store, int value, boolean guard) {
            UnsafeAccess.unsafePutInt(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), value, this);
        }

        private void setIntFieldInternal(DynamicObject store, int value) {
            if (UseVarHandle) {
                field.varHandle().set(store, value & 0xffff_ffffL);
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutLong(store, getFieldOffset(), value & 0xffff_ffffL, this);
        }

        @Override
        void clear(DynamicObject store) {
            setInt(store, 0, false, true);
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Integer;
        }

        @Override
        Class<Integer> getType() {
            return int.class;
        }

        @Override
        int primitiveArrayCount() {
            return isArrayLocation() ? INT_ARRAY_SLOT_SIZE : 0;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            if (field == null) {
                locationVisitor.visitPrimitiveArray(getIndex(), INT_ARRAY_SLOT_SIZE);
            } else {
                locationVisitor.visitPrimitiveField(getIndex(), 1);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getInt(DynamicObject store, Shape shape) {
            return getInt(store, store.getShape() == shape);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void setInt(DynamicObject store, int value, Shape shape) {
            setInt(store, value, store.getShape() == shape, false);
        }
    }

    static final class DoubleLocation extends AbstractPrimitiveLocation implements com.oracle.truffle.api.object.DoubleLocation {
        private final boolean allowInt;

        DoubleLocation(int index, boolean allowInt, AbstractAssumption finalAssumption) {
            super(index, finalAssumption);
            this.allowInt = allowInt;
        }

        DoubleLocation(int index, FieldInfo field, boolean allowInt, AbstractAssumption finalAssumption) {
            super(index, field, finalAssumption);
            this.allowInt = allowInt;
        }

        static DoubleLocation createDoubleArrayLocation(int index, boolean allowInt, AbstractAssumption finalAssumption) {
            return new DoubleLocation(index, allowInt, finalAssumption);
        }

        static DoubleLocation createDoubleFieldLocation(int index, FieldInfo field, boolean allowInt, AbstractAssumption finalAssumption) {
            return new DoubleLocation(index, field, allowInt, finalAssumption);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getDouble(store, guard);
        }

        @Override
        public double getDouble(DynamicObject store, boolean guard) {
            if (field == null) {
                return getDoubleArray(store, guard);
            } else {
                return getDoubleField(store, guard);
            }
        }

        private double getDoubleArray(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetDouble(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), guard, this);
        }

        private double getDoubleField(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return Double.longBitsToDouble((long) field.varHandle().get(store));
            }
            field.receiverCheck(store);
            return Double.longBitsToDouble(UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this));
        }

        @Override
        public void setDouble(DynamicObject store, double value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            if (field == null) {
                setDoubleArrayInternal(store, value, guard);
            } else {
                setDoubleFieldInternal(store, value);
            }
        }

        private void setDoubleArrayInternal(DynamicObject store, double value, boolean guard) {
            UnsafeAccess.unsafePutDouble(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), value, this);
        }

        private void setDoubleFieldInternal(DynamicObject store, double value) {
            if (UseVarHandle) {
                field.varHandle().set(store, Double.doubleToRawLongBits(value));
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutLong(store, getFieldOffset(), Double.doubleToRawLongBits(value), this);
        }

        @Override
        void clear(DynamicObject store) {
            setDouble(store, 0, false, true);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (canStore(value)) {
                setDouble(store, doubleValue(value), guard, init);
            } else {
                throw incompatibleLocationException();
            }
        }

        double doubleValue(Object value) {
            if (!allowInt || value instanceof Double) {
                return ((Double) value).doubleValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).doubleValue();
            } else {
                throw shouldNotReachHere();
            }
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Double || (allowInt && value instanceof Integer);
        }

        @Override
        Class<Double> getType() {
            return double.class;
        }

        @Override
        int primitiveArrayCount() {
            return isArrayLocation() ? DOUBLE_ARRAY_SLOT_SIZE : 0;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            if (field == null) {
                locationVisitor.visitPrimitiveArray(getIndex(), DOUBLE_ARRAY_SLOT_SIZE);
            } else {
                locationVisitor.visitPrimitiveField(getIndex(), 1);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int hash = super.hashCode();
            hash = hash * prime + Boolean.hashCode(allowInt);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((DoubleLocation) obj).allowInt;
        }

        @Override
        boolean isImplicitCastIntToDouble() {
            return allowInt;
        }

        @SuppressWarnings("deprecation")
        @Override
        public double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, checkShape(store, shape));
        }

        @SuppressWarnings("deprecation")
        @Override
        public void setDouble(DynamicObject store, double value, Shape shape) {
            setDouble(store, value, store.getShape() == shape, false);
        }
    }

    static final class LongLocation extends AbstractPrimitiveLocation implements com.oracle.truffle.api.object.LongLocation {
        private final boolean allowInt;

        LongLocation(int index, boolean allowInt, AbstractAssumption finalAssumption) {
            super(index, finalAssumption);
            this.allowInt = allowInt;
        }

        LongLocation(int index, FieldInfo field, boolean allowInt, AbstractAssumption finalAssumption) {
            super(index, field, finalAssumption);
            this.allowInt = allowInt;
        }

        static LongLocation createLongArrayLocation(int index, boolean allowInt, AbstractAssumption finalAssumption) {
            return new LongLocation(index, allowInt, finalAssumption);
        }

        static LongLocation createLongFieldLocation(int index, FieldInfo field, boolean allowInt, AbstractAssumption finalAssumption) {
            return new LongLocation(index, field, allowInt, finalAssumption);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getLong(store, guard);
        }

        @Override
        public long getLong(DynamicObject store, boolean guard) {
            if (field == null) {
                return getLongArray(store, guard);
            } else {
                return getLongField(store, guard);
            }
        }

        private long getLongArray(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetLong(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), guard, this);
        }

        private long getLongField(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return (long) field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetLong(store, getFieldOffset(), guard, this);
        }

        @Override
        public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            if (field == null) {
                setLongArrayInternal(store, value, guard);
            } else {
                setLongFieldInternal(store, value);
            }
        }

        private void setLongArrayInternal(DynamicObject store, long value, boolean guard) {
            UnsafeAccess.unsafePutLong(getPrimitiveArray(store, guard), getPrimitiveArrayOffset(), value, this);
        }

        private void setLongFieldInternal(DynamicObject store, long value) {
            if (UseVarHandle) {
                field.varHandle().set(store, value);
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutLong(store, getFieldOffset(), value, this);
        }

        @Override
        void clear(DynamicObject store) {
            setLong(store, 0L, false, true);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (canStore(value)) {
                setLong(store, longValue(value), guard, init);
            } else {
                throw incompatibleLocationException();
            }
        }

        long longValue(Object value) {
            if (!allowInt || value instanceof Long) {
                return ((Long) value).longValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else {
                throw shouldNotReachHere();
            }
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Long || (allowInt && value instanceof Integer);
        }

        @Override
        Class<Long> getType() {
            return long.class;
        }

        @Override
        int primitiveArrayCount() {
            return isArrayLocation() ? LONG_ARRAY_SLOT_SIZE : 0;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            if (field == null) {
                locationVisitor.visitPrimitiveArray(getIndex(), LONG_ARRAY_SLOT_SIZE);
            } else {
                locationVisitor.visitPrimitiveField(getIndex(), 1);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int hash = super.hashCode();
            hash = hash * prime + Boolean.hashCode(allowInt);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongLocation) obj).allowInt;
        }

        @Override
        boolean isImplicitCastIntToLong() {
            return allowInt;
        }

        @SuppressWarnings("deprecation")
        @Override
        public long getLong(DynamicObject store, Shape shape) {
            return getLong(store, store.getShape() == shape);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void setLong(DynamicObject store, long value, Shape shape) {
            setLong(store, value, store.getShape() == shape, false);
        }
    }

    /**
     * Assumption-based object location type speculation.
     */
    static final class TypeAssumption {
        final AbstractAssumption assumption;
        final Class<? extends Object> type;
        final boolean nonNull;

        static final TypeAssumption ANY = new TypeAssumption((AbstractAssumption) Assumption.ALWAYS_VALID, Object.class, false);

        TypeAssumption(AbstractAssumption assumption, Class<? extends Object> type, boolean nonNull) {
            this.assumption = assumption;
            this.type = type;
            this.nonNull = nonNull;
        }

        public AbstractAssumption getAssumption() {
            return assumption;
        }

        @Override
        public String toString() {
            return toString(type, nonNull) + (assumption.isValid() ? "" : "(invalid)");
        }

        static String toString(Class<?> type, boolean nonNull) {
            if (type == Object.class && !nonNull) {
                return "ANY";
            }
            return (nonNull ? "!" : "") + type.getTypeName();
        }
    }

    static RuntimeException shouldNotReachHere() {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException();
    }
}
