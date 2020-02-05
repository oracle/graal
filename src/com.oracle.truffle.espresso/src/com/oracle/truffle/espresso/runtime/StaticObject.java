/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.castExact;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.Stable;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

/**
 * Implementation of the Espresso object model.
 *
 * <p>
 * For performance reasons, all guest objects, including arrays, classes and <b>null</b>, are
 * instances of {@link StaticObject}.
 */
@ExportLibrary(InteropLibrary.class)
public final class StaticObject implements TruffleObject {

    // region Interop

    @ExportMessage
    public static boolean isNull(StaticObject object) {
        assert object != null;
        return object == StaticObject.NULL;
    }

    public static boolean isEspressoNull(Object object) {
        return object == StaticObject.NULL;
    }

    @ExportMessage
    public boolean isString() {
        return StaticObject.notNull(this) && getKlass() == getKlass().getMeta().java_lang_String;
    }

    @ExportMessage
    public String asString() {
        return Meta.toHostString(this);
    }

    // endregion Interop

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    public static final StaticObject[] EMPTY_ARRAY = new StaticObject[0];

    public static final StaticObject VOID = new StaticObject();
    public static final StaticObject NULL = new StaticObject();

    private volatile EspressoLock lock;

    /**
     * Returns an {@link EspressoLock} instance for use with this {@link StaticObject} instance.
     *
     * <p>
     * The {@link EspressoLock} instance will be unique and cached. Calling this method on
     * {@link StaticObject#NULL} is an invalid operation.
     *
     * <p>
     * The returned {@link EspressoLock} instance supports the same usages as do the {@link Object}
     * monitor methods ({@link Object#wait() wait}, {@link Object#notify notify}, and
     * {@link Object#notifyAll notifyAll}) when used with the built-in monitor lock.
     */
    public EspressoLock getLock() {
        if (isNull(this)) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("StaticObject.NULL.getLock()");
        }
        EspressoLock l = lock;
        if (l == null) {
            synchronized (this) {
                l = lock;
                if (l == null) {
                    lock = l = EspressoLock.create();
                }
            }
        }
        return l;
    }

    // Only non-primitive fields are stored in this
    private final Object fields;

    /**
     * Stores all primitive types contiguously in a single byte array, without any unused bits
     * between prims (except for 7 bits with booleans). In order to quickly reconstruct a long (for
     * example), which would require reading 16 bytes and concatenating them, call Unsafe which can
     * directly read a long.
     */
    // Note: For the time being, Graal does not allow virtualization of byte arrays with access
    // kinds bigger than a byte.
    // @see: VirtualizerToolImpl.setVirtualEntry
    private final byte[] primitiveFields;

    public byte[] cloneFields() {
        return primitiveFields.clone();
    }

    // Dedicated constructor for VOID and NULL pseudo-singletons
    private StaticObject() {
        this.klass = null;
        this.fields = null;
        this.primitiveFields = null;
    }

    // Constructor for object copy
    StaticObject(ObjectKlass klass, Object[] fields, byte[] primitiveFields) {
        this.klass = klass;
        this.fields = fields;
        this.primitiveFields = primitiveFields;
    }

    // Constructor for regular objects.
    public StaticObject(ObjectKlass klass) {
        this(klass, false);
    }

    // Constructor for Class objects
    public StaticObject(ObjectKlass guestClass, Klass thisKlass) {
        assert thisKlass != null;
        assert guestClass == guestClass.getMeta().java_lang_Class;
        this.klass = guestClass;
        // assert !isStatic || klass.isInitialized(); else {
        int primitiveFieldCount = guestClass.getPrimitiveFieldTotalByteCount();
        this.fields = guestClass.getObjectFieldsCount() > 0 ? new Object[guestClass.getObjectFieldsCount()] : null;
        this.primitiveFields = primitiveFieldCount > 0 ? new byte[primitiveFieldCount] : null;
        initFields(guestClass, false);
        setHiddenField(thisKlass.getMeta().HIDDEN_MIRROR_KLASS, thisKlass);
    }

    public StaticObject(ObjectKlass klass, boolean isStatic) {
        assert klass != klass.getMeta().java_lang_Class || isStatic;
        this.klass = klass;
        // assert !isStatic || klass.isInitialized();
        if (isStatic) {
            this.fields = klass.getStaticObjectFieldsCount() > 0 ? new Object[klass.getStaticObjectFieldsCount()] : null;
            this.primitiveFields = klass.getPrimitiveStaticFieldTotalByteCount() > 0 ? new byte[klass.getPrimitiveStaticFieldTotalByteCount()] : null;
        } else {
            this.fields = klass.getObjectFieldsCount() > 0 ? new Object[klass.getObjectFieldsCount()] : null;
            this.primitiveFields = klass.getPrimitiveFieldTotalByteCount() > 0 ? new byte[klass.getPrimitiveFieldTotalByteCount()] : null;
        }
        initFields(klass, isStatic);
    }

    // Use an explicit method to create array, avoids confusion.
    public static StaticObject createArray(ArrayKlass klass, Object array) {
        return new StaticObject(klass, array);
    }

    /**
     * Constructor for Array objects.
     *
     * Current implementation stores the array in lieu of fields. fields being an Object, a char
     * array can be stored under it without any boxing happening. The array could have been stored
     * in fields[0], but getting to the array would then require an additional indirection.
     *
     * Regular objects still always have an Object[] hiding under fields. In order to preserve the
     * behavior and avoid casting to Object[] (a non-leaf cast), we perform field accesses with
     * Unsafe operations.
     */
    public StaticObject(ArrayKlass klass, Object array) {
        this.klass = klass;
        assert klass.isArray();
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        this.fields = array;
        this.primitiveFields = null;
    }

    private final Klass klass;

    public Klass getKlass() {
        return klass;
    }

    public static boolean notNull(StaticObject object) {
        return !isNull(object);
    }

    public boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    // FIXME(peterssen): Klass does not need to be initialized, just prepared?.
    public boolean isStatic() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        if (isNull(this)) {
            return NULL;
        }
        if (getKlass().isArray()) {
            return new StaticObject((ArrayKlass) getKlass(), cloneWrapped());
        } else {
            return new StaticObject((ObjectKlass) getKlass(), fields == null ? null : ((Object[]) fields).clone(), primitiveFields == null ? null : primitiveFields.clone());
        }
    }

    @ExplodeLoop
    private void initFields(ObjectKlass thisKlass, boolean isStatic) {
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        if (isStatic) {
            for (Field f : thisKlass.getStaticFieldTable()) {
                assert f.isStatic();
                if (f.getKind() == JavaKind.Object) {
                    setUnsafeField(f.getFieldIndex(), StaticObject.NULL);
                }
            }
        } else {
            for (Field f : thisKlass.getFieldTable()) {
                assert !f.isStatic();
                if (!f.isHidden()) {
                    if (f.getKind() == JavaKind.Object) {
                        setUnsafeField(f.getFieldIndex(), StaticObject.NULL);
                    }
                }
            }
        }
    }

    // Start non primitive field handling.

    private static long getObjectFieldIndex(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index;
    }

    @TruffleBoundary
    public StaticObject getFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return (StaticObject) UNSAFE.getObjectVolatile(fields, getObjectFieldIndex(field.getFieldIndex()));
    }

    // Not to be used to access hidden fields !
    public StaticObject getField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        Object result;
        if (field.isVolatile()) {
            result = getFieldVolatile(field);
        } else {
            result = getUnsafeField(field.getFieldIndex());
        }
        assert result != null;
        return (StaticObject) result;
    }

    // Use with caution. Can be used with hidden fields
    public Object getUnsafeField(int fieldIndex) {
        return UNSAFE.getObject(fields, getObjectFieldIndex(fieldIndex));
    }

    @TruffleBoundary
    public void setFieldVolatile(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putObjectVolatile(fields, getObjectFieldIndex(field.getFieldIndex()), value);
    }

    public void setField(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        if (field.isVolatile()) {
            setFieldVolatile(field, value);
        } else {
            Object[] fieldArray = castExact(fields, Object[].class);
            fieldArray[field.getFieldIndex()] = value;
        }
    }

    private void setUnsafeField(int index, Object value) {
        UNSAFE.putObject(fields, getObjectFieldIndex(index), value);
    }

    public boolean compareAndSwapField(Field field, Object before, Object after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapObject(fields, getObjectFieldIndex(field.getFieldIndex()), before, after);
    }

    // End non-primitive field handling
    // Start subword field handling

    // Have a getter/Setter pair for each kind of primitive. Though a bit ugly, it avoids a switch
    // when kind is known beforehand.

    private static long getPrimitiveFieldIndex(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) index;
    }

    public boolean getBooleanField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field) != 0;
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex())) != 0;
        }
    }

    @TruffleBoundary
    public byte getByteFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getByteVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public byte getByteField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field);
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    public char getCharField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            return getCharFieldVolatile(field);
        } else {
            return UNSAFE.getChar(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public char getCharFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getCharVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public short getShortField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            return getShortFieldVolatile(field);
        } else {
            return UNSAFE.getShort(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public short getShortFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getShortVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public int getIntField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int;
        if (field.isVolatile()) {
            return getIntFieldVolatile(field);
        } else {
            return UNSAFE.getInt(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public int getIntFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getIntVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public float getFloatField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            return getFloatFieldVolatile(field);
        } else {
            return UNSAFE.getFloat(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public float getFloatFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getFloatVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public double getDoubleField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            return getDoubleFieldVolatile(field);
        } else {
            return UNSAFE.getDouble(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public double getDoubleFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getDoubleVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    // Field setters

    public void setBooleanField(Field field, boolean value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            setBooleanFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), (byte) (value ? 1 : 0));
        }
    }

    @TruffleBoundary
    public void setBooleanFieldVolatile(Field field, boolean value) {
        setByteFieldVolatile(field, (byte) (value ? 1 : 0));
    }

    public void setByteField(Field field, byte value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            setByteFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setByteFieldVolatile(Field field, byte value) {
        UNSAFE.putByteVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setCharField(Field field, char value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            setCharFieldVolatile(field, value);
        } else {
            UNSAFE.putChar(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setCharFieldVolatile(Field field, char value) {
        UNSAFE.putCharVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setShortField(Field field, short value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            setShortFieldVolatile(field, value);
        } else {
            UNSAFE.putShort(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setShortFieldVolatile(Field field, short value) {
        UNSAFE.putShortVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setIntField(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int || field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setIntFieldVolatile(field, value);
        } else {
            UNSAFE.putInt(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setIntFieldVolatile(Field field, int value) {
        UNSAFE.putIntVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setFloatField(Field field, float value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setFloatFieldVolatile(field, value);
        } else {
            UNSAFE.putFloat(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setDoubleFieldVolatile(Field field, double value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putDoubleVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setDoubleField(Field field, double value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            setDoubleFieldVolatile(field, value);
        } else {
            UNSAFE.putDouble(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    @TruffleBoundary
    public void setFloatFieldVolatile(Field field, float value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putFloatVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public boolean compareAndSwapIntField(Field field, int before, int after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapInt(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), before, after);
    }

    // End subword field handling
    // start big words field handling

    @TruffleBoundary
    public long getLongFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getLongVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
    }

    public long getLongField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            return getLongFieldVolatile(field);
        } else {
            return UNSAFE.getLong(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()));
        }
    }

    @TruffleBoundary
    public void setLongFieldVolatile(Field field, long value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putLongVolatile(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
    }

    public void setLongField(Field field, long value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            setLongFieldVolatile(field, value);
        } else {
            UNSAFE.putLong(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), value);
        }
    }

    public boolean compareAndSwapLongField(Field field, long before, long after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapLong(primitiveFields, getPrimitiveFieldIndex(field.getFieldIndex()), before, after);
    }

    // End big words field handling.

    // Given a guest Class, get the corresponding Klass.
    public Klass getMirrorKlass() {
        assert getKlass().getType() == Type.java_lang_Class;
        Klass result = (Klass) getHiddenField(getKlass().getMeta().HIDDEN_MIRROR_KLASS);
        if (result == null) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Uninitialized mirror class");
        }
        return result;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (this == VOID) {
            return "void";
        }
        if (this == NULL) {
            return "null";
        }
        if (getKlass() == getKlass().getMeta().java_lang_String) {
            return Meta.toHostString(this);
        }
        if (isArray()) {
            return unwrap().toString();
        }
        if (getKlass() == getKlass().getMeta().java_lang_Class) {
            return "mirror: " + getMirrorKlass().toString();
        }
        return getKlass().getType().toString();
    }

    @TruffleBoundary
    public String toVerboseString() {
        if (this == VOID) {
            return "void";
        }
        if (this == NULL) {
            return "null";
        }
        if (getKlass() == getKlass().getMeta().java_lang_String) {
            return Meta.toHostString(this);
        }
        if (isArray()) {
            return unwrap().toString();
        }
        if (getKlass() == getKlass().getMeta().java_lang_Class) {
            return "mirror: " + getMirrorKlass().toString();
        }
        StringBuilder str = new StringBuilder(getKlass().getType().toString());
        for (Field f : ((ObjectKlass) getKlass()).getFieldTable()) {
            // Also prints hidden fields
            str.append("\n    ").append(f.getName()).append(": ").append(f.get(this).toString());
        }
        return str.toString();
    }

    public void setHiddenField(Field hiddenField, Object value) {
        assert hiddenField.isHidden();
        setUnsafeField(hiddenField.getFieldIndex(), value);
    }

    public Object getHiddenField(Field hiddenField) {
        assert hiddenField.isHidden();
        return getUnsafeField(hiddenField.getFieldIndex());
    }

    /**
     * Start of Array manipulation.
     */

    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        assert isArray();
        return (T) fields;
    }

    public <T> T get(int index) {
        assert isArray();
        return this.<T[]> unwrap()[index];
    }

    /**
     * Workaround to avoid casting to Object[] in InterpreterToVM (non-leaf type check).
     */
    public void putObject(StaticObject value, int index, Meta meta) {
        assert isArray();
        if (index >= 0 && index < length()) {
            UNSAFE.putObject(fields, getObjectFieldIndex(index), arrayStoreExCheck(value, klass.getComponentType(), meta));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    private static Object arrayStoreExCheck(StaticObject value, Klass componentType, Meta meta) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            throw meta.throwException(meta.java_lang_ArrayStoreException);
        }
    }

    public int length() {
        assert isArray();
        return Array.getLength(fields);
    }

    private Object cloneWrapped() {
        assert isArray();
        if (fields instanceof boolean[]) {
            return this.<boolean[]> unwrap().clone();
        }
        if (fields instanceof byte[]) {
            return this.<byte[]> unwrap().clone();
        }
        if (fields instanceof char[]) {
            return this.<char[]> unwrap().clone();
        }
        if (fields instanceof short[]) {
            return this.<short[]> unwrap().clone();
        }
        if (fields instanceof int[]) {
            return this.<int[]> unwrap().clone();
        }
        if (fields instanceof float[]) {
            return this.<float[]> unwrap().clone();
        }
        if (fields instanceof double[]) {
            return this.<double[]> unwrap().clone();
        }
        if (fields instanceof long[]) {
            return this.<long[]> unwrap().clone();
        }
        return this.<StaticObject[]> unwrap().clone();
    }

    public static StaticObject wrap(StaticObject[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta.java_lang_Object_array, array);
    }

    public static StaticObject wrap(byte[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._byte_array, array);
    }

    public static StaticObject wrap(boolean[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._boolean_array, array);
    }

    public static StaticObject wrap(char[] array) {
        return wrap(array, EspressoLanguage.getCurrentContext().getMeta());
    }

    public static StaticObject wrap(char[] array, Meta meta) {
        return new StaticObject(meta._char_array, array);
    }

    public static StaticObject wrap(short[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._short_array, array);
    }

    public static StaticObject wrap(int[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._int_array, array);
    }

    public static StaticObject wrap(float[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._float_array, array);
    }

    public static StaticObject wrap(double[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._double_array, array);
    }

    public static StaticObject wrap(long[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._long_array, array);
    }

    public static StaticObject wrapPrimitiveArray(Object array) {
        assert array != null;
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (array instanceof boolean[]) {
            return wrap((boolean[]) array);
        }
        if (array instanceof byte[]) {
            return wrap((byte[]) array);
        }
        if (array instanceof char[]) {
            return wrap((char[]) array);
        }
        if (array instanceof short[]) {
            return wrap((short[]) array);
        }
        if (array instanceof int[]) {
            return wrap((int[]) array);
        }
        if (array instanceof float[]) {
            return wrap((float[]) array);
        }
        if (array instanceof double[]) {
            return wrap((double[]) array);
        }
        if (array instanceof long[]) {
            return wrap((long[]) array);
        }
        throw EspressoError.shouldNotReachHere("Not a primitive array " + array);
    }

    public boolean isArray() {
        return getKlass().isArray();
    }

    public static long getArrayByteOffset(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) index;
    }

    static {
        // Assert a byte array has the same representation as a boolean array.
        assert (Unsafe.ARRAY_BYTE_BASE_OFFSET == Unsafe.ARRAY_BOOLEAN_BASE_OFFSET &&
                        Unsafe.ARRAY_BYTE_INDEX_SCALE == Unsafe.ARRAY_BOOLEAN_INDEX_SCALE);
    }

    public void setArrayByte(byte value, int index, Meta meta) {
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            UNSAFE.putByte(fields, getArrayByteOffset(index), value);
        } else {
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public byte getArrayByte(int index, Meta meta) {
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            return UNSAFE.getByte(fields, getArrayByteOffset(index));
        } else {
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public StaticObject getAndSetObject(Field field, StaticObject value) {
        return (StaticObject) UNSAFE.getAndSetObject(fields, getObjectFieldIndex(field.getFieldIndex()), value);
    }
}
