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

import static com.oracle.truffle.api.object.ExtLayout.UseVarHandle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;

import sun.misc.Unsafe;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 * @see DynamicObject
 */
@SuppressWarnings({"deprecation", "cast"})
abstract class ExtLocations {
    static final int INT_FIELD_SLOT_SIZE = 1;
    static final int INT_ARRAY_SLOT_SIZE = 1;
    static final int DOUBLE_ARRAY_SLOT_SIZE = 2;
    static final int LONG_ARRAY_SLOT_SIZE = 2;
    static final int OBJECT_SLOT_SIZE = 1;
    static final int MAX_DYNAMIC_FIELDS = 1000;

    interface TypedLocation {
        Class<?> getType();
    }

    interface ObjectLocation extends TypedLocation {
        @Override
        Class<? extends Object> getType();

        /** If {@code true}, this location does not accept {@code null} values. */
        boolean isNonNull();
    }

    interface IntLocation extends TypedLocation, com.oracle.truffle.api.object.IntLocation {
        @Override
        int getInt(DynamicObject store, boolean guard);

        @Override
        default int getInt(DynamicObject store, Shape shape) {
            return getInt(store, store.getShape() == shape);
        }

        void setInt(DynamicObject store, int value, boolean guard, boolean init);

        @Override
        default Class<Integer> getType() {
            return int.class;
        }

        @Override
        default void setInt(DynamicObject store, int value, Shape shape) {
            setInt(store, value, store.getShape() == shape, false);
        }
    }

    interface LongLocation extends TypedLocation, com.oracle.truffle.api.object.LongLocation {
        @Override
        long getLong(DynamicObject store, boolean guard);

        @Override
        default long getLong(DynamicObject store, Shape shape) {
            return getLong(store, store.getShape() == shape);
        }

        void setLong(DynamicObject store, long value, boolean guard, boolean init);

        @Override
        default Class<Long> getType() {
            return long.class;
        }

        boolean isImplicitCastIntToLong();

        @Override
        default void setLong(DynamicObject store, long value, Shape shape) {
            setLong(store, value, store.getShape() == shape, false);
        }
    }

    interface DoubleLocation extends TypedLocation, com.oracle.truffle.api.object.DoubleLocation {
        @Override
        double getDouble(DynamicObject store, boolean guard);

