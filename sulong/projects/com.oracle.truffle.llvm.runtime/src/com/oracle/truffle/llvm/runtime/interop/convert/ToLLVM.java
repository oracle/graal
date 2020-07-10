/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Converts value to the target type. For fast path code, targetType will typically be constant in
 * which case this node has zero overhead.
 */
@GenerateUncached
public abstract class ToLLVM extends LLVMNode {

    private static final UnsupportedMessageException REWRITE = UnsupportedMessageException.create();

    public abstract Object executeWithType(Object value, LLVMInteropType.Value incomingType, ForeignToLLVMType targetType);

    @Specialization(guards = {"incomingType != null", "incomingType.getSize() == targetType.getSizeInBytes()"}, rewriteOn = UnsupportedMessageException.class)
    Object doConvert(Object value, LLVMInteropType.Value incomingType, ForeignToLLVMType targetType,
                    @Cached ReadValue read,
                    @Cached ConvertValue convert) throws UnsupportedMessageException {
        Object incoming = read.execute(value, incomingType);
        return convert.execute(incoming, targetType);
    }

    @Specialization(guards = "incomingType != null", replaces = "doConvert")
    Object doConvertTypeMismatch(Object value, LLVMInteropType.Value incomingType, ForeignToLLVMType targetType,
                    @Cached ReadValue read,
                    @Cached ConvertValue convert,
                    @Cached ReadUnknown readUnknown) {
        if (incomingType.getSize() == targetType.getSizeInBytes()) {
            try {
                return doConvert(value, incomingType, targetType, read, convert);
            } catch (UnsupportedMessageException ex) {
            }
        }

        // if we get an unexpected return type, retry with the targetType
        return readUnknown.executeWithType(value, targetType);
    }

    @Specialization(guards = "incomingType == null")
    static Object doUnknownType(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType, ForeignToLLVMType targetType,
                    @Cached ReadUnknown read) {
        return read.executeWithType(value, targetType);
    }

    @GenerateUncached
    abstract static class WrapPointer extends LLVMNode {

        protected abstract LLVMPointer execute(Object value, LLVMInteropType.Structured type);

        @Specialization
        LLVMPointer doPointer(LLVMPointer pointer, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return pointer;
        }

        @Fallback
        LLVMPointer doOther(Object value, LLVMInteropType.Structured type) {
            LLVMTypedForeignObject typed = LLVMTypedForeignObject.create(value, type);
            return LLVMManagedPointer.create(typed);
        }
    }

    @GenerateUncached
    @ImportStatic(LLVMInteropType.ValueKind.class)
    abstract static class ReadValue extends LLVMNode {

