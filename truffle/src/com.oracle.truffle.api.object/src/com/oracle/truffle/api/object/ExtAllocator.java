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

import static com.oracle.truffle.api.object.ExtLocations.DOUBLE_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.INT_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.LONG_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.OBJECT_SLOT_SIZE;
import static com.oracle.truffle.api.object.ObjectStorageOptions.DoubleLocations;
import static com.oracle.truffle.api.object.ObjectStorageOptions.InObjectFields;
import static com.oracle.truffle.api.object.ObjectStorageOptions.IntegerLocations;
import static com.oracle.truffle.api.object.ObjectStorageOptions.LongLocations;
import static com.oracle.truffle.api.object.ObjectStorageOptions.NewFinalSpeculation;
import static com.oracle.truffle.api.object.ObjectStorageOptions.NewTypeSpeculation;
import static com.oracle.truffle.api.object.ObjectStorageOptions.PrimitiveLocations;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.ExtLocations.AbstractObjectLocation;
import com.oracle.truffle.api.object.ExtLocations.DoubleArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.DoubleFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.DoubleLocation;
import com.oracle.truffle.api.object.ExtLocations.InstanceLocation;
import com.oracle.truffle.api.object.ExtLocations.IntArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.IntFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.IntLocation;
import com.oracle.truffle.api.object.ExtLocations.LongArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.LongFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.LongLocation;
import com.oracle.truffle.api.object.ExtLocations.ObjectArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.ObjectFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.TypeAssumption;

import sun.misc.Unsafe;

final class ExtAllocator extends BaseAllocator {

    /** Placeholder for when no value is available or no type speculation should be performed. */
    private static final Object NO_VALUE = new Object();

    ExtAllocator(Shape shape) {
        super(shape);
    }

    private LayoutImpl getLayout() {
        return layout;
    }

    @Override
    public LocationImpl constantLocation(Object value) {
        return new ExtLocations.ConstantLocation(value);
    }

    @Override
    public LocationImpl declaredLocation(Object value) {
        return new ExtLocations.DeclaredLocation(value);
    }

    @Override
    protected Location moveLocation(Location oldLocation) {
        final boolean decorateFinal = false;
        if (oldLocation instanceof IntLocation) {
            return newIntLocation(decorateFinal, oldLocation, NO_VALUE);
        } else if (oldLocation instanceof DoubleLocation) {
            return newDoubleLocation(decorateFinal, ((DoubleLocation) oldLocation).isImplicitCastIntToDouble(), oldLocation, NO_VALUE);
        } else if (oldLocation instanceof LongLocation) {
            return newLongLocation(decorateFinal, ((LongLocation) oldLocation).isImplicitCastIntToLong(), oldLocation, NO_VALUE);
        } else if (oldLocation instanceof ObjectFieldLocation) {
            return newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        } else if (oldLocation instanceof ObjectArrayLocation) {
            return newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        }
        assert oldLocation.isValue();
        return advance(oldLocation);
    }

    private Location newObjectLocation(boolean decorateFinal, Location oldLocation, Object value) {
        if (InObjectFields) {
            LayoutImpl l = getLayout();
            int insertPos = objectFieldSize;
            if (insertPos + OBJECT_SLOT_SIZE <= l.getObjectFieldCount()) {
                FieldInfo fieldInfo = l.getObjectField(insertPos);
                TypeAssumption initialTypeAssumption = getTypeAssumption(oldLocation, value);
                Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                LocationImpl location = newObjectFieldLocationWithAssumption(insertPos, fieldInfo, initialTypeAssumption, initialFinalAssumption);
                return advance(location);
            }
        }
        return newObjectArrayLocation(decorateFinal, oldLocation, value);
    }

    private Location newObjectArrayLocation(boolean decorateFinal, Location oldLocation, Object value) {
        int index = objectArraySize;
        TypeAssumption initialTypeAssumption = getTypeAssumption(oldLocation, value);
        Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
        LocationImpl location = newObjectArrayLocationWithAssumption(index, initialTypeAssumption, initialFinalAssumption);
        return advance(location);
    }

