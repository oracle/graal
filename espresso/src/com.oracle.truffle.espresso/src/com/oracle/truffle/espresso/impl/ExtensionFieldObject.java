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

    public ExtensionFieldObject(ObjectKlass holder, List<ParserField> initialFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields) {
        this();

        for (ParserField initialField : initialFields) {
            LinkedField linkedField = new LinkedField(initialField, nextAvailableFieldSlot.getAndDecrement(), LinkedField.IdMode.REGULAR);
            Field field = new Field(holder, linkedField, pool, true);
            LIBRARY.put(fieldStorage, field.getSlot(), new FieldAndValueObject(field));

            // mark a compatible field where
            // state could potentially be copied from
            field.setCompatibleField(compatibleFields.get(initialField));
        }
    }

    public void addStaticNewFields(ObjectKlass holder, List<ParserField> newFields, RuntimeConstantPool pool, Map<ParserField, Field> compatibleFields) {
        for (ParserField newField : newFields) {
            LinkedField linkedField = new LinkedField(newField, nextAvailableFieldSlot.getAndDecrement(), LinkedField.IdMode.REGULAR);
            Field field = new Field(holder, linkedField, pool, true);
            LIBRARY.put(fieldStorage, field.getSlot(), new FieldAndValueObject(field));

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

    public Object getValue(Field field) {
        return getOrCreateFieldAndValue(field).value;
    }

    public void setValue(Field field, Object value) {
        getOrCreateFieldAndValue(field).value = value;
    }

    private FieldAndValueObject getFieldAndValue(int slot) {
        return (FieldAndValueObject) LIBRARY.getOrDefault(fieldStorage, slot, NULL_OBJECT);
    }

    private FieldAndValueObject getOrCreateFieldAndValue(Field field) {
        // fetch to check is exist to avoid producing garbage
        FieldAndValueObject result = getFieldAndValue(field.getSlot());
        if (result == NULL_OBJECT) {
            result = new FieldAndValueObject(field);
            LIBRARY.put(fieldStorage, field.getSlot(), result);
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

    private static final class FieldsHolderObject extends DynamicObject implements TruffleObject {
        FieldsHolderObject(Shape shape) {
            super(shape);
        }
    }

    private static final class FieldAndValueObject {
        private final Field field;
        private Object value;

        public FieldAndValueObject(Field field) {
            this.field = field;
        }
    }
}
