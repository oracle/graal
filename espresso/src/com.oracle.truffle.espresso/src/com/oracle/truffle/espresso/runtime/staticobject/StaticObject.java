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
package com.oracle.truffle.espresso.runtime.staticobject;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.BlockingSupport;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.SharedInterop;

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

    private static final EspressoLock FOREIGN_MARKER = EspressoLock.create(BlockingSupport.UNINTERRUPTIBLE);

    private final Klass klass; // != PrimitiveKlass

    private EspressoLock lockOrForeignMarker;

    // region Constructors
    protected StaticObject(Klass klass) {
        this(klass, false);
    }

    protected StaticObject(Klass klass, boolean isForeign) {
        if (isForeign) {
            // This assignment is visible by all threads as a side-effect of the setting of the
            // final `klass` field in the constructor.
            lockOrForeignMarker = FOREIGN_MARKER;
        }
        this.klass = klass;
    }

    @Override
    @TruffleBoundary
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    // endregion Constructors

    public final boolean isString() {
        return StaticObject.notNull(this) && getKlass() == getKlass().getMeta().java_lang_String;
    }

    public static boolean isNull(StaticObject object) {
        assert object != null;
        // GR-37710: do not call `EspressoLanguage.get(null)`
        assert (object.getKlass() != null) || object == NULL || (object.isForeignObject() &&
                        InteropLibrary.getUncached().isNull(object.rawForeignObject(EspressoLanguage.get(null)))) : "klass can only be null for Espresso null (NULL) and interop nulls";
        return object.getKlass() == null;
    }

    @ExportMessage
    public final Class<?> dispatch() {
        // BaseInterop is context independent. We can assign it directly for null and foreign
        // objects.
        if (isNull(this) || isForeignObject()) {
            return BaseInterop.class;
        }
        if (getKlass().getContext().getLanguage().isShared()) {
            return SharedInterop.class;
        }
        return getKlass().getDispatch();
    }

    public final Klass getKlass() {
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
     *
     * @param context
     */
    @SuppressFBWarnings(value = "DC", justification = "Implementations of EspressoLock have only final and volatile fields")
    public final EspressoLock getLock(EspressoContext context) {
        checkNotForeign();
        if (isNull(this)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("StaticObject.NULL.getLock()");
        }
        EspressoLock l = lockOrForeignMarker;
        if (l == null) {
            synchronized (this) {
                l = lockOrForeignMarker;
                if (l == null) {
                    lockOrForeignMarker = l = EspressoLock.create(context.getBlockingSupport());
                }
            }
        }
        return l;
    }

    public static boolean notNull(StaticObject object) {
        return !isNull(object);
    }

    public final void checkNotForeign() {
        assert checkNotForeignImpl();
    }

    private boolean checkNotForeignImpl() {
        if (isForeignObject()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Unexpected foreign object");
        }
        return true;
    }

    public final boolean isForeignObject() {
        return lockOrForeignMarker == FOREIGN_MARKER;
    }

    public final boolean isEspressoObject() {
        return !isForeignObject();
    }

    public final Object rawForeignObject(EspressoLanguage language) {
        assert isForeignObject();
        return language.getForeignProperty().getObject(this);
    }

    public final EspressoType[] getTypeArguments(EspressoLanguage language) {
        assert isForeignObject();
        return (EspressoType[]) language.getTypeArgumentProperty().getObject(this);
    }

    public final boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    public final long getObjectSize(EspressoLanguage language) {
        if (isNull(this)) {
            return 0L;
        }
        long size = 0L;
        size += JavaKind.Long.getByteCount(); // Klass storage
        size += JavaKind.Long.getByteCount(); // Monitor storage
        if (isForeignObject()) {
            size += JavaKind.Long.getByteCount(); // Foreign storage
            size += JavaKind.Long.getByteCount(); // Type Argument storage
        } else if (getKlass() instanceof ArrayKlass k) {
            JavaKind componentKind = k.getComponentType().getJavaKind();
            size += (long) (componentKind == JavaKind.Object ? JavaKind.Long.getByteCount() : componentKind.getByteCount()) * length(language);
        } else if (getKlass() instanceof ObjectKlass k) {
            size += k.getInstanceSize();
        } else {
            EspressoContext.get(null).getLogger().warning(() -> "Unknown static object with class " + getKlass());
        }
        return size;
    }

    /**
     * Given a guest Class, get the corresponding Klass. This method has the disadvantage of not
     * being able to constant fold the {@link Meta} object that is extracted from {@code this} if
     * {@code this} is not constant. If performance is a concern, rather use
     * {@link #getMirrorKlass(Meta)}, passing a constant {@link Meta} object.
     */
    public final Klass getMirrorKlass() {
        return getMirrorKlass(getKlass().getMeta());
    }

    public final boolean isMirrorKlass() {
        return getKlass().getType() == Types.java_lang_Class && !isStaticStorage();
    }

    /**
     * Same as {@link #getMirrorKlass()}, but passing a {@code meta} argument allows some constant
     * folding, even if {@code this} is not constant.
     */
    public final Klass getMirrorKlass(Meta meta) {
        assert isMirrorKlass();
        checkNotForeign();
        Klass result = (Klass) meta.HIDDEN_MIRROR_KLASS.getHiddenObject(this);
        assert result != null : "Uninitialized mirror class";
        return result;
    }

    @TruffleBoundary
    @Override
    public final String toString() {
        if (this == NULL) {
            return "null";
        }
        if (isForeignObject()) {
            return "foreign object: " + getKlass().getTypeAsString();
        }
        Meta meta = getKlass().getMeta();
        if (getKlass() == meta.java_lang_String) {
            StaticObject value = meta.java_lang_String_value.getObject(this);
            if (value == null || isNull(value)) {
                // Prevents debugger crashes when trying to inspect a string in construction.
                return "<UNINITIALIZED>";
            }
            return Meta.toHostStringStatic(this);
        }
        if (isArray()) {
            return unwrap(meta.getLanguage()).toString();
        }
        if (isStaticStorage()) {
            return "statics: " + getKlass().getType();
        }
        if (isMirrorKlass()) {
            return "mirror: " + getMirrorKlass().getType();
        }
        return getKlass().getType().toString();
    }

    @TruffleBoundary
    public final String toVerboseString() {
        if (this == NULL) {
            return "null";
        }
        if (getKlass() == null) {
            return "foreign object: null";
        }
        if (isForeignObject()) {
            return String.format("foreign object: %s\n%s", getKlass().getTypeAsString(), InteropLibrary.getUncached().toDisplayString(rawForeignObject(getKlass().getContext().getLanguage())));
        }
        Meta meta = getKlass().getMeta();
        if (getKlass() == meta.java_lang_String) {
            StaticObject value = meta.java_lang_String_value.getObject(this);
            if (value == null || isNull(value)) {
                // Prevents debugger crashes when trying to inspect a string in construction.
                return "<UNINITIALIZED>";
            }
            return Meta.toHostStringStatic(this);
        }
        if (isArray()) {
            return unwrap(meta.getLanguage()).toString();
        }
        if (getKlass() == meta.java_lang_Class) {
            return "mirror: " + getMirrorKlass().toString();
        }
        StringBuilder str = new StringBuilder(getKlass().getType().toString());
        for (Field f : ((ObjectKlass) getKlass()).getFieldTable()) {
            // Also prints hidden fields
            if (!f.isRemoved()) {
                str.append("\n    ").append(f.getName()).append(": ").append(f.get(this).toString());
            }
        }
        return str.toString();
    }

    /**
     * Start of Array manipulation.
     */
    private Object getArray(EspressoLanguage language) {
        return language.getArrayProperty().getObject(this);
    }

    /**
     * Returns a Java array based on this static object, which must be a guest array.
     */
    @SuppressWarnings("unchecked")
    public final <T> T unwrap(EspressoLanguage language) {
        checkNotForeign();
        assert isArray();
        return (T) getArray(language);
    }

    public final <T> T get(EspressoLanguage language, int index) {
        checkNotForeign();
        assert isArray();
        return this.<T[]> unwrap(language)[index];
    }

    public final int length(EspressoLanguage language) {
        checkNotForeign();
        assert isArray();
        return Array.getLength(getArray(language));
    }

    public final Object cloneWrappedArray(EspressoLanguage language) {
        checkNotForeign();
        assert isArray();
        Object array = getArray(language);
        if (array instanceof byte[]) {
            return ((byte[]) array).clone();
        }
        if (array instanceof char[]) {
            return ((char[]) array).clone();
        }
        if (array instanceof short[]) {
            return ((short[]) array).clone();
        }
        if (array instanceof int[]) {
            return ((int[]) array).clone();
        }
        if (array instanceof float[]) {
            return ((float[]) array).clone();
        }
        if (array instanceof double[]) {
            return ((double[]) array).clone();
        }
        if (array instanceof long[]) {
            return ((long[]) array).clone();
        }
        return ((Object[]) array).clone();
    }

    public final StaticObject copy(EspressoContext context) {
        return context.getAllocator().copy(this);
    }

    public static StaticObject createForeign(EspressoLanguage language, Klass klass, Object value, InteropLibrary library) {
        return GuestAllocator.createForeign(language, klass, value, library);
    }

    public static StaticObject createForeignException(EspressoContext context, Object value, InteropLibrary library) {
        return context.getAllocator().createForeignException(context, value, library);
    }

    public static StaticObject createForeignNull(EspressoLanguage language, Object value) {
        return GuestAllocator.createForeignNull(language, value);
    }

    public static StaticObject createArray(ArrayKlass klass, Object array, EspressoContext context) {
        return context.getAllocator().wrapArrayAs(klass, array);
    }

    public static StaticObject wrap(StaticObject[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta.java_lang_Object_array, array);
    }

    public static StaticObject wrap(ArrayKlass klass, StaticObject[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(klass, array);
    }

    public static StaticObject wrap(byte[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._byte_array, array);
    }

    public static StaticObject wrap(char[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._char_array, array);
    }

    public static StaticObject wrap(short[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._short_array, array);
    }

    public static StaticObject wrap(int[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._int_array, array);
    }

    public static StaticObject wrap(float[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._float_array, array);
    }

    public static StaticObject wrap(double[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._double_array, array);
    }

    public static StaticObject wrap(long[] array, Meta meta) {
        return meta.getAllocator().wrapArrayAs(meta._long_array, array);
    }

    public static StaticObject wrapPrimitiveArray(Object array, Meta meta) {
        assert array != null;
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (array instanceof boolean[]) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Not a primitive array " + array);
    }

    public final boolean isArray() {
        return !isNull(this) && getKlass().isArray();
    }

    // region Factory interface.
    public interface StaticObjectFactory {
        StaticObject create(Klass klass);

        StaticObject create(Klass klass, boolean isForeign);
    }
    // endregion Factory interface.
}
