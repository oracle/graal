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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
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

    private static final byte[] INTEROP_MARKER = new byte[0];

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
        assert (object.getKlass() != null) || object == NULL ||
                        (object.isInteropObject() && InteropLibrary.getUncached().isNull(object.rawInteropObject())) : "klass can only be null for Espresso null (NULL) and interop nulls";
        return object.getKlass() == null;
    }

    @ExportMessage
    public boolean isString() {
        return StaticObject.notNull(this) && getKlass() == getKlass().getMeta().java_lang_String;
    }

    @ExportMessage
    public String asString() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        return Meta.toHostString(this);
    }

    @ExportMessage
    public boolean isBoolean() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (isNull(this)) {
            return false;
        }
        return klass == klass.getMeta().java_lang_Boolean;
    }

    @ExportMessage
    public boolean asBoolean() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!isBoolean()) {
            throw UnsupportedMessageException.create();
        }
        return (boolean) klass.getMeta().java_lang_Boolean_value.get(this);
    }

    @ExportMessage
    public boolean isNumber() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (isNull(this)) {
            return false;
        }
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Integer || klass == meta.java_lang_Long || klass == meta.java_lang_Float ||
                        klass == meta.java_lang_Double;
    }

    @ExportMessage
    boolean fitsInByte() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
    public boolean fitsInInt() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (isNull(this)) {
            return false;
        }
        if (isAtMostFloat(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        // We might lose precision when we convert an int or a long to a float, however, we
        // still
        // perform the conversion.
        // This is consistent with Truffle interop, see GR-22718 for more details.
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInByte()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().byteValue();
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInShort()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().shortValue();
    }

    @ExportMessage
    public int asInt() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInInt()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().intValue();
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInLong()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().longValue();
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInFloat()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().floatValue();
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!fitsInDouble()) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue().doubleValue();
    }

    @ExportMessage
    long getArraySize(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isInteropObject()) {
            error.enter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (!isArray()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        return length();
    }

    @ExportMessage
    boolean hasArrayElements(@Shared("error") @Cached BranchProfile error) {
        if (isInteropObject()) {
            error.enter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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

    protected static boolean isPrimitiveArray(StaticObject object) {
        return isBooleanArray(object) || isCharArray(object) || isByteArray(object) || isShortArray(object) || isIntArray(object) || isLongArray(object) || isFloatArray(object) ||
                        isDoubleArray(object);
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        if (isArray()) {
            return index >= 0 && index < length();
        }
        return false;
    }

    @ExportMessage
    boolean isArrayElementModifiable(long index) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        return isPrimitiveArray(this) && index >= 0 && index < length();
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    boolean isArrayElementInsertable(long index) {
        return false;
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
        if (isInteropObject()) {
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            try {
                return "Foreign object: " + interopLibrary.asString(interopLibrary.toDisplayString(rawInteropObject(), allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Interop library failed to convert display string to string");
            }
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        return notNull(this);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        return notNull(this) && getKlass() == getKlass().getMeta().java_lang_Class //
                        && (CLASS_TO_STATIC.equals(member) || STATIC_TO_CLASS.equals(member));
    }

    private static final KeysArray CLASS_MEMBERS = new KeysArray(new String[]{CLASS_TO_STATIC, STATIC_TO_CLASS});

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
        return (notNull(this) && getKlass() == getKlass().getMeta().java_lang_Class)
                        ? CLASS_MEMBERS // .static and .class
                        : KeysArray.EMPTY;
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
        this.primitiveFields = lk.getPrimitiveFieldTotalByteCount() > 0 ? new byte[lk.getPrimitiveFieldTotalByteCount()] : null;
        initInstanceFields(klass);
    }

    // Constructor for Class objects.
    private StaticObject(Klass klass) {
        ObjectKlass guestClass = klass.getMeta().java_lang_Class;
        this.klass = guestClass;
        LinkedKlass lgk = guestClass.getLinkedKlass();
        int primitiveFieldCount = lgk.getPrimitiveFieldTotalByteCount();
        this.fields = lgk.getObjectFieldsCount() > 0 ? new Object[lgk.getObjectFieldsCount()] : null;
        this.primitiveFields = primitiveFieldCount > 0 ? new byte[primitiveFieldCount] : null;
        initInstanceFields(guestClass);
        if (klass.getContext().modulesEnabled()) {
            setField(klass.getMeta().java_lang_Class_classLoader, klass.getDefiningClassLoader());
            setModule(klass);
        }
        setHiddenField(klass.getMeta().HIDDEN_MIRROR_KLASS, klass);
    }

    private void setModule(Klass klass) {
        if (klass instanceof ObjectKlass) {
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
    }

    // Constructor for static fields storage.
    private StaticObject(ObjectKlass klass, @SuppressWarnings("unused") Void unused) {
        assert klass != null;
        this.klass = klass;
        LinkedKlass lk = klass.getLinkedKlass();
        this.fields = lk.getStaticObjectFieldsCount() > 0 ? new Object[lk.getStaticObjectFieldsCount()] : null;
        this.primitiveFields = lk.getPrimitiveStaticFieldTotalByteCount() > 0 ? new byte[lk.getPrimitiveStaticFieldTotalByteCount()] : null;
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

    private StaticObject(Klass klass, Object interopObject, @SuppressWarnings("unused") Void unused) {
        this.klass = klass;
        assert interopObject != null;
        this.primitiveFields = INTEROP_MARKER;
        this.fields = interopObject;
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

    public static StaticObject createInterop(Klass klass, Object interopObject) {
        assert interopObject != null;
        if (InteropLibrary.getUncached().isNull(interopObject)) {
            return new StaticObject(null, interopObject, null);
        }
        return new StaticObject(klass, interopObject, null);
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected interop object");
        }
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

    public boolean isInteropObject() {
        return this.primitiveFields == INTEROP_MARKER;
    }

    public boolean isEspressoObject() {
        return !isInteropObject();
    }

    public Object rawInteropObject() {
        assert isInteropObject();
        return this.fields;
    }

    public boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return (StaticObject) UNSAFE.getObjectVolatile(CompilerDirectives.castExact(fields, Object[].class), getObjectFieldIndex(field.getIndex()));
    }

    // Not to be used to access hidden fields !
    public StaticObject getField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        return UNSAFE.getObject(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeField(int index, Object value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putObject(fields, getObjectFieldIndex(index), value);
    }

    private Object getUnsafeFieldVolatile(int fieldIndex) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        return UNSAFE.getObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(fieldIndex));
    }

    private void setUnsafeFieldVolatile(int index, Object value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putObjectVolatile(fields, getObjectFieldIndex(index), value);
    }

    @TruffleBoundary(allowInlining = true)
    public void setFieldVolatile(Field field, Object value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putObjectVolatile(castExact(fields, Object[].class), getObjectFieldIndex(field.getIndex()), value);
    }

    public void setField(Field field, Object value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        assert !(value instanceof StaticObject) ||
                        (StaticObject.isNull((StaticObject) value)) ||
                        field.isHidden() ||
                        getKlass().getMeta().resolveSymbolOrFail(field.getType(), getKlass().getDefiningClassLoader()) //
                                        .isAssignableFrom(((StaticObject) value).getKlass());
        if (field.isVolatile()) {
            setFieldVolatile(field, value);
        } else {
            Object[] fieldArray = castExact(fields, Object[].class);
            fieldArray[field.getIndex()] = value;
        }
    }

    public boolean compareAndSwapField(Field field, Object before, Object after) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapObject(fields, getObjectFieldIndex(field.getIndex()), before, after);
    }

    // End non-primitive field handling
    // Start subword field handling

    // Have a getter/Setter pair for each kind of primitive. Though a bit ugly, it avoids a switch
    // when kind is known beforehand.

    private static long getPrimitiveFieldIndex(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) index;
    }

    public boolean getBooleanField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field) != 0;
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldIndex(field.getIndex())) != 0;
        }
    }

    @TruffleBoundary(allowInlining = true)
    public byte getByteFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getByteVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public byte getByteField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field);
        } else {
            return UNSAFE.getByte(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    public char getCharField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            return getCharFieldVolatile(field);
        } else {
            return UNSAFE.getChar(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public char getCharFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getCharVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public short getShortField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            return getShortFieldVolatile(field);
        } else {
            return UNSAFE.getShort(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public short getShortFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getShortVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public int getIntField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int;
        if (field.isVolatile()) {
            return getIntFieldVolatile(field);
        } else {
            return UNSAFE.getInt(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public int getIntFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getIntVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public float getFloatField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            return getFloatFieldVolatile(field);
        } else {
            return UNSAFE.getFloat(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public float getFloatFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getFloatVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public double getDoubleField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            return getDoubleFieldVolatile(field);
        } else {
            return UNSAFE.getDouble(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public double getDoubleFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getDoubleVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    // Field setters

    public void setBooleanField(Field field, boolean value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            setBooleanFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), (byte) (value ? 1 : 0));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setBooleanFieldVolatile(Field field, boolean value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        setByteFieldVolatile(field, (byte) (value ? 1 : 0));
    }

    public void setByteField(Field field, byte value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            setByteFieldVolatile(field, value);
        } else {
            UNSAFE.putByte(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setByteFieldVolatile(Field field, byte value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putByteVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setCharField(Field field, char value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            setCharFieldVolatile(field, value);
        } else {
            UNSAFE.putChar(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setCharFieldVolatile(Field field, char value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putCharVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setShortField(Field field, short value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            setShortFieldVolatile(field, value);
        } else {
            UNSAFE.putShort(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setShortFieldVolatile(Field field, short value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putShortVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setIntField(Field field, int value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int || field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setIntFieldVolatile(field, value);
        } else {
            UNSAFE.putInt(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setIntFieldVolatile(Field field, int value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        UNSAFE.putIntVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setFloatField(Field field, float value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            setFloatFieldVolatile(field, value);
        } else {
            UNSAFE.putFloat(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setDoubleFieldVolatile(Field field, double value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putDoubleVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setDoubleField(Field field, double value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Double;
        if (field.isVolatile()) {
            setDoubleFieldVolatile(field, value);
        } else {
            UNSAFE.putDouble(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setFloatFieldVolatile(Field field, float value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putFloatVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public boolean compareAndSwapIntField(Field field, int before, int after) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapInt(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), before, after);
    }

    // End subword field handling
    // start big words field handling

    @TruffleBoundary(allowInlining = true)
    public long getLongFieldVolatile(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.getLongVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
    }

    public long getLongField(Field field) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            return getLongFieldVolatile(field);
        } else {
            return UNSAFE.getLong(primitiveFields, getPrimitiveFieldIndex(field.getIndex()));
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setLongFieldVolatile(Field field, long value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        UNSAFE.putLongVolatile(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
    }

    public void setLongField(Field field, long value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            setLongFieldVolatile(field, value);
        } else {
            UNSAFE.putLong(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), value);
        }
    }

    public boolean compareAndSwapLongField(Field field, long before, long after) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return UNSAFE.compareAndSwapLong(primitiveFields, getPrimitiveFieldIndex(field.getIndex()), before, after);
    }

    // End big words field handling.

    // Given a guest Class, get the corresponding Klass.
    public Klass getMirrorKlass() {
        assert getKlass().getType() == Type.java_lang_Class;
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            return "interop object: " + getKlass().getTypeAsString();
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
        if (isInteropObject()) {
            return String.format("interop object: %s\n%s", getKlass().getTypeAsString(), InteropLibrary.getUncached().toDisplayString(rawInteropObject()));
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert hiddenField.isHidden();
        setUnsafeField(hiddenField.getIndex(), value);
    }

    public Object getHiddenField(Field hiddenField) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert hiddenField.isHidden();
        return getUnsafeField(hiddenField.getIndex());
    }

    public void setHiddenFieldVolatile(Field hiddenField, Object value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert hiddenField.isHidden();
        setUnsafeFieldVolatile(hiddenField.getIndex(), value);
    }

    public Object getHiddenFieldVolatile(Field hiddenField) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert hiddenField.isHidden();
        return getUnsafeFieldVolatile(hiddenField.getIndex());
    }

    /**
     * Start of Array manipulation.
     */

    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert isArray();
        return (T) fields;
    }

    public <T> T get(int index) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert isArray();
        return this.<T[]> unwrap()[index];
    }

    /**
     * Workaround to avoid casting to Object[] in InterpreterToVM (non-leaf type check).
     */
    public void putObject(StaticObject value, int index, Meta meta) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert isArray();
        return Array.getLength(fields);
    }

    private Object cloneWrappedArray() {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
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
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            UNSAFE.putByte(fields, getArrayByteOffset(index), value);
        } else {
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public byte getArrayByte(int index, Meta meta) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        assert isArray() && (fields instanceof byte[] || fields instanceof boolean[]);
        if (index >= 0 && index < length()) {
            return UNSAFE.getByte(fields, getArrayByteOffset(index));
        } else {
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    public StaticObject getAndSetObject(Field field, StaticObject value) {
        if (isInteropObject()) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("Unexpected wrapped interop object");
        }
        return (StaticObject) UNSAFE.getAndSetObject(fields, getObjectFieldIndex(field.getIndex()), value);
    }
}
