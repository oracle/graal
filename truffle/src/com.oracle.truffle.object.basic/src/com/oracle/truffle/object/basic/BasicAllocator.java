/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import static com.oracle.truffle.object.basic.BasicLocations.LONG_SIZE;
import static com.oracle.truffle.object.basic.BasicLocations.OBJECT_SIZE;

import com.oracle.truffle.api.object.BooleanLocation;
import com.oracle.truffle.api.object.DoubleLocation;
import com.oracle.truffle.api.object.IntLocation;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LongLocation;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.TypedLocation;
import com.oracle.truffle.object.LayoutImpl;
import com.oracle.truffle.object.LocationImpl.InternalLongLocation;
import com.oracle.truffle.object.Locations.ConstantLocation;
import com.oracle.truffle.object.Locations.DeclaredLocation;
import com.oracle.truffle.object.Locations.ValueLocation;
import com.oracle.truffle.object.ObjectStorageOptions;
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.basic.BasicLocations.BooleanLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.DoubleLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.IntLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.LongArrayLocation;
import com.oracle.truffle.object.basic.BasicLocations.LongFieldLocation;
import com.oracle.truffle.object.basic.BasicLocations.LongLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.ObjectArrayLocation;
import com.oracle.truffle.object.basic.BasicLocations.PrimitiveLocationDecorator;

class BasicAllocator extends ShapeImpl.BaseAllocator {

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
        if (oldLocation instanceof LongLocation) {
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
            if (insertPos + OBJECT_SIZE <= getLayout().getObjectFieldCount()) {
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
        assert !oldLocation.canSet(value);

        if (oldLocation instanceof DeclaredLocation) {
            return locationForValue(value);
        } else if (oldLocation instanceof ConstantLocation) {
            return constantLocation(value);
        } else if (oldLocation instanceof TypedLocation && ((TypedLocation) oldLocation).getType().isPrimitive()) {
            if (!shared && ((TypedLocation) oldLocation).getType() == int.class) {
                InternalLongLocation primLocation = ((PrimitiveLocationDecorator) oldLocation).getInternalLocation();
                if (layout.isAllowedIntToLong() && value instanceof Long) {
                    return new LongLocationDecorator(primLocation, true);
                } else if (layout.isAllowedIntToDouble() && value instanceof Double) {
                    return new DoubleLocationDecorator(primLocation, true);
                }
            }
            return newObjectLocation(oldLocation.isFinal(), value != null);
        }
        return locationForValue(value);
    }
}
