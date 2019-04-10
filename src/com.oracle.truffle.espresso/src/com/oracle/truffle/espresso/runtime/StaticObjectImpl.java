/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import sun.misc.Unsafe;

public class StaticObjectImpl extends StaticObject {

    private static final Unsafe U;

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // Only non-primitive fields are stored in this
    private final Object[] fields;

    /**
     * Stores all primitive types contiguously in a single byte array, without any unused bits
     * between prims (except for 7 bits with booleans). In order to quickly reconstruct a long (for
     * example), which would require reading 16 bytes and concatenating them, call Unsafe which can
     * directly read a long.
     */
    private final byte[] primitiveFields;

    public StaticObjectImpl(ObjectKlass klass, Object[] fields, byte[] primitiveFields) {
        super(klass);
        this.fields = fields;
        this.primitiveFields = primitiveFields;
    }

    // FIXME(peterssen): Klass does not need to be initialized, just prepared?.
    public boolean isStatic() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        return new StaticObjectImpl((ObjectKlass) getKlass(), fields == null ? null : fields.clone(), primitiveFields == null ? null : primitiveFields.clone());
    }

    public StaticObjectImpl(ObjectKlass klass) {
        this(klass, false);
    }

    public StaticObjectImpl(ObjectKlass klass, boolean isStatic) {
        super(klass);
        // assert !isStatic || klass.isInitialized();
        if (isStatic) {
            this.fields = klass.getStaticObjectFieldsCount() > 0 ? new Object[klass.getStaticObjectFieldsCount()] : null;
            this.primitiveFields = klass.getStaticWordFieldsCount() > 0 ? new byte[klass.getStaticWordFieldsCount()] : null;
        } else {
            this.fields = klass.getObjectFieldsCount() > 0 ? new Object[klass.getObjectFieldsCount()] : null;
            this.primitiveFields = klass.getWordFieldsCount() > 0 ? new byte[klass.getWordFieldsCount()] : null;
        }
        initFields(klass, isStatic);
    }

    @ExplodeLoop
    private void initFields(ObjectKlass klass, boolean isStatic) {
        CompilerAsserts.partialEvaluationConstant(klass);
        if (isStatic) {
            for (Field f : klass.getStaticFieldTable()) {
                assert f.isStatic();
                if (f.getKind().isSubWord()) {
                    setWordField(f, MetaUtil.defaultWordFieldValue(f.getKind()));
                } else if (f.getKind().isPrimitive()) {
                    setLongField(f, (long) MetaUtil.defaultFieldValue(f.getKind()));
                } else {
                    fields[f.getFieldIndex()] = MetaUtil.defaultFieldValue(f.getKind());
                }
            }
        } else {
            for (Field f : klass.getFieldTable()) {
                assert !f.isStatic();
                if (f.isHidden()) {
                    fields[f.getFieldIndex()] = null;
                } else {
                    if (f.getKind().isSubWord()) {
                        setWordField(f, MetaUtil.defaultWordFieldValue(f.getKind()));
                    } else if (f.getKind().isPrimitive()) {
                        setLongField(f, (long) MetaUtil.defaultFieldValue(f.getKind()));
                    } else {
                        fields[f.getFieldIndex()] = MetaUtil.defaultFieldValue(f.getKind());
                    }
                }
            }
        }
    }

    // Start non primitive field handling.

    public final Object getFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getObjectVolatile(fields, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * field.getFieldIndex());
    }

    public final Object getField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        Object result;
        if (field.isVolatile()) {
            result = getFieldVolatile(field);
        } else {
            result = fields[field.getFieldIndex()];
        }
        assert result != null;
        return result;
    }

    public final Object getUnsafeField(int fieldIndex) {
        return fields[fieldIndex];
    }

    public final void setFieldVolatile(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        U.putObjectVolatile(fields, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * field.getFieldIndex(), value);
    }

    public final void setField(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        if (field.isVolatile()) {
            setFieldVolatile(field, value);
        } else {
            fields[field.getFieldIndex()] = value;
        }
    }

    // End non-primitive field handling
    // Start subword field handling

    public final int getWordField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().isSubWord();
        int result;
        if (field.isVolatile()) {
            result = getWordFieldVolatile(field);
        } else {
            result = applyGetWordField(field);
        }
        return result;
    }

    private int applyGetWordField(Field field) {
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                return U.getByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Char:
                return U.getChar(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Short:
                return U.getShort(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Int:
            case Float:
                return U.getInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    public final int getWordFieldVolatile(Field field) {
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                return U.getByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Char:
                return U.getCharVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Short:
                return U.getShortVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            case Int:
            case Float:
                return U.getIntVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    public final void setWordFieldVolatile(Field field, int value) {
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                U.putByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) value);
                break;
            case Char:
                U.putCharVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (char) value);
                break;
            case Short:
                U.putShortVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (short) value);
                break;
            case Int:
            case Float:
                U.putIntVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    public final void setWordField(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().isSubWord();
        if (field.isVolatile()) {
            setWordFieldVolatile(field, value);
        } else {
            applySetWordField(field, value);
        }
    }

    private void applySetWordField(Field field, int value) {
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                U.putByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) value);
                break;
            case Char:
                U.putChar(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (char) value);
                break;
            case Short:
                U.putShort(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (short) value);
                break;
            case Int:
            case Float:
                U.putInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    // End subword field handling
    // start big words field handling

    public final long getLongFieldVolatile(Field field) {
        return U.getLongVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final long getLongField(Field field) {
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            return getLongFieldVolatile(field);
        } else {
            return U.getLong(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public final void setLongFieldVolatile(Field field, long value) {
        U.putLongVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
    }

    public final void setLongField(Field field, long value) {
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            setLongFieldVolatile(field, value);
        } else {
            U.putLong(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    // End big words field handling.

    // Given a guest Class, get the corresponding Klass.
    public final Klass getMirrorKlass() {
        assert getKlass().getType() == Symbol.Type.Class;
        return (Klass) getHiddenField(getKlass().getMeta().HIDDEN_MIRROR_KLASS);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (getKlass() == getKlass().getMeta().String) {
            return Meta.toHostString(this);
        }
        return getKlass().getType().toString();
    }

    public void setHiddenField(Field hiddenField, Object value) {
        assert hiddenField.isHidden();
        fields[hiddenField.getFieldIndex()] = value;
    }

    public Object getHiddenField(Field hiddenField) {
        assert hiddenField.isHidden();
        return fields[hiddenField.getFieldIndex()];
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return StaticObjectMessageResolutionForeign.ACCESS;
    }
}
