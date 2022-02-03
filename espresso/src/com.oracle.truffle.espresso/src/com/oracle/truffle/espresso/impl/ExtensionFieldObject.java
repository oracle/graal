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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.espresso.runtime.StaticObject;

// This class models the data in fields that were added by class redefinition
// each instance of this class maps to one guest instance. Either a class object
// for which static field state is maintained or a regular object for which
// instance field state is managed.
public final class ExtensionFieldObject {

    private static final FieldStorageObject NULL_OBJECT = new FieldStorageObject();
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();
    private static final Shape fieldsHolderObjectShape = Shape.newBuilder().layout(FieldsHolderObject.class).build();

    // expandable object that manages the storage of each added field
    private final DynamicObject fieldStorage;

    public ExtensionFieldObject() {
        this.fieldStorage = new FieldsHolderObject(fieldsHolderObjectShape);
    }

    private FieldStorageObject getFieldAndValue(int slot) {
        return (FieldStorageObject) LIBRARY.getOrDefault(fieldStorage, slot, NULL_OBJECT);
    }

    private FieldStorageObject getOrCreateFieldAndValue(Field field) {
        // fetch to check if exists to avoid producing garbage
        FieldStorageObject result = getFieldAndValue(field.getSlot());
        if (result == NULL_OBJECT) {
            synchronized (fieldStorage) {
                result = getFieldAndValue(field.getSlot());
                if (result == NULL_OBJECT) {
                    result = field.getExtensionShape().getFactory().create();
                    LIBRARY.put(fieldStorage, field.getSlot(), result);
                }
            }
        }
        return result;
    }

    // region field value read/write/CAS
    public StaticObject getObject(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        Object result;
        if (forceVolatile) {
            result = field.linkedField.getObjectVolatile(fieldAndValue);
        } else {
            result = field.linkedField.getObject(fieldAndValue);
        }
        return result == null ? StaticObject.NULL : (StaticObject) result;
    }