    private static ObjectFieldLocation newObjectFieldLocationWithAssumption(int index, FieldInfo fieldInfo,
                    TypeAssumption initialTypeAssumption, Assumption initialFinalAssumption) {
        return new ObjectFieldLocation(index, fieldInfo, initialFinalAssumption, initialTypeAssumption);
    }

    private static ObjectArrayLocation newObjectArrayLocationWithAssumption(int index,
                    TypeAssumption initialTypeAssumption, Assumption initialFinalAssumption) {
        return new ObjectArrayLocation(index, initialFinalAssumption, initialTypeAssumption);
    }

    private static boolean allowTypeSpeculation(Location oldLocation, Object value) {
        return (value != NO_VALUE && oldLocation == null) || (oldLocation instanceof AbstractObjectLocation);
    }

    private static TypeAssumption getTypeAssumption(Location oldLocation, Object value) {
        if (NewTypeSpeculation && allowTypeSpeculation(oldLocation, value)) {
            if (value != NO_VALUE && oldLocation == null) {
                if (AbstractObjectLocation.LAZY_ASSUMPTION) {
                    return null;
                }
                return AbstractObjectLocation.createTypeAssumptionFromValue(value);
            } else if (oldLocation instanceof AbstractObjectLocation) {
                return ((AbstractObjectLocation) oldLocation).getTypeAssumption();
            }
        }
        return TypeAssumption.ANY;
    }

    private static Assumption getFinalAssumption(Location oldLocation, boolean allowFinalSpeculation) {
        if (NewFinalSpeculation && allowFinalSpeculation) {
            if (oldLocation == null) {
                if (InstanceLocation.LAZY_FINAL_ASSUMPTION) {
                    return null;
                }
                return InstanceLocation.createFinalAssumption();
            } else if (oldLocation instanceof InstanceLocation) {
                return ((InstanceLocation) oldLocation).getFinalAssumptionField();
            }
        }
        return Assumption.NEVER_VALID;
    }

    private static int tryAllocatePrimitiveSlot(LayoutImpl l, int startIndex, final int desiredBytes) {
        assert desiredBytes <= Long.BYTES;
        return startIndex < l.getPrimitiveFieldCount() ? startIndex : -1;
    }

