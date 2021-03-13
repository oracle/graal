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
import com.oracle.truffle.espresso.runtime.dispatch.BaseInterop;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

/**
 * Implementation of the Espresso object model.
 *
 * <p>
 * For performance reasons, all guest objects, including arrays, classes and <b>null</b>, are
 * instances of {@link StaticObject}.
 */
@ExportLibrary(DynamicDispatchLibrary.class)
public class StaticObject implements TruffleObject, Cloneable {

    public static final StaticObject[] EMPTY_ARRAY = new StaticObject[0];
    public static final StaticObject NULL = new StaticObject(null);
    public static final String CLASS_TO_STATIC = "static";

    private final Klass klass; // != PrimitiveKlass

    private Object arrayOrForeignObject;

    private boolean isForeign;

    private EspressoLock lock;

    // region Constructors
    protected StaticObject(Klass klass) {
        this.klass = klass;
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
        StaticObject newObj = klass.getLinkedKlass().getShape(false).getFactory().create(klass);
        newObj.initInstanceFields(klass);
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createClass(Klass klass) {
        ObjectKlass guestClass = klass.getMeta().java_lang_Class;
        StaticObject newObj = guestClass.getLinkedKlass().getShape(false).getFactory().create(guestClass);
        newObj.initInstanceFields(guestClass);
        if (klass.getContext().getJavaVersion().modulesEnabled()) {
            klass.getMeta().java_lang_Class_classLoader.setObject(newObj, klass.getDefiningClassLoader());
            newObj.setModule(klass);
        }
        klass.getMeta().HIDDEN_MIRROR_KLASS.setHiddenObject(newObj, klass);
        // Will be overriden by JVM_DefineKlass if necessary.
        klass.getMeta().HIDDEN_PROTECTION_DOMAIN.setHiddenObject(newObj, StaticObject.NULL);
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createStatics(ObjectKlass klass) {
        StaticObject newObj = klass.getLinkedKlass().getShape(true).getFactory().create(klass);
        newObj.initStaticFields(klass);
        return trackAllocation(klass, newObj);
    }

    // Use an explicit method to create array, avoids confusion.
    public static StaticObject createArray(ArrayKlass klass, Object array) {
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        StaticObject newObj = new StaticObject(klass);
        newObj.arrayOrForeignObject = array;
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
        StaticObject newObj = new StaticObject(klass);
        newObj.arrayOrForeignObject = foreignObject;
        newObj.isForeign = true;
        return trackAllocation(klass, newObj);
    }

    public static StaticObject createForeignNull(Object foreignObject) {
        assert foreignObject != null;
        assert InteropLibrary.getUncached().isNull(foreignObject);
        StaticObject newObj = new StaticObject(null);
        newObj.arrayOrForeignObject = foreignObject;
        newObj.isForeign = true;
        return newObj;
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
            obj = (StaticObject) clone();
        }
        return trackAllocation(getKlass(), obj);
    }

    @Override
    @TruffleBoundary
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    private static StaticObject trackAllocation(Klass klass, StaticObject obj) {
        return klass.getContext().trackAllocation(obj);
    }

    // endregion Constructors

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
    public Class<?> dispatch() {
        if (isNull(this) || isForeignObject()) {
            return BaseInterop.class;
        }
        return getKlass().getDispatch();
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
        return isForeign;
    }

    public boolean isEspressoObject() {
        return !isForeignObject();
    }

    public Object rawForeignObject() {
        assert isForeignObject();
        return this.arrayOrForeignObject;
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
        return (T) arrayOrForeignObject;
    }

    public <T> T get(int index) {
        checkNotForeign();
        assert isArray();
        return this.<T[]> unwrap()[index];
    }

    public int length() {
        checkNotForeign();
        assert isArray();
        return Array.getLength(arrayOrForeignObject);
    }

    private Object cloneWrappedArray() {
        checkNotForeign();
        assert isArray();
        if (arrayOrForeignObject instanceof byte[]) {
            return this.<byte[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof char[]) {
            return this.<char[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof short[]) {
            return this.<short[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof int[]) {
            return this.<int[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof float[]) {
            return this.<float[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof double[]) {
            return this.<double[]> unwrap().clone();
        }
        if (arrayOrForeignObject instanceof long[]) {
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

    // region Factory interface.
    public interface StaticObjectFactory {
        StaticObject create(Klass klass);
    }
    // endregion Factory interface.

    public static final class DefaultArrayBasedStaticObject extends StaticObject {
        public final byte[] primitive;
        public final Object[] object;

        private DefaultArrayBasedStaticObject(Klass klass, int primitiveArraySize, int objectArraySize) {
            super(klass);
            primitive = new byte[primitiveArraySize];
            object = new Object[objectArraySize];
        }
    }

    public static final class DefaultArrayBasedStaticObjectFactory implements StaticObjectFactory {
        private final int primitiveArraySize;
        private final int objectArraySize;

        public DefaultArrayBasedStaticObjectFactory(int primitiveArraySize, int objectArraySize) {
            this.primitiveArraySize = primitiveArraySize;
            this.objectArraySize = objectArraySize;
        }

        @Override
        public StaticObject create(Klass klass) {
            return new DefaultArrayBasedStaticObject(klass, primitiveArraySize, objectArraySize);
        }
    }
}
