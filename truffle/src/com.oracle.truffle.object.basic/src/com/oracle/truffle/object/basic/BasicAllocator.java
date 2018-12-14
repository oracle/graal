/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.basic.BasicLocations.BooleanLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.DoubleLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.IntLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.LongArrayLocation;
import com.oracle.truffle.object.basic.BasicLocations.LongFieldLocation;
import com.oracle.truffle.object.basic.BasicLocations.LongLocationDecorator;
import com.oracle.truffle.object.basic.BasicLocations.ObjectArrayLocation;
import com.oracle.truffle.object.basic.BasicLocations.PrimitiveLocationDecorator;

@SuppressWarnings("deprecation")
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
        if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields) {
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
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && com.oracle.truffle.object.ObjectStorageOptions.IntegerLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance(new IntLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new IntLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation())));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newDoubleLocation(boolean useFinal) {
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && com.oracle.truffle.object.ObjectStorageOptions.DoubleLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance(new DoubleLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), getLayout().isAllowedIntToDouble()));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new DoubleLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation()), getLayout().isAllowedIntToDouble()));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newLongLocation(boolean useFinal) {
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && com.oracle.truffle.object.ObjectStorageOptions.LongLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + LONG_SIZE <= getLayout().getPrimitiveFieldCount()) {
                return advance((Location) LongFieldLocation.create(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), getLayout().isAllowedIntToLong()));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation(), getLayout().isAllowedIntToLong()));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newBooleanLocation(boolean useFinal) {
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && com.oracle.truffle.object.ObjectStorageOptions.BooleanLocations) {
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