    public void setObject(Field field, Object value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setObjectVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setObject(fieldAndValue, value);
        }
    }

    public StaticObject getAndSetObject(Field field, StaticObject value) {
        Object result = field.linkedField.getAndSetObject(getOrCreateFieldAndValue(field), value);
        return result == null ? StaticObject.NULL : (StaticObject) result;
    }

    public boolean compareAndSwapObject(Field field, Object before, Object after) {
        return field.linkedField.compareAndSwapObject(getOrCreateFieldAndValue(field), before, after);
    }

    public StaticObject compareAndExchangeObject(Field field, Object before, Object after) {
        Object result = field.linkedField.compareAndExchangeObject(getOrCreateFieldAndValue(field), before, after);
        return result == null ? StaticObject.NULL : (StaticObject) result;
    }

    public boolean getBoolean(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getBooleanVolatile(fieldAndValue);
        } else {
            return field.linkedField.getBoolean(fieldAndValue);
        }
    }

    public void setBoolean(Field field, boolean value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setBooleanVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setBoolean(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapBoolean(Field field, boolean before, boolean after) {
        return field.linkedField.compareAndSwapBoolean(getOrCreateFieldAndValue(field), before, after);
    }

    public boolean compareAndExchangeBoolean(Field field, boolean before, boolean after) {
        return field.linkedField.compareAndExchangeBoolean(getOrCreateFieldAndValue(field), before, after);
    }

    public byte getByte(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getByteVolatile(fieldAndValue);
        } else {
            return field.linkedField.getByte(fieldAndValue);
        }
    }

    public void setByte(Field field, byte value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setByteVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setByte(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapByte(Field field, byte before, byte after) {
        return field.linkedField.compareAndSwapByte(getOrCreateFieldAndValue(field), before, after);
    }

    public byte compareAndExchangeByte(Field field, byte before, byte after) {
        return field.linkedField.compareAndExchangeByte(getOrCreateFieldAndValue(field), before, after);
    }

    public char getChar(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getCharVolatile(fieldAndValue);
        } else {
            return field.linkedField.getChar(fieldAndValue);
        }
    }

    public void setChar(Field field, char value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setCharVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setChar(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapChar(Field field, char before, char after) {
        return field.linkedField.compareAndSwapChar(getOrCreateFieldAndValue(field), before, after);
    }

    public char compareAndExchangeChar(Field field, char before, char after) {
        return field.linkedField.compareAndExchangeChar(getOrCreateFieldAndValue(field), before, after);
    }

    public double getDouble(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getDoubleVolatile(fieldAndValue);
        } else {
            return field.linkedField.getDouble(fieldAndValue);
        }
    }

    public void setDouble(Field field, double value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setDoubleVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setDouble(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapDouble(Field field, double before, double after) {
        return field.linkedField.compareAndSwapDouble(getOrCreateFieldAndValue(field), before, after);
    }

    public double compareAndExchangeDouble(Field field, double before, double after) {
        return field.linkedField.compareAndExchangeDouble(getOrCreateFieldAndValue(field), before, after);
    }

    public float getFloat(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getFloatVolatile(fieldAndValue);
        } else {
            return field.linkedField.getFloat(fieldAndValue);
        }
    }

    public void setFloat(Field field, float value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setFloatVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setFloat(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapFloat(Field field, float before, float after) {
        return field.linkedField.compareAndSwapFloat(getOrCreateFieldAndValue(field), before, after);
    }

    public float compareAndExchangeFloat(Field field, float before, float after) {
        return field.linkedField.compareAndExchangeFloat(getOrCreateFieldAndValue(field), before, after);
    }

    public int getInt(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getIntVolatile(fieldAndValue);
        } else {
            return field.linkedField.getInt(fieldAndValue);
        }
    }

    public void setInt(Field field, int value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setIntVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setInt(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapInt(Field field, int before, int after) {
        return field.linkedField.compareAndSwapInt(getOrCreateFieldAndValue(field), before, after);
    }

    public int compareAndExchangeInt(Field field, int before, int after) {
        return field.linkedField.compareAndExchangeInt(getOrCreateFieldAndValue(field), before, after);
    }

    public long getLong(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getLongVolatile(fieldAndValue);
        } else {
            return field.linkedField.getLong(fieldAndValue);
        }
    }

    public void setLong(Field field, long value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setLongVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setLong(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapLong(Field field, long before, long after) {
        return field.linkedField.compareAndSwapLong(getOrCreateFieldAndValue(field), before, after);
    }

    public long compareAndExchangeLong(Field field, long before, long after) {
        return field.linkedField.compareAndExchangeLong(getOrCreateFieldAndValue(field), before, after);
    }

    public short getShort(Field field, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            return field.linkedField.getShortVolatile(fieldAndValue);
        } else {
            return field.linkedField.getShort(fieldAndValue);
        }
    }

    public void setShort(Field field, short value, boolean forceVolatile) {
        FieldStorageObject fieldAndValue = getOrCreateFieldAndValue(field);
        if (forceVolatile) {
            field.linkedField.setShortVolatile(fieldAndValue, value);
        } else {
            field.linkedField.setShort(fieldAndValue, value);
        }
    }

    public boolean compareAndSwapShort(Field field, short before, short after) {
        return field.linkedField.compareAndSwapShort(getOrCreateFieldAndValue(field), before, after);
    }

    public short compareAndExchangeShort(Field field, short before, short after) {
        return field.linkedField.compareAndExchangeShort(getOrCreateFieldAndValue(field), before, after);
    }
    // endregion field value read/write/CAS

    private static final class FieldsHolderObject extends DynamicObject implements TruffleObject {
        FieldsHolderObject(Shape shape) {
            super(shape);
        }
    }

    public static class FieldStorageObject {

    }

    public interface ExtensionFieldObjectFactory {
        FieldStorageObject create();
    }
}
