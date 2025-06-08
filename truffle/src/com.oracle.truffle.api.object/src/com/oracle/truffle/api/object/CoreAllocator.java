/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.object.CoreLocations.OBJECT_SLOT_SIZE;

import com.oracle.truffle.api.object.CoreLocations.BooleanLocation;
import com.oracle.truffle.api.object.CoreLocations.BooleanLocationDecorator;
import com.oracle.truffle.api.object.CoreLocations.ConstantLocation;
import com.oracle.truffle.api.object.CoreLocations.DeclaredLocation;
import com.oracle.truffle.api.object.CoreLocations.DoubleLocation;
import com.oracle.truffle.api.object.CoreLocations.DoubleLocationDecorator;
import com.oracle.truffle.api.object.CoreLocations.IntLocation;
import com.oracle.truffle.api.object.CoreLocations.IntLocationDecorator;
import com.oracle.truffle.api.object.CoreLocations.LongArrayLocation;
import com.oracle.truffle.api.object.CoreLocations.LongLocation;
import com.oracle.truffle.api.object.CoreLocations.LongLocationDecorator;
import com.oracle.truffle.api.object.CoreLocations.ObjectArrayLocation;
import com.oracle.truffle.api.object.CoreLocations.ObjectLocation;
import com.oracle.truffle.api.object.CoreLocations.PrimitiveLocationDecorator;
import com.oracle.truffle.api.object.CoreLocations.TypedLocation;
import com.oracle.truffle.api.object.CoreLocations.ValueLocation;

import sun.misc.Unsafe;

class CoreAllocator extends ShapeImpl.BaseAllocator {

    CoreAllocator(LayoutImpl layout) {
        super(layout);
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
        if (oldLocation instanceof LongLocation longLocation) {
            return newLongLocation(longLocation.isImplicitCastIntToLong());
        } else if (oldLocation instanceof IntLocation) {
            return newIntLocation();
        } else if (oldLocation instanceof DoubleLocation doubleLocation) {
            return newDoubleLocation(doubleLocation.isImplicitCastIntToDouble());
        } else if (oldLocation instanceof BooleanLocation) {
            return newBooleanLocation();
        } else if (oldLocation instanceof ObjectLocation) {
            return newObjectLocation();
        } else {
            assert oldLocation instanceof CoreLocations.ValueLocation : oldLocation;
            return advance(oldLocation);
        }
    }

    private Location newObjectLocation() {
        if (ObjectStorageOptions.InObjectFields) {
            int insertPos = objectFieldSize;
            if (insertPos + OBJECT_SLOT_SIZE <= getLayout().getObjectFieldCount()) {
                return advance((Location) getLayout().getObjectFieldLocation(insertPos));
            }
        }
        return newObjectArrayLocation();
    }

    private Location newObjectArrayLocation() {
        return advance(new ObjectArrayLocation(objectArraySize));
    }

    private Location newIntLocation() {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.IntegerLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new IntLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            } else if (getLayout().hasPrimitiveExtensionArray()) {
                int alignedIndex = alignArrayIndex(primitiveArraySize, Long.BYTES);
                return advance(new IntLocationDecorator(new LongArrayLocation(alignedIndex)));
            }
        }
        return newObjectLocation();
    }

    private Location newDoubleLocation() {
        return newDoubleLocation(getLayout().isAllowedIntToDouble());
    }

    Location newDoubleLocation(boolean allowedIntToDouble) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.DoubleLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new DoubleLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), allowedIntToDouble));
            } else if (getLayout().hasPrimitiveExtensionArray()) {
                int alignedIndex = alignArrayIndex(primitiveArraySize, Long.BYTES);
                return advance(new DoubleLocationDecorator(new LongArrayLocation(alignedIndex), allowedIntToDouble));
            }
        }
        return newObjectLocation();
    }

    private Location newLongLocation() {
        return newLongLocation(getLayout().isAllowedIntToLong());
    }

    Location newLongLocation(boolean allowedIntToLong) {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.LongLocations) {
            if (ObjectStorageOptions.InObjectFields && primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance((Location) CoreLocations.createLongLocation(getLayout().getPrimitiveFieldLocation(primitiveFieldSize), allowedIntToLong));
            } else if (getLayout().hasPrimitiveExtensionArray()) {
                int alignedIndex = alignArrayIndex(primitiveArraySize, Long.BYTES);
                return advance(new LongArrayLocation(alignedIndex, allowedIntToLong));
            }
        }
        return newObjectLocation();
    }

    private Location newBooleanLocation() {
        if (ObjectStorageOptions.PrimitiveLocations && ObjectStorageOptions.BooleanLocations) {
            if (primitiveFieldSize + getLayout().getLongFieldSize() <= getLayout().getPrimitiveFieldCount()) {
                return advance(new BooleanLocationDecorator(getLayout().getPrimitiveFieldLocation(primitiveFieldSize)));
            }
        }
        return newObjectLocation();
    }

    @Override
    public Location locationForValue(Object value) {
        return locationForValue(value, 0);
    }

    Location locationForValue(Object value, int putFlags) {
        if (Flags.isConstant(putFlags)) {
            return constantLocation(value);
        } else if (Flags.isDeclaration(putFlags)) {
            return declaredLocation(value);
        }
        if (value instanceof Integer) {
            return newIntLocation();
        } else if (value instanceof Double) {
            return newDoubleLocation(Flags.isImplicitCastIntToDouble(putFlags) || layout.isAllowedIntToDouble());
        } else if (value instanceof Long) {
            return newLongLocation(Flags.isImplicitCastIntToLong(putFlags) || layout.isAllowedIntToLong());
        } else if (value instanceof Boolean) {
            return newBooleanLocation();
        }
        return newObjectLocation();
    }

    @Override
    public Location locationForType(Class<?> type) {
        if (type == int.class) {
            return newIntLocation();
        } else if (type == double.class) {
            return newDoubleLocation();
        } else if (type == long.class) {
            return newLongLocation();
        } else if (type == boolean.class) {
            return newBooleanLocation();
        }
        return newObjectLocation();
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation, int putFlags) {
        assert !oldLocation.canStore(value);

        if (oldLocation instanceof ConstantLocation && Flags.isConstant(putFlags)) {
            return constantLocation(value);
        } else if (oldLocation instanceof ValueLocation) {
            return locationForValue(value, putFlags);
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
            return newObjectLocation();
        }
        return locationForValue(value, putFlags);
    }

    /**
     * Adjust index to ensure alignment for slots larger than the array element size, e.g. long and
     * double slots in an int[] array. Note that array element 0 is not necessarily 8-byte aligned.
     */
    private static int alignArrayIndex(int index, int bytes) {
        assert bytes > 0 && (bytes & (bytes - 1)) == 0;
        final int baseOffset = Unsafe.ARRAY_INT_BASE_OFFSET;
        final int indexScale = Unsafe.ARRAY_INT_INDEX_SCALE;
        if (bytes <= indexScale) {
            // Always aligned.
            return index;
        } else {
            int misalignment = (baseOffset + indexScale * index) & (bytes - 1);
            if (misalignment == 0) {
                return index;
            } else {
                return index + ((bytes - misalignment) / indexScale);
            }
        }
    }
}
