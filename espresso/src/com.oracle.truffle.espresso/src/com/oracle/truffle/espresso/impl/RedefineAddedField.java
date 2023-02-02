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

import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class RedefineAddedField extends Field {

    private Field compatibleField;
    private StaticShape<ExtensionFieldObjectFactory> extensionShape;

    private final FieldStorageObject staticStorageObject;
    private final WeakHashMap<StaticObject, FieldStorageObject> storageObjects = new WeakHashMap<>(1);

    public RedefineAddedField(ObjectKlass.KlassVersion holder, LinkedField linkedField, RuntimeConstantPool pool, boolean isDelegation) {
        super(holder, linkedField, pool);
        if (!isDelegation) {
            StaticShape.Builder shapeBuilder = StaticShape.newBuilder(getDeclaringKlass().getLanguage());
            shapeBuilder.property(linkedField, linkedField.getParserField().getPropertyType(), isFinalFlagSet());
            this.extensionShape = shapeBuilder.build(FieldStorageObject.class, ExtensionFieldObjectFactory.class);
        }
        if (isStatic() && !isDelegation) {
            // create the extension field object eagerly for static fields
            staticStorageObject = extensionShape.getFactory().create();
            if (getKind().isObject()) {
                linkedField.setObject(staticStorageObject, StaticObject.NULL);
            }
        } else {
            staticStorageObject = null;
        }
    }

    public static Field createDelegationField(Field field) {
        // update holder to latest klass version to ensure we
        // only re-resolve again when the class is redefined
        RedefineAddedField delegationField = new RedefineAddedField(field.getDeclaringKlass().getKlassVersion(), field.linkedField, field.pool, true);
        delegationField.setCompatibleField(field);
        return delegationField;
    }

    @Override
    public void setCompatibleField(Field field) {
        compatibleField = field;
    }

    @Override
    public boolean hasCompatibleField() {
        return compatibleField != null;
    }

    @Override
    public Field getCompatibleField() {
        return compatibleField;
    }

    @TruffleBoundary
    private FieldStorageObject getStorageObject(StaticObject instance) {
        if (isStatic()) {
            return staticStorageObject;
        }

        FieldStorageObject storageObject = storageObjects.get(instance);
        if (storageObject == null) {
            synchronized (storageObjects) {
                storageObject = storageObjects.get(instance);
                if (storageObject == null) {
                    storageObject = extensionShape.getFactory().create();
                    if (getKind().isObject()) {
                        linkedField.setObject(storageObject, StaticObject.NULL);
                    }
                    if (getDeclaringKlass() != instance.getKlass()) {
                        // we have to check if there's a field value
                        // in a subclass field that was removed in order
                        // to proeserve the state f a pull-up field
                        checkPullUpField(instance, storageObject);
                    }
                    storageObjects.put(instance, storageObject);
                }
            }
        }
        return storageObject;
    }

    private void checkPullUpField(StaticObject instance, FieldStorageObject storageObject) {
        if (instance.getKlass() instanceof ObjectKlass) {
            ObjectKlass current = (ObjectKlass) instance.getKlass();
            while (current != getDeclaringKlass()) {
                Field removedField = current.getRemovedField(this);
                if (removedField != null) {
                    // OK, copy the state to the extension object
                    // @formatter:off
                    switch (getKind()) {
                        case Boolean: linkedField.setBoolean(storageObject, removedField.linkedField.getBooleanVolatile(instance)); return;
                        case Byte: linkedField.setByte(storageObject, removedField.linkedField.getByteVolatile(instance)); return;
                        case Short: linkedField.setShort(storageObject, removedField.linkedField.getShortVolatile(instance)); return;
                        case Char: linkedField.setChar(storageObject, removedField.linkedField.getCharVolatile(instance)); return;
                        case Int: linkedField.setInt(storageObject, removedField.linkedField.getIntVolatile(instance)); return;
                        case Float: linkedField.setFloat(storageObject, removedField.linkedField.getFloatVolatile(instance)); return;
                        case Long: linkedField.setLong(storageObject, removedField.linkedField.getLongVolatile(instance)); return;
                        case Double: linkedField.setDouble(storageObject, removedField.linkedField.getDoubleVolatile(instance)); return;
                        case Object: linkedField.setObject(storageObject, removedField.linkedField.getObjectVolatile(instance)); return;
                        default: break;
                    }
                    // @formatter:on
                }
                current = current.getSuperKlass();
            }
        }
    }

    @Override
    public StaticObject getObject(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getObject(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            StaticObject result;
            if (forceVolatile) {
                result = (StaticObject) linkedField.getObjectVolatile(storageObject);
            } else {
                result = (StaticObject) linkedField.getObject(storageObject);
            }
            if (result == StaticObject.NULL) {
                return result;
            }
            if (getDeclaringKlass().getContext().anyHierarchyChanged()) {
                return checkGetValueValidity(result);
            }
            return result;
        }
    }

    @Override
    public void setObject(StaticObject obj, Object value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setObject(obj, value, forceVolatile);
        } else {
            if (getDeclaringKlass().getContext().anyHierarchyChanged()) {
                checkSetValueValifity(value);
            }
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setObjectVolatile(storageObject, value);
            } else {
                linkedField.setObject(storageObject, value);
            }
        }
    }

    @Override
    public StaticObject getAndSetObject(StaticObject obj, StaticObject value) {
        if (hasCompatibleField()) {
            return getCompatibleField().getAndSetObject(obj, value);
        } else {
            return (StaticObject) linkedField.getAndSetObject(getStorageObject(obj), value);
        }
    }

    @Override
    public boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapObject(obj, before, after);
        } else {
            return linkedField.compareAndSwapObject(getStorageObject(obj), before, after);
        }
    }

    @Override
    public StaticObject compareAndExchangeObject(StaticObject obj, Object before, Object after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeObject(obj, before, after);
        } else {
            return (StaticObject) linkedField.compareAndExchangeObject(getStorageObject(obj), before, after);
        }
    }

    @Override
    public boolean getBoolean(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getBoolean(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getBooleanVolatile(storageObject);
            } else {
                return linkedField.getBoolean(storageObject);
            }
        }
    }

    @Override
    public void setBoolean(StaticObject obj, boolean value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setBoolean(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setBooleanVolatile(storageObject, value);
            } else {
                linkedField.setBoolean(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapBoolean(StaticObject obj, boolean before, boolean after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapBoolean(obj, before, after);
        } else {
            return linkedField.compareAndSwapBoolean(getStorageObject(obj), before, after);
        }
    }

    @Override
    public boolean compareAndExchangeBoolean(StaticObject obj, boolean before, boolean after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeBoolean(obj, before, after);
        } else {
            return linkedField.compareAndExchangeBoolean(getStorageObject(obj), before, after);
        }
    }

    @Override
    public byte getByte(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getByte(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getByteVolatile(storageObject);
            } else {
                return linkedField.getByte(storageObject);
            }
        }
    }

    @Override
    public void setByte(StaticObject obj, byte value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setByte(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setByteVolatile(storageObject, value);
            } else {
                linkedField.setByte(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapByte(StaticObject obj, byte before, byte after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapByte(obj, before, after);
        } else {
            return linkedField.compareAndSwapByte(getStorageObject(obj), before, after);
        }
    }

    @Override
    public byte compareAndExchangeByte(StaticObject obj, byte before, byte after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeByte(obj, before, after);
        } else {
            return linkedField.compareAndExchangeByte(getStorageObject(obj), before, after);
        }
    }

    @Override
    public char getChar(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getChar(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getCharVolatile(storageObject);
            } else {
                return linkedField.getChar(storageObject);
            }
        }
    }

    @Override
    public void setChar(StaticObject obj, char value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setChar(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setCharVolatile(storageObject, value);
            } else {
                linkedField.setChar(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapChar(StaticObject obj, char before, char after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapChar(obj, before, after);
        } else {
            return linkedField.compareAndSwapChar(getStorageObject(obj), before, after);
        }
    }

    @Override
    public char compareAndExchangeChar(StaticObject obj, char before, char after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeChar(obj, before, after);
        } else {
            return linkedField.compareAndExchangeChar(getStorageObject(obj), before, after);
        }
    }

    @Override
    public double getDouble(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getDouble(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getDoubleVolatile(storageObject);
            } else {
                return linkedField.getDouble(storageObject);
            }
        }
    }

    @Override
    public void setDouble(StaticObject obj, double value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setDouble(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setDoubleVolatile(storageObject, value);
            } else {
                linkedField.setDouble(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapDouble(StaticObject obj, double before, double after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapDouble(obj, before, after);
        } else {
            return linkedField.compareAndSwapDouble(getStorageObject(obj), before, after);
        }
    }

    @Override
    public double compareAndExchangeDouble(StaticObject obj, double before, double after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeDouble(obj, before, after);
        } else {
            return linkedField.compareAndExchangeDouble(getStorageObject(obj), before, after);
        }
    }

    @Override
    public float getFloat(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getFloat(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getFloatVolatile(storageObject);
            } else {
                return linkedField.getFloat(storageObject);
            }
        }
    }

    @Override
    public void setFloat(StaticObject obj, float value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setFloat(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setFloatVolatile(storageObject, value);
            } else {
                linkedField.setFloat(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapFloat(StaticObject obj, float before, float after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapFloat(obj, before, after);
        } else {
            return linkedField.compareAndSwapFloat(getStorageObject(obj), before, after);
        }
    }

    @Override
    public float compareAndExchangeFloat(StaticObject obj, float before, float after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeFloat(obj, before, after);
        } else {
            return linkedField.compareAndExchangeFloat(getStorageObject(obj), before, after);
        }
    }

    @Override
    public int getInt(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getInt(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getIntVolatile(storageObject);
            } else {
                return linkedField.getInt(storageObject);
            }
        }
    }

    @Override
    public void setInt(StaticObject obj, int value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setInt(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setIntVolatile(storageObject, value);
            } else {
                linkedField.setInt(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapInt(obj, before, after);
        } else {
            return linkedField.compareAndSwapInt(getStorageObject(obj), before, after);
        }
    }

    @Override
    public int compareAndExchangeInt(StaticObject obj, int before, int after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeInt(obj, before, after);
        } else {
            return linkedField.compareAndExchangeInt(getStorageObject(obj), before, after);
        }
    }

    @Override
    public long getLong(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getLong(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getLongVolatile(storageObject);
            } else {
                return linkedField.getLong(storageObject);
            }
        }
    }

    @Override
    public void setLong(StaticObject obj, long value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setLong(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setLongVolatile(storageObject, value);
            } else {
                linkedField.setLong(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapLong(obj, before, after);
        } else {
            return linkedField.compareAndSwapLong(getStorageObject(obj), before, after);
        }
    }

    @Override
    public long compareAndExchangeLong(StaticObject obj, long before, long after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeLong(obj, before, after);
        } else {
            return linkedField.compareAndExchangeLong(getStorageObject(obj), before, after);
        }
    }

    @Override
    public short getShort(StaticObject obj, boolean forceVolatile) {
        if (hasCompatibleField()) {
            return getCompatibleField().getShort(obj, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                return linkedField.getShortVolatile(storageObject);
            } else {
                return linkedField.getShort(storageObject);
            }
        }
    }

    @Override
    public void setShort(StaticObject obj, short value, boolean forceVolatile) {
        if (hasCompatibleField()) {
            getCompatibleField().setShort(obj, value, forceVolatile);
        } else {
            FieldStorageObject storageObject = getStorageObject(obj);
            if (forceVolatile) {
                linkedField.setShortVolatile(storageObject, value);
            } else {
                linkedField.setShort(storageObject, value);
            }
        }
    }

    @Override
    public boolean compareAndSwapShort(StaticObject obj, short before, short after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndSwapShort(obj, before, after);
        } else {
            return linkedField.compareAndSwapShort(getStorageObject(obj), before, after);
        }
    }

    @Override
    public short compareAndExchangeShort(StaticObject obj, short before, short after) {
        if (hasCompatibleField()) {
            return getCompatibleField().compareAndExchangeShort(obj, before, after);
        } else {
            return linkedField.compareAndExchangeShort(getStorageObject(obj), before, after);
        }
    }

    public static class FieldStorageObject implements TruffleObject {
    }

    public interface ExtensionFieldObjectFactory {
        FieldStorageObject create();
    }
}
