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
import static com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import static com.oracle.truffle.espresso.impl.Klass.STATIC_TO_CLASS;
import static com.oracle.truffle.espresso.runtime.InteropUtils.*;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
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

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    public static final StaticObject[] EMPTY_ARRAY = new StaticObject[0];

    public static final StaticObject NULL = new StaticObject();

    private volatile EspressoLock lock;

    private final Klass klass; // != PrimitiveKlass

    // Stores non-primitive fields only.
    private final Object fields;

    /**
     * Stores all primitive types contiguously in a single byte array, without any unused bits
     * between prims (except for 7 bits with booleans). In order to quickly reconstruct a long (for
     * example), which would require reading 8 bytes and concatenating them, call Unsafe which can
     * directly read a long.
     */
    private final byte[] primitiveFields;

    // region Interop

    @ExportMessage
    public static boolean isNull(StaticObject object) {
        assert object != null;
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

    @ExportMessage
    public boolean isBoolean() {
        if (isNull(this)) {
            return false;
        }
        return getKlass() == getKlass().getMeta().java_lang_Boolean;
    }

    @ExportMessage
    public boolean asBoolean() throws UnsupportedMessageException {
        if (!isBoolean()) {
            throw UnsupportedMessageException.create();
        }
        return (boolean) getKlass().lookupMethod(Name.booleanValue, Signature._boolean).invokeDirect(this);
    }

    @ExportMessage
    public boolean isNumber() {
        if (isNull(this)) {
            return false;
        }
        Klass thisKlass = getKlass();
        Meta meta = thisKlass.getMeta();
        return thisKlass == meta.java_lang_Byte || thisKlass == meta.java_lang_Short || thisKlass == meta.java_lang_Integer || thisKlass == meta.java_lang_Long || thisKlass == meta.java_lang_Float ||
                        thisKlass == meta.java_lang_Double;
    }

    @ExportMessage
    boolean fitsInByte() {
        if (isNull(this)) {
            return false;
        }
        return isAtMostByte(getKlass());
    }

    @ExportMessage
    boolean fitsInShort() {
        if (isNull(this)) {
            return false;
        }
        return isAtMostShort(getKlass());
    }

    @ExportMessage
    public boolean fitsInInt() {
        if (isNull(this)) {
            return false;
        }
        if (isAtMostInt(getKlass())) {
            return true;
        }
        Klass thisKlass = getKlass();
        Meta meta = thisKlass.getMeta();
        if (thisKlass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            return (int) content == content;
        }
        if (thisKlass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return inIntRange(content) && (int) content == content;
        }
        if (thisKlass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return inIntRange(content) && (int) content == content;
        }
        return false;
    }

    @ExportMessage
    boolean fitsInLong() {
        if (isNull(this)) {
            return false;
        }
        return isAtMostLong(getKlass());
    }

    @ExportMessage
    boolean fitsInFloat() {
        if (isNull(this)) {
            return false;
        }
        return isAtMostFloat(getKlass());
    }

    @ExportMessage
    boolean fitsInDouble() {
        if (isNull(this)) {
            return false;
        }
        if (isAtMostDouble(getKlass())) {
            return true;
        }
        Klass thisKlass = getKlass();
        Meta meta = thisKlass.getMeta();
        if (thisKlass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            return (double) content == content;
        }
        return false;
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        if (!fitsInByte()) {
            throw UnsupportedMessageException.create();
        }
        return (byte) getKlass().lookupMethod(Name.byteValue, Signature._byte).invokeDirect(this);
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        if (!fitsInShort()) {
            throw UnsupportedMessageException.create();
        }
        return (short) getKlass().lookupMethod(Name.shortValue, Signature._short).invokeDirect(this);
    }

    @ExportMessage
    public int asInt() throws UnsupportedMessageException {
        if (!fitsInInt()) {
            throw UnsupportedMessageException.create();
        }
        return (int) getKlass().lookupMethod(Name.intValue, Signature._int).invokeDirect(this);
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (!fitsInLong()) {
            throw UnsupportedMessageException.create();
        }
        return (long) getKlass().lookupMethod(Name.longValue, Signature._long).invokeDirect(this);
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        if (!fitsInFloat()) {
            throw UnsupportedMessageException.create();
        }
        return (float) getKlass().lookupMethod(Name.floatValue, Signature._float).invokeDirect(this);
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (!fitsInDouble()) {
            throw UnsupportedMessageException.create();
        }
        return (double) getKlass().lookupMethod(Name.doubleValue, Signature._double).invokeDirect(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }

    @TruffleBoundary
    @ExportMessage
    Object toDisplayString(boolean allowSideEffects) {
        if (StaticObject.isNull(this)) {
            return "NULL";
        }
        Klass thisKlass = getKlass();

        if (allowSideEffects) {
            // Call guest toString.
            int toStringIndex = thisKlass.getMeta().java_lang_Object_toString.getVTableIndex();
            Method toString = thisKlass.vtableLookup(toStringIndex);
            return Meta.toHostString((StaticObject) toString.invokeDirect(this));
        }

        // Handle some special instances without side effects.
        if (thisKlass == thisKlass.getMeta().java_lang_Class) {
            return "class " + thisKlass.getTypeAsString();
        }
        if (thisKlass == thisKlass.getMeta().java_lang_String) {
            return Meta.toHostString(this);
        }
        return thisKlass.getTypeAsString() + "@" + Integer.toHexString(System.identityHashCode(this));
    }

    public static final String CLASS_TO_STATIC = "static";

    @ExportMessage
    Object readMember(String member) throws UnknownIdentifierException {
        if (notNull(this)) {
            // Class<T>.static == Klass<T>
            if (CLASS_TO_STATIC.equals(member)) {
                if (getKlass() == getKlass().getMeta().java_lang_Class) {
                    return getMirrorKlass();
                }
            }
            // Class<T>.class == Class<T>
            if (STATIC_TO_CLASS.equals(member)) {
                if (getKlass() == getKlass().getMeta().java_lang_Class) {
                    return this;
                }
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean hasMembers() {
        return !isNull(this);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return notNull(this) && getKlass() == getKlass().getMeta().java_lang_Class //
                        && (CLASS_TO_STATIC.equals(member) || STATIC_TO_CLASS.equals(member));
    }

    private static final KeysArray CLASS_MEMBERS = new KeysArray(new String[]{CLASS_TO_STATIC, STATIC_TO_CLASS});

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return (notNull(this) && getKlass() == getKlass().getMeta().java_lang_Class)
                        ? KeysArray.EMPTY
                        : CLASS_MEMBERS; // .static and .class
    }

    // endregion Interop

    // Dedicated constructor for NULL.
    private StaticObject() {
        assert NULL == null : "Only meant for StaticObject.NULL";
        this.klass = null;
        this.fields = null;
        this.primitiveFields = null;
    }

    // Constructor for object copy.
    private StaticObject(ObjectKlass klass, Object[] fields, byte[] primitiveFields) {
        this.klass = klass;
        this.fields = fields;
        this.primitiveFields = primitiveFields;
    }

    // Constructor for regular objects.
    private StaticObject(ObjectKlass klass) {
        assert klass != klass.getMeta().java_lang_Class;
        this.klass = klass;
        this.fields = klass.getObjectFieldsCount() > 0 ? new Object[klass.getObjectFieldsCount()] : null;
        this.primitiveFields = klass.getPrimitiveFieldTotalByteCount() > 0 ? new byte[klass.getPrimitiveFieldTotalByteCount()] : null;
        initInstanceFields(klass);
    }

    // Constructor for Class objects.
    private StaticObject(Klass klass) {
        ObjectKlass guestClass = klass.getMeta().java_lang_Class;
        this.klass = guestClass;
        int primitiveFieldCount = guestClass.getPrimitiveFieldTotalByteCount();
        this.fields = guestClass.getObjectFieldsCount() > 0 ? new Object[guestClass.getObjectFieldsCount()] : null;
        this.primitiveFields = primitiveFieldCount > 0 ? new byte[primitiveFieldCount] : null;
        initInstanceFields(guestClass);
        setHiddenField(klass.getMeta().HIDDEN_MIRROR_KLASS, klass);
    }

    // Constructor for static fields storage.
    private StaticObject(ObjectKlass klass, @SuppressWarnings("unused") Void unused) {
        this.klass = klass;
        this.fields = klass.getStaticObjectFieldsCount() > 0 ? new Object[klass.getStaticObjectFieldsCount()] : null;
        this.primitiveFields = klass.getPrimitiveStaticFieldTotalByteCount() > 0 ? new byte[klass.getPrimitiveStaticFieldTotalByteCount()] : null;
        initStaticFields(klass);
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
    private StaticObject(ArrayKlass klass, Object array) {
        this.klass = klass;
        assert klass.isArray();
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        this.fields = array;
        this.primitiveFields = null;
    }

    public static StaticObject createNew(ObjectKlass klass) {
        assert !klass.isAbstract() && !klass.isInterface();
        return new StaticObject(klass);
    }

    public static StaticObject createClass(Klass klass) {
        return new StaticObject(klass);
    }

    public static StaticObject createStatics(ObjectKlass klass) {
        return new StaticObject(klass, null);
    }

    // Use an explicit method to create array, avoids confusion.
    public static StaticObject createArray(ArrayKlass klass, Object array) {
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        return new StaticObject(klass, array);
    }

    public Klass getKlass() {
        return klass;
    }

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

    public static boolean notNull(StaticObject object) {
        return !isNull(object);
    }

    public boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        if (isNull(this)) {
            return NULL;
        }
        if (getKlass().isArray()) {
            return createArray((ArrayKlass) getKlass(), cloneWrappedArray());
        } else {
            return new StaticObject((ObjectKlass) getKlass(), fields == null ? null : ((Object[]) fields).clone(), primitiveFields == null ? null : primitiveFields.clone());
        }
    }

    @ExplodeLoop
    private void initInstanceFields(ObjectKlass thisKlass) {
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        for (Field f : thisKlass.getFieldTable()) {
            assert !f.isStatic();
            if (!f.isHidden()) {
                if (f.getKind() == JavaKind.Object) {
                    setUnsafeField(f.getFieldIndex(), StaticObject.NULL);
                }
            }
        }
    }

    @ExplodeLoop
    private void initStaticFields(ObjectKlass thisKlass) {
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        for (Field f : thisKlass.getStaticFieldTable()) {
            assert f.isStatic();
            if (f.getKind() == JavaKind.Object) {
                setUnsafeField(f.getFieldIndex(), StaticObject.NULL);
            }
        }
    }

    // Start non primitive field handling.

    private static long getObjectFieldIndex(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index;
    }

    @TruffleBoundary(allowInlining = true)
    public StaticObject getFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return (StaticObject) UNSAFE.getObjectVolatile(CompilerDirectives.castExact(fields, Object[].class), getObjectFieldIndex(field.getFieldIndex()));
    }

    // Not to be used to access hidden fields !
    public StaticObject getField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        Object result;
        if (field.isVolatile()) {
            result = getFieldVolatile(field);
        } else {
            result = castExact(fields, Object[].class)[field.getFieldIndex()];
        }
        assert result != null;
        return (StaticObject) result;
    }

    // Use with caution. Can be used with hidden fields.
    public Object getUnsafeField(int fieldIndex) {
        return UNSAFE.getObject(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeField(int index, Object value) {
        UNSAFE.putObject(fields, getObjectFieldIndex(index), value);
    }

    private Object getUnsafeFieldVolatile(int fieldIndex) {
        return UNSAFE.getObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeFieldVolatile(int index, Object value) {
        UNSAFE.putObjectVolatile(fields, getObjectFieldIndex(index), value);
    }

    @TruffleBoundary(allowInlining = true)
    public void setFieldVolatile(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(field.getFieldIndex()), value);
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    @TruffleBoundary(allowInlining = true)
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

    public void setHiddenFieldVolatile(Field hiddenField, Object value) {
        assert hiddenField.isHidden();
        setUnsafeFieldVolatile(hiddenField.getFieldIndex(), value);
    }

    public Object getHiddenFieldVolatile(Field hiddenField) {
        assert hiddenField.isHidden();
        return getUnsafeFieldVolatile(hiddenField.getFieldIndex());
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
            UNSAFE.putObject(fields, getObjectFieldIndex(index), arrayStoreExCheck(value, ((ArrayKlass) klass).getComponentType(), meta));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    private static Object arrayStoreExCheck(StaticObject value, Klass componentType, Meta meta) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            throw Meta.throwException(meta.java_lang_ArrayStoreException);
        }
    }

    public int length() {
        assert isArray();
        return Array.getLength(fields);
    }

    private Object cloneWrappedArray() {
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

    public static StaticObject wrap(StaticObject[] array, Meta meta) {
        return createArray(meta.java_lang_Object_array, array);
    }

    public static StaticObject wrap(byte[] array, Meta meta) {
        return createArray(meta._byte_array, array);
    }

    public static StaticObject wrap(boolean[] array, Meta meta) {
        return createArray(meta._boolean_array, array);
    }

    public static StaticObject wrap(char[] array, Meta meta) {
        return createArray(meta._char_array, array);
    }

    public static StaticObject wrap(short[] array, Meta meta) {
        return createArray(meta._short_array, array);
    }

    public static StaticObject wrap(int[] array, Meta meta) {
        return createArray(meta._int_array, array);
    }

    public static StaticObject wrap(float[] array, Meta meta) {
        return createArray(meta._float_array, array);
    }

    public static StaticObject wrap(double[] array, Meta meta) {
        return createArray(meta._double_array, array);
    }

    public static StaticObject wrap(long[] array, Meta meta) {
        return createArray(meta._long_array, array);
    }

    public static StaticObject wrapPrimitiveArray(Object array, Meta meta) {
        assert array != null;
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (array instanceof boolean[]) {
            return wrap((boolean[]) array, meta);
        }
        if (array instanceof byte[]) {
            return wrap((byte[]) array, meta);
        }
        if (array instanceof char[]) {
            return wrap((char[]) array, meta);
        }
        if (array instanceof short[]) {
            return wrap((short[]) array, meta);
        }
        if (array instanceof int[]) {
            return wrap((int[]) array, meta);
        }
        if (array instanceof float[]) {
            return wrap((float[]) array, meta);
        }
        if (array instanceof double[]) {
            return wrap((double[]) array, meta);
        }
        if (array instanceof long[]) {
            return wrap((long[]) array, meta);
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
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public byte getArrayByte(int index, Meta meta) {
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            return UNSAFE.getByte(fields, getArrayByteOffset(index));
        } else {
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public StaticObject getAndSetObject(Field field, StaticObject value) {
        return (StaticObject) UNSAFE.getAndSetObject(fields, getObjectFieldIndex(field.getFieldIndex()), value);
    }
}
