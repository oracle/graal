/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.object;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.object.*;

/**
 * Property location.
 *
 * @see Location
 * @see Shape
 * @see Property
 * @see DynamicObject
 */
public abstract class Locations {
    public abstract static class ValueLocation extends LocationImpl {

        private final Object value;

        public ValueLocation(Object value) {
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
            if (!super.equals(obj)) {
                return false;
            }
            ValueLocation other = (ValueLocation) obj;
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public final Object get(DynamicObject store, boolean condition) {
            return value;
        }

        @Override
        public final void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
            if (!canStoreFinal(store, value)) {
                throw finalLocation();
            }
        }

        @Override
        protected boolean canStoreFinal(DynamicObject store, Object val) {
            return valueEquals(this.value, val);
        }

        @Override
        public final void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (!canStoreFinal(store, value)) {
                CompilerDirectives.transferToInterpreter();
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
    }

    public static final class ConstantLocation extends ValueLocation {

        public ConstantLocation(Object value) {
            super(value);
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    public static final class DeclaredLocation extends ValueLocation {

        public DeclaredLocation(Object value) {
            super(value);
        }
    }

    public static class DualLocation extends LocationImpl implements TypedLocation {
        protected final InternalLongLocation primitiveLocation;
        protected final ObjectLocation objectLocation;
        protected final LayoutImpl layout;
        private final Class<?> type;

        public DualLocation(InternalLongLocation primitiveLocation, ObjectLocation objectLocation, LayoutImpl layout) {
            this(primitiveLocation, objectLocation, layout, null);
        }

        public DualLocation(InternalLongLocation primitiveLocation, ObjectLocation objectLocation, LayoutImpl layout, Class<?> type) {
            this.primitiveLocation = primitiveLocation;
            this.objectLocation = objectLocation;
            this.layout = layout;
            this.type = type;
        }

        @Override
        public Object get(DynamicObject store, boolean condition) {
            if (type == Object.class) {
                return objectLocation.get(store, condition);
            } else {
                long rawValue = primitiveLocation.getLong(store, condition);
                if (type == int.class) {
                    return (int) rawValue;
                } else if (type == long.class) {
                    return rawValue;
                } else if (type == double.class) {
                    return Double.longBitsToDouble(rawValue);
                } else if (type == boolean.class) {
                    return rawValue != 0;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException();
                }
            }
        }

        @Override
        public void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (type == Object.class) {
                ((LocationImpl) objectLocation).setInternal(store, value);
            } else {
                long rawValue;
                if (type == int.class && value instanceof Integer) {
                    rawValue = (int) value;
                } else if (type == long.class && value instanceof Long) {
                    rawValue = (long) value;
                } else if (type == long.class && layout.isAllowedIntToLong() && value instanceof Integer) {
                    rawValue = (int) value;
                } else if (type == double.class && value instanceof Double) {
                    rawValue = Double.doubleToRawLongBits((double) value);
                } else if (type == double.class && layout.isAllowedIntToDouble() && value instanceof Integer) {
                    rawValue = Double.doubleToRawLongBits((int) value);
                } else if (type == boolean.class && value instanceof Boolean) {
                    rawValue = (boolean) value ? 1 : 0;
                } else {
                    throw incompatibleLocation();
                }

                primitiveLocation.setLongInternal(store, rawValue);
            }
        }

        @Override
        public int primitiveFieldCount() {
            return ((LocationImpl) primitiveLocation).primitiveFieldCount();
        }

        @Override
        public int primitiveArrayCount() {
            return ((LocationImpl) primitiveLocation).primitiveArrayCount();
        }

        @Override
        public int objectFieldCount() {
            return ((LocationImpl) objectLocation).objectFieldCount();
        }

        @Override
        public int objectArrayCount() {
            return ((LocationImpl) objectLocation).objectArrayCount();
        }

        @Override
        public final void accept(LocationVisitor locationVisitor) {
            ((LocationImpl) primitiveLocation).accept(locationVisitor);
            ((LocationImpl) objectLocation).accept(locationVisitor);
        }

        @Override
        public String toString() {
            return objectLocation.toString() + "," + primitiveLocation.toString() + "," + type;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            DualLocation other = (DualLocation) obj;
            return getObjectLocation().equals(other.getObjectLocation()) && primitiveLocation.equals(other.primitiveLocation) && layout.equals(other.layout) && Objects.equals(type, other.type);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + (getObjectLocation() == null ? 0 : getObjectLocation().hashCode());
            result = prime * result + (primitiveLocation == null ? 0 : primitiveLocation.hashCode());
            result = prime * result + (type == null ? 0 : type.hashCode());
            return result;
        }

        public ObjectLocation getObjectLocation() {
            return objectLocation;
        }

        public DualLocation changeType(Class<?> newType) {
            return new DualLocation(primitiveLocation, objectLocation, layout, newType);
        }

        public Class<?> getType() {
            return type;
        }

        public boolean isNonNull() {
            return false;
        }

        @Override
        public boolean canStore(Object value) {
            if (type == null) {
                return false;
            } else if (type == int.class) {
                return value instanceof Integer;
            } else if (type == long.class) {
                return value instanceof Long || (layout.isAllowedIntToLong() && value instanceof Integer);
            } else if (type == double.class) {
                return value instanceof Double || (layout.isAllowedIntToDouble() && value instanceof Integer);
            } else if (type == boolean.class) {
                return value instanceof Boolean;
            } else if (type == Object.class) {
                return true;
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public static class DeclaredDualLocation extends DualLocation {
        private final Object defaultValue;

        public DeclaredDualLocation(InternalLongLocation primitiveLocation, ObjectLocation objectLocation, Object defaultValue, LayoutImpl layout) {
            super(primitiveLocation, objectLocation, layout);
            this.defaultValue = defaultValue;
        }

        @Override
        public Object get(DynamicObject store, boolean condition) {
            return defaultValue;
        }

        @Override
        public void setInternal(DynamicObject store, Object value) throws IncompatibleLocationException {
            if (valueEquals(defaultValue, value)) {
                return;
            } else {
                throw incompatibleLocation();
            }
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && Objects.equals(defaultValue, ((DeclaredDualLocation) obj).defaultValue);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public DualLocation changeType(Class<?> newType) {
            return new DualLocation(primitiveLocation, objectLocation, layout, newType);
        }

        @Override
        public boolean canStore(Object value) {
            return valueEquals(defaultValue, value);
        }

        @Override
        public String toString() {
            return objectLocation.toString() + "," + primitiveLocation.toString() + ",unset";
        }
    }
}
