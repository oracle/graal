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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class ExtensionFieldObject extends StaticObject {
    private static AtomicInteger nextAvailableInstanceSlot = new AtomicInteger(-1);

    private final Map<Field, Object> fieldValues;

    @CompilationFinal(dimensions = 1) private Field[] addedInstanceFields = Field.EMPTY_ARRAY;

    private int nextAvailableStaticSlot;

    public ExtensionFieldObject(ObjectKlass holder, List<ParserField> initialFields, RuntimeConstantPool pool) {
        super(null, false);
        nextAvailableStaticSlot = holder.getStaticFieldTable().length;

        this.fieldValues = new HashMap<>(initialFields.size());
        for (ParserField initialField : initialFields) {
            LinkedField linkedField = new LinkedField(initialField, nextAvailableStaticSlot++, LinkedField.IdMode.REGULAR);
            Field field = new Field(holder, linkedField, pool, true);
            fieldValues.put(field, null);
        }
    }

    public ExtensionFieldObject() {
        super(null, false);
        this.fieldValues = new HashMap<>(1);
    }

    public void addStaticNewFields(ObjectKlass holder, List<ParserField> newFields, RuntimeConstantPool pool) {
        synchronized (fieldValues) {
            for (ParserField newField : newFields) {
                LinkedField linkedField = new LinkedField(newField, nextAvailableStaticSlot++, LinkedField.IdMode.REGULAR);
                Field field = new Field(holder, linkedField, pool, true);
                fieldValues.put(field, null);
            }
        }
    }

    public Collection<Field> getDeclaredAddedFields() {
        synchronized (fieldValues) {
            synchronized (addedInstanceFields) {
                List<Field> result = new ArrayList<>();
                result.addAll(fieldValues.keySet());
                result.addAll(Arrays.asList(addedInstanceFields));
                return Collections.unmodifiableList(result);
            }
        }
    }

    @TruffleBoundary
    public Object getValue(Field field) {
        synchronized (fieldValues) {
            return fieldValues.get(field);
        }
    }

    @TruffleBoundary
    public void setValue(Field field, Object value) {
        synchronized (fieldValues) {
            fieldValues.put(field, value);
        }
    }

    public void addNewInstanceFields(ObjectKlass holder, List<ParserField> instanceFields, RuntimeConstantPool pool) {
        CompilerAsserts.neverPartOfCompilation();
        synchronized (addedInstanceFields) {
            List<Field> toAdd = new ArrayList<>(instanceFields.size());
            for (ParserField newField : instanceFields) {
                LinkedField linkedField = new LinkedField(newField, nextAvailableInstanceSlot.getAndDecrement(), LinkedField.IdMode.REGULAR);
                Field field = new Field(holder, linkedField, pool, true);
                toAdd.add(field);
            }
            int nextIndex = addedInstanceFields.length;
            addedInstanceFields = Arrays.copyOf(addedInstanceFields, addedInstanceFields.length + toAdd.size());
            for (Field field : toAdd) {
                addedInstanceFields[nextIndex++] = field;
            }
        }
    }

    @TruffleBoundary
    public Field getStaticFieldAtSlot(int slot) throws IndexOutOfBoundsException {
        for (Field field : fieldValues.keySet()) {
            if (field.getSlot() == slot) {
                return field;
            }
        }
        throw new IndexOutOfBoundsException("Index out of range: " + slot);
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
}
