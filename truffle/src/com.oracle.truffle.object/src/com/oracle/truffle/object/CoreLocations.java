/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 */
@SuppressWarnings("deprecation")
abstract class CoreLocations {

    static final int LONG_FIELD_SIZE = 1;
    static final int LONG_ARRAY_SIZE = 1;
    static final int OBJECT_SIZE = 1;
    static final int MAX_DYNAMIC_FIELDS = 1000;

    public interface TypedLocation extends com.oracle.truffle.api.object.TypedLocation {
        Class<?> getType();
    }

    public interface ObjectLocation extends TypedLocation, com.oracle.truffle.api.object.ObjectLocation {
        Class<? extends Object> getType();

        /**
         * If {@code true}, this location does not accept {@code null} values.
         */
        boolean isNonNull();
    }

    public interface IntLocation extends TypedLocation, com.oracle.truffle.api.object.IntLocation {
        int getInt(DynamicObject store, boolean condition);

        void setInt(DynamicObject store, int value, boolean condition);

        default Class<Integer> getType() {
            return int.class;
        }

        // --- deprecated methods below ---

        @Override
        default int getInt(DynamicObject store, Shape shape) {
            return getInt(store, store.getShape() == shape);
        }

        @Override
        default void setInt(DynamicObject store, int value, Shape shape) throws FinalLocationException {
            setInt(store, value, store.getShape() == shape);
        }

        @Override
        default void setInt(DynamicObject store, int value) throws FinalLocationException {
            setInt(store, value, false);
        }

        @Override
        default void setInt(DynamicObject store, int value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setInt(store, value, false);
        }
    }

    public interface LongLocation extends TypedLocation, com.oracle.truffle.api.object.LongLocation {
        long getLong(DynamicObject store, boolean condition);

        void setLong(DynamicObject store, long value, boolean condition);

        default Class<Long> getType() {
            return long.class;
        }

        default boolean isImplicitCastIntToLong() {
            return false;
        }

        // --- deprecated methods below ---

        @Override
        default long getLong(DynamicObject store, Shape shape) {
            return getLong(store, store.getShape() == shape);
        }

        @Override
        default void setLong(DynamicObject store, long value, Shape shape) throws FinalLocationException {
            setLong(store, value, store.getShape() == shape);
        }

        @Override
        default void setLong(DynamicObject store, long value) throws FinalLocationException {
            setLong(store, value, false);
        }

        @Override
        default void setLong(DynamicObject store, long value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLong(store, value, false);
        }
    }

    public interface DoubleLocation extends TypedLocation, com.oracle.truffle.api.object.DoubleLocation {
        double getDouble(DynamicObject store, boolean condition);

        void setDouble(DynamicObject store, double value, boolean condition);

        default Class<Double> getType() {
            return double.class;
        }

        boolean isImplicitCastIntToDouble();

        // --- deprecated methods below ---