        protected abstract Object execute(Object value, LLVMInteropType.Value incomingType) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = "incomingType.getKind() == I1")
        static boolean doI1(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.isBoolean(value)) {
                throw REWRITE;
            }
            return interop.asBoolean(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == I8")
        static byte doI8(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInByte(value)) {
                throw REWRITE;
            }
            return interop.asByte(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == I16")
        static short doI16(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInShort(value)) {
                throw REWRITE;
            }
            return interop.asShort(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == I32")
        static int doI32(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInInt(value)) {
                throw REWRITE;
            }
            return interop.asInt(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == I64")
        static long doI64(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInLong(value)) {
                throw REWRITE;
            }
            return interop.asLong(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == FLOAT")
        static float doFloat(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInFloat(value)) {
                throw REWRITE;
            }
            return interop.asFloat(value);
        }

        @Specialization(limit = "3", guards = "incomingType.getKind() == DOUBLE")
        static double doDouble(Object value, @SuppressWarnings("unused") LLVMInteropType.Value incomingType,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException {
            if (!interop.fitsInDouble(value)) {
                throw REWRITE;
            }
            return interop.asDouble(value);
        }

        @Specialization(guards = "incomingType.getKind() == POINTER")
        static LLVMPointer doPointer(Object value, LLVMInteropType.Value incomingType,
                        @Cached WrapPointer wrap) {
            return wrap.execute(value, incomingType.getBaseType());
        }
    }

    @GenerateUncached
    @ImportStatic(ForeignToLLVMType.class)
    abstract static class ConvertValue extends LLVMNode {

        protected abstract Object execute(Object value, ForeignToLLVMType targetType);

        @Specialization(guards = "targetType == I1")
        static boolean doI1(boolean value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == I8")
        static byte doI8(byte value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == I16")
        static short doI16(short value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == I32")
        static int doI32(int value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == I32")
        static int doI32(float value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return Float.floatToRawIntBits(value);
        }

        @Specialization(guards = "targetType == I64")
        static long doI64(long value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == I64")
        static long doI64(double value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return Double.doubleToRawLongBits(value);
        }

        @Specialization(guards = "targetType == I64")
        static LLVMPointer doI64(LLVMPointer value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == FLOAT")
        static float doFloat(float value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == FLOAT")
        static float doFloat(int value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return Float.intBitsToFloat(value);
        }

        @Specialization(guards = "targetType == DOUBLE")
        static double doDouble(double value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == DOUBLE")
        static double doDouble(long value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return Double.longBitsToDouble(value);
        }

        @Specialization(guards = "targetType == DOUBLE")
        double doDouble(@SuppressWarnings("unused") LLVMPointer value, ForeignToLLVMType targetType) {
            throw new LLVMPolyglotException(this, "Cannot convert a pointer to %s", targetType);
        }

        @Specialization(guards = "targetType == POINTER")
        static LLVMPointer doPointer(LLVMPointer value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return value;
        }

        @Specialization(guards = "targetType == POINTER")
        static LLVMPointer doPointer(long value, @SuppressWarnings("unused") ForeignToLLVMType targetType) {
            return LLVMNativePointer.create(value);
        }

        @Specialization(guards = "targetType == POINTER")
        LLVMPointer doPointer(@SuppressWarnings("unused") double value, ForeignToLLVMType targetType) {
            throw new LLVMPolyglotException(this, "Cannot convert a double to %s", targetType);
        }
    }

    @GenerateUncached
    @ImportStatic(ForeignToLLVMType.class)
    abstract static class ReadUnknown extends LLVMNode {

        protected abstract Object executeWithType(Object value, ForeignToLLVMType targetType);

        @Specialization(guards = "isI1(targetType)")
        static boolean toI1(Object value, ForeignToLLVMType targetType,
                        @Cached("createToI1()") ForeignToLLVM toI1) {
            return LLVMTypesGen.asBoolean(toI1.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isI8(targetType)")
        static byte toI8(Object value, ForeignToLLVMType targetType,
                        @Cached("createToI8()") ForeignToLLVM toI8) {
            return LLVMTypesGen.asByte(toI8.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isI16(targetType)")
        static short toI16(Object value, ForeignToLLVMType targetType,
                        @Cached("createToI16()") ForeignToLLVM toI16) {
            return LLVMTypesGen.asShort(toI16.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isI32(targetType)")
        static int toI32(Object value, ForeignToLLVMType targetType,
                        @Cached("createToI32()") ForeignToLLVM toI32) {
            return LLVMTypesGen.asInteger(toI32.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isI64(targetType)")
        static Object toI64(Object value, ForeignToLLVMType targetType,
                        @Cached("createToI64()") ForeignToLLVM toI64) {
            return toI64.executeWithForeignToLLVMType(value, null, targetType);
        }

        @Specialization(guards = "isFloat(targetType)")
        static float toFloat(Object value, ForeignToLLVMType targetType,
                        @Cached("createToFloat()") ForeignToLLVM toFloat) {
            return LLVMTypesGen.asFloat(toFloat.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isDouble(targetType)")
        static double toDouble(Object value, ForeignToLLVMType targetType,
                        @Cached("createToDouble()") ForeignToLLVM toDouble) {
            return LLVMTypesGen.asDouble(toDouble.executeWithForeignToLLVMType(value, null, targetType));
        }

        @Specialization(guards = "isPointer(targetType)")
        static Object toPointer(Object value, ForeignToLLVMType targetType,
                        @Cached("createToPointer()") ForeignToLLVM toPointer) {
            return toPointer.executeWithForeignToLLVMType(value, null, targetType);
        }

        static boolean isI1(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.I1;
        }

        static boolean isI8(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.I8;
        }

        static boolean isI16(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.I16;
        }

        static boolean isI32(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.I32;
        }

        static boolean isI64(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.I64;
        }

        static boolean isFloat(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.FLOAT;
        }

        static boolean isDouble(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.DOUBLE;
        }

        static boolean isPointer(ForeignToLLVMType targetType) {
            return targetType == ForeignToLLVMType.POINTER;
        }

        protected ForeignToLLVM createToI1() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I1);
        }

        protected ForeignToLLVM createToI8() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I8);
        }

        protected ForeignToLLVM createToI16() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I16);
        }

        protected ForeignToLLVM createToI32() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I32);
        }

        protected ForeignToLLVM createToI64() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I64);
        }

        protected ForeignToLLVM createToFloat() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.FLOAT);
        }

        protected ForeignToLLVM createToDouble() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.DOUBLE);
        }

        protected ForeignToLLVM createToPointer() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.POINTER);
        }
    }
}
