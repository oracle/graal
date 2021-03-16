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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.dispatch.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;
import com.oracle.truffle.espresso.runtime.dispatch.IteratorInterop;
import com.oracle.truffle.espresso.runtime.dispatch.ListInterop;
import com.oracle.truffle.espresso.runtime.dispatch.MapInterop;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

/**
 * Implementation of the Espresso object model.
 *
 * <p>
 * For performance reasons, all guest objects, including arrays, classes and <b>null</b>, are
 * instances of {@link StaticObject}.
 */
@ExportLibrary(DynamicDispatchLibrary.class)
public final class StaticObject implements TruffleObject {

    public static final StaticObject[] EMPTY_ARRAY = new StaticObject[0];
    public static final StaticObject NULL = new StaticObject();
    public static final String CLASS_TO_STATIC = "static";

    private static final Unsafe UNSAFE = UnsafeAccess.get();
    private static final byte[] FOREIGN_OBJECT_MARKER = new byte[0];

    private final Klass klass; // != PrimitiveKlass

    /**
     * Stores non-primitive fields only.
     */
    private final Object fields;

    /**
     * Stores all primitive types contiguously in a single byte array, without any unused bits
     * between prims (except for 7 bits with booleans). In order to quickly reconstruct a long (for
     * example), which would require reading 8 bytes and concatenating them, call Unsafe which can
     * directly read a long.
     */
    private final byte[] primitiveFields;

    private volatile EspressoLock lock;

    static {
        // Assert a byte array has the same representation as a boolean array.
        assert (Unsafe.ARRAY_BYTE_BASE_OFFSET == Unsafe.ARRAY_BOOLEAN_BASE_OFFSET &&
                        Unsafe.ARRAY_BYTE_INDEX_SCALE == Unsafe.ARRAY_BOOLEAN_INDEX_SCALE);
    }

    // region Constructors

    // Dedicated constructor for NULL.
    private StaticObject() {
        assert NULL == null : "Only meant for StaticObject.NULL";
        this.klass = null;
        this.fields = null;
        this.primitiveFields = null;
    }

    // Constructor for object copy.
    private StaticObject(ObjectKlass klass, Object[] fields, byte[] primitiveFields) {
        assert klass != null;
        this.klass = klass;
        this.fields = fields;
        this.primitiveFields = primitiveFields;
    }

    // Constructor for regular objects.
    private StaticObject(ObjectKlass klass) {
        assert klass != null;
        assert klass != klass.getMeta().java_lang_Class;
        this.klass = klass;
        LinkedKlass lk = klass.getLinkedKlass();
        this.fields = lk.getObjectFieldsCount() > 0 ? new Object[lk.getObjectFieldsCount()] : null;
        int toAlloc = lk.getInstancePrimitiveToAlloc();
        this.primitiveFields = toAlloc > 0 ? new byte[toAlloc] : null;
        initInstanceFields(klass);
    }

    // Constructor for Class objects.
    private StaticObject(Klass klass) {
        ObjectKlass guestClass = klass.getMeta().java_lang_Class;
        this.klass = guestClass;
        LinkedKlass lgk = guestClass.getLinkedKlass();
        this.fields = lgk.getObjectFieldsCount() > 0 ? new Object[lgk.getObjectFieldsCount()] : null;
        int primitiveFieldCount = lgk.getInstancePrimitiveToAlloc();
        this.primitiveFields = primitiveFieldCount > 0 ? new byte[primitiveFieldCount] : null;
        initInstanceFields(guestClass);
        if (klass.getContext().getJavaVersion().modulesEnabled()) {
            klass.getMeta().java_lang_Class_classLoader.setObject(this, klass.getDefiningClassLoader());
            setModule(klass);
        }
        klass.getMeta().HIDDEN_MIRROR_KLASS.setHiddenObject(this, klass);
        // Will be overriden by JVM_DefineKlass if necessary.
        klass.getMeta().HIDDEN_PROTECTION_DOMAIN.setHiddenObject(this, StaticObject.NULL);
    }

