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

import static com.oracle.truffle.api.object.ExtLayout.BooleanLocations;
import static com.oracle.truffle.api.object.ExtLayout.DoubleLocations;
import static com.oracle.truffle.api.object.ExtLayout.InObjectFields;
import static com.oracle.truffle.api.object.ExtLayout.IntegerLocations;
import static com.oracle.truffle.api.object.ExtLayout.LongLocations;
import static com.oracle.truffle.api.object.ExtLayout.PrimitiveLocations;
import static com.oracle.truffle.api.object.ExtLayout.TypedObjectLocations;
import static com.oracle.truffle.api.object.ExtLocations.DOUBLE_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.INT_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.LONG_ARRAY_SLOT_SIZE;
import static com.oracle.truffle.api.object.ExtLocations.OBJECT_SLOT_SIZE;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.ExtLocations.ATypedObjectArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.ATypedObjectFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.AbstractObjectArrayLocation;
import com.oracle.truffle.api.object.ExtLocations.AbstractObjectFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.AbstractObjectLocation;
import com.oracle.truffle.api.object.ExtLocations.BooleanFieldLocation;
import com.oracle.truffle.api.object.ExtLocations.BooleanLocation;
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
import com.oracle.truffle.api.object.ExtLocations.ObjectLocation;
import com.oracle.truffle.api.object.ExtLocations.TypeAssumption;
import com.oracle.truffle.api.object.ShapeImpl.BaseAllocator;

import sun.misc.Unsafe;

abstract class ExtAllocator extends BaseAllocator {

    /** Placeholder for when no value is available or no type speculation should be performed. */
    private static final Object NO_VALUE = new Object();

    ExtAllocator(LayoutImpl layout) {
        super(layout);
    }

    ExtAllocator(ShapeImpl shape) {
        super(shape);
    }

    private ExtLayout getLayout() {
        return (ExtLayout) layout;
    }

    @Override
    public ExtLocation constantLocation(Object value) {
        return new ExtLocations.ConstantLocation(value);
    }

