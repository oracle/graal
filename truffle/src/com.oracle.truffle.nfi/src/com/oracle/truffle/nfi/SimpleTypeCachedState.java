/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.NFIType.TypeCachedState;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.InjectedFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.NopConvertFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.NothingFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.NullableToNativeFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.PointerFromNativeFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToDoubleFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToFloatFactory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToInt16Factory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToInt32Factory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToInt64Factory;
import com.oracle.truffle.nfi.SimpleTypeCachedStateFactory.ToInt8Factory;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

final class SimpleTypeCachedState {

    private static final TypeCachedState nopCachedState;
    private static final TypeCachedState injectedCachedState;
    private static final TypeCachedState[] simpleCachedState;

    static {
        TypeCachedState[] c = new TypeCachedState[NativeSimpleType.values().length];

        nopCachedState = new TypeCachedState(1, NopConvertFactory.getInstance(), NopConvertFactory.getInstance());
        injectedCachedState = new TypeCachedState(0, InjectedFactory.getInstance(), NothingFactory.getInstance());

        c[NativeSimpleType.VOID.ordinal()] = new TypeCachedState(1, NothingFactory.getInstance(), NopConvertFactory.getInstance());

        c[NativeSimpleType.SINT8.ordinal()] = new TypeCachedState(1, ToInt8Factory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.SINT16.ordinal()] = new TypeCachedState(1, ToInt16Factory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.SINT32.ordinal()] = new TypeCachedState(1, ToInt32Factory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.SINT64.ordinal()] = new TypeCachedState(1, ToInt64Factory.getInstance(), NopConvertFactory.getInstance());

        c[NativeSimpleType.UINT8.ordinal()] = new TypeCachedState(1, ToInt8Factory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.UINT16.ordinal()] = new TypeCachedState(1, ToInt16Factory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.UINT32.ordinal()] = new TypeCachedState(1, ToInt32Factory.getInstance(), NopConvertFactory.getInstance());

        // TODO: need interop unsigned long type
        c[NativeSimpleType.UINT64.ordinal()] = c[NativeSimpleType.SINT64.ordinal()];

        c[NativeSimpleType.FLOAT.ordinal()] = new TypeCachedState(1, ToFloatFactory.getInstance(), NopConvertFactory.getInstance());
        c[NativeSimpleType.DOUBLE.ordinal()] = new TypeCachedState(1, ToDoubleFactory.getInstance(), NopConvertFactory.getInstance());

        c[NativeSimpleType.POINTER.ordinal()] = new TypeCachedState(1, NopConvertFactory.getInstance(), PointerFromNativeFactory.getInstance());
        c[NativeSimpleType.NULLABLE.ordinal()] = new TypeCachedState(1, NullableToNativeFactory.getInstance(), PointerFromNativeFactory.getInstance());
        c[NativeSimpleType.STRING.ordinal()] = new TypeCachedState(1, NopConvertFactory.getInstance(), PointerFromNativeFactory.getInstance());
        c[NativeSimpleType.OBJECT.ordinal()] = nopCachedState;

        simpleCachedState = c;
    }

    public static TypeCachedState get(NativeSimpleType type) {
        return simpleCachedState[type.ordinal()];
    }

    public static TypeCachedState nop() {
        return nopCachedState;
    }