    private Location newIntLocation(boolean decorateFinal, Location oldLocation, Object value) {
        if (PrimitiveLocations && IntegerLocations) {
            LayoutImpl l = getLayout();
            if (InObjectFields) {
                int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Integer.BYTES);
                if (fieldIndex >= 0) {
                    FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new IntFieldLocation(fieldIndex, fieldInfo, initialFinalAssumption);
                    return advance(location);
                }
            }
            if (l.hasPrimitiveExtensionArray()) {
                int index = alignArrayIndex(primitiveArraySize, INT_ARRAY_SLOT_SIZE);
                Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                LocationImpl location = new IntArrayLocation(index, initialFinalAssumption);
                return advance(location);
            }
        }
        return newObjectLocation(decorateFinal, oldLocation, value);
    }

    private Location newDoubleLocation(boolean decorateFinal, boolean allowIntToDouble, Location oldLocation, Object value) {
        if (PrimitiveLocations && DoubleLocations) {
            LayoutImpl l = getLayout();
            if (InObjectFields) {
                int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Double.BYTES);
                if (fieldIndex >= 0) {
                    FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new DoubleFieldLocation(fieldIndex, fieldInfo, allowIntToDouble, initialFinalAssumption);
                    return advance(location);
                }
            }
            if (l.hasPrimitiveExtensionArray()) {
                int index = alignArrayIndex(primitiveArraySize, DOUBLE_ARRAY_SLOT_SIZE);
                Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                LocationImpl location = new DoubleArrayLocation(index, allowIntToDouble, initialFinalAssumption);
                return advance(location);
            }
        }
        return newObjectLocation(decorateFinal, oldLocation, value);
    }

    /**
     * Adjust index to ensure alignment for double and long slots into a primitive int[] array. Note
     * that array element 0 is not necessarily 8-byte aligned.
     */
    private static int alignArrayIndex(int index, int slotSize) {
        if (slotSize == 2) {
            if ((Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * index) % 8 != 0) {
                return index + 1;
            } else {
                return index;
            }
        } else if (slotSize == 1) {
            return index;
        } else {
            throw new AssertionError(slotSize);
        }
    }

    private Location newLongLocation(boolean decorateFinal, boolean allowIntToLong, Location oldLocation, Object value) {
        if (PrimitiveLocations && LongLocations) {
            LayoutImpl l = getLayout();
            if (InObjectFields) {
                int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Long.BYTES);
                if (fieldIndex >= 0) {
                    FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new LongFieldLocation(fieldIndex, fieldInfo, allowIntToLong, initialFinalAssumption);
                    return advance(location);
                }
            }
            if (l.hasPrimitiveExtensionArray()) {
                int index = alignArrayIndex(primitiveArraySize, LONG_ARRAY_SLOT_SIZE);
                Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                LocationImpl location = new LongArrayLocation(index, allowIntToLong, initialFinalAssumption);
                return advance(location);
            }
        }
        return newObjectLocation(decorateFinal, oldLocation, value);
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation, int putFlags) {
        assert !oldLocation.canStore(value);

        if (oldLocation instanceof ExtLocations.ConstantLocation && Flags.isConstant(putFlags)) {
            return constantLocation(value);
        }

        // Object-typed locations should be able to store all values and therefore not reach here.
        assert !(oldLocation instanceof AbstractObjectLocation) : oldLocation;
        final boolean decorateFinal = false;
        Location newLocation = null;
        // if shape is shared, transition to an untyped location directly
        if (!shared && oldLocation instanceof IntLocation) {
            boolean allowedIntToDouble = getLayout().isAllowedIntToDouble() || Flags.isImplicitCastIntToDouble(putFlags);
            boolean allowedIntToLong = getLayout().isAllowedIntToLong() || Flags.isImplicitCastIntToLong(putFlags);
            if (value instanceof Double && allowedIntToDouble) {
                newLocation = newDoubleLocation(decorateFinal, allowedIntToDouble, oldLocation, NO_VALUE);
            } else if (value instanceof Long && allowedIntToLong) {
                newLocation = newLongLocation(decorateFinal, allowedIntToLong, oldLocation, NO_VALUE);
            }
        }

        if (newLocation == null) {
            newLocation = newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        }

        return newLocation;
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
        boolean decorateFinal = true;
        if (value instanceof Integer) {
            return newIntLocation(decorateFinal, null, value);
        } else if (value instanceof Double) {
            return newDoubleLocation(decorateFinal, getLayout().isAllowedIntToDouble(), null, value);
        } else if (value instanceof Long) {
            return newLongLocation(decorateFinal, getLayout().isAllowedIntToLong(), null, value);
        }
        return newObjectLocation(decorateFinal, null, value);
    }

    static Class<?> getCommonSuperclass(Class<?> a, Class<?> b) {
        Class<?> type = a;
        while (type != Object.class) {
            type = type.getSuperclass();
            if (type != Object.class) {
                if (type.isAssignableFrom(a) && type.isAssignableFrom(b)) {
                    return type;
                }
            } else {
                break;
            }
        }
        return Object.class;
    }

    static Class<?> getCommonSuperclassForValue(Class<?> a, Object value) {
        Class<?> type = a;
        while (type != Object.class) {
            type = type.getSuperclass();
            if (type != Object.class) {
                if (type.isAssignableFrom(a) && (value == null || type.isInstance(value))) {
                    return type;
                }
            } else {
                break;
            }
        }
        return Object.class;
    }

}