    @Override
    public ExtLocation declaredLocation(Object value) {
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
        } else if (oldLocation instanceof BooleanLocation) {
            return newBooleanLocation(decorateFinal, oldLocation, NO_VALUE);
        } else if (oldLocation instanceof AbstractObjectFieldLocation) {
            return newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        } else if (oldLocation instanceof AbstractObjectArrayLocation) {
            return newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        }
        assert oldLocation.isValue();
        return advance(oldLocation);
    }

    @Override
    public Location newObjectLocation(boolean useFinal, boolean nonNull) {
        return newObjectLocation(false, null, NO_VALUE);
    }

    private Location newObjectLocation(boolean decorateFinal, Location oldLocation, Object value) {
        if (InObjectFields) {
            ExtLayout l = getLayout();
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

    @Override
    public Location newTypedObjectLocation(boolean useFinal, Class<?> type, boolean nonNull) {
        return newTypedObjectLocation(type, nonNull, false, null, NO_VALUE);
    }

    Location newTypedObjectLocation(Class<?> type, boolean nonNull, boolean decorateFinal, Location oldLocation, Object value) {
        if (TypedObjectLocations) {
            if (InObjectFields) {
                ExtLayout l = getLayout();
                int insertPos = objectFieldSize;
                if (insertPos + OBJECT_SLOT_SIZE <= l.getObjectFieldCount()) {
                    FieldInfo fieldInfo = l.getObjectField(insertPos);
                    TypeAssumption initialTypeAssumption = getTypeAssumptionForTypeOrValue(type, nonNull, oldLocation, value);
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = newObjectFieldLocationWithAssumption(insertPos, fieldInfo, initialTypeAssumption, initialFinalAssumption);
                    return advance(location);
                }
            }
            return newTypedObjectArrayLocation(type, nonNull, decorateFinal, oldLocation, value);
        } else {
            return newObjectLocation(decorateFinal, oldLocation, value);
        }
    }

    private Location newTypedObjectArrayLocation(Class<?> type, boolean nonNull, boolean decorateFinal, Location oldLocation, Object value) {
        if (type != Object.class) {
            int index = objectArraySize;
            TypeAssumption initialTypeAssumption = getTypeAssumptionForTypeOrValue(type, nonNull, oldLocation, value);
            Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
            LocationImpl location = newObjectArrayLocationWithAssumption(index, initialTypeAssumption, initialFinalAssumption);
            return advance(location);
        } else {
            return newObjectArrayLocation(decorateFinal, oldLocation, value);
        }
    }

    private static AbstractObjectFieldLocation newObjectFieldLocationWithAssumption(int index, FieldInfo fieldInfo,
                    TypeAssumption initialTypeAssumption, Assumption initialFinalAssumption) {
        return new ATypedObjectFieldLocation(index, fieldInfo, initialTypeAssumption, initialFinalAssumption);
    }

    private static AbstractObjectArrayLocation newObjectArrayLocationWithAssumption(int index,
                    TypeAssumption initialTypeAssumption, Assumption initialFinalAssumption) {
        return new ATypedObjectArrayLocation(index, initialTypeAssumption, initialFinalAssumption);
    }

    private static boolean allowTypeSpeculation(Location oldLocation, Object value) {
        return (value != NO_VALUE && oldLocation == null) || (oldLocation instanceof AbstractObjectLocation);
    }

    private static TypeAssumption getTypeAssumption(Location oldLocation, Object value) {
        if (ExtLayout.NewTypeSpeculation && allowTypeSpeculation(oldLocation, value)) {
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

    private static TypeAssumption getTypeAssumptionForTypeOrValue(Class<?> type, boolean nonNull, Location oldLocation, Object value) {
        if (!ExtLayout.NewTypeSpeculation && !ExtLayout.NewFinalSpeculation && value == NO_VALUE) {
            if (oldLocation instanceof AbstractObjectLocation) {
                return ((AbstractObjectLocation) oldLocation).getTypeAssumption();
            }
            return AbstractObjectLocation.createTypeAssumption(type, nonNull);
        } else {
            return getTypeAssumption(oldLocation, value);
        }
    }

    private static Assumption getFinalAssumption(Location oldLocation, boolean allowFinalSpeculation) {
        if (ExtLayout.NewFinalSpeculation && allowFinalSpeculation) {
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

    private static int tryAllocatePrimitiveSlot(ExtLayout l, int startIndex, final int desiredBytes) {
        if (desiredBytes > l.getPrimitiveFieldMaxSize()) {
            // no primitive fields in this layout that are wide enough
            return -1;
        }
        for (int fieldIndex = startIndex; fieldIndex < l.getPrimitiveFieldCount(); fieldIndex++) {
            // ensure alignment
            final int align = desiredBytes - 1;
            FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
            if ((fieldInfo.offset() & align) != 0) {
                continue;
            }

            int availableBytes = fieldInfo.getBytes();
            if (availableBytes < desiredBytes) {
                // this field is not suitable for the desired number of bytes, try the next one
                continue;
            }

            return fieldIndex;
        }
        return -1;
    }

    @Override
    public Location newIntLocation(boolean useFinal) {
        return newIntLocation(false, null, NO_VALUE);
    }

    private Location newIntLocation(boolean decorateFinal, Location oldLocation, Object value) {
        if (PrimitiveLocations && IntegerLocations) {
            ExtLayout l = getLayout();
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

    @Override
    public Location newDoubleLocation(boolean useFinal) {
        return newDoubleLocation(false, getLayout().isAllowedIntToDouble(), null, NO_VALUE);
    }

    private Location newDoubleLocation(boolean decorateFinal, boolean allowIntToDouble, Location oldLocation, Object value) {
        if (PrimitiveLocations && DoubleLocations) {
            ExtLayout l = getLayout();
            if (InObjectFields) {
                int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Double.BYTES);
                if (fieldIndex >= 0) {
                    FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                    int slotCount = Double.BYTES / fieldInfo.getBytes();
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new DoubleFieldLocation(fieldIndex, fieldInfo, allowIntToDouble, slotCount, initialFinalAssumption);
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

    @Override
    public Location newLongLocation(boolean useFinal) {
        return newLongLocation(false, getLayout().isAllowedIntToLong(), null, NO_VALUE);
    }

    private Location newLongLocation(boolean decorateFinal, boolean allowIntToLong, Location oldLocation, Object value) {
        if (PrimitiveLocations && LongLocations) {
            ExtLayout l = getLayout();
            if (InObjectFields) {
                int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Long.BYTES);
                if (fieldIndex >= 0) {
                    FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                    int slotCount = Long.BYTES / fieldInfo.getBytes();
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new LongFieldLocation(fieldIndex, fieldInfo, allowIntToLong, slotCount, initialFinalAssumption);
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
    public Location newBooleanLocation(boolean useFinal) {
        return newBooleanLocation(false, null, NO_VALUE);
    }

    private Location newBooleanLocation(boolean decorateFinal, Location oldLocation, Object value) {
        if (PrimitiveLocations && BooleanLocations && InObjectFields) {
            ExtLayout l = getLayout();
            int fieldIndex = tryAllocatePrimitiveSlot(l, primitiveFieldSize, Integer.BYTES);
            if (fieldIndex >= 0) {
                FieldInfo fieldInfo = l.getPrimitiveField(fieldIndex);
                if (fieldInfo.type() == int.class) {
                    Assumption initialFinalAssumption = getFinalAssumption(oldLocation, decorateFinal);
                    LocationImpl location = new BooleanFieldLocation(fieldIndex, fieldInfo, initialFinalAssumption);
                    return advance(location);
                }
            }
        }
        return newObjectLocation(decorateFinal, oldLocation, value);
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation) {
        return locationForValueUpcast(value, oldLocation, 0);
    }

    @Override
    protected Location locationForValueUpcast(Object value, Location oldLocation, int putFlags) {
        assert !oldLocation.canStore(value);

        if (oldLocation instanceof ExtLocations.ConstantLocation && Flags.isConstant(putFlags)) {
            return constantLocation(value);
        }

        assert !oldLocation.isFinal();
        final boolean decorateFinal = false;
        Location newLocation = null;
        if (shared) {
            // if shape is shared, transition to an untyped location directly
            newLocation = oldLocation instanceof AbstractObjectArrayLocation
                            ? newObjectArrayLocation(decorateFinal, oldLocation, NO_VALUE)
                            : newObjectLocation(decorateFinal, oldLocation, NO_VALUE);
        } else if (oldLocation instanceof IntLocation) {
            boolean allowedIntToDouble = getLayout().isAllowedIntToDouble() || Flags.isImplicitCastIntToDouble(putFlags);
            boolean allowedIntToLong = getLayout().isAllowedIntToLong() || Flags.isImplicitCastIntToLong(putFlags);
            if (value instanceof Double && allowedIntToDouble) {
                newLocation = newDoubleLocation(decorateFinal, allowedIntToDouble, oldLocation, NO_VALUE);
            } else if (value instanceof Long && allowedIntToLong) {
                newLocation = newLongLocation(decorateFinal, allowedIntToLong, oldLocation, NO_VALUE);
            }
        }

        if (newLocation == null) {
            boolean nonNull = value != null && (!(oldLocation instanceof ObjectLocation) || ((ObjectLocation) oldLocation).isNonNull());
            Class<?> type = oldLocation instanceof ObjectLocation ? ((ObjectLocation) oldLocation).getType() : Object.class;
            boolean isArrayLocation = oldLocation instanceof AbstractObjectArrayLocation;
            if (type != Object.class && (value == null || type.isInstance(value))) {
                newLocation = isArrayLocation
                                ? newTypedObjectArrayLocation(type, nonNull, decorateFinal, null, NO_VALUE)
                                : newTypedObjectLocation(type, nonNull, decorateFinal, null, NO_VALUE);
            } else {
                // try superclass
                type = type != Object.class ? getCommonSuperclassForValue(type, value) : type;
                if (type != Object.class) {
                    newLocation = isArrayLocation
                                    ? newTypedObjectArrayLocation(type, nonNull, decorateFinal, null, NO_VALUE)
                                    : newTypedObjectLocation(type, nonNull, decorateFinal, null, NO_VALUE);
                } else {
                    newLocation = isArrayLocation
                                    ? newObjectArrayLocation(decorateFinal, null, NO_VALUE)
                                    : newObjectLocation(decorateFinal, null, NO_VALUE);
                }
            }
        }

        return newLocation;
    }

    @Override
    protected Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
        return locationForValue(value, nonNull, 0);
    }

    protected Location locationForValue(Object value, boolean nonNull, int putFlags) {
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
        } else if (value instanceof Boolean) {
            return newBooleanLocation(decorateFinal, null, value);
        } else if (TypedObjectLocations && value != null) {
            return newTypedObjectLocation(value.getClass(), nonNull, decorateFinal, null, value);
        }
        return newObjectLocation(decorateFinal, null, value);
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
        } else if (ExtLayout.TypedObjectLocations && type != null && type != Object.class) {
            assert !type.isPrimitive() : "unsupported primitive type";
            return newTypedObjectLocation(useFinal, type, nonNull);
        }
        return newObjectLocation(useFinal, nonNull);
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
