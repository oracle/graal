/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import com.oracle.truffle.api.object.BooleanLocation;
import com.oracle.truffle.api.object.DoubleLocation;
import com.oracle.truffle.api.object.IntLocation;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.object.LayoutImpl;
import com.oracle.truffle.object.LocationImpl.InternalLongLocation;
import com.oracle.truffle.object.Locations.ConstantLocation;
import com.oracle.truffle.object.Locations.DeclaredDualLocation;
import com.oracle.truffle.object.Locations.DualLocation;
import com.oracle.truffle.object.Locations.ValueLocation;
import com.oracle.truffle.object.ObjectStorageOptions;
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.basic.BasicLocations.BooleanLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.DoubleLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.IntLocationDecorator;
import static com.oracle.truffle.object.basic.BasicLocations.LONG_SIZE;
import com.oracle.truffle.object.basic.BasicLocations.LongArrayLocation;
import com.oracle.truffle.object.basic.BasicLocations.LongFieldLocation;
import static com.oracle.truffle.object.basic.BasicLocations.OBJECT_SIZE;
import com.oracle.truffle.object.basic.BasicLocations.ObjectArrayLocation;

abstract class BasicAllocator extends ShapeImpl.BaseAllocator {

    BasicAllocator(LayoutImpl layout) {
        super(layout);
        advance(((BasicLayout) layout).getPrimitiveArrayLocation());
    }

    BasicAllocator(ShapeImpl shape) {
        super(shape);
    }

    private BasicLayout getLayout() {
        return (BasicLayout) layout;
    }

    @Override
    protected Location moveLocation(Location oldLocation) {
        if (oldLocation instanceof DeclaredDualLocation) {
            return advance(newDeclaredDualLocation(((DeclaredDualLocation) oldLocation).get(null, false)));
        } else if (oldLocation instanceof DualLocation) {
            return advance(newDualLocation(((DualLocation) oldLocation).getType()));
        } else if (oldLocation instanceof LongLocation) {
            return newLongLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof IntLocation) {
            return newIntLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof DoubleLocation) {
            return newDoubleLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof BooleanLocation) {
            return newBooleanLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof ObjectLocation) {
            return newObjectLocation(oldLocation.isFinal(), ((ObjectLocation) oldLocation).isNonNull());
        } else {
            assert oldLocation instanceof ValueLocation;
            return advance(oldLocation);
        }
    }

    @Override
    public Location newObjectLocation(boolean useFinal, boolean nonNull) {
        if (ObjectStorageOptions.InObjectFields) {
            int insertPos = objectFieldSize;
            while (insertPos + OBJECT_SIZE <= getLayout().getObjectFieldCount()) {
                return advance((Location) getLayout().getObjectFieldLocation(insertPos));
            }
        }
        return newObjectArrayLocation(useFinal, nonNull);
    }

    @SuppressWarnings("unused")
    private Location newObjectArrayLocation(boolean useFinal, boolean nonNull) {
        return advance(new ObjectArrayLocation(objectArraySize, getLayout().getObjectArrayLocation()));
    }

    @Override
    public Location newTypedObjectLocation(boolean useFinal, Class<?> type, boolean nonNull) {
        return newObjectLocation(useFinal, nonNull);
    }

    @Override
    public Location newIntLocation(boolean useFinal) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.IntegerLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance(new IntLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new IntLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation())));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newDoubleLocation(boolean useFinal) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.DoubleLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance(new DoubleLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), getLayout().isAllowedIntToDouble()));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new DoubleLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation()), getLayout().isAllowedIntToDouble()));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newLongLocation(boolean useFinal) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.LongLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance((Location) LongFieldLocation.create(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), getLayout().isAllowedIntToLong()));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation(), getLayout().isAllowedIntToLong()));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newBooleanLocation(boolean useFinal) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.BooleanLocations) {
            if (primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance(new BooleanLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    private boolean isPrimitiveExtensionArrayAvailable() {
        return hasPrimitiveArray;
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation) {
        assert !(value instanceof Class);
        if (oldLocation instanceof DualLocation) {
            DualLocation dualLocation = (DualLocation) oldLocation;
            if (dualLocation.getType() == null) {
                if (value instanceof Integer) {
                    return dualLocation.changeType(int.class);
                } else if (value instanceof Double) {
                    return dualLocation.changeType(double.class);
                } else if (value instanceof Long) {
                    return dualLocation.changeType(long.class);
                } else if (value instanceof Boolean) {
                    return dualLocation.changeType(boolean.class);
                } else {
                    return dualLocation.changeType(Object.class);
                }
            } else if (dualLocation.getType().isPrimitive()) {
                return dualLocation.changeType(Object.class);
            } else {
                throw new UnsupportedOperationException();
            }
        } else if (oldLocation instanceof ConstantLocation) {
            return constantLocation(value);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected DeclaredDualLocation newDeclaredDualLocation(Object value) {
        return new DeclaredDualLocation((InternalLongLocation) newLongLocation(false), (ObjectLocation) newObjectLocation(false, false), value, layout);
    }
}