    // Constructor for static fields storage.
    private StaticObject(ObjectKlass klass, @SuppressWarnings("unused") Void unused) {
        assert klass != null;
        this.klass = klass;
        LinkedKlass lk = klass.getLinkedKlass();
        this.fields = lk.getStaticObjectFieldsCount() > 0 ? new Object[lk.getStaticObjectFieldsCount()] : null;
        int toAlloc = lk.getStaticPrimitiveToAlloc();
        this.primitiveFields = toAlloc > 0 ? new byte[toAlloc] : null;
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

    private StaticObject(Klass klass, Object foreignObject, @SuppressWarnings("unused") Void unused) {
        this.klass = klass;
        assert foreignObject != null;
        assert !(foreignObject instanceof StaticObject) : "Espresso objects cannot be wrapped";
        this.primitiveFields = FOREIGN_OBJECT_MARKER;
        this.fields = foreignObject;
    }

    @ExplodeLoop
    private void initInstanceFields(ObjectKlass thisKlass) {
        checkNotForeign();
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        for (Field f : thisKlass.getFieldTable()) {
            assert !f.isStatic();
            if (!f.isHidden()) {
                if (f.getKind() == JavaKind.Object) {
                    f.setObject(this, StaticObject.NULL);
                }
            }
        }
    }

    @ExplodeLoop
    private void initStaticFields(ObjectKlass thisKlass) {
        checkNotForeign();
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        for (Field f : thisKlass.getStaticFieldTable()) {
            assert f.isStatic();
            if (f.getKind() == JavaKind.Object) {
                f.setObject(this, StaticObject.NULL);
            }
        }
    }

    public static StaticObject createNew(ObjectKlass klass) {
        assert !klass.isAbstract() && !klass.isInterface();
        StaticObject newObj = new StaticObject(klass);
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createClass(Klass klass) {
        StaticObject newObj = new StaticObject(klass);
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createStatics(ObjectKlass klass) {
        StaticObject newObj = new StaticObject(klass, null);
        return trackAllocation(klass, newObj);
    }

    // Use an explicit method to create array, avoids confusion.
    public static StaticObject createArray(ArrayKlass klass, Object array) {
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        StaticObject newObj = new StaticObject(klass, array);
        return trackAllocation(klass, newObj);
    }

    /**
     * Wraps a foreign {@link InteropLibrary#isException(Object) exception} as a guest
     * ForeignException.
     */
    public static @Host(typeName = "Lcom/oracle/truffle/espresso/polyglot/ForeignException;") StaticObject createForeignException(Meta meta, Object foreignObject, InteropLibrary interopLibrary) {
        assert meta.polyglot != null;
        assert meta.getContext().Polyglot;
        assert interopLibrary.isException(foreignObject);
        assert !(foreignObject instanceof StaticObject);
        return createForeign(meta.polyglot.ForeignException, foreignObject, interopLibrary);
    }

    public static StaticObject createForeign(Klass klass, Object foreignObject, InteropLibrary interopLibrary) {
        assert foreignObject != null;
        if (interopLibrary.isNull(foreignObject)) {
            return createForeignNull(foreignObject);
        }
        StaticObject newObj = new StaticObject(klass, foreignObject, null);
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createForeignNull(Object foreignObject) {
        assert foreignObject != null;
        assert InteropLibrary.getUncached().isNull(foreignObject);
        return new StaticObject(null, foreignObject, null);
    }

    // Shallow copy.
    public StaticObject copy() {
        if (isNull(this)) {
            return this;
        }
        checkNotForeign();
        StaticObject obj;
        if (getKlass().isArray()) {
            obj = createArray((ArrayKlass) getKlass(), cloneWrappedArray());
        } else {
            obj = new StaticObject((ObjectKlass) getKlass(), fields == null ? null : ((Object[]) fields).clone(), primitiveFields == null ? null : primitiveFields.clone());
        }
        return trackAllocation(getKlass(), obj);
    }

    private static StaticObject trackAllocation(Klass klass, StaticObject obj) {
        return klass.getContext().trackAllocation(obj);
    }

    // endregion Constructors

    // region Accessors for field accesses of non-array, non-foreign objects
    public Object[] getObjectFieldStorage() {
        assert !isArray();
        checkNotForeign();
        return castExact(fields, Object[].class);
    }

    public byte[] getPrimitiveFieldStorage() {
        assert !isArray();
        checkNotForeign();
        return primitiveFields;
    }
    // endregion Accessors for field accesses of non-array, non-foreign objects

    public boolean isString() {
        return StaticObject.notNull(this) && getKlass() == getKlass().getMeta().java_lang_String;
    }

    public static boolean isNull(StaticObject object) {
        assert object != null;
        assert (object.getKlass() != null) || object == NULL ||
                        (object.isForeignObject() && InteropLibrary.getUncached().isNull(object.rawForeignObject())) : "klass can only be null for Espresso null (NULL) and interop nulls";
        return object.getKlass() == null;
    }

    @ExportMessage
    static class Dispatch {
        static final int LIMIT = 4;

        @Specialization(guards = "receiver.isForeignObject()")
        static Class<?> dispatchForeign(@SuppressWarnings("unused") StaticObject receiver) {
            return BaseInterop.class;
        }

        @Specialization(guards = {
                        "receiver.isEspressoObject()",
                        "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
        static Class<?> dispatchCached(
                        @SuppressWarnings("unused") StaticObject receiver,
                        @SuppressWarnings("unused") @Cached(value = "receiver.getKlass()", allowUncached = true) Klass cachedKlass,
                        @Cached(value = "dispatchGeneric(receiver)", allowUncached = true) Class<?> library) {
            return library;
        }

        @Specialization
        static Class<?> dispatchGeneric(StaticObject receiver) {
            if (isNull(receiver) || receiver.isForeignObject()) {
                return BaseInterop.class;
            }
            Meta meta = receiver.getKlass().getMeta();
            if (InterpreterToVM.instanceOf(receiver, meta.java_util_List)) {
                return ListInterop.class;
            }
            if (InterpreterToVM.instanceOf(receiver, meta.java_util_Map)) {
                return MapInterop.class;
            }
            if (InterpreterToVM.instanceOf(receiver, meta.java_util_Iterator)) {
                return IteratorInterop.class;
            }
            // if (InterpreterToVM.instanceOf(receiver, meta.groovy_lang_GroovyObject)) {
            // return GroovyObjectInterop.class;
            // }

            // Default
            return EspressoInterop.class;
        }
    }

    private void setModule(Klass klass) {
        StaticObject module = klass.module().module();
        if (StaticObject.isNull(module)) {
            if (klass.getRegistries().javaBaseDefined()) {
                klass.getMeta().java_lang_Class_module.setObject(this, klass.getRegistries().getJavaBaseModule().module());
            } else {
                klass.getRegistries().addToFixupList(klass);
            }
        } else {
            klass.getMeta().java_lang_Class_module.setObject(this, module);
        }
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
        checkNotForeign();
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

    public void checkNotForeign() {
        if (isForeignObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected foreign object");
        }
    }

    public boolean isForeignObject() {
        return this.primitiveFields == FOREIGN_OBJECT_MARKER;
    }

    public boolean isEspressoObject() {
        return !isForeignObject();
    }

    public Object rawForeignObject() {
        assert isForeignObject();
        return this.fields;
    }

    public boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    // Given a guest Class, get the corresponding Klass.
    public Klass getMirrorKlass() {
        assert getKlass().getType() == Type.java_lang_Class;
        checkNotForeign();
        Klass result = (Klass) getKlass().getMeta().HIDDEN_MIRROR_KLASS.getHiddenObject(this);
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
        if (isForeignObject()) {
            return "foreign object: " + getKlass().getTypeAsString();
        }
        if (getKlass() == getKlass().getMeta().java_lang_String) {
            Meta meta = getKlass().getMeta();
            StaticObject value = meta.java_lang_String_value.getObject(this);
            if (value == null || isNull(value)) {
                // Prevents debugger crashes when trying to inspect a string in construction.
                return "<UNINITIALIZED>";
            }
            return Meta.toHostStringStatic(this);
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
        if (isForeignObject()) {
            return String.format("foreign object: %s\n%s", getKlass().getTypeAsString(), InteropLibrary.getUncached().toDisplayString(rawForeignObject()));
        }
        if (getKlass() == getKlass().getMeta().java_lang_String) {
            Meta meta = getKlass().getMeta();
            StaticObject value = meta.java_lang_String_value.getObject(this);
            if (value == null || isNull(value)) {
                // Prevents debugger crashes when trying to inspect a string in construction.
                return "<UNINITIALIZED>";
            }
            return Meta.toHostStringStatic(this);
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
            if (!f.isHidden()) {
                str.append("\n    ").append(f.getName()).append(": ").append(f.get(this).toString());
            } else {
                str.append("\n    ").append(f.getName()).append(": ").append((f.getHiddenObject(this)).toString());
            }
        }
        return str.toString();
    }

    /**
     * Start of Array manipulation.
     */

    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        checkNotForeign();
        assert isArray();
        return (T) fields;
    }

    public <T> T get(int index) {
        checkNotForeign();
        assert isArray();
        return this.<T[]> unwrap()[index];
    }

    public void putObjectUnsafe(StaticObject value, int index) {
        UNSAFE.putObject(fields, (long) getObjectArrayOffset(index), value);
    }

    public void putObject(StaticObject value, int index, Meta meta) {
        putObject(value, index, meta, null);
    }

    /**
     * Workaround to avoid casting to Object[] in InterpreterToVM (non-leaf type check).
     */
    public void putObject(StaticObject value, int index, Meta meta, BytecodeNode bytecodeNode) {
        checkNotForeign();
        assert isArray();
        if (index >= 0 && index < length()) {
            // TODO(peterssen): Use different profiles for index-out-of-bounds and array-store
            // exceptions.
            putObjectUnsafe(arrayStoreExCheck(value, ((ArrayKlass) klass).getComponentType(), meta, bytecodeNode), index);
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    private static StaticObject arrayStoreExCheck(StaticObject value, Klass componentType, Meta meta, BytecodeNode bytecodeNode) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw meta.throwException(meta.java_lang_ArrayStoreException);
        }
    }

    public static int getObjectArrayOffset(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    public static long getByteArrayOffset(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) index;
    }

    public void setArrayByte(byte value, int index, Meta meta) {
        setArrayByte(value, index, meta, null);
    }

    public void setArrayByte(byte value, int index, Meta meta, BytecodeNode bytecodeNode) {
        checkNotForeign();
        assert isArray() && fields instanceof byte[];
        if (index >= 0 && index < length()) {
            UNSAFE.putByte(fields, getByteArrayOffset(index), value);
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public byte getArrayByte(int index, Meta meta) {
        return getArrayByte(index, meta, null);
    }

    public byte getArrayByte(int index, Meta meta, BytecodeNode bytecodeNode) {
        checkNotForeign();
        assert isArray() && fields instanceof byte[];
        if (index >= 0 && index < length()) {
            return UNSAFE.getByte(fields, getByteArrayOffset(index));
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public int length() {
        checkNotForeign();
        assert isArray();
        return Array.getLength(fields);
    }

    private Object cloneWrappedArray() {
        checkNotForeign();
        assert isArray();
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

    public static StaticObject wrap(ArrayKlass klass, StaticObject[] array) {
        return createArray(klass, array);
    }

    public static StaticObject wrap(byte[] array, Meta meta) {
        return createArray(meta._byte_array, array);
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
            throw EspressoError.shouldNotReachHere("Cannot wrap a boolean[]. Create a byte[] and call `StaticObject.createArray(meta._boolean_array, byteArray)`.");
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
        return !isNull(this) && getKlass().isArray();
    }
}
