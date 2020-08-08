/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.object.CoreLocations.OBJECT_SLOT_SIZE;

import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.object.CoreLocations.BooleanLocation;
import com.oracle.truffle.object.CoreLocations.BooleanLocationDecorator;
import com.oracle.truffle.object.CoreLocations.ConstantLocation;
import com.oracle.truffle.object.CoreLocations.DeclaredLocation;
import com.oracle.truffle.object.CoreLocations.DoubleLocation;
import com.oracle.truffle.object.CoreLocations.DoubleLocationDecorator;
import com.oracle.truffle.object.CoreLocations.IntLocation;
import com.oracle.truffle.object.CoreLocations.IntLocationDecorator;
import com.oracle.truffle.object.CoreLocations.LongArrayLocation;
import com.oracle.truffle.object.CoreLocations.LongLocation;
import com.oracle.truffle.object.CoreLocations.LongLocationDecorator;
import com.oracle.truffle.object.CoreLocations.ObjectArrayLocation;
import com.oracle.truffle.object.CoreLocations.ObjectLocation;
import com.oracle.truffle.object.CoreLocations.PrimitiveLocationDecorator;
import com.oracle.truffle.object.CoreLocations.TypedLocation;
import com.oracle.truffle.object.CoreLocations.ValueLocation;

@SuppressWarnings("deprecation")
class CoreAllocator extends ShapeImpl.BaseAllocator {

    CoreAllocator(LayoutImpl layout) {
        super(layout);
        advance(layout.getPrimitiveArrayLocation());
    }

    CoreAllocator(ShapeImpl shape) {
        super(shape);
    }

    private DefaultLayout getLayout() {
        return (DefaultLayout) layout;
    }

    @Override
    public CoreLocation constantLocation(Object value) {
        return new ConstantLocation(value);
    }

    @Override
    public CoreLocation declaredLocation(Object value) {
        return new DeclaredLocation(value);
    }

    @Override
    protected Location moveLocation(Location oldLocation) {
        if (oldLocation instanceof LongLocation) {
            return newLongLocation(oldLocation.isFinal(), ((LongLocation) oldLocation).isImplicitCastIntToLong());
        } else if (oldLocation instanceof IntLocation) {
            return newIntLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof DoubleLocation) {
            return newDoubleLocation(oldLocation.isFinal(), ((DoubleLocation) oldLocation).isImplicitCastIntToDouble());
        } else if (oldLocation instanceof BooleanLocation) {
            return newBooleanLocation(oldLocation.isFinal());
        } else if (oldLocation instanceof ObjectLocation) {
            return newObjectLocation(oldLocation.isFinal(), ((ObjectLocation) oldLocation).isNonNull());
        } else {
            assert oldLocation instanceof CoreLocations.ValueLocation : oldLocation;
            return advance(oldLocation);
        }
    }

    @Override
    public Location newObjectLocation(boolean useFinal, boolean nonNull) {
        if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields) {
            int insertPos = objectFieldSize;
            if (insertPos + OBJECT_SLOT_SIZE <= getLayout().getObjectFieldCount()) {
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
    protected Location newIntLocation(boolean useFinal) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.IntegerLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new IntLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new IntLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation())));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newDoubleLocation(boolean useFinal) {
        return newDoubleLocation(useFinal, getLayout().isAllowedIntToDouble());
    }

    Location newDoubleLocation(boolean useFinal, boolean allowedIntToDouble) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.DoubleLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new DoubleLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), allowedIntToDouble));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new DoubleLocationDecorator(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation()), allowedIntToDouble));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newLongLocation(boolean useFinal) {
        return newLongLocation(useFinal, getLayout().isAllowedIntToLong());
    }

    Location newLongLocation(boolean useFinal, boolean allowedIntToLong) {
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.LongLocations) {
            if (com.oracle.truffle.object.ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance((Location) CoreLocations.createLongLocation(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), allowedIntToLong));
            } else if (getLayout().hasPrimitiveExtensionArray() && isPrimitiveExtensionArrayAvailable()) {
                return advance(new LongArrayLocation(primitiveArraySize, getLayout().getPrimitiveArrayLocation(), allowedIntToLong));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    @Override
    public Location newBooleanLocation(boolean useFinal) {
        if (com.oracle.truffle.object.ObjectStorageOptions.PrimitiveLocations && com.oracle.truffle.object.ObjectStorageOptions.BooleanLocations) {
            if (primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new BooleanLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            }
        }
        return newObjectLocation(useFinal, true);
    }

    private boolean isPrimitiveExtensionArrayAvailable() {
        return hasPrimitiveArray;
    }

    @Override
    protected Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
        return locationForValue(value, useFinal, nonNull, 0);
    }

    @SuppressWarnings("unused")
    Location locationForValue(Object value, boolean useFinal, boolean nonNull, long putFlags) {
        if (value instanceof Integer) {
            return newIntLocation(useFinal);
        } else if (value instanceof Double) {
            return newDoubleLocation(useFinal, Flags.isImplicitCastIntToDouble(putFlags) || layout.isAllowedIntToDouble());
        } else if (value instanceof Long) {
            return newLongLocation(useFinal, Flags.isImplicitCastIntToLong(putFlags) || layout.isAllowedIntToLong());
        } else if (value instanceof Boolean) {
            return newBooleanLocation(useFinal);
        } else if (com.oracle.truffle.object.ObjectStorageOptions.TypedObjectLocations && value != null) {
            return newTypedObjectLocation(useFinal, value.getClass(), nonNull);
        }
        return newObjectLocation(useFinal, nonNull && value != null);
    }

    @Override
    protected Location locationForType(Class<?> type, boolean useFinal, boolean nonNull) {
        if (type == int.class) {
            return newIntLocation(useFinal);
        } else if (type == double.class) {
            return newDoubleLocation(useFinal);
        } else if (type == long.class) {
            return newLongLocation(useFinal);
        } else if (type == boolean.class) {
            return newBooleanLocation(useFinal);
        } else if (com.oracle.truffle.object.ObjectStorageOptions.TypedObjectLocations && type != null && type != Object.class) {
            assert !type.isPrimitive() : "unsupported primitive type";
            return newTypedObjectLocation(useFinal, type, nonNull);
        }
        return newObjectLocation(useFinal, nonNull);
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation, long putFlags) {
        assert !oldLocation.canSet(value);

        if (oldLocation instanceof ConstantLocation && (Flags.isConstant(putFlags) || getLayout().isLegacyLayout())) {
            return constantLocation(value);
        } else if (oldLocation instanceof ValueLocation) {
            return locationForValue(value, false, value != null);
        } else if (oldLocation instanceof TypedLocation && ((TypedLocation) oldLocation).getType().isPrimitive()) {
            if (!shared && ((TypedLocation) oldLocation).getType() == int.class) {
                LongLocation primLocation = ((PrimitiveLocationDecorator) oldLocation).getInternalLongLocation();
                boolean allowedIntToLong = layout.isAllowedIntToLong() || Flags.isImplicitCastIntToLong(putFlags);
                boolean allowedIntToDouble = layout.isAllowedIntToDouble() || Flags.isImplicitCastIntToDouble(putFlags);
                if (allowedIntToLong && value instanceof Long) {
                    return new LongLocationDecorator(primLocation, true);
                } else if (allowedIntToDouble && value instanceof Double) {
                    return new DoubleLocationDecorator(primLocation, true);
                }
            }
            return newObjectLocation(oldLocation.isFinal(), value != null);
        }
        return locationForValue(value, false, value != null);
    }
}