        @Override
        default double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, store.getShape() == shape);
        }

        @Override
        default void setDouble(DynamicObject store, double value, Shape shape) throws FinalLocationException {
            setDouble(store, value, store.getShape() == shape);
        }

        @Override
        default void setDouble(DynamicObject store, double value) throws FinalLocationException {
            setDouble(store, value, false);
        }

        @Override
        default void setDouble(DynamicObject store, double value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setDouble(store, value, false);
        }
    }

    public interface BooleanLocation extends TypedLocation, com.oracle.truffle.api.object.BooleanLocation {
        boolean getBoolean(DynamicObject store, boolean condition);

        void setBoolean(DynamicObject store, boolean value, boolean condition);

        default Class<Boolean> getType() {
            return boolean.class;
        }

        // --- deprecated methods below ---

        @Override
        default boolean getBoolean(DynamicObject store, Shape shape) {
            return getBoolean(store, store.getShape() == shape);
        }

        @Override
        default void setBoolean(DynamicObject store, boolean value, Shape shape) throws FinalLocationException {
            setBoolean(store, value, store.getShape() == shape);
        }

        @Override
        default void setBoolean(DynamicObject store, boolean value) throws FinalLocationException {
            setBoolean(store, value, false);
        }

        @Override
        default void setBoolean(DynamicObject store, boolean value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setBoolean(store, value, false);
        }
    }

    public abstract static class ValueLocation extends CoreLocation {

        private final Object value;

        ValueLocation(Object value) {
            assert !(value instanceof Location);
            this.value = value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((value == null) ? 0 : 0 /* value.hashCode() */);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && Objects.equals(value, ((ValueLocation) obj).value);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return value;
        }

        @Override
        public boolean canStore(Object val) {
            return valueEquals(this.value, val);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (!canStore(value)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException();
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

        @Override
        public final void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
            if (!canStore(value)) {
                throw finalLocation();
            }
        }
    }

    public static final class ConstantLocation extends ValueLocation {

        ConstantLocation(Object value) {
            super(value);
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    public static final class DeclaredLocation extends ValueLocation {

        DeclaredLocation(Object value) {
            super(value);
        }

        @Override
        public boolean isDeclared() {
            return true;
        }
    }

    public abstract static class ArrayLocation extends CoreLocation {
        protected final int index;
        protected final CoreLocation arrayLocation;

        protected ArrayLocation(int index, CoreLocation arrayLocation) {
            this.index = index;
            this.arrayLocation = arrayLocation;
        }

        protected final Object getArray(DynamicObject store, boolean condition) {
            // non-null cast
            return arrayLocation.get(store, condition);
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
            ArrayLocation other = (ArrayLocation) obj;
            if (index != other.index) {
                return false;
            }
            return true;
        }

        public final int getIndex() {
            return index;
        }

        @Override
        public String getWhereString() {
            return "[" + index + "]";
        }
    }

    public abstract static class FieldLocation extends CoreLocation {
        private final int index;

        protected FieldLocation(int index) {
            this.index = index;
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
            FieldLocation other = (FieldLocation) obj;
            if (index != other.index) {
                return false;
            }
            return true;
        }

        public final int getIndex() {
            return index;
        }

        @Override
        public String getWhereString() {
            return "@" + index;
        }

        public abstract Class<? extends DynamicObject> getDeclaringClass();
    }

    static class ObjectArrayLocation extends ArrayLocation implements ObjectLocation {
        protected ObjectArrayLocation(int index, CoreLocation arrayLocation) {
            super(index, arrayLocation);
        }

        @Override
        public Object get(DynamicObject store, boolean condition) {
            return ((Object[]) getArray(store, condition))[index];
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) {
            ((Object[]) getArray(store, false))[index] = value;
        }

        @Override
        public boolean canStore(Object value) {
            return true;
        }

        public Class<? extends Object> getType() {
            return Object.class;
        }

        public final boolean isNonNull() {
            return false;
        }

        @Override
        public int objectArrayCount() {
            return OBJECT_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitObjectArray(index, OBJECT_SIZE);
        }
    }

    public abstract static class SimpleObjectFieldLocation extends FieldLocation implements ObjectLocation {

        protected SimpleObjectFieldLocation(int index) {
            super(index);
        }

        @Override
        public abstract Object get(DynamicObject store, boolean condition);

        @Override
        public abstract void setInternal(DynamicObject store, Object value, boolean condition);

        @Override
        public boolean canStore(Object value) {
            return true;
        }

        public Class<? extends Object> getType() {
            return Object.class;
        }

        public boolean isNonNull() {
            return false;
        }

        @Override
        public int objectFieldCount() {
            return OBJECT_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitObjectField(getIndex(), OBJECT_SIZE);
        }
    }

    static class LongArrayLocation extends ArrayLocation implements LongLocation {
        protected final boolean allowInt;

        protected LongArrayLocation(int index, CoreLocation arrayLocation, boolean allowInt) {
            super(index, arrayLocation);
            this.allowInt = allowInt;
        }

        protected LongArrayLocation(int index, CoreLocation arrayLocation) {
            this(index, arrayLocation, false);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLongInternal(store, longValue(value));
            } else {
                throw incompatibleLocation();
            }
        }

        private long longValue(Object value) {
            if (!allowInt || value instanceof Long) {
                return ((Long) value).longValue();
            } else {
                return ((Integer) value).longValue();
            }
        }

        @Override
        public long getLong(DynamicObject store, boolean condition) {
            return ((long[]) getArray(store, condition))[index];
        }

        public final void setLongInternal(DynamicObject store, long value) {
            ((long[]) getArray(store, false))[index] = value;
        }

        @Override
        public void setLong(DynamicObject store, long value, boolean condition) {
            setLongInternal(store, value);
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Long || (allowInt && value instanceof Integer);
        }

        public final Class<Long> getType() {
            return long.class;
        }

        @Override
        public int primitiveArrayCount() {
            return LONG_ARRAY_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveArray(getIndex(), LONG_ARRAY_SIZE);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongArrayLocation) obj).allowInt;
        }

        public boolean isImplicitCastIntToLong() {
            return allowInt;
        }
    }

    public static LongLocation createLongLocation(LongLocation longLocation, boolean allowInt) {
        if ((!allowInt && (longLocation instanceof LongLocationDecorator)) || (longLocation instanceof LongLocationDecorator && ((LongLocationDecorator) longLocation).allowInt == allowInt)) {
            return longLocation;
        } else {
            return new LongLocationDecorator(longLocation, allowInt);
        }
    }

    static class LongLocationDecorator extends PrimitiveLocationDecorator implements LongLocation {
        protected final boolean allowInt;

        protected LongLocationDecorator(LongLocation longLocation, boolean allowInt) {
            super(longLocation);
            this.allowInt = allowInt;
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public long getLong(DynamicObject store, boolean condition) {
            return super.getLongInternal(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLong(store, longValue(value), condition);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public void setLong(DynamicObject store, long value, boolean condition) {
            super.setLongInternal(store, value, condition);
        }

        private long longValue(Object value) {
            if (!allowInt || value instanceof Long) {
                return ((Long) value).longValue();
            } else {
                return ((Integer) value).longValue();
            }
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Long || (allowInt && value instanceof Integer);
        }

        @Override
        public Class<Long> getType() {
            return long.class;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongLocationDecorator) obj).allowInt;
        }

        public boolean isImplicitCastIntToLong() {
            return allowInt;
        }
    }

    public abstract static class SimpleLongFieldLocation extends FieldLocation implements LongLocation {

        protected SimpleLongFieldLocation(int index) {
            super(index);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLong(store, ((Long) value).longValue(), condition);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Long;
        }

        @Override
        public abstract long getLong(DynamicObject store, boolean condition);

        @Override
        public final long getLong(DynamicObject store, Shape shape) {
            return getLong(store, checkShape(store, shape));
        }

        public abstract void setLong(DynamicObject store, long value, boolean condition);

        @Override
        public int primitiveFieldCount() {
            return LONG_FIELD_SIZE;
        }

        @Override
        public final Class<Long> getType() {
            return long.class;
        }

        @Override
        public void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), LONG_FIELD_SIZE);
        }
    }

    public abstract static class PrimitiveLocationDecorator extends CoreLocation {
        private final LongLocation longLocation;

        protected PrimitiveLocationDecorator(LongLocation longLocation) {
            this.longLocation = longLocation;
        }

        public final long getLongInternal(DynamicObject store, boolean condition) {
            return longLocation.getLong(store, condition);
        }

        public final void setLongInternal(DynamicObject store, long value, boolean condition) {
            longLocation.setLong(store, value, condition);
        }

        public final LongLocation getInternalLocation() {
            return longLocation;
        }

        @Override
        public final int primitiveFieldCount() {
            return ((LocationImpl) longLocation).primitiveFieldCount();
        }

        @Override
        public final int primitiveArrayCount() {
            return ((LocationImpl) longLocation).primitiveArrayCount();
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            ((LocationImpl) longLocation).accept(locationVisitor);
        }

        @Override
        public String getWhereString() {
            return ((LocationImpl) longLocation).getWhereString();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.longLocation.equals(((PrimitiveLocationDecorator) obj).longLocation);
        }

        @Override
        public int hashCode() {
            return longLocation.hashCode();
        }
    }

    static class IntLocationDecorator extends PrimitiveLocationDecorator implements IntLocation {
        protected IntLocationDecorator(LongLocation longLocation) {
            super(longLocation);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getInt(store, condition);
        }

        @Override
        public int getInt(DynamicObject store, boolean condition) {
            return (int) getLongInternal(store, condition);
        }

        @Override
        public void setInt(DynamicObject store, int value, boolean condition) {
            setLongInternal(store, value, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLongInternal(store, (int) value, condition);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public final int getInt(DynamicObject store, Shape shape) {
            return getInt(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Integer;
        }

        public Class<Integer> getType() {
            return int.class;
        }
    }

    static class DoubleLocationDecorator extends PrimitiveLocationDecorator implements DoubleLocation {
        private final boolean allowInt;

        protected DoubleLocationDecorator(LongLocation longLocation, boolean allowInt) {
            super(longLocation);
            this.allowInt = allowInt;
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getDouble(store, condition);
        }

        @Override
        public double getDouble(DynamicObject store, boolean condition) {
            return Double.longBitsToDouble(getLongInternal(store, condition));
        }

        @Override
        public void setDouble(DynamicObject store, double value, boolean condition) {
            setLongInternal(store, Double.doubleToRawLongBits(value), condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setDouble(store, doubleValue(value), condition);
            } else {
                throw incompatibleLocation();
            }
        }

        private double doubleValue(Object value) {
            if (!allowInt || value instanceof Double) {
                return ((Double) value).doubleValue();
            } else {
                return ((Integer) value).doubleValue();
            }
        }

        @Override
        public final double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Double || (allowInt && value instanceof Integer);
        }

        @Override
        public Class<Double> getType() {
            return double.class;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((DoubleLocationDecorator) obj).allowInt;
        }

        @Override
        public boolean isImplicitCastIntToDouble() {
            return allowInt;
        }
    }

    static class BooleanLocationDecorator extends PrimitiveLocationDecorator implements BooleanLocation {
        protected BooleanLocationDecorator(LongLocation longLocation) {
            super(longLocation);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getBoolean(store, condition);
        }

        @Override
        public boolean getBoolean(DynamicObject store, boolean condition) {
            return getLongInternal(store, condition) != 0;
        }

        @Override
        public void setBoolean(DynamicObject store, boolean value, boolean condition) {
            setLongInternal(store, value ? 1 : 0, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value, boolean condition) throws IncompatibleLocationException {
            if (canStore(value)) {
                setBoolean(store, (boolean) value, condition);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public final boolean getBoolean(DynamicObject store, Shape shape) {
            return getBoolean(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Boolean;
        }

        @Override
        public Class<Boolean> getType() {
            return boolean.class;
        }
    }

    static boolean isSameLocation(CoreLocation loc1, CoreLocation loc2) {
        return loc1 == loc2 || getInternalLocation(loc1).equals(getInternalLocation(loc2));
    }

    static CoreLocation getInternalLocation(CoreLocation loc) {
        return loc instanceof CoreLocations.PrimitiveLocationDecorator ? (CoreLocation) ((CoreLocations.PrimitiveLocationDecorator) loc).getInternalLocation() : loc;
    }

    static int getLocationOrdinal(CoreLocation loc) {
        CoreLocation internal = CoreLocations.getInternalLocation(Objects.requireNonNull(loc));
        boolean isPrimitive = internal instanceof CoreLocations.LongLocation;
        if (internal instanceof CoreLocations.FieldLocation) {
            return (isPrimitive ? Integer.MIN_VALUE : 0) + ((CoreLocations.FieldLocation) internal).getIndex();
        } else if (internal instanceof CoreLocations.ArrayLocation) {
            return (isPrimitive ? Integer.MIN_VALUE : 0) + MAX_DYNAMIC_FIELDS + ((CoreLocations.ArrayLocation) internal).getIndex();
        } else {
            throw new IllegalArgumentException(internal.getClass().getName());
        }
    }

}
