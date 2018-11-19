/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.BooleanLocation;
import com.oracle.truffle.api.object.DoubleLocation;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.IntLocation;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.LocationImpl;
import com.oracle.truffle.object.LocationImpl.InternalLongLocation;

import java.lang.invoke.MethodHandle;

/**
 * Property location.
 *
 * @see Shape
 * @see Property
 * @see DynamicObject
 */
public abstract class BasicLocations {
    static final int LONG_SIZE = 1;
    static final int OBJECT_SIZE = 1;

    public abstract static class ArrayLocation extends LocationImpl {
        protected final int index;
        protected final Location arrayLocation;

        public ArrayLocation(int index, Location arrayLocation) {
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

    public abstract static class FieldLocation extends LocationImpl {
        private final int index;

        public FieldLocation(int index) {
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
    }

    public abstract static class MethodHandleFieldLocation extends FieldLocation {
        protected final MethodHandle getter;
        protected final MethodHandle setter;

        public MethodHandleFieldLocation(int index, MethodHandle getter, MethodHandle setter) {
            super(index);
            this.getter = getter;
            this.setter = setter;
        }
    }

    public static class ObjectArrayLocation extends ArrayLocation implements ObjectLocation {
        public ObjectArrayLocation(int index, Location arrayLocation) {
            super(index, arrayLocation);
        }

        @Override
        public Object get(DynamicObject store, boolean condition) {
            return ((Object[]) getArray(store, condition))[index];
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
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

    public static class ObjectFieldLocation extends MethodHandleFieldLocation implements ObjectLocation {

        public ObjectFieldLocation(int index, MethodHandle getter, MethodHandle setter) {
            super(index, getter, setter);
        }

        @Override
        public Object get(DynamicObject store, boolean condition) {
            try {
                return getter.invokeExact(store);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            try {
                setter.invokeExact(store, value);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

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

    public abstract static class SimpleObjectFieldLocation extends FieldLocation implements ObjectLocation {

        public SimpleObjectFieldLocation(int index) {
            super(index);
        }

        @Override
        public abstract Object get(DynamicObject store, boolean condition);

        @Override
        public abstract void setInternal(DynamicObject store, Object value);

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

    public static class LongArrayLocation extends ArrayLocation implements InternalLongLocation {
        protected final boolean allowInt;

        public LongArrayLocation(int index, Location arrayLocation, boolean allowInt) {
            super(index, arrayLocation);
            this.allowInt = allowInt;
        }

        public LongArrayLocation(int index, Location arrayLocation) {
            this(index, arrayLocation, false);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
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
        public void setLong(DynamicObject store, long value, Shape shape) throws FinalLocationException {
            setLongInternal(store, value);
        }

        @Override
        public final void setLong(DynamicObject store, long value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLongInternal(store, value);
        }

        @Override
        public final void setLong(DynamicObject store, long value) throws FinalLocationException {
            setLong(store, value, null);
        }

        public final long getLong(DynamicObject store, Shape shape) {
            return getLong(store, checkShape(store, shape));
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
            return LONG_SIZE;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveArray(getIndex(), LONG_SIZE);
        }
    }

    public static class LongFieldLocation extends MethodHandleFieldLocation implements InternalLongLocation {
        public LongFieldLocation(int index, MethodHandle getter, MethodHandle setter) {
            super(index, getter, setter);
        }

        public static LongLocation create(InternalLongLocation longLocation, boolean allowInt) {
            if ((!allowInt && (longLocation instanceof LongLocationDecorator)) || (longLocation instanceof LongLocationDecorator && ((LongLocationDecorator) longLocation).allowInt == allowInt)) {
                return longLocation;
            } else {
                return new LongLocationDecorator(longLocation, allowInt);
            }
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLongInternal(store, (long) value);
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Long;
        }

        @Override
        public final void setLong(DynamicObject store, long value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLongInternal(store, value);
        }

        public long getLong(DynamicObject store, boolean condition) {
            try {
                return (long) getter.invokeExact(store);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        public void setLong(DynamicObject store, long value, Shape shape) {
            setLongInternal(store, value);
        }

        public final void setLong(DynamicObject store, long value) throws FinalLocationException {
            setLong(store, value, null);
        }

        public final void setLongInternal(DynamicObject store, long value) {
            try {
                setter.invokeExact(store, value);
            } catch (Throwable e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        public final long getLong(DynamicObject store, Shape shape) {
            return getLong(store, checkShape(store, shape));
        }

        @Override
        public final int primitiveFieldCount() {
            return LONG_SIZE;
        }

        public final Class<Long> getType() {
            return long.class;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), LONG_SIZE);
        }
    }

    public static class LongLocationDecorator extends PrimitiveLocationDecorator implements InternalLongLocation {
        protected final boolean allowInt;

        public LongLocationDecorator(InternalLongLocation longLocation, boolean allowInt) {
            super(longLocation);
            this.allowInt = allowInt;
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
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
        public final boolean canStore(Object value) {
            return value instanceof Long || (allowInt && value instanceof Integer);
        }

        @Override
        public final void setLong(DynamicObject store, long value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLongInternal(store, value);
        }

        public Class<Long> getType() {
            return long.class;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((LongLocationDecorator) obj).allowInt;
        }
    }

    public abstract static class SimpleLongFieldLocation extends FieldLocation implements InternalLongLocation {

        public SimpleLongFieldLocation(int index) {
            super(index);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getLong(store, condition);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLongInternal(store, ((Long) value).longValue());
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Long;
        }

        @Override
        public final void setLong(DynamicObject store, long value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLongInternal(store, value);
        }

        public abstract long getLong(DynamicObject store, boolean condition);

        public final long getLong(DynamicObject store, Shape shape) {
            return getLong(store, checkShape(store, shape));
        }

        public final void setLong(DynamicObject store, long value) {
            setLong(store, value, null);
        }

        public void setLong(DynamicObject store, long value, Shape shape) {
            setLongInternal(store, value);
        }

        public abstract void setLongInternal(DynamicObject store, long value);

        @Override
        public final int primitiveFieldCount() {
            return LONG_SIZE;
        }

        public final Class<Long> getType() {
            return long.class;
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            locationVisitor.visitPrimitiveField(getIndex(), LONG_SIZE);
        }
    }

    public abstract static class PrimitiveLocationDecorator extends LocationImpl {
        private final InternalLongLocation longLocation;

        public PrimitiveLocationDecorator(InternalLongLocation longLocation) {
            this.longLocation = longLocation;
        }

        public final long getLong(DynamicObject store, Shape shape) {
            return longLocation.getLong(store, shape);
        }

        public final long getLong(DynamicObject store, boolean condition) {
            return longLocation.getLong(store, condition);
        }

        public final void setLong(DynamicObject store, long value, Shape shape) throws FinalLocationException {
            longLocation.setLong(store, value, shape);
        }

        public final void setLong(DynamicObject store, long value) throws FinalLocationException {
            longLocation.setLong(store, value);
        }

        public final void setLongInternal(DynamicObject store, long value) {
            longLocation.setLongInternal(store, value);
        }

        public final InternalLongLocation getInternalLocation() {
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
            return longLocation.getWhereString();
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

    public static class IntLocationDecorator extends PrimitiveLocationDecorator implements IntLocation {
        public IntLocationDecorator(InternalLongLocation longLocation) {
            super(longLocation);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getInt(store, condition);
        }

        public int getInt(DynamicObject store, boolean condition) {
            return (int) getLong(store, condition);
        }

        public void setInt(DynamicObject store, int value, Shape shape) throws FinalLocationException {
            setLong(store, value, shape);
        }

        @Override
        public final void setInt(DynamicObject store, int value) throws FinalLocationException {
            setInt(store, value, null);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (canStore(value)) {
                setLongInternal(store, (int) value);
            } else {
                throw incompatibleLocation();
            }
        }

        public final int getInt(DynamicObject store, Shape shape) {
            return getInt(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Integer;
        }

        @Override
        public final void setInt(DynamicObject store, int value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setLongInternal(store, value);
        }

        public Class<Integer> getType() {
            return int.class;
        }
    }

    public static class DoubleLocationDecorator extends PrimitiveLocationDecorator implements DoubleLocation {
        private final boolean allowInt;

        public DoubleLocationDecorator(InternalLongLocation longLocation, boolean allowInt) {
            super(longLocation);
            this.allowInt = allowInt;
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getDouble(store, condition);
        }

        public double getDouble(DynamicObject store, boolean condition) {
            return Double.longBitsToDouble(getLong(store, condition));
        }

        public void setDouble(DynamicObject store, double value, Shape shape) {
            setLongInternal(store, Double.doubleToRawLongBits(value));
        }

        public void setDouble(DynamicObject store, double value) {
            setDouble(store, value, null);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (canStore(value)) {
                setDouble(store, doubleValue(value), null);
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

        public final double getDouble(DynamicObject store, Shape shape) {
            return getDouble(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Double || (allowInt && value instanceof Integer);
        }

        @Override
        public final void setDouble(DynamicObject store, double value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setDouble(store, value, newShape);
        }

        public Class<Double> getType() {
            return double.class;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && this.allowInt == ((DoubleLocationDecorator) obj).allowInt;
        }
    }

    public static class BooleanLocationDecorator extends PrimitiveLocationDecorator implements BooleanLocation {
        public BooleanLocationDecorator(InternalLongLocation longLocation) {
            super(longLocation);
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return getBoolean(store, condition);
        }

        public boolean getBoolean(DynamicObject store, boolean condition) {
            return getLong(store, condition) != 0;
        }

        public void setBoolean(DynamicObject store, boolean value, Shape shape) {
            setLongInternal(store, value ? 1 : 0);
        }

        public void setBoolean(DynamicObject store, boolean value) {
            setBoolean(store, value, null);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (canStore(value)) {
                setBoolean(store, (boolean) value, null);
            } else {
                throw incompatibleLocation();
            }
        }

        public final boolean getBoolean(DynamicObject store, Shape shape) {
            return getBoolean(store, checkShape(store, shape));
        }

        @Override
        public final boolean canStore(Object value) {
            return value instanceof Boolean;
        }

        @Override
        public final void setBoolean(DynamicObject store, boolean value, Shape oldShape, Shape newShape) {
            store.setShapeAndGrow(oldShape, newShape);
            setBoolean(store, value, newShape);
        }

        public Class<Boolean> getType() {
            return boolean.class;
        }
    }
}