        @Override
        default double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, store.getShape() == shape);
        }

        void setDouble(DynamicObject store, double value, boolean guard, boolean init);

        @Override
        default Class<Double> getType() {
            return double.class;
        }

        boolean isImplicitCastIntToDouble();

        @Override
        default void setDouble(DynamicObject store, double value, Shape shape) {
            setDouble(store, value, store.getShape() == shape, false);
        }
    }

    interface BooleanLocation extends TypedLocation, com.oracle.truffle.api.object.BooleanLocation {
        @Override
        boolean getBoolean(DynamicObject store, boolean guard);

        @Override
        default boolean getBoolean(DynamicObject store, Shape shape) {
            return getBoolean(store, store.getShape() == shape);
        }

        void setBoolean(DynamicObject store, boolean value, boolean guard, boolean init);

        @Override
        default void setBoolean(DynamicObject store, boolean value, Shape shape) {
            setBoolean(store, value, store.getShape() == shape, false);
        }

        @Override
        default Class<Boolean> getType() {
            return boolean.class;
        }
    }

    abstract static class ValueLocation extends ExtLocation {

        private final Object value;

        ValueLocation(Object value) {
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
        public final void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (!canStore(value)) {
                if (init) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                } else {
                    throw incompatibleLocation();
                }
            }
        }

        @Override
        public String toString() {
            return "=" + String.valueOf(value);
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
        }

        @Override
        public final boolean isValue() {
            return true;
        }
    }

    static final class ConstantLocation extends ValueLocation {

        ConstantLocation(Object value) {
            super(value);
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    static final class DeclaredLocation extends ValueLocation {

        DeclaredLocation(Object value) {
            super(value);
        }

        @Override
        public boolean isDeclared() {
            return true;
        }
    }

    abstract static class InstanceLocation extends ExtLocation {

        protected final int index;

        @CompilationFinal protected volatile Assumption finalAssumption;

        private static final AtomicReferenceFieldUpdater<InstanceLocation, Assumption> FINAL_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                        InstanceLocation.class, Assumption.class, "finalAssumption");

        static final boolean LAZY_FINAL_ASSUMPTION = true;
        private static final DebugCounter assumedFinalLocationAssumptionCount = DebugCounter.create("Final location assumptions allocated");
        private static final DebugCounter assumedFinalLocationAssumptionInvalidationCount = DebugCounter.create("Final location assumptions invalidated");

        protected InstanceLocation(int index, Assumption finalAssumption) {
            this.index = index;
            this.finalAssumption = finalAssumption;
        }

        final int getIndex() {
            return index;
        }

        final Assumption getFinalAssumptionField() {
            return finalAssumption;
        }

        static Assumption createFinalAssumption() {
            assumedFinalLocationAssumptionCount.inc();
            return Truffle.getRuntime().createAssumption("final location");
        }

        protected final void maybeInvalidateFinalAssumption() {
            Assumption assumption = getFinalAssumptionField();
            if (assumption == null || assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidateFinalAssumption(assumption);
            }
        }

        @SuppressWarnings("unchecked")
        private void invalidateFinalAssumption(Assumption lastAssumption) {
            CompilerAsserts.neverPartOfCompilation();
            AtomicReferenceFieldUpdater<InstanceLocation, Assumption> updater = FINAL_ASSUMPTION_UPDATER;
            assumedFinalLocationAssumptionInvalidationCount.inc();
            Assumption assumption = lastAssumption;
            if (assumption == null) {
                while (!updater.compareAndSet(this, assumption, Assumption.NEVER_VALID)) {
                    assumption = updater.get(this);
                    if (assumption == Assumption.NEVER_VALID) {
                        break;
                    }
                    assumption.invalidate();
                }
            } else if (assumption.isValid()) {
                assumption.invalidate();
                updater.set(this, Assumption.NEVER_VALID);
            }
        }

        /**
         * Needs to be implemented so that {@link Location#getFinalAssumption()} is overridden.
         */
        @Override
        public final Assumption getFinalAssumption() {
            Assumption assumption = getFinalAssumptionField();
            if (assumption == null) {
                return initializeFinalAssumption();
            } else {
                return assumption;
            }
        }

        @SuppressWarnings("unchecked")
        private Assumption initializeFinalAssumption() {
            AtomicReferenceFieldUpdater<InstanceLocation, Assumption> updater = FINAL_ASSUMPTION_UPDATER;
            Assumption newAssumption = createFinalAssumption();
            if (updater.compareAndSet(this, null, newAssumption)) {
                return newAssumption;
            } else {
                // if CAS failed, assumption is already initialized; cannot be null after that.
                return Objects.requireNonNull(updater.get(this));
            }
        }

        /**
         * Needs to be implemented so that {@link Location#isAssumedFinal()} is overridden.
         */
        @Override
        public final boolean isAssumedFinal() {
            Assumption assumption = getFinalAssumptionField();
            return assumption == null || assumption.isValid();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + index;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            InstanceLocation other = (InstanceLocation) obj;
            if (index != other.index) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return super.toString() + ("[final=" + isAssumedFinal() + "]");
        }

        @Override
        public String getWhereString() {
            return this instanceof ArrayLocation ? ("[" + index + "]") : ("@" + index);
        }

        @Override
        int getOrdinal() {
            boolean isPrimitive = this instanceof AbstractPrimitiveFieldLocation || this instanceof AbstractPrimitiveArrayLocation;
            int ordinal = (isPrimitive ? -Integer.MAX_VALUE : 0) + getIndex();
            if (this instanceof ArrayLocation) {
                ordinal += MAX_DYNAMIC_FIELDS;
            }
            return ordinal;
        }
    }

    interface ArrayLocation {
    }

    interface FieldLocation {
    }

    abstract static class AbstractObjectLocation extends InstanceLocation implements ObjectLocation {

        @CompilationFinal protected volatile TypeAssumption typeAssumption;

        private static final AtomicReferenceFieldUpdater<AbstractObjectLocation, TypeAssumption> TYPE_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(AbstractObjectLocation.class,
                        TypeAssumption.class, "typeAssumption");

        static final boolean LAZY_ASSUMPTION = false;
        private static final DebugCounter assumedTypeLocationAssumptionCount = DebugCounter.create("Typed location assumptions allocated");
        private static final DebugCounter assumedTypeLocationAssumptionInvalidationCount = DebugCounter.create("Typed location assumptions invalidated");
        private static final DebugCounter assumedTypeLocationAssumptionRenewCount = DebugCounter.create("Typed location assumptions renewed");

        AbstractObjectLocation(int index, Assumption finalAssumption, TypeAssumption typeAssumption) {
            super(index, finalAssumption);
            this.typeAssumption = typeAssumption;
        }

        public final TypeAssumption getTypeAssumption() {
            return typeAssumption;
        }

        @Override
        public final boolean canStore(Object value) {
            return true;
        }

        @Override
        public final Class<? extends Object> getType() {
            return Object.class;
        }

        @Override
        public final boolean isNonNull() {
            return false;
        }

        protected final boolean canStoreInternal(Object value) {
            TypeAssumption curr = getTypeAssumption();
            if (curr == TypeAssumption.ANY) {
                return true;
            } else if (curr != null && curr.getAssumption().isValid()) {
                if (value == null) {
                    return !curr.nonNull;
                }
                Class<? extends Object> type = curr.type;
                return type == Object.class || type.isInstance(value);
            } else {
                return false;
            }
        }

        protected final Object assumedTypeCast(Object value, boolean condition) {
            if (CompilerDirectives.inInterpreter()) {
                return value;
            }
            TypeAssumption curr = getTypeAssumption();
            if (curr != null && curr != TypeAssumption.ANY && curr.getAssumption().isValid()) {
                Class<? extends Object> type = curr.type;
                boolean nonNull = curr.nonNull;
                return UnsafeAccess.unsafeCast(value, type, condition, nonNull);
            } else {
                return value;
            }
        }

        protected final Class<? extends Object> getAssumedType() {
            TypeAssumption curr = getTypeAssumption();
            if (curr != null && curr.getAssumption().isValid()) {
                return curr.type;
            } else {
                return Object.class;
            }
        }

        protected final boolean isAssumedNonNull() {
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
            assumedTypeLocationAssumptionCount.inc();
            return new TypeAssumption(Truffle.getRuntime().createAssumption("typed object location"), type, nonNull);
        }

        static TypeAssumption createTypeAssumptionFromValue(Object value) {
            boolean nonNull = value != null;
            Class<? extends Object> type = nonNull ? value.getClass() : Object.class;
            return createTypeAssumption(type, nonNull);
        }

        protected final void maybeInvalidateTypeAssumption(Object value) {
            if (canStoreInternal(value)) {
                return;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            invalidateTypeAssumption(value);
        }

        private void invalidateTypeAssumption(Object value) {
            CompilerAsserts.neverPartOfCompilation();
            AtomicReferenceFieldUpdater<AbstractObjectLocation, TypeAssumption> updater = TYPE_ASSUMPTION_UPDATER;
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
                        assumedTypeLocationAssumptionRenewCount.inc();
                    }
                } else {
                    curr.getAssumption().invalidate("generalizing object type " +
                                    TypeAssumption.toString(curr.type, curr.nonNull) + " => " +
                                    TypeAssumption.toString(type, nonNull) + " " + this);
                    assumedTypeLocationAssumptionInvalidationCount.inc();
                }
                TypeAssumption next = createTypeAssumption(type, nonNull);
                if (updater.compareAndSet(this, curr, next)) {
                    break;
                }
            }
        }

        final void mergeTypeAssumption(TypeAssumption other) {
            CompilerAsserts.neverPartOfCompilation();
            AtomicReferenceFieldUpdater<AbstractObjectLocation, TypeAssumption> updater = TYPE_ASSUMPTION_UPDATER;
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
                        assumedTypeLocationAssumptionRenewCount.inc();
                    }
                } else {
                    curr.getAssumption().invalidate("generalizing object type " +
                                    TypeAssumption.toString(curr.type, curr.nonNull) + " => " +
                                    TypeAssumption.toString(type, nonNull) + " " + this);
                    assumedTypeLocationAssumptionInvalidationCount.inc();
                }
                TypeAssumption next = createTypeAssumption(type, nonNull);
                if (updater.compareAndSet(this, curr, next)) {
                    break;
                }
            }
        }

        @Override
        public String toString() {
            TypeAssumption assumed = getTypeAssumption();
            return super.toString() + ("[type=" + TypeAssumption.toString(assumed.type, assumed.nonNull) + "]");
        }
    }

    abstract static class AbstractObjectArrayLocation extends AbstractObjectLocation implements ArrayLocation {

        AbstractObjectArrayLocation(int index, Assumption finalAssumption, TypeAssumption typeAssumption) {
            super(index, finalAssumption, typeAssumption);
        }

        protected static Object getArray(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeCast(store.getObjectStore(), Object[].class, condition, true, true);
        }

        protected final long getOffset() {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetObject(getArray(store, guard), getOffset(), guard, this);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            boolean valueGuard = canStore(value);
            if (valueGuard) {
                setObjectInternal(store, value, guard);
            } else {
                throw incompatibleLocation();
            }
        }

        protected final void setObjectInternal(DynamicObject store, Object value, boolean guard) {
            UnsafeAccess.unsafePutObject(getArray(store, guard), getOffset(), value, this);
        }

        protected final Object getFinalObject(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeGetFinalObject(getArray(store, condition), getOffset(), condition, this);
        }

        @Override
        protected final void clear(DynamicObject store) {
            UnsafeAccess.unsafePutObject(getArray(store, false), getOffset(), null, this);
        }

        @Override
        public final int objectArrayCount() {
            return OBJECT_SLOT_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitObjectArray(index, OBJECT_SLOT_SIZE);
        }
    }

    /**
     * Object array location with assumption-based type speculation.
     */
    static final class ATypedObjectArrayLocation extends AbstractObjectArrayLocation {

        ATypedObjectArrayLocation(int index, TypeAssumption typeAssumption, Assumption finalAssumption) {
            super(index, finalAssumption, typeAssumption);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            maybeInvalidateTypeAssumption(value);
            super.setObjectInternal(store, value, guard);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return assumedTypeCast(super.get(store, guard), guard);
        }
    }

    abstract static class AbstractObjectFieldLocation extends AbstractObjectLocation implements FieldLocation {
        protected final FieldInfo field;

        AbstractObjectFieldLocation(int index, FieldInfo field, Assumption finalAssumption, TypeAssumption typeAssumption) {
            super(index, finalAssumption, typeAssumption);
            this.field = Objects.requireNonNull(field);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetObject(store, getOffset(), guard, this);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            boolean condition = canStore(value);
            if (condition) {
                setObjectInternal(store, value);
            } else {
                throw incompatibleLocation();
            }
        }

        protected final void setObjectInternal(DynamicObject store, Object value) {
            if (UseVarHandle) {
                field.varHandle().set(store, value);
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutObject(store, getOffset(), value, this);
        }

        protected final Object getFinalObject(DynamicObject store, boolean condition) {
            if (UseVarHandle) {
                return field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetFinalObject(store, getOffset(), condition, this);
        }

        @Override
        protected final void clear(DynamicObject store) {
            UnsafeAccess.unsafePutObject(store, getOffset(), null, this);
        }

        @Override
        public final int objectFieldCount() {
            return OBJECT_SLOT_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitObjectField(index, OBJECT_SLOT_SIZE);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + field.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            AbstractObjectFieldLocation other = (AbstractObjectFieldLocation) obj;
            return this.field.equals(other.field);
        }

        final long getOffset() {
            return field.offset();
        }
    }

    /**
     * Object field location with assumption-based type speculation.
     */
    static final class ATypedObjectFieldLocation extends AbstractObjectFieldLocation {

        ATypedObjectFieldLocation(int index, FieldInfo field, TypeAssumption typeAssumption, Assumption finalAssumption) {
            super(index, field, finalAssumption, typeAssumption);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            maybeInvalidateTypeAssumption(value);
            super.setObjectInternal(store, value);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return assumedTypeCast(super.get(store, guard), guard);
        }
    }

    abstract static class AbstractPrimitiveFieldLocation extends InstanceLocation implements FieldLocation {

        protected final FieldInfo field;

        AbstractPrimitiveFieldLocation(int index, FieldInfo field, Assumption finalAssumption) {
            super(index, finalAssumption);
            this.field = Objects.requireNonNull(field);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + field.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            AbstractPrimitiveFieldLocation other = (AbstractPrimitiveFieldLocation) obj;
            return this.field.equals(other.field);
        }

        final long getOffset() {
            return field.offset();
        }
    }

    static final class IntFieldLocation extends AbstractPrimitiveFieldLocation implements IntLocation {

        IntFieldLocation(int index, FieldInfo field, Assumption finalAssumption) {
            super(index, field, finalAssumption);
            assert field.type() == long.class || field.type() == int.class : field;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getInt(store, guard);
        }

        @Override
        public int getInt(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                if (field.type() == long.class) {
                    return (int) (long) field.varHandle().get(store);
                } else {
                    return (int) field.varHandle().get(store);
                }
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                return (int) UnsafeAccess.unsafeGetLong(store, getOffset(), guard, this);
            } else {
                return UnsafeAccess.unsafeGetInt(store, getOffset(), guard, this);
            }
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setInt(store, (int) value, guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public void setInt(DynamicObject store, int value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setIntInternal(store, value);
        }

        protected void setIntInternal(DynamicObject store, int value) {
            if (UseVarHandle) {
                if (field.type() == long.class) {
                    field.varHandle().set(store, value & 0xffff_ffffL);
                } else {
                    field.varHandle().set(store, value);
                }
                return;
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                UnsafeAccess.unsafePutLong(store, getOffset(), value & 0xffff_ffffL, this);
            } else {
                UnsafeAccess.unsafePutInt(store, getOffset(), value, this);
            }
        }

        protected int getFinalInt(DynamicObject store, boolean condition) {
            if (UseVarHandle) {
                if (field.type() == long.class) {
                    return (int) (long) field.varHandle().get(store);
                } else {
                    return (int) field.varHandle().get(store);
                }
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                return (int) UnsafeAccess.unsafeGetFinalLong(store, getOffset(), condition, this);
            } else {
                return UnsafeAccess.unsafeGetFinalInt(store, getOffset(), condition, this);
            }
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Integer;
        }

        @Override
        public Class<Integer> getType() {
            return int.class;
        }

        @Override
        public int primitiveFieldCount() {
            return INT_FIELD_SLOT_SIZE;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), INT_FIELD_SLOT_SIZE);
        }
    }

    static final class DoubleFieldLocation extends AbstractPrimitiveFieldLocation implements DoubleLocation {
        protected final boolean allowInt;
        protected final byte slotSize;

        DoubleFieldLocation(int index, FieldInfo field, boolean allowInt, int slotSize, Assumption finalAssumption) {
            super(index, field, finalAssumption);
            this.allowInt = allowInt;
            assert slotSize >= 1 && slotSize <= 2;
            this.slotSize = (byte) slotSize;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getDouble(store, guard);
        }

        @Override
        public double getDouble(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return Double.longBitsToDouble((long) field.varHandle().get(store));
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                return Double.longBitsToDouble(UnsafeAccess.unsafeGetLong(store, getOffset(), guard, this));
            } else {
                return UnsafeAccess.unsafeGetDouble(store, getOffset(), guard, this);
            }
        }

        @Override
        public void setDouble(DynamicObject store, double value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setDoubleInternal(store, value);
        }

        protected void setDoubleInternal(DynamicObject store, double value) {
            if (UseVarHandle) {
                field.varHandle().set(store, Double.doubleToRawLongBits(value));
                return;
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                UnsafeAccess.unsafePutLong(store, getOffset(), Double.doubleToRawLongBits(value), this);
            } else {
                UnsafeAccess.unsafePutDouble(store, getOffset(), value, this);
            }
        }

        protected double getFinalDouble(DynamicObject store, boolean condition) {
            if (UseVarHandle) {
                return Double.longBitsToDouble((long) field.varHandle().get(store));
            }
            field.receiverCheck(store);
            if (field.type() == long.class) {
                return Double.longBitsToDouble(UnsafeAccess.unsafeGetFinalLong(store, getOffset(), condition, this));
            } else {
                return UnsafeAccess.unsafeGetFinalDouble(store, getOffset(), condition, this);
            }
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setDouble(store, doubleValue(value), guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        protected double doubleValue(Object value) {
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
        public double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, checkShape(store, shape));
        }

        @Override
        public Class<Double> getType() {
            return double.class;
        }

        @Override
        public int primitiveFieldCount() {
            return slotSize;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), slotSize);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((DoubleFieldLocation) obj).allowInt;
        }

        @Override
        public boolean isImplicitCastIntToDouble() {
            return allowInt;
        }
    }

    static final class BooleanFieldLocation extends AbstractPrimitiveFieldLocation implements BooleanLocation {

        BooleanFieldLocation(int index, FieldInfo field, Assumption finalAssumption) {
            super(index, field, finalAssumption);
            assert field.type() == int.class : field;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getBoolean(store, guard);
        }

        @Override
        public boolean getBoolean(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return UnsafeAccess.booleanCast((int) field.varHandle().get(store));
            }
            field.receiverCheck(store);
            return UnsafeAccess.booleanCast(UnsafeAccess.unsafeGetInt(store, getOffset(), guard, this));
        }

        @Override
        public void setBoolean(DynamicObject store, boolean value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setBooleanInternal(store, value);
        }

        protected void setBooleanInternal(DynamicObject store, boolean value) {
            if (UseVarHandle) {
                field.varHandle().set(store, UnsafeAccess.intCast(value));
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutInt(store, getOffset(), UnsafeAccess.intCast(value), this);
        }

        protected boolean getFinalBoolean(DynamicObject store, boolean condition) {
            if (UseVarHandle) {
                return UnsafeAccess.booleanCast((int) field.varHandle().get(store));
            }
            field.receiverCheck(store);
            return UnsafeAccess.booleanCast(UnsafeAccess.unsafeGetFinalInt(store, getOffset(), condition, this));
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setBoolean(store, (boolean) value, guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Boolean;
        }

        @Override
        public Class<Boolean> getType() {
            return boolean.class;
        }

        @Override
        public int primitiveFieldCount() {
            return INT_FIELD_SLOT_SIZE;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), INT_FIELD_SLOT_SIZE);
        }
    }

    abstract static class AbstractPrimitiveArrayLocation extends InstanceLocation implements ArrayLocation {

        AbstractPrimitiveArrayLocation(int index, Assumption finalAssumption) {
            super(index, finalAssumption);
        }

        protected final long getOffset() {
            return Unsafe.ARRAY_INT_BASE_OFFSET + (long) Unsafe.ARRAY_INT_INDEX_SCALE * index;
        }

        protected abstract int getBytes();

        protected static Object getArray(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeCast(store.getPrimitiveStore(), int[].class, condition, true, true);
        }
    }

    static final class IntArrayLocation extends AbstractPrimitiveArrayLocation implements IntLocation {

        IntArrayLocation(int index, Assumption finalAssumption) {
            super(index, finalAssumption);
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getInt(store, guard);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setInt(store, (int) value, guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public int getInt(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetInt(getArray(store, guard), getOffset(), guard, this);
        }

        protected void setIntInternal(DynamicObject store, int value, boolean guard) {
            UnsafeAccess.unsafePutInt(getArray(store, guard), getOffset(), value, this);
        }

        protected int getFinalInt(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeGetFinalInt(getArray(store, condition), getOffset(), condition, this);
        }

        @Override
        public void setInt(DynamicObject store, int value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setIntInternal(store, value, guard);
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Integer;
        }

        @Override
        public Class<Integer> getType() {
            return int.class;
        }

        @Override
        public int primitiveArrayCount() {
            return INT_ARRAY_SLOT_SIZE;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveArray(index, INT_ARRAY_SLOT_SIZE);
        }

        @Override
        protected int getBytes() {
            return Integer.BYTES;
        }
    }

    static final class DoubleArrayLocation extends AbstractPrimitiveArrayLocation implements DoubleLocation {
        protected final boolean allowInt;

        DoubleArrayLocation(int index, boolean allowInt, Assumption finalAssumption) {
            super(index, finalAssumption);
            this.allowInt = allowInt;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getDouble(store, guard);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setDouble(store, doubleValue(value), guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        protected double doubleValue(Object value) {
            if (!allowInt || value instanceof Double) {
                return ((Double) value).doubleValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).doubleValue();
            } else {
                throw shouldNotReachHere();
            }
        }

        @Override
        public double getDouble(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetDouble(getArray(store, guard), getOffset(), guard, this);
        }

        protected void setDoubleInternal(DynamicObject store, double value, boolean guard) {
            UnsafeAccess.unsafePutDouble(getArray(store, guard), getOffset(), value, this);
        }

        protected double getFinalDouble(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeGetFinalDouble(getArray(store, condition), getOffset(), condition, this);
        }

        @Override
        public void setDouble(DynamicObject store, double value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setDoubleInternal(store, value, guard);
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Double || (allowInt && value instanceof Integer);
        }

        @Override
        public Class<Double> getType() {
            return double.class;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveArray(index, DOUBLE_ARRAY_SLOT_SIZE);
        }

        @Override
        public int primitiveArrayCount() {
            return DOUBLE_ARRAY_SLOT_SIZE;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((DoubleArrayLocation) obj).allowInt;
        }

        @Override
        public boolean isImplicitCastIntToDouble() {
            return allowInt;
        }

        @Override
        protected int getBytes() {
            return Double.BYTES;
        }
    }

    static final class LongFieldLocation extends AbstractPrimitiveFieldLocation implements LongLocation {
        protected final boolean allowInt;
        protected final byte slotSize;

        LongFieldLocation(int index, FieldInfo field, boolean allowInt, int slotSize, Assumption finalAssumption) {
            super(index, field, finalAssumption);
            this.allowInt = allowInt;
            assert slotSize >= 1 && slotSize <= 2;
            this.slotSize = (byte) slotSize;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getLong(store, guard);
        }

        @Override
        public long getLong(DynamicObject store, boolean guard) {
            if (UseVarHandle) {
                return (long) field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetLong(store, getOffset(), guard, this);
        }

        @Override
        public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setLongInternal(store, value);
        }

        protected void setLongInternal(DynamicObject store, long value) {
            if (UseVarHandle) {
                field.varHandle().set(store, value);
                return;
            }
            field.receiverCheck(store);
            UnsafeAccess.unsafePutLong(store, getOffset(), value, this);
        }

        protected long getFinalLong(DynamicObject store, boolean condition) {
            if (UseVarHandle) {
                return (long) field.varHandle().get(store);
            }
            field.receiverCheck(store);
            return UnsafeAccess.unsafeGetFinalLong(store, getOffset(), condition, this);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setLong(store, longValue(value), guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        protected long longValue(Object value) {
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
        public Class<Long> getType() {
            return long.class;
        }

        @Override
        public int primitiveFieldCount() {
            return slotSize;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), slotSize);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongFieldLocation) obj).allowInt;
        }

        @Override
        public boolean isImplicitCastIntToLong() {
            return allowInt;
        }
    }

    static final class LongArrayLocation extends AbstractPrimitiveArrayLocation implements LongLocation {
        protected final boolean allowInt;

        LongArrayLocation(int index, boolean allowInt, Assumption finalAssumption) {
            super(index, finalAssumption);
            this.allowInt = allowInt;
        }

        @Override
        public Object get(DynamicObject store, boolean guard) {
            return getLong(store, guard);
        }

        @Override
        protected void set(DynamicObject store, Object value, boolean guard, boolean init) throws com.oracle.truffle.api.object.IncompatibleLocationException {
            if (canStore(value)) {
                setLong(store, longValue(value), guard, init);
            } else {
                throw incompatibleLocation();
            }
        }

        protected long longValue(Object value) {
            if (!allowInt || value instanceof Long) {
                return ((Long) value).longValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else {
                throw shouldNotReachHere();
            }
        }

        @Override
        public long getLong(DynamicObject store, boolean guard) {
            return UnsafeAccess.unsafeGetLong(getArray(store, guard), getOffset(), guard, this);
        }

        protected void setLongInternal(DynamicObject store, long value, boolean guard) {
            UnsafeAccess.unsafePutLong(getArray(store, guard), getOffset(), value, this);
        }

        protected long getFinalLong(DynamicObject store, boolean condition) {
            return UnsafeAccess.unsafeGetFinalLong(getArray(store, condition), getOffset(), condition, this);
        }

        @Override
        public void setLong(DynamicObject store, long value, boolean guard, boolean init) {
            if (!init) {
                maybeInvalidateFinalAssumption();
            }
            setLongInternal(store, value, guard);
        }

        @Override
        public boolean canStore(Object value) {
            return value instanceof Long || (allowInt && value instanceof Integer);
        }

        @Override
        public long getLong(DynamicObject store, Shape shape) {
            return getLong(store, checkShape(store, shape));
        }

        @Override
        public Class<Long> getType() {
            return long.class;
        }

        @Override
        public int primitiveArrayCount() {
            return LONG_ARRAY_SLOT_SIZE;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveArray(index, LONG_ARRAY_SLOT_SIZE);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongArrayLocation) obj).allowInt;
        }

        @Override
        public boolean isImplicitCastIntToLong() {
            return allowInt;
        }

        @Override
        protected int getBytes() {
            return Long.BYTES;
        }
    }

    /**
     * Assumption-based object location type speculation.
     */
    static final class TypeAssumption {
        final Assumption assumption;
        final Class<? extends Object> type;
        final boolean nonNull;

        static final TypeAssumption ANY = new TypeAssumption(Assumption.ALWAYS_VALID, Object.class, false);

        TypeAssumption(Assumption assumption, Class<? extends Object> type, boolean nonNull) {
            this.assumption = assumption;
            this.type = type;
            this.nonNull = nonNull;
        }

        public Assumption getAssumption() {
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

    static int getLocationOrdinal(ExtLocation loc) {
        return loc.getOrdinal();
    }

    static RuntimeException shouldNotReachHere() {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException();
    }
}