    public static TypeCachedState injected() {
        return injectedCachedState;
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class NopConvert extends ConvertTypeNode {

        @Specialization
        Object doConvert(@SuppressWarnings("unused") NFIType type, Object arg) {
            return arg;
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class Nothing extends ConvertTypeNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doConvert(NFIType type, Object arg) {
            return NFIPointer.nullPtr();
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class Injected extends ConvertTypeNode {

        @Specialization
        Object doConvert(NFIType type, @SuppressWarnings("unused") Object arg) {
            return type.runtimeData;
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class NullableToNative extends ConvertTypeNode {

        @GenerateAOT.Exclude
        @Specialization(limit = "3", guards = "interop.isNull(arg)")
        @SuppressWarnings("unused")
        Object doNull(@SuppressWarnings("unused") NFIType type, Object arg,
                        @SuppressWarnings("unused") @CachedLibrary("arg") InteropLibrary interop) {
            return NFIPointer.nullPtr();
        }

        @GenerateAOT.Exclude
        @Specialization(limit = "3", guards = "!interop.isNull(arg)")
        Object doObject(@SuppressWarnings("unused") NFIType type, Object arg,
                        @SuppressWarnings("unused") @CachedLibrary("arg") InteropLibrary interop) {
            return arg;
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class PointerFromNative extends ConvertTypeNode {

        @Specialization(guards = "arg == null")
        @SuppressWarnings("unused")
        Object doNull(NFIType type, Object arg) {
            return NFIPointer.nullPtr();
        }

        @Specialization
        Object doLong(@SuppressWarnings("unused") NFIType type, long arg) {
            return NFIPointer.create(arg);
        }

        @Specialization(guards = "arg != null")
        Object doObject(@SuppressWarnings("unused") NFIType type, Object arg) {
            return arg;
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToInt8 extends ConvertTypeNode {

        @Specialization
        byte doPrimitive(@SuppressWarnings("unused") NFIType type, byte arg) {
            return arg;
        }

        @Specialization
        byte doChar(@SuppressWarnings("unused") NFIType type, char arg) {
            return (byte) arg; // C 'char' is an uint8, so allow Java characters, too
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInByte(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        byte doByte(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asByte(arg);
        }

        @Specialization(limit = "3", replaces = "doByte", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        byte doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInByte(arg)) {
                    return interop.asByte(arg);
                } else if (interop.fitsInShort(arg)) {
                    // be lenient with signed vs unsigned values
                    short val = interop.asShort(arg);
                    if (Integer.compareUnsigned(val, 1 << Byte.SIZE) < 0) {
                        return (byte) val;
                    }
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        byte doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? (byte) 1 : 0;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToInt16 extends ConvertTypeNode {

        @Specialization
        short doPrimitive(@SuppressWarnings("unused") NFIType type, short arg) {
            return arg;
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInShort(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        short doShort(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asShort(arg);
        }

        @Specialization(limit = "3", replaces = "doShort", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        short doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInShort(arg)) {
                    return interop.asShort(arg);
                } else if (interop.fitsInInt(arg)) {
                    // be lenient with signed vs unsigned values
                    int val = interop.asInt(arg);
                    if (Integer.compareUnsigned(val, 1 << Short.SIZE) < 0) {
                        return (short) val;
                    }
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        short doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? (short) 1 : 0;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToInt32 extends ConvertTypeNode {

        @Specialization
        int doPrimitive(@SuppressWarnings("unused") NFIType type, int arg) {
            return arg;
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInInt(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        int doInt(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asInt(arg);
        }

        @Specialization(limit = "3", replaces = "doInt", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        int doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInInt(arg)) {
                    return interop.asInt(arg);
                } else if (interop.fitsInLong(arg)) {
                    // be lenient with signed vs unsigned values
                    long val = interop.asLong(arg);
                    if (Long.compareUnsigned(val, 1L << Integer.SIZE) < 0) {
                        return (int) val;
                    }
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        int doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? 1 : 0;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToInt64 extends ConvertTypeNode {

        @Specialization
        long doPrimitive(@SuppressWarnings("unused") NFIType type, long arg) {
            return arg;
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInLong(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        long doLong(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asLong(arg);
        }

        @Specialization(limit = "3", replaces = "doLong", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        long doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInLong(arg)) {
                    return interop.asLong(arg);
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        long doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? 1L : 0L;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToFloat extends ConvertTypeNode {

        @Specialization
        float doPrimitive(@SuppressWarnings("unused") NFIType type, float arg) {
            return arg;
        }

        @Specialization
        float doPrimitive(@SuppressWarnings("unused") NFIType type, double arg) {
            return (float) arg; // allow implicit precision losing casts
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInFloat(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        float doFloat(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asFloat(arg);
        }

        @Specialization(limit = "3", replaces = "doFloat", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        float doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInDouble(arg)) {
                    return (float) interop.asDouble(arg);
                }
            } catch (UnsupportedMessageException e) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        float doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? 1.0f : 0.0f;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    @GenerateUncached
    @GenerateNodeFactory
    abstract static class ToDouble extends ConvertTypeNode {

        @Specialization
        double doPrimitive(@SuppressWarnings("unused") NFIType type, double arg) {
            return arg;
        }

        @Specialization(limit = "3", replaces = "doPrimitive", guards = "interop.fitsInDouble(arg)", rewriteOn = UnsupportedMessageException.class)
        @GenerateAOT.Exclude
        double doDouble(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asDouble(arg);
        }

        @Specialization(limit = "3", replaces = "doDouble", guards = "interop.isNumber(arg)")
        @GenerateAOT.Exclude
        double doNumber(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                if (interop.fitsInDouble(arg)) {
                    return interop.asDouble(arg);
                }
            } catch (UnsupportedMessageException e) {
                // fallthrough
            }
            exception.enter();
            throw UnsupportedTypeException.create(new Object[]{arg});
        }

        @Specialization(limit = "3", guards = "interop.isBoolean(arg)")
        @GenerateAOT.Exclude
        double doBoolean(@SuppressWarnings("unused") NFIType type, Object arg,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached BranchProfile exception) throws UnsupportedTypeException {
            try {
                return interop.asBoolean(arg) ? 1.0 : 0.0;
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw UnsupportedTypeException.create(new Object[]{arg});
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        byte doFail(NFIType type, Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }
}
