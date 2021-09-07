/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class ExtensionFieldObject extends StaticObject {
    private static AtomicInteger nextAvailableFieldSlot = new AtomicInteger(-1);
    private static final FieldAndValueObject NULL_OBJECT = new FieldAndValueObject(null);
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    private final DynamicObject fieldStorage;

    @CompilationFinal(dimensions = 1) private Field[] addedInstanceFields = Field.EMPTY_ARRAY;

    public ExtensionFieldObject() {
        super(null, false);
        this.fieldStorage = new FieldsHolderObject(Shape.newBuilder().layout(FieldsHolderObject.class).build());
    }

    public ExtensionFieldObject(ObjectKlass holder, List<ParserField> newStaticFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields) {
        this();
        addNewStaticFields(holder, newStaticFields, pool, compatibleFields);
    }

    public void addNewStaticFields(ObjectKlass holder, List<ParserField> newFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields) {
        for (ParserField newField : newFields) {
            LinkedField linkedField = new LinkedField(newField, nextAvailableFieldSlot.getAndDecrement(), LinkedField.IdMode.REGULAR);
            Field field = new Field(holder, linkedField, pool, true);
            getOrCreateFieldAndValue(field);

            // mark a compatible field where
            // state could potentially be copied from
            field.setCompatibleField(compatibleFields.get(newField));
        }
    }

    public Collection<Field> getDeclaredAddedFields() {
        List<Field> result = new ArrayList<>();
        Property[] propertyArray = LIBRARY.getPropertyArray(fieldStorage);
        for (Property property : propertyArray) {
            result.add(getFieldAndValue((int) property.getKey()).field);
        }
        result.addAll(Arrays.asList(addedInstanceFields));
        return Collections.unmodifiableList(result);
    }

    private FieldAndValueObject getFieldAndValue(int slot) {
        return (FieldAndValueObject) LIBRARY.getOrDefault(fieldStorage, slot, NULL_OBJECT);
    }

    private FieldAndValueObject getOrCreateFieldAndValue(Field field) {
        // fetch to check if exists to avoid producing garbage
        FieldAndValueObject result = getFieldAndValue(field.getSlot());
        if (result == NULL_OBJECT) {
            synchronized (field) {
                result = getFieldAndValue(field.getSlot());
                if (result == NULL_OBJECT) {
                    result = field.getExtensionShape().getFactory().create(field);
                    LIBRARY.put(fieldStorage, field.getSlot(), result);
                }
            }
        }
        return result;
    }

    public void addNewInstanceFields(ObjectKlass holder, List<ParserField> instanceFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields) {
        CompilerAsserts.neverPartOfCompilation();
        if (instanceFields.isEmpty()) {
            return;
        }
        List<Field> toAdd = new ArrayList<>(instanceFields.size());
        for (ParserField newField : instanceFields) {
            LinkedField linkedField = new LinkedField(newField, nextAvailableFieldSlot.getAndDecrement(), LinkedField.IdMode.REGULAR);
            Field field = new Field(holder, linkedField, pool, true);
            toAdd.add(field);

            // mark a compatible field where
            // state could potentially be copied from
            field.setCompatibleField(compatibleFields.get(newField));
        }
        int nextIndex = addedInstanceFields.length;
        addedInstanceFields = Arrays.copyOf(addedInstanceFields, addedInstanceFields.length + toAdd.size());
        for (Field field : toAdd) {
            addedInstanceFields[nextIndex++] = field;
        }
    }

    public Field getStaticFieldAtSlot(int slot) throws IndexOutOfBoundsException {
        FieldAndValueObject fieldAndValue = getFieldAndValue(slot);
        if (fieldAndValue == NULL_OBJECT) {
            throw new IndexOutOfBoundsException("Index out of range: " + slot);
        } else {
            return fieldAndValue.field;
        }
    }

    public Field getInstanceFieldAtSlot(int slot) throws NoSuchFieldException {
        return binarySearch(addedInstanceFields, slot);
    }

    private static Field binarySearch(Field[] arr, int slot) throws NoSuchFieldException {
        int firstIndex = 0;
        int lastIndex = arr.length - 1;

        while (firstIndex <= lastIndex) {
            int middleIndex = (firstIndex + lastIndex) / 2;

            if (arr[middleIndex].getSlot() == slot) {
                return arr[middleIndex];
            } else if (arr[middleIndex].getSlot() > slot) {
                firstIndex = middleIndex + 1;
            } else if (arr[middleIndex].getSlot() < slot) {
                lastIndex = middleIndex - 1;
            }
        }
        throw new NoSuchFieldException("Index out of range: " + slot);
    }

    // region field value read/write/CAS
    public StaticObject getObject(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        Object result;
        if (forceVolatile) {
            result = field.getLinkedField().getObjectVolatile(fieldAndValue);
        } else {
            result = field.getLinkedField().getObject(fieldAndValue);
        }
        return result == null ? StaticObject.NULL : (StaticObject) result;
    }

    public void setObject(Field field, Object value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setObjectVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setObject(fieldAndValue, value);
        }
    }

    public boolean getBoolean(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getBooleanVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getBoolean(fieldAndValue);
        }
    }

    public void setBoolean(Field field, boolean value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setBooleanVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setBoolean(fieldAndValue, value);
        }
    }

    public byte getByte(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getByteVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getByte(fieldAndValue);
        }
    }

    public void setByte(Field field, byte value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setByteVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setByte(fieldAndValue, value);
        }
    }

    public char getChar(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getCharVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getChar(fieldAndValue);
        }
    }

    public void setChar(Field field, char value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setCharVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setChar(fieldAndValue, value);
        }
    }

    public double getDouble(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getDoubleVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getDouble(fieldAndValue);
        }
    }

    public void setDouble(Field field, double value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setDoubleVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setDouble(fieldAndValue, value);
        }
    }

    public float getFloat(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getFloatVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getFloat(fieldAndValue);
        }
    }

    public void setFloat(Field field, float value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setFloatVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setFloat(fieldAndValue, value);
        }
    }

    public int getInt(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getIntVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getInt(fieldAndValue);
        }
    }

    public void setInt(Field field, int value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setIntVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setInt(fieldAndValue, value);
        }
    }

    public long getLong(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getLongVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getLong(fieldAndValue);
        }
    }

    public void setLong(Field field, long value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setLongVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setLong(fieldAndValue, value);
        }
    }

    public short getShort(Field field, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.getLinkedField().getShortVolatile(fieldAndValue);
        } else {
            return field.getLinkedField().getShort(fieldAndValue);
        }
    }

    public void setShort(Field field, short value, boolean forceVolatile) {
        FieldAndValueObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.getLinkedField().setShortVolatile(fieldAndValue, value);
        } else {
            field.getLinkedField().setShort(fieldAndValue, value);
        }
    }
    // endregion field value read/write/CAS

    private static final class FieldsHolderObject extends DynamicObject implements TruffleObject {
        FieldsHolderObject(Shape shape) {
            super(shape);
        }
    }

    public static class FieldAndValueObject {
        private final Field field;

        public FieldAndValueObject(Field field) {
            this.field = field;
        }
    }

    public interface ExtensionFieldObjectFactory {
        FieldAndValueObject create(Field field);
    }
}
