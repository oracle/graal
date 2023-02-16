/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime.dispatch;

import static com.oracle.truffle.espresso.impl.Klass.STATIC_TO_CLASS;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostByte;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostFloat;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostInt;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostLong;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isAtMostShort;
import static com.oracle.truffle.espresso.runtime.InteropUtils.isNegativeZero;
import static com.oracle.truffle.espresso.runtime.StaticObject.CLASS_TO_STATIC;
import static com.oracle.truffle.espresso.runtime.StaticObject.notNull;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.EmptyKeysArray;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.CandidateMethodWithArgs;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupInstanceFieldNode;
import com.oracle.truffle.espresso.nodes.interop.LookupVirtualMethodNode;
import com.oracle.truffle.espresso.nodes.interop.MethodArgsUtils;
import com.oracle.truffle.espresso.nodes.interop.OverLoadedMethodSelectorNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoFunction;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BaseInterop (isNull, is/asString, meta-instance, identity, exceptions, toDisplayString) Support
 * Espresso and foreign objects and null.
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class EspressoInterop extends BaseInterop {
    // region ### is/as checks/conversions

    static final Object[] EMPTY_ARGS = new Object[]{};

    public static Meta getMeta() {
        CompilerAsserts.neverPartOfCompilation();
        return EspressoContext.get(null).getMeta();
    }

    @ExportMessage
    static boolean isBoolean(StaticObject receiver) {
        if (receiver.isForeignObject()) {
            return false;
        }
        if (isNull(receiver)) {
            return false;
        }
        return receiver.getKlass() == receiver.getKlass().getMeta().java_lang_Boolean;
    }

    @ExportMessage
    static boolean asBoolean(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!isBoolean(receiver)) {
            throw UnsupportedMessageException.create();
        }
        return (boolean) receiver.getKlass().getMeta().java_lang_Boolean_value.get(receiver);
    }

    @ExportMessage
    static boolean isNumber(StaticObject receiver) {
        if (receiver.isForeignObject()) {
            return false;
        }
        if (isNull(receiver)) {
            return false;
        }
        Meta meta = receiver.getKlass().getMeta();
        return receiver.getKlass() == meta.java_lang_Byte || receiver.getKlass() == meta.java_lang_Short || receiver.getKlass() == meta.java_lang_Integer ||
                        receiver.getKlass() == meta.java_lang_Long || receiver.getKlass() == meta.java_lang_Float ||
                        receiver.getKlass() == meta.java_lang_Double;
    }

    @ExportMessage
    static boolean fitsInByte(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
        if (isAtMostByte(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Short) {
            short content = meta.java_lang_Short_value.getShort(receiver);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Integer) {
            int content = meta.java_lang_Integer_value.getInt(receiver);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = meta.java_lang_Long_value.getLong(receiver);
            return (byte) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = meta.java_lang_Float_value.getFloat(receiver);
            return (byte) content == content && !isNegativeZero(content);
        }
        if (klass == meta.java_lang_Double) {
            double content = meta.java_lang_Double_value.getDouble(receiver);
            return (byte) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInShort(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
        if (isAtMostShort(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Integer) {
            int content = meta.java_lang_Integer_value.getInt(receiver);
            return (short) content == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = meta.java_lang_Long_value.getLong(receiver);
            return (short) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = meta.java_lang_Float_value.getFloat(receiver);
            return (short) content == content && !isNegativeZero(content);
        }
        if (klass == meta.java_lang_Double) {
            double content = meta.java_lang_Double_value.getDouble(receiver);
            return (short) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInInt(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
        if (isAtMostInt(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Long) {
            long content = meta.java_lang_Long_value.getLong(receiver);
            return (int) content == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = meta.java_lang_Float_value.getFloat(receiver);
            return !isNegativeZero(content) && (int) content == content && (int) content != Integer.MAX_VALUE;
        }
        if (klass == meta.java_lang_Double) {
            double content = meta.java_lang_Double_value.getDouble(receiver);
            return (int) content == content && !isNegativeZero(content);
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInLong(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
        if (isAtMostLong(klass)) {
            return true;
        }

        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Float) {
            float content = meta.java_lang_Float_value.getFloat(receiver);
            return !isNegativeZero(content) && (long) content == content && (long) content != Long.MAX_VALUE;
        }
        if (klass == meta.java_lang_Double) {
            double content = meta.java_lang_Double_value.getDouble(receiver);
            return !isNegativeZero(content) && (long) content == content && (long) content != Long.MAX_VALUE;
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInFloat(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
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
            int content = meta.java_lang_Integer_value.getInt(receiver);
            float floatContent = content;
            return content != Integer.MAX_VALUE && (int) floatContent == content;
        }
        if (klass == meta.java_lang_Long) {
            long content = meta.java_lang_Long_value.getLong(receiver);
            float floatContent = content;
            return content != Long.MAX_VALUE && (long) floatContent == content;
        }
        if (klass == meta.java_lang_Double) {
            double content = meta.java_lang_Double_value.getDouble(receiver);
            return !Double.isFinite(content) || (float) content == content;
        }
        return false;
    }

    @ExportMessage
    static boolean fitsInDouble(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Klass klass = receiver.getKlass();
        Meta meta = klass.getMeta();
        if (isAtMostInt(klass) || klass == meta.java_lang_Double) {
            return true;
        }
        if (klass == meta.java_lang_Long) {
            long content = meta.java_lang_Long_value.getLong(receiver);
            double doubleContent = content;
            return content != Long.MAX_VALUE && (long) doubleContent == content;
        }
        if (klass == meta.java_lang_Float) {
            float content = meta.java_lang_Float_value.getFloat(receiver);
            return !Float.isFinite(content) || (double) content == content;
        }
        return false;
    }

    private static Number readNumberValue(StaticObject receiver) throws UnsupportedMessageException {
        assert receiver.isEspressoObject();
        Klass klass = receiver.getKlass();
        Meta meta = klass.getMeta();
        if (klass == meta.java_lang_Byte) {
            return (Byte) meta.java_lang_Byte_value.get(receiver);
        }
        if (klass == meta.java_lang_Short) {
            return (Short) meta.java_lang_Short_value.get(receiver);
        }
        if (klass == meta.java_lang_Integer) {
            return (Integer) meta.java_lang_Integer_value.get(receiver);
        }
        if (klass == meta.java_lang_Long) {
            return (Long) meta.java_lang_Long_value.get(receiver);
        }
        if (klass == meta.java_lang_Float) {
            return (Float) meta.java_lang_Float_value.get(receiver);
        }
        if (klass == meta.java_lang_Double) {
            return (Double) meta.java_lang_Double_value.get(receiver);
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static byte asByte(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInByte(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).byteValue();
    }

    @ExportMessage
    static short asShort(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInShort(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).shortValue();
    }

    @ExportMessage
    static int asInt(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInInt(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).intValue();
    }

    @ExportMessage
    static long asLong(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInLong(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).longValue();
    }

    @ExportMessage
    static float asFloat(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInFloat(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).floatValue();
    }

    @ExportMessage
    static double asDouble(StaticObject receiver) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!fitsInDouble(receiver)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        return readNumberValue(receiver).doubleValue();
    }

    // endregion ### is/as checks/conversions

    // region ### Arrays

    @ExportMessage
    static long getArraySize(StaticObject receiver,
                    @CachedLibrary("receiver") InteropLibrary receiverLib,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (!receiver.isArray()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        return receiver.length(EspressoLanguage.get(receiverLib));
    }

    @ExportMessage
    static boolean hasArrayElements(StaticObject receiver) {
        if (receiver.isForeignObject()) {
            return false;
        }
        return receiver.isArray();
    }

    @ExportMessage
    abstract static class ReadArrayElement {
        @Specialization(guards = {"isBooleanArray(receiver)", "receiver.isEspressoObject()"})
        static boolean doBoolean(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<byte[]> unwrap(language)[(int) index] != 0;
        }

        @Specialization(guards = {"isCharArray(receiver)", "receiver.isEspressoObject()"})
        static char doChar(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<char[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isByteArray(receiver)", "receiver.isEspressoObject()"})
        static byte doByte(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<byte[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isShortArray(receiver)", "receiver.isEspressoObject()"})
        static short doShort(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<short[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isIntArray(receiver)", "receiver.isEspressoObject()"})
        static int doInt(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<int[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isLongArray(receiver)", "receiver.isEspressoObject()"})
        static long doLong(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<long[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isFloatArray(receiver)", "receiver.isEspressoObject()"})
        static float doFloat(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<float[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"isDoubleArray(receiver)", "receiver.isEspressoObject()"})
        static double doDouble(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<double[]> unwrap(language)[(int) index];
        }

        @Specialization(guards = {"receiver.isArray()", "receiver.isEspressoObject()", "!isPrimitiveArray(receiver)"})
        static Object doObject(StaticObject receiver, long index,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return receiver.<StaticObject[]> unwrap(language)[(int) index];
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object doOther(StaticObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class WriteArrayElement {
        @Specialization(guards = {"isBooleanArray(receiver)", "receiver.isEspressoObject()"})
        static void doBoolean(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            boolean boolValue;
            try {
                boolValue = valueLib.asBoolean(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<byte[]> unwrap(language)[(int) index] = boolValue ? (byte) 1 : (byte) 0;
        }

        @Specialization(guards = {"isCharArray(receiver)", "receiver.isEspressoObject()"})
        static void doChar(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            char charValue;
            try {
                String s = valueLib.asString(value);
                if (s.length() != 1) {
                    error.enter();
                    String message = EspressoError.format("Expected a string of length 1 as an element of char array, got %s", s);
                    throw UnsupportedTypeException.create(new Object[]{value}, message);
                }
                charValue = s.charAt(0);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<char[]> unwrap(language)[(int) index] = charValue;
        }

        @Specialization(guards = {"isByteArray(receiver)", "receiver.isEspressoObject()"})
        static void doByte(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            byte byteValue;
            try {
                byteValue = valueLib.asByte(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<byte[]> unwrap(language)[(int) index] = byteValue;
        }

        @Specialization(guards = {"isShortArray(receiver)", "receiver.isEspressoObject()"})
        static void doShort(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            short shortValue;
            try {
                shortValue = valueLib.asShort(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<short[]> unwrap(language)[(int) index] = shortValue;
        }

        @Specialization(guards = {"isIntArray(receiver)", "receiver.isEspressoObject()"})
        static void doInt(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            int intValue;
            try {
                intValue = valueLib.asInt(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<int[]> unwrap(language)[(int) index] = intValue;
        }

        @Specialization(guards = {"isLongArray(receiver)", "receiver.isEspressoObject()"})
        static void doLong(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            long longValue;
            try {
                longValue = valueLib.asLong(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<long[]> unwrap(language)[(int) index] = longValue;
        }

        @Specialization(guards = {"isFloatArray(receiver)", "receiver.isEspressoObject()"})
        static void doFloat(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            float floatValue;
            try {
                floatValue = valueLib.asFloat(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<float[]> unwrap(language)[(int) index] = floatValue;
        }

        @Specialization(guards = {"isDoubleArray(receiver)", "receiver.isEspressoObject()"})
        static void doDouble(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            double doubleValue;
            try {
                doubleValue = valueLib.asDouble(value);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<double[]> unwrap(language)[(int) index] = doubleValue;
        }

        @Specialization(guards = {"isStringArray(receiver)", "receiver.isEspressoObject()"})
        static void doString(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib, // GR-37680
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            StaticObject stringValue;
            try {
                stringValue = receiver.getKlass().getMeta().toGuestString(valueLib.asString(value));
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<StaticObject[]> unwrap(language)[(int) index] = stringValue;
        }

        @Specialization(guards = {"receiver.isArray()", "!isStringArray(receiver)", "receiver.isEspressoObject()", "!isPrimitiveArray(receiver)"})
        static void doEspressoObject(StaticObject receiver, long index, StaticObject value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Klass componentType = ((ArrayKlass) receiver.getKlass()).getComponentType();
            if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
                receiver.<StaticObject[]> unwrap(language)[(int) index] = value;
            } else {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, "Incompatible types");
            }
        }

        @TruffleBoundary
        private static String getMessageBoundary(Throwable e) {
            return e.getMessage();
        }

        @Specialization(guards = {"receiver.isArray()", "!isStringArray(receiver)", "receiver.isEspressoObject()", "!isPrimitiveArray(receiver)", "!isStaticObject(value)"})
        static void doEspressoGeneric(StaticObject receiver, long index, Object value,
                        @CachedLibrary("receiver") InteropLibrary receiverLib,
                        @Shared("toEspresso") @Cached ToEspressoNode toEspressoNode,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            EspressoLanguage language = EspressoLanguage.get(receiverLib);
            if (index < 0 || receiver.length(language) <= index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            StaticObject espressoValue;
            try {
                Klass componentType = ((ArrayKlass) receiver.getKlass()).getComponentType();
                espressoValue = (StaticObject) toEspressoNode.execute(value, componentType);
            } catch (UnsupportedOperationException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessageBoundary(e));
            }
            receiver.<StaticObject[]> unwrap(language)[(int) index] = espressoValue;
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

    protected static boolean isStaticObject(Object object) {
        return object instanceof StaticObject;
    }

    @ExportMessage
    @ExportMessage(name = "isArrayElementModifiable")
    static boolean isArrayElementReadable(StaticObject receiver, long index, @CachedLibrary("receiver") InteropLibrary receiverLib) {
        receiver.checkNotForeign();
        return receiver.isArray() && 0 <= index && index < receiver.length(EspressoLanguage.get(receiverLib));
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    static boolean isArrayElementInsertable(StaticObject receiver, long index) {
        return false;
    }

    // endregion ### Arrays

    // region ### Members

    @ExportMessage
    static Object readMember(StaticObject receiver, String member,
                    @Cached @Exclusive LookupInstanceFieldNode lookupField,
                    @Cached @Exclusive LookupVirtualMethodNode lookupMethod) throws UnknownIdentifierException {
        receiver.checkNotForeign();
        if (notNull(receiver)) {
            Field f = lookupField.execute(getInteropKlass(receiver), member);
            if (f != null) {
                return InteropUtils.unwrap(EspressoLanguage.get(lookupField), f.get(receiver), receiver.getKlass().getMeta());
            }
            try {
                Method[] candidates = lookupMethod.execute(getInteropKlass(receiver), member, -1);
                if (candidates != null) {
                    if (candidates.length == 1) {
                        return EspressoFunction.createInstanceInvocable(candidates[0], receiver);
                    }
                }
            } catch (ArityException e) {
                /* Ignore */
            }
            // Class<T>.static == Klass<T>
            if (CLASS_TO_STATIC.equals(member)) {
                if (receiver.getKlass() == receiver.getKlass().getMeta().java_lang_Class) {
                    return receiver.getMirrorKlass();
                }
            }
            // Class<T>.class == Class<T>
            if (STATIC_TO_CLASS.equals(member)) {
                if (receiver.getKlass() == receiver.getKlass().getMeta().java_lang_Class) {
                    return receiver;
                }
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    static boolean hasMembers(StaticObject receiver) {
        if (receiver.isForeignObject()) {
            return false;
        }
        return notNull(receiver);
    }

    @ExportMessage
    static boolean isMemberReadable(StaticObject receiver, String member,
                    @Cached @Exclusive LookupInstanceFieldNode lookupField,
                    @Cached @Exclusive LookupVirtualMethodNode lookupMethod) {
        receiver.checkNotForeign();
        Field f = lookupField.execute(getInteropKlass(receiver), member);
        if (f != null) {
            return true;
        }
        if (lookupMethod.isInvocable(getInteropKlass(receiver), member)) {
            return true;
        }
        return notNull(receiver) && receiver.getKlass() == receiver.getKlass().getMeta().java_lang_Class //
                        && (CLASS_TO_STATIC.equals(member) || STATIC_TO_CLASS.equals(member));
    }

    @ExportMessage
    static boolean isMemberModifiable(StaticObject receiver, String member,
                    @Cached @Exclusive LookupInstanceFieldNode lookup) {
        receiver.checkNotForeign();
        Field f = lookup.execute(getInteropKlass(receiver), member);
        if (f != null) {
            return !f.isFinalFlagSet();
        }
        return false;
    }

    @ExportMessage
    static void writeMember(StaticObject receiver, String member, Object value,
                    @Cached @Exclusive LookupInstanceFieldNode lookup,
                    @Shared("toEspresso") @Cached ToEspressoNode toEspresso,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedTypeException, UnknownIdentifierException, UnsupportedMessageException {
        receiver.checkNotForeign();
        Field f = lookup.execute(getInteropKlass(receiver), member);
        if (f != null) {
            if (f.isFinalFlagSet()) {
                error.enter();
                throw UnsupportedMessageException.create();
            }
            f.set(receiver, toEspresso.execute(value, f.resolveTypeKlass()));
            return;
        }
        error.enter();
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean isMemberInsertable(StaticObject receiver, String member) {
        return false;
    }

    private static final String[] CLASS_KEYS = {CLASS_TO_STATIC, STATIC_TO_CLASS};

    public static ObjectKlass getInteropKlass(StaticObject receiver) {
        if (receiver.getKlass().isArray()) {
            return receiver.getKlass().getMeta().java_lang_Object;
        } else {
            assert !receiver.getKlass().isPrimitive() : "Static Object should not represent a primitive.";
            return (ObjectKlass) receiver.getKlass();
        }
    }

    @ExportMessage
    @TruffleBoundary
    static Object getMembers(StaticObject receiver,
                    @SuppressWarnings("unused") boolean includeInternal) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return EmptyKeysArray.INSTANCE;
        }
        ArrayList<String> members = new ArrayList<>();
        if (receiver.getKlass() == receiver.getKlass().getMeta().java_lang_Class) {
            // SVM does not like ArrayList.addAll(). Do manual copy.
            for (String s : CLASS_KEYS) {
                members.add(s);
            }
        }
        ObjectKlass k = getInteropKlass(receiver);

        for (Field f : k.getFieldTable()) {
            if (f.isPublic() && !f.isRemoved()) {
                members.add(f.getNameAsString());
            }
        }

        for (Method.MethodVersion m : k.getVTable()) {
            if (LookupVirtualMethodNode.isCandidate(m.getMethod())) {
                // Note: If there are overloading, the same key may appear twice.
                // TODO: Cache the keys array in the Klass.
                members.add(m.getMethod().getInteropString());
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
    static boolean isMemberInvocable(StaticObject receiver,
                    String member,
                    @Exclusive @Cached LookupVirtualMethodNode lookupMethod) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        ObjectKlass k = getInteropKlass(receiver);
        return lookupMethod.isInvocable(k, member);
    }

    @ExportMessage
    static Object invokeMember(StaticObject receiver,
                    String member,
                    Object[] arguments,
                    @Exclusive @Cached LookupVirtualMethodNode lookupMethod,
                    @Exclusive @Cached OverLoadedMethodSelectorNode selectorNode,
                    @Exclusive @Cached InvokeEspressoNode invoke,
                    @Exclusive @Cached ToEspressoNode toEspressoNode)
                    throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        Method[] candidates = lookupMethod.execute(receiver.getKlass(), member, arguments.length);
        try {
            if (candidates != null) {
                if (candidates.length == 1) {
                    // common case with no overloads
                    Method m = candidates[0];
                    assert !m.isStatic() && m.isPublic();
                    assert member.startsWith(m.getNameAsString());
                    if (!m.isVarargs()) {
                        assert m.getParameterCount() == arguments.length;
                        return invoke.execute(m, receiver, arguments);
                    } else {
                        CandidateMethodWithArgs matched = MethodArgsUtils.matchCandidate(m, arguments, m.resolveParameterKlasses(), toEspressoNode);
                        if (matched != null) {
                            matched = MethodArgsUtils.ensureVarArgsArrayCreated(matched, toEspressoNode);
                            if (matched != null) {
                                return invoke.execute(matched.getMethod(), receiver, matched.getConvertedArgs(), true);
                            }
                        }
                    }
                } else {
                    // multiple overloaded methods found
                    // find method with type matches
                    CandidateMethodWithArgs typeMatched = selectorNode.execute(candidates, arguments);
                    if (typeMatched != null) {
                        // single match found!
                        return invoke.execute(typeMatched.getMethod(), receiver, typeMatched.getConvertedArgs(), true);
                    } else {
                        // unable to select exactly one best candidate for the input args!
                        throw UnknownIdentifierException.create(member);
                    }
                }
            }
        } catch (EspressoException e) {
            Meta meta = e.getGuestException().getKlass().getMeta();
            if (e.getGuestException().getKlass() == meta.polyglot.ForeignException) {
                // rethrow the original foreign exception when leaving espresso interop
                EspressoLanguage language = receiver.getKlass().getContext().getLanguage();
                throw (AbstractTruffleException) meta.java_lang_Throwable_backtrace.getObject(e.getGuestException()).rawForeignObject(language);
            }
            throw e;
        }
        throw UnknownIdentifierException.create(member);
    }

    // endregion ### Members

    // region ### Date/time conversions

    @ExportMessage
    static boolean isDate(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Meta meta = receiver.getKlass().getMeta();
        return instanceOf(receiver, meta.java_time_LocalDate) ||
                        instanceOf(receiver, meta.java_time_LocalDateTime) ||
                        instanceOf(receiver, meta.java_time_Instant) ||
                        instanceOf(receiver, meta.java_time_ZonedDateTime) ||
                        instanceOf(receiver, meta.java_util_Date);
    }

    @ExportMessage
    static @TruffleBoundary LocalDate asDate(StaticObject receiver,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (isDate(receiver)) {
            Meta meta = receiver.getKlass().getMeta();
            if (instanceOf(receiver, meta.java_time_LocalDate)) {
                int year = (int) meta.java_time_LocalDate_year.get(receiver);
                short month = (short) meta.java_time_LocalDate_month.get(receiver);
                short day = (short) meta.java_time_LocalDate_day.get(receiver);
                return LocalDate.of(year, month, day);
            } else if (instanceOf(receiver, meta.java_time_LocalDateTime)) {
                StaticObject localDate = (StaticObject) meta.java_time_LocalDateTime_toLocalDate.invokeDirect(receiver);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return asDate(localDate, error);
            } else if (instanceOf(receiver, meta.java_time_Instant)) {
                StaticObject zoneIdUTC = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString("UTC"));
                assert instanceOf(zoneIdUTC, meta.java_time_ZoneId);
                StaticObject zonedDateTime = (StaticObject) meta.java_time_Instant_atZone.invokeDirect(receiver, zoneIdUTC);
                assert instanceOf(zonedDateTime, meta.java_time_ZonedDateTime);
                StaticObject localDate = (StaticObject) meta.java_time_ZonedDateTime_toLocalDate.invokeDirect(zonedDateTime);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return asDate(localDate, error);
            } else if (instanceOf(receiver, meta.java_time_ZonedDateTime)) {
                StaticObject localDate = (StaticObject) meta.java_time_ZonedDateTime_toLocalDate.invokeDirect(receiver);
                assert instanceOf(localDate, meta.java_time_LocalDate);
                return asDate(localDate, error);
            } else if (instanceOf(receiver, meta.java_util_Date)) {
                // return ((Date) obj).toInstant().atZone(UTC).toLocalDate();
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = receiver.getKlass().vtableLookup(index);
                StaticObject instant = (StaticObject) virtualToInstant.invokeDirect(receiver);
                return asDate(instant, error);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isTime(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Meta meta = receiver.getKlass().getMeta();
        return instanceOf(receiver, meta.java_time_LocalTime) ||
                        instanceOf(receiver, meta.java_time_Instant) ||
                        instanceOf(receiver, meta.java_time_ZonedDateTime) ||
                        instanceOf(receiver, meta.java_util_Date);
    }

    @ExportMessage
    static @TruffleBoundary LocalTime asTime(StaticObject receiver,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (isTime(receiver)) {
            Meta meta = receiver.getKlass().getMeta();
            if (instanceOf(receiver, meta.java_time_LocalTime)) {
                byte hour = (byte) meta.java_time_LocalTime_hour.get(receiver);
                byte minute = (byte) meta.java_time_LocalTime_minute.get(receiver);
                byte second = (byte) meta.java_time_LocalTime_second.get(receiver);
                int nano = (int) meta.java_time_LocalTime_nano.get(receiver);
                return LocalTime.of(hour, minute, second, nano);
            } else if (instanceOf(receiver, meta.java_time_LocalDateTime)) {
                StaticObject localTime = (StaticObject) meta.java_time_LocalDateTime_toLocalTime.invokeDirect(receiver);
                return asTime(localTime, error);
            } else if (instanceOf(receiver, meta.java_time_ZonedDateTime)) {
                StaticObject localTime = (StaticObject) meta.java_time_ZonedDateTime_toLocalTime.invokeDirect(receiver);
                return asTime(localTime, error);
            } else if (instanceOf(receiver, meta.java_time_Instant)) {
                // return ((Instant) obj).atZone(UTC).toLocalTime();
                StaticObject zoneIdUTC = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString("UTC"));
                assert instanceOf(zoneIdUTC, meta.java_time_ZoneId);
                StaticObject zonedDateTime = (StaticObject) meta.java_time_Instant_atZone.invokeDirect(receiver, zoneIdUTC);
                assert instanceOf(zonedDateTime, meta.java_time_ZonedDateTime);
                StaticObject localTime = (StaticObject) meta.java_time_ZonedDateTime_toLocalTime.invokeDirect(zonedDateTime);
                assert instanceOf(localTime, meta.java_time_LocalTime);
                return asTime(localTime, error);
            } else if (instanceOf(receiver, meta.java_util_Date)) {
                // return ((Date) obj).toInstant().atZone(UTC).toLocalTime();
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = receiver.getKlass().vtableLookup(index);
                StaticObject instant = (StaticObject) virtualToInstant.invokeDirect(receiver);
                return asTime(instant, error);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isTimeZone(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Meta meta = receiver.getKlass().getMeta();
        return instanceOf(receiver, meta.java_time_ZoneId) ||
                        instanceOf(receiver, meta.java_time_Instant) ||
                        instanceOf(receiver, meta.java_time_ZonedDateTime) ||
                        instanceOf(receiver, meta.java_util_Date);
    }

    @ExportMessage
    static @TruffleBoundary ZoneId asTimeZone(StaticObject receiver,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (isTimeZone(receiver)) {
            Meta meta = receiver.getKlass().getMeta();
            if (instanceOf(receiver, meta.java_time_ZoneId)) {
                int index = meta.java_time_ZoneId_getId.getVTableIndex();
                StaticObject zoneIdEspresso = (StaticObject) receiver.getKlass().vtableLookup(index).invokeDirect(receiver);
                String zoneId = Meta.toHostStringStatic(zoneIdEspresso);
                return ZoneId.of(zoneId, ZoneId.SHORT_IDS);
            } else if (instanceOf(receiver, meta.java_time_ZonedDateTime)) {
                StaticObject zoneId = (StaticObject) meta.java_time_ZonedDateTime_getZone.invokeDirect(receiver);
                return asTimeZone(zoneId, error);
            } else if (instanceOf(receiver, meta.java_time_Instant) ||
                            instanceOf(receiver, meta.java_util_Date)) {
                return ZoneId.of("UTC");
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static @TruffleBoundary Instant asInstant(StaticObject receiver,
                    @CachedLibrary("receiver") InteropLibrary receiverLibrary, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (receiverLibrary.isInstant(receiver)) {
            StaticObject instant;
            Meta meta = receiver.getKlass().getMeta();
            if (instanceOf(receiver, meta.java_time_ZonedDateTime)) {
                instant = (StaticObject) meta.java_time_ZonedDateTime_toInstant.invokeDirect(receiver);
            } else if (instanceOf(receiver, meta.java_util_Date)) {
                int index = meta.java_util_Date_toInstant.getVTableIndex();
                Method virtualToInstant = receiver.getKlass().vtableLookup(index);
                instant = (StaticObject) virtualToInstant.invokeDirect(receiver);
            } else {
                instant = receiver;
            }
            assert instanceOf(instant, meta.java_time_Instant);
            long seconds = (long) meta.java_time_Instant_seconds.get(instant);
            int nanos = (int) meta.java_time_Instant_nanos.get(instant);
            return Instant.ofEpochSecond(seconds, nanos);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isDuration(StaticObject receiver) {
        receiver.checkNotForeign();
        if (isNull(receiver)) {
            return false;
        }
        Meta meta = receiver.getKlass().getMeta();
        return instanceOf(receiver, meta.java_time_Duration);
    }

    @ExportMessage
    static Duration asDuration(StaticObject receiver,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        receiver.checkNotForeign();
        if (isDuration(receiver)) {
            Meta meta = receiver.getKlass().getMeta();
            // Avoid expensive calls to Duration.{getSeconds/getNano} by extracting the private
            // fields directly.
            long seconds = (long) meta.java_time_Duration_seconds.get(receiver);
            int nanos = (int) meta.java_time_Duration_nanos.get(receiver);
            return Duration.ofSeconds(seconds, nanos);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Date/time conversions
}
