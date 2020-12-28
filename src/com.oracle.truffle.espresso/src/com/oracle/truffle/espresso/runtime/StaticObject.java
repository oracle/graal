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
import static com.oracle.truffle.espresso.impl.Klass.STATIC_TO_CLASS;
import static com.oracle.truffle.espresso.runtime.InteropUtils.inSafeIntegerRange;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostByte;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostFloat;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostInt;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostLong;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostShort;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isNegativeZero;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupVirtualMethodNode;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import com.oracle.truffle.espresso.vm.VM;

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

    private static final byte[] FOREIGN_OBJECT_MARKER = new byte[0];

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

    public static final String CLASS_TO_STATIC = "static";

    // region ### is/as checks/conversions

    @ExportMessage
    public boolean isString() {
        return StaticObject.notNull(this) && getKlass() == getKlass().getMeta().java_lang_String;
    }

    @ExportMessage
    public static boolean isNull(StaticObject object) {
        assert object != null;
        assert (object.getKlass() != null) || object == NULL ||
                        (object.isForeignObject() && InteropLibrary.getUncached().isNull(object.rawForeignObject())) : "klass can only be null for Espresso null (NULL) and interop nulls";
        return object.getKlass() == null;
    }

    @ExportMessage
    String asString() throws UnsupportedMessageException {
        checkNotForeign();
        if (!isString()) {
            throw UnsupportedMessageException.create();
        }
        return Meta.toHostStringStatic(this);
    }

    @ExportMessage
    boolean isBoolean() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        return klass == klass.getMeta().java_lang_Boolean;
    }

    @ExportMessage
    boolean asBoolean() throws UnsupportedMessageException {
        checkNotForeign();
        if (!isBoolean()) {
            throw UnsupportedMessageException.create();
        }
        return (boolean) klass.getMeta().java_lang_Boolean_value.get(this);
    }

    @ExportMessage
    boolean isNumber() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Integer || klass == meta.java_lang_Long || klass == meta.java_lang_Float ||
                        klass == meta.java_lang_Double;
    }

    @ExportMessage
    boolean fitsInByte() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        if (isAtMostByte(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Short) {
            short content = getShortField(meta.java_lang_Short_value);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Integer) {
            int content = getIntField(meta.java_lang_Integer_value);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return (byte) content == content && !isNegativeZero(content);
        }
        if (klass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return (byte) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    boolean fitsInShort() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        if (isAtMostShort(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Integer) {
            int content = getIntField(meta.java_lang_Integer_value);
            return (short) content == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            return (short) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return (short) content == content && !isNegativeZero(content);
        }
        if (klass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return (short) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    boolean fitsInInt() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        if (isAtMostInt(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            return (int) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return inSafeIntegerRange(content) && !isNegativeZero(content) && (int) content == content;
        }
        if (klass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return (int) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    boolean fitsInLong() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        if (isAtMostLong(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return inSafeIntegerRange(content) && !isNegativeZero(content) && (long) content == content;
        }
        if (klass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return inSafeIntegerRange(content) && !isNegativeZero(content) && (long) content == content;
        }
        return false;
    }

    @ExportMessage
    boolean fitsInFloat() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        if (isAtMostFloat(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        /*
         * We might lose precision when we convert an int or a long to a float, however, we still
         * perform the conversion. This is consistent with Truffle interop, see GR-22718 for more
         * details.
         */
        if (klass == meta.java_lang_Integer) {
            int content = getIntField(meta.java_lang_Integer_value);
            float floatContent = content;
            return (int) floatContent == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            float floatContent = content;
            return (long) floatContent == content;
        }
        if (klass == meta.java_lang_Double) {
            double content = getDoubleField(meta.java_lang_Double_value);
            return !Double.isFinite(content) || (float) content == content;
        }
        return false;
    }

    @ExportMessage
    boolean fitsInDouble() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }

        Meta meta = klass.getMeta();
        if (isAtMostInt(klass) || klass == meta.java_lang_Double) {
            return true;
        }
        if (klass == meta.java_lang_Long) {
            long content = getLongField(meta.java_lang_Long_value);
            double doubleContent = content;
            return (long) doubleContent == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = getFloatField(meta.java_lang_Float_value);
            return !Float.isFinite(content) || (double) content == content;
        }
        return false;
    }

    private Number readNumberValue() throws UnsupportedMessageException {
        assert isEspressoObject();
        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Byte) {
            return (Byte) meta.java_lang_Byte_value.get(this);
        }
        if (klass == meta.java_lang_Short) {
            return (Short) meta.java_lang_Short_value.get(this);
        }
        if (klass == meta.java_lang_Integer) {
            return (Integer) meta.java_lang_Integer_value.get(this);
        }
        if (klass == meta.java_lang_Long) {
            return (Long) meta.java_lang_Long_value.get(this);
        }
        if (klass == meta.java_lang_Float) {
            return (Float) meta.java_lang_Float_value.get(this);
        }
        if (klass == meta.java_lang_Double) {
            return (Double) meta.java_lang_Double_value.get(this);
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInByte()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().byteValue();
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInShort()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().shortValue();
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInInt()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().intValue();
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInLong()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().longValue();
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInFloat()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().floatValue();
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        checkNotForeign();
        if (!fitsInDouble()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().doubleValue();
    }

    // endregion ### is/as checks/conversions

    // region ### Arrays

    @ExportMessage
    long getArraySize(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (!isArray()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        return length();
    }

    @ExportMessage
    boolean hasArrayElements() {
        checkNotForeign();
        return isArray();
    }

    @ExportMessage
    abstract static class ReadArrayElement {
        @Specialization(guards = {"isBooleanArray(receiver)", "receiver.isEspressoObject()"})
        static boolean doBoolean(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<boolean[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isCharArray(receiver)", "receiver.isEspressoObject()"})
        static char doChar(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<char[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isByteArray(receiver)", "receiver.isEspressoObject()"})
        static byte doByte(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<byte[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isShortArray(receiver)", "receiver.isEspressoObject()"})
        static short doShort(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<short[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isIntArray(receiver)", "receiver.isEspressoObject()"})
        static int doInt(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<int[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isLongArray(receiver)", "receiver.isEspressoObject()"})
        static long doLong(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<long[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isFloatArray(receiver)", "receiver.isEspressoObject()"})
        static float doFloat(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<float[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isDoubleArray(receiver)", "receiver.isEspressoObject()"})
        static double doDouble(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<double[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"receiver.isArray()", "receiver.isEspressoObject()", "!isPrimitiveArray(receiver)"})
        static Object doObject(StaticObject receiver, long index,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return receiver.<Object[]> unwrap()[(int) index];
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object doOther(StaticObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class WriteArrayElement {
        @Specialization(guards = {"isBooleanArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doBoolean(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            boolean boolValue;
            try {
                boolValue = interopLibrary.asBoolean(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<boolean[]> unwrap()[(int) index] = boolValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isCharArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doChar(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            char charValue;
            try {
                String s = interopLibrary.asString(value);
                if (s.length() != 1) {
                    error.enter();
                    String message = "Expected a string of length 1 as an element of char array, got " + s;
                    throw UnsupportedTypeException.create(new Object[]{value}, message);
                }
                charValue = s.charAt(0);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<char[]> unwrap()[(int) index] = charValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isByteArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doByte(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            byte byteValue;
            try {
                byteValue = interopLibrary.asByte(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<byte[]> unwrap()[(int) index] = byteValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isShortArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doShort(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            short shortValue;
            try {
                shortValue = interopLibrary.asShort(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<short[]> unwrap()[(int) index] = shortValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isIntArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doInt(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            int intValue;
            try {
                intValue = interopLibrary.asInt(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<int[]> unwrap()[(int) index] = intValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isLongArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doLong(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            long longValue;
            try {
                longValue = interopLibrary.asLong(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<long[]> unwrap()[(int) index] = longValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isFloatArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doFloat(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            float floatValue;
            try {
                floatValue = interopLibrary.asFloat(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<float[]> unwrap()[(int) index] = floatValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isDoubleArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doDouble(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            double doubleValue;
            try {
                doubleValue = interopLibrary.asDouble(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<double[]> unwrap()[(int) index] = doubleValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isStringArray(receiver)", "receiver.isEspressoObject()"}, limit = "1")
        static void doString(StaticObject receiver, long index, Object value,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            StaticObject stringValue;
            try {
                stringValue = receiver.getKlass().getMeta().toGuestString(interopLibrary.asString(value));
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
            }
            try {
                receiver.<StaticObject[]> unwrap()[(int) index] = stringValue;
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static void doOther(StaticObject receiver, long index, Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    protected static boolean isBooleanArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._boolean_array);
    }

    protected static boolean isCharArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._char_array);
    }

    protected static boolean isByteArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._byte_array);
    }

    protected static boolean isShortArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._short_array);
    }

    protected static boolean isIntArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._int_array);
    }

    protected static boolean isLongArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._long_array);
    }

    protected static boolean isFloatArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._float_array);
    }

    protected static boolean isDoubleArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta()._double_array);
    }

    protected static boolean isStringArray(StaticObject object) {
        return !isNull(object) && object.getKlass().equals(object.getKlass().getMeta().java_lang_String.array());
    }

    protected static boolean isPrimitiveArray(StaticObject object) {
        return isBooleanArray(object) || isCharArray(object) || isByteArray(object) || isShortArray(object) || isIntArray(object) || isLongArray(object) || isFloatArray(object) ||
                        isDoubleArray(object);
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        checkNotForeign();
        if (isArray()) {
            return index >= 0 && index < length();
        }
        return false;
    }

    @ExportMessage
    boolean isArrayElementModifiable(long index) {
        checkNotForeign();
        return isPrimitiveArray(this) && index >= 0 && index < length();
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    boolean isArrayElementInsertable(long index) {
        return false;
    }

    // endregion ### Arrays

    // region ### Members

    @ExportMessage
    Object readMember(String member) throws UnknownIdentifierException {
        checkNotForeign();
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
        checkNotForeign();
        return notNull(this);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        checkNotForeign();
        return notNull(this) && getKlass() == getKlass().getMeta().java_lang_Class //
                        && (CLASS_TO_STATIC.equals(member) || STATIC_TO_CLASS.equals(member));
    }

    private static final String[] CLASS_KEYS = {CLASS_TO_STATIC, STATIC_TO_CLASS};

    private ObjectKlass getInteropKlass() {
        if (getKlass().isArray()) {
            return getKlass().getMeta().java_lang_Object;
        } else {
            assert !getKlass().isPrimitive() : "Static Object should not represent a primitive.";
            return (ObjectKlass) getKlass();
        }
    }

    @TruffleBoundary
    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        checkNotForeign();
        if (isNull(this)) {
            return KeysArray.EMPTY;
        }
        ArrayList<String> members = new ArrayList<>();
        if (getKlass() == getKlass().getMeta().java_lang_Class) {
            // SVM does not like ArrayList.addAll(). Do manual copy.
            for (String s : CLASS_KEYS) {
                members.add(s);
            }
        }
        ObjectKlass k = getInteropKlass();

        for (Method m : k.getVTable()) {
            if (LookupVirtualMethodNode.isCanditate(m)) {
                // Note: If there are overloading, the same key may appear twice.
                // TODO: Cache the keys array in the Klass.
                members.add(m.getNameAsString());
            }
        }
        // SVM does not like ArrayList.toArray(). Do manual copy.
        String[] array = new String[members.size()];
        int pos = 0;
        for (String str : members) {
            array[pos++] = str;
        }
        return new KeysArray(array);
    }

    @ExportMessage
    boolean isMemberInvocable(String member,
                    @Cached.Exclusive @Cached LookupVirtualMethodNode lookupMethod) {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        ObjectKlass k = getInteropKlass();
        return lookupMethod.isInvocable(k, member);
    }

    @ExportMessage
    Object invokeMember(String member,
                    Object[] arguments,
                    @Cached.Exclusive @Cached LookupVirtualMethodNode lookupMethod,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke)
                    throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        Method method = lookupMethod.execute(getKlass(), member, arguments.length);
        if (method != null) {
            assert !method.isStatic() && method.isPublic();
            assert member.startsWith(method.getNameAsString());
            assert method.getParameterCount() == arguments.length;
            return invoke.execute(method, this, arguments);
        }
        throw UnknownIdentifierException.create(member);
    }

    // endregion ### Members

    // region ### Exceptions

    @ExportMessage
    boolean isException() {
        checkNotForeign();
        return !isNull(this) && instanceOf(this, getKlass().getMeta().java_lang_Throwable);
    }

    @ExportMessage
    RuntimeException throwException(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isException()) {
            throw Meta.throwException(this);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Exceptions

    // region ### Meta-objects

    @ExportMessage
    boolean isMetaObject() {
        checkNotForeign();
        return !isNull(this) && getKlass() == getKlass().getMeta().java_lang_Class;
    }

    @ExportMessage
    public Object getMetaQualifiedName(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isMetaObject()) {
            return getKlass().getMeta().java_lang_Class_getTypeName.invokeDirect(this);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    Object getMetaSimpleName(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isMetaObject()) {
            return getKlass().getMeta().java_lang_Class_getSimpleName.invokeDirect(this);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isMetaInstance(Object instance, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isMetaObject()) {
            return instance instanceof StaticObject && instanceOf((StaticObject) instance, getMirrorKlass());
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasMetaObject() {
        checkNotForeign();
        return !isNull(this);
    }

    @ExportMessage
    Object getMetaObject(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (hasMetaObject()) {
            return getKlass().mirror();
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Meta-objects

    // region ### Identity/hashCode

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doStaticObject(StaticObject receiver, StaticObject other) {
            receiver.checkNotForeign();
            other.checkNotForeign();
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(@SuppressWarnings("unused") StaticObject receiver, @SuppressWarnings("unused") Object other) {
            receiver.checkNotForeign();
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    int identityHashCode(@CachedLibrary("this") InteropLibrary thisLibrary, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (thisLibrary.hasIdentity(this)) {
            return VM.JVM_IHashCode(this);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Identity/hashCode

    // region ### Date/time conversions

    @ExportMessage
    boolean isDate() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        Meta meta = getKlass().getMeta();
        return instanceOf(this, meta.java_time_LocalDate) ||
                        instanceOf(this, meta.java_time_LocalDateTime) ||
                        instanceOf(this, meta.java_time_Instant) ||
                        instanceOf(this, meta.java_time_ZonedDateTime) ||
                        instanceOf(this, meta.java_util_Date);
    }

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isDate()) {
            Meta meta = getKlass().getMeta();
            if (instanceOf(this, meta.java_time_LocalDate)) {
                int year = (int) meta.java_time_LocalDate_year.get(this);
                short month = (short) meta.java_time_LocalDate_month.get(this);
                short day = (short) meta.java_time_LocalDate_day.get(this);
                return LocalDate.of(year, month, day);
            } else if (instanceOf(this, meta.java_time_LocalDateTime)) {
                StaticObject localDate = (StaticObject) meta.java_time_LocalDateTime_toLocalDate.invokeDirect(this);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return localDate.asDate(error);
            } else if (instanceOf(this, meta.java_time_Instant)) {
                StaticObject zoneIdUTC = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString("UTC"));
                assert instanceOf(zoneIdUTC, meta.java_time_ZoneId);
                StaticObject zonedDateTime = (StaticObject) meta.java_time_Instant_atZone.invokeDirect(this, zoneIdUTC);
                assert instanceOf(zonedDateTime, meta.java_time_ZonedDateTime);
                StaticObject localDate = (StaticObject) meta.java_time_ZonedDateTime_toLocalDate.invokeDirect(zonedDateTime);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return localDate.asDate(error);
            } else if (instanceOf(this, meta.java_time_ZonedDateTime)) {
                StaticObject localDate = (StaticObject) meta.java_time_ZonedDateTime_toLocalDate.invokeDirect(this);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return localDate.asDate(error);
            } else if (instanceOf(this, meta.java_util_Date)) {
                // return ((Date) obj).toInstant().atZone(UTC).toLocalDate();
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = getKlass().vtableLookup(index);
                StaticObject instant = (StaticObject) virtualToInstant.invokeDirect(this);
                return instant.asDate(error);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isTime() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        Meta meta = getKlass().getMeta();
        return instanceOf(this, meta.java_time_LocalTime) ||
                        instanceOf(this, meta.java_time_Instant) ||
                        instanceOf(this, meta.java_time_ZonedDateTime) ||
                        instanceOf(this, meta.java_util_Date);
    }

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isTime()) {
            Meta meta = getKlass().getMeta();
            if (instanceOf(this, meta.java_time_LocalTime)) {
                byte hour = (byte) meta.java_time_LocalTime_hour.get(this);
                byte minute = (byte) meta.java_time_LocalTime_minute.get(this);
                byte second = (byte) meta.java_time_LocalTime_second.get(this);
                int nano = (int) meta.java_time_LocalTime_nano.get(this);
                return LocalTime.of(hour, minute, second, nano);
            } else if (instanceOf(this, meta.java_time_LocalDateTime)) {
                StaticObject localTime = (StaticObject) meta.java_time_LocalDateTime_toLocalTime.invokeDirect(this);
                return localTime.asTime(error);
            } else if (instanceOf(this, meta.java_time_ZonedDateTime)) {
                StaticObject localTime = (StaticObject) meta.java_time_ZonedDateTime_toLocalTime.invokeDirect(this);
                return localTime.asTime(error);
            } else if (instanceOf(this, meta.java_time_Instant)) {
                // return ((Instant) obj).atZone(UTC).toLocalTime();
                StaticObject zoneIdUTC = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString("UTC"));
                assert instanceOf(zoneIdUTC, meta.java_time_ZoneId);
                StaticObject zonedDateTime = (StaticObject) meta.java_time_Instant_atZone.invokeDirect(this, zoneIdUTC);
                assert instanceOf(zonedDateTime, meta.java_time_ZonedDateTime);
                StaticObject localTime = (StaticObject) meta.java_time_ZonedDateTime_toLocalTime.invokeDirect(zonedDateTime);
                assert instanceOf(localTime, meta.java_time_LocalTime);
                return localTime.asTime(error);
            } else if (instanceOf(this, meta.java_util_Date)) {
                // return ((Date) obj).toInstant().atZone(UTC).toLocalTime();
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = getKlass().vtableLookup(index);
                StaticObject instant = (StaticObject) virtualToInstant.invokeDirect(this);
                return instant.asTime(error);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isTimeZone() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        Meta meta = getKlass().getMeta();
        return instanceOf(this, meta.java_time_ZoneId) ||
                        instanceOf(this, meta.java_time_Instant) ||
                        instanceOf(this, meta.java_time_ZonedDateTime) ||
                        instanceOf(this, meta.java_util_Date);
    }

    @ExportMessage
    @TruffleBoundary
    ZoneId asTimeZone(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isTimeZone()) {
            Meta meta = getKlass().getMeta();
            if (instanceOf(this, meta.java_time_ZoneId)) {
                int index = meta.java_time_ZoneId_getId.getVTableIndex();
                StaticObject zoneIdEspresso = (StaticObject) getKlass().vtableLookup(index).invokeDirect(this);
                String zoneId = Meta.toHostStringStatic(zoneIdEspresso);
                return ZoneId.of(zoneId, ZoneId.SHORT_IDS);
            } else if (instanceOf(this, meta.java_time_ZonedDateTime)) {
                StaticObject zoneId = (StaticObject) meta.java_time_ZonedDateTime_getZone.invokeDirect(this);
                return zoneId.asTimeZone(error);
            } else if (instanceOf(this, meta.java_time_Instant) ||
                            instanceOf(this, meta.java_util_Date)) {
                return ZoneId.of("UTC");
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    Instant asInstant(@CachedLibrary("this") InteropLibrary thisLibrary, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (thisLibrary.isInstant(this)) {
            Meta meta = getKlass().getMeta();
            if (instanceOf(this, meta.java_time_Instant)) {
                long seconds = (long) meta.java_time_Instant_seconds.get(this);
                int nanos = (int) meta.java_time_Instant_nanos.get(this);
                return Instant.ofEpochSecond(seconds, nanos);
            } else if (instanceOf(this, meta.java_time_ZonedDateTime)) {
                StaticObject instant = (StaticObject) meta.java_time_ZonedDateTime_toInstant.invokeDirect(this);
                // Interop library should be compatible.
                assert thisLibrary.accepts(instant);
                return instant.asInstant(thisLibrary, error);
            } else if (instanceOf(this, meta.java_util_Date)) {
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = getKlass().vtableLookup(index);
                StaticObject instant = (StaticObject) virtualToInstant.invokeDirect(this);
                // Interop library should be compatible.
                assert thisLibrary.accepts(instant);
                return instant.asInstant(thisLibrary, error);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isDuration() {
        checkNotForeign();
        if (isNull(this)) {
            return false;
        }
        Meta meta = getKlass().getMeta();
        return instanceOf(this, meta.java_time_Duration);
    }

    @ExportMessage
    Duration asDuration(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        checkNotForeign();
        if (isDuration()) {
            Meta meta = getKlass().getMeta();
            // Avoid expensive calls to Duration.{getSeconds/getNano} by extracting the private
            // fields directly.
            long seconds = (long) meta.java_time_Duration_seconds.get(this);
            int nanos = (int) meta.java_time_Duration_nanos.get(this);
            return Duration.ofSeconds(seconds, nanos);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Date/time conversions

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

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(boolean allowSideEffects) {
        if (isForeignObject()) {
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            try {
                return "Foreign object: " + interopLibrary.asString(interopLibrary.toDisplayString(rawForeignObject(), allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Interop library failed to convert display string to string");
            }
        }
        if (StaticObject.isNull(this)) {
            return "NULL";
        }
        Klass thisKlass = getKlass();
        Meta meta = thisKlass.getMeta();
        if (allowSideEffects) {
            // Call guest toString.
            int toStringIndex = meta.java_lang_Object_toString.getVTableIndex();
            Method toString = thisKlass.vtableLookup(toStringIndex);
            return meta.toHostString((StaticObject) toString.invokeDirect(this));
        }

        // Handle some special instances without side effects.
        if (thisKlass == meta.java_lang_Class) {
            return "class " + thisKlass.getTypeAsString();
        }
        if (thisKlass == meta.java_lang_String) {
            return meta.toHostString(this);
        }
        return thisKlass.getTypeAsString() + "@" + Integer.toHexString(System.identityHashCode(this));
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
            setField(klass.getMeta().java_lang_Class_classLoader, klass.getDefiningClassLoader());
            setModule(klass);
        }
        setHiddenField(klass.getMeta().HIDDEN_MIRROR_KLASS, klass);
        // Will be overriden by JVM_DefineKlass if necessary.
        setHiddenField(klass.getMeta().HIDDEN_PROTECTION_DOMAIN, StaticObject.NULL);
    }

    private void setModule(Klass klass) {
        StaticObject module = klass.module().module();
        if (StaticObject.isNull(module)) {
            if (klass.getRegistries().javaBaseDefined()) {
                setField(klass.getMeta().java_lang_Class_module, klass.getRegistries().getJavaBaseModule().module());
            } else {
                klass.getRegistries().addToFixupList(klass);
            }
        } else {
            setField(klass.getMeta().java_lang_Class_module, module);
        }
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

    public static StaticObject createForeign(Klass klass, Object foreignObject, InteropLibrary interopLibrary) {
        assert foreignObject != null;
        if (interopLibrary.isNull(foreignObject)) {
            return createForeignNull(foreignObject);
        }
        return new StaticObject(klass, foreignObject, null);
    }

    public static StaticObject createForeignNull(Object foreignObject) {
        assert foreignObject != null;
        assert InteropLibrary.getUncached().isNull(foreignObject);
        return new StaticObject(null, foreignObject, null);
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

    // Shallow copy.
    public StaticObject copy() {
        if (isNull(this)) {
            return this;
        }
        checkNotForeign();
        if (getKlass().isArray()) {
            return createArray((ArrayKlass) getKlass(), cloneWrappedArray());
        } else {
            return new StaticObject((ObjectKlass) getKlass(), fields == null ? null : ((Object[]) fields).clone(), primitiveFields == null ? null : primitiveFields.clone());
        }
    }

    @ExplodeLoop
    private void initInstanceFields(ObjectKlass thisKlass) {
        checkNotForeign();
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        for (Field f : thisKlass.getFieldTable()) {
            assert !f.isStatic();
            if (!f.isHidden()) {
                if (f.getKind() == JavaKind.Object) {
                    setUnsafeField(f.getIndex(), StaticObject.NULL);
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
                setUnsafeField(f.getIndex(), StaticObject.NULL);
            }
        }
    }

    // Start non primitive field handling.

    private static long getObjectFieldIndex(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index;
    }

    @TruffleBoundary(allowInlining = true)
    public StaticObject getFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return (StaticObject) UNSAFE.getObjectVolatile(CompilerDirectives.castExact(fields, Object[].class), getObjectFieldIndex(field.getIndex()));
    }

    // Not to be used to access hidden fields !
    public StaticObject getField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        Object result;
        if (field.isVolatile()) {
            result = getFieldVolatile(field);
        } else {
            result = castExact(fields, Object[].class)[field.getIndex()];
        }
        assert result != null;
        return (StaticObject) result;
    }

    // Use with caution. Can be used with hidden fields.
    public Object getUnsafeField(int fieldIndex) {
        checkNotForeign();
        return UNSAFE.getObject(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeField(int index, Object value) {
        checkNotForeign();
        UNSAFE.putObject(fields, getObjectFieldIndex(index), value);
    }

    private Object getUnsafeFieldVolatile(int fieldIndex) {
        checkNotForeign();
        return UNSAFE.getObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeFieldVolatile(int index, Object value) {
        checkNotForeign();
        UNSAFE.putObjectVolatile(fields, getObjectFieldIndex(index), value);
    }

    @TruffleBoundary(allowInlining = true)
    public void setFieldVolatile(Field field, Object value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(field.getIndex()), value);
    }

    public void setField(Field field, Object value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        assert !(value instanceof StaticObject) ||
                        (StaticObject.isNull((StaticObject) value)) ||
                        field.isHidden() ||
                        getKlass().getMeta().resolveSymbolOrFail(field.getType(),
                                        getKlass().getDefiningClassLoader(), getKlass().protectionDomain()) //
                                        .isAssignableFrom(((StaticObject) value).getKlass());
        if (field.isVolatile()) {
            setFieldVolatile(field, value);
        } else {
            Object[] fieldArray = castExact(fields, Object[].class);
            fieldArray[field.getIndex()] = value;
        }
    }

    public boolean compareAndSwapField(Field field, Object before, Object after) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapObject(fields, getObjectFieldIndex(field.getIndex()), before, after);
    }

    // End non-primitive field handling
    // Start subword field handling

    // Have a getter/Setter pair for each kind of primitive. Though a bit ugly, it avoids a switch
    // when kind is known beforehand.

    private static long getPrimitiveFieldOffset(int index) {
        return index;
    }

    public boolean getBooleanField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field) != 0;
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldOffset(field.getIndex())) != 0;
        }
    }

    @TruffleBoundary(allowInlining = true)
    public byte getByteFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getByteVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public byte getByteField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field);
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    public char getCharField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            return getCharFieldVolatile(field);
        } else {
            return UNSAFE.getChar(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public char getCharFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getCharVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public short getShortField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            return getShortFieldVolatile(field);
        } else {
            return UNSAFE.getShort(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    private void checkNotForeign() {
        if (isForeignObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected foreign object");
        }
    }

    @TruffleBoundary(allowInlining = true)
    public short getShortFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getShortVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public int getIntField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int;
        if (field.isVolatile()) {
            return getIntFieldVolatile(field);
        } else {
            return UNSAFE.getInt(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public int getIntFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getIntVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public float getFloatField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            return getFloatFieldVolatile(field);
        } else {
            return UNSAFE.getFloat(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public float getFloatFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getFloatVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public double getDoubleField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            return getDoubleFieldVolatile(field);
        } else {
            return UNSAFE.getDouble(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public double getDoubleFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getDoubleVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    // Field setters

    public void setBooleanField(Field field, boolean value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            setBooleanFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), (byte) (value ? 1 : 0));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setBooleanFieldVolatile(Field field, boolean value) {
        checkNotForeign();
        setByteFieldVolatile(field, (byte) (value ? 1 : 0));
    }

    public void setByteField(Field field, byte value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            setByteFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setByteFieldVolatile(Field field, byte value) {
        checkNotForeign();
        UNSAFE.putByteVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setCharField(Field field, char value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            setCharFieldVolatile(field, value);
        } else {
            UNSAFE.putChar(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setCharFieldVolatile(Field field, char value) {
        checkNotForeign();
        UNSAFE.putCharVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setShortField(Field field, short value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            setShortFieldVolatile(field, value);
        } else {
            UNSAFE.putShort(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setShortFieldVolatile(Field field, short value) {
        checkNotForeign();
        UNSAFE.putShortVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setIntField(Field field, int value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int || field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setIntFieldVolatile(field, value);
        } else {
            UNSAFE.putInt(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setIntFieldVolatile(Field field, int value) {
        checkNotForeign();
        UNSAFE.putIntVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setFloatField(Field field, float value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setFloatFieldVolatile(field, value);
        } else {
            UNSAFE.putFloat(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setDoubleFieldVolatile(Field field, double value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putDoubleVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setDoubleField(Field field, double value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            setDoubleFieldVolatile(field, value);
        } else {
            UNSAFE.putDouble(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setFloatFieldVolatile(Field field, float value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putFloatVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public boolean compareAndSwapIntField(Field field, int before, int after) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapInt(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), before, after);
    }

    // End subword field handling
    // start big words field handling

    @TruffleBoundary(allowInlining = true)
    public long getLongFieldVolatile(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getLongVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
    }

    public long getLongField(Field field) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            return getLongFieldVolatile(field);
        } else {
            return UNSAFE.getLong(primitiveFields, getPrimitiveFieldOffset(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setLongFieldVolatile(Field field, long value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putLongVolatile(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
    }

    public void setLongField(Field field, long value) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            setLongFieldVolatile(field, value);
        } else {
            UNSAFE.putLong(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), value);
        }
    }

    public boolean compareAndSwapLongField(Field field, long before, long after) {
        checkNotForeign();
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapLong(primitiveFields, getPrimitiveFieldOffset(field.getIndex()), before, after);
    }

    // End big words field handling.

    // Given a guest Class, get the corresponding Klass.
    public Klass getMirrorKlass() {
        assert getKlass().getType() == Type.java_lang_Class;
        checkNotForeign();
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
        if (isForeignObject()) {
            return "foreign object: " + getKlass().getTypeAsString();
        }
        if (getKlass() == getKlass().getMeta().java_lang_String) {
            Meta meta = getKlass().getMeta();
            StaticObject value = getField(meta.java_lang_String_value);
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
            StaticObject value = getField(meta.java_lang_String_value);
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
                str.append("\n    ").append(f.getName()).append(": ").append((this.getHiddenField(f)).toString());
            }
        }
        return str.toString();
    }

    public void setHiddenField(Field hiddenField, Object value) {
        checkNotForeign();
        assert hiddenField.isHidden();
        setUnsafeField(hiddenField.getIndex(), value);
    }

    public Object getHiddenField(Field hiddenField) {
        checkNotForeign();
        assert hiddenField.isHidden();
        return getUnsafeField(hiddenField.getIndex());
    }

    public void setHiddenFieldVolatile(Field hiddenField, Object value) {
        checkNotForeign();
        assert hiddenField.isHidden();
        setUnsafeFieldVolatile(hiddenField.getIndex(), value);
    }

    public Object getHiddenFieldVolatile(Field hiddenField) {
        checkNotForeign();
        assert hiddenField.isHidden();
        return getUnsafeFieldVolatile(hiddenField.getIndex());
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
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public void putObjectUnsafe(StaticObject value, int index) {
        UNSAFE.putObject(fields, getObjectFieldIndex(index), value);
    }

    private static StaticObject arrayStoreExCheck(StaticObject value, Klass componentType, Meta meta, BytecodeNode bytecodeNode) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw Meta.throwException(meta.java_lang_ArrayStoreException);
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

    public static StaticObject wrap(ArrayKlass klass, StaticObject[] array) {
        return createArray(klass, array);
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
        return !isNull(this) && getKlass().isArray();
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
        setArrayByte(value, index, meta, null);
    }

    public void setArrayByte(byte value, int index, Meta meta, BytecodeNode bytecodeNode) {
        checkNotForeign();
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            UNSAFE.putByte(fields, getArrayByteOffset(index), value);
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public byte getArrayByte(int index, Meta meta) {
        return getArrayByte(index, meta, null);
    }

    public byte getArrayByte(int index, Meta meta, BytecodeNode bytecodeNode) {
        checkNotForeign();
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            return UNSAFE.getByte(fields, getArrayByteOffset(index));
        } else {
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public StaticObject getAndSetObject(Field field, StaticObject value) {
        checkNotForeign();
        return (StaticObject) UNSAFE.getAndSetObject(fields, getObjectFieldIndex(field.getIndex()), value);
    }
}
