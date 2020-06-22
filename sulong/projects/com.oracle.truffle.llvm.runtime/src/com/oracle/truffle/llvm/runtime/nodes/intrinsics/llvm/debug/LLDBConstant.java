/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

abstract class LLDBConstant implements LLVMDebugValue {

    @Override
    public Object readBoolean(long bitOffset) {
        return cannotInterpret(LLVMDebugTypeConstants.BOOLEAN_NAME, bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE);
    }

    @Override
    public Object readFloat(long bitOffset) {
        return cannotInterpret(LLVMDebugTypeConstants.FLOAT_NAME, bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
    }

    @Override
    public Object readDouble(long bitOffset) {
        return cannotInterpret(LLVMDebugTypeConstants.DOUBLE_NAME, bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        return cannotInterpret(LLVMDebugTypeConstants.LLVM80BIT_NAME, bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
    }

    @Override
    public Object readAddress(long bitOffset) {
        return cannotInterpret(LLVMDebugTypeConstants.ADDRESS_NAME, bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        return describeValue(bitOffset, bitSize);
    }

    @Override
    public Object computeAddress(long bitOffset) {
        return UNAVAILABLE_VALUE;
    }

    @Override
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        return cannotInterpret(LLVMDebugTypeConstants.getIntegerKind(bitSize, signed), bitOffset, bitSize);
    }

    @Override
    public LLVMDebugValue dereferencePointer(long bitOffset) {
        return null;
    }

    @Override
    public boolean isInteropValue() {
        return false;
    }

    @Override
    public Object asInteropValue() {
        return null;
    }

    protected abstract Object getBaseValue();

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        if (bitOffset != 0 || bitSize != 0) {
            return String.format("%s at offset %s in %s", LLDBSupport.toSizeString(bitSize), LLDBSupport.toSizeString(bitOffset), getBaseValue());
        } else {
            return String.valueOf(getBaseValue());
        }
    }

    static final class Integer extends LLDBConstant {

        private final long size;
        private final long value;

        Integer(long size, long value) {
            this.size = size;
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset + bits <= size;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return value != 0;
        }

        @Override
        @TruffleBoundary
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return describeValue(bitOffset, bitSize);
            }

            long result = value;
            result <<= Long.SIZE - bitSize - bitOffset;
            if (signed) {
                result >>= Long.SIZE - bitSize;
                return BigInteger.valueOf(result);
            } else {
                result >>>= Long.SIZE - bitSize;
                return new BigInteger(Long.toUnsignedString(result));
            }
        }

        @Override
        public Object computeAddress(long bitOffset) {
            return new Pointer(LLVMNativePointer.create(value)).computeAddress(bitOffset);
        }
    }

    static final class IVarBit extends LLDBConstant {

        private final LLVMIVarBit value;

        IVarBit(LLVMIVarBit value) {
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset + bits <= value.getBitSize();
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return !value.isZero();
        }

        @Override
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return cannotInterpret(LLVMDebugTypeConstants.getIntegerKind(bitSize, signed), bitOffset, bitSize);
            }

            if (value.isZero()) {
                return BigInteger.ZERO;
            }

            LLVMIVarBit result = value;

            if (bitSize != value.getBitSize()) {
                result = result.leftShift(LLVMIVarBit.fromLong(Long.SIZE, result.getBitSize() - bitSize - bitOffset));
                if (signed) {
                    result = result.arithmeticRightShift(LLVMIVarBit.fromLong(Long.SIZE, result.getBitSize() - bitSize));
                } else {
                    result = result.logicalRightShift(LLVMIVarBit.fromLong(Long.SIZE, result.getBitSize() - bitSize));
                }
            }

            return result.getDebugValue(signed);
        }

        @Override
        public Object readFloat(long bitOffset) {
            final Object bigIntVal = readBigInteger(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE, false);
            if (bigIntVal instanceof BigInteger) {
                final int intVal = ((BigInteger) bigIntVal).intValue();
                return java.lang.Float.intBitsToFloat(intVal);
            }
            return super.readFloat(bitOffset);
        }

        @Override
        public Object readDouble(long bitOffset) {
            final Object bigIntVal = readBigInteger(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE, false);
            if (bigIntVal instanceof BigInteger) {
                final long longVal = ((BigInteger) bigIntVal).longValue();
                return java.lang.Double.longBitsToDouble(longVal);
            }
            return super.readDouble(bitOffset);
        }

        @Override
        public Object readAddress(long bitOffset) {
            final Object bigIntVal = readBigInteger(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE, false);
            if (bigIntVal instanceof BigInteger) {
                final long longVal = ((BigInteger) bigIntVal).longValue();
                return LLVMNativePointer.create(longVal);
            }
            return super.readAddress(bitOffset);
        }
    }

    static final class Pointer extends LLDBConstant {

        private final LLVMPointer pointer;

        Pointer(LLVMPointer pointer) {
            this.pointer = pointer;
        }

        @Override
        protected Object getBaseValue() {
            return pointer;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return LLVMDebugTypeConstants.ADDRESS_SIZE - bits - bitOffset >= 0;
        }

        @Override
        @TruffleBoundary
        public String describeValue(long bitOffset, int bitSize) {
            String value = String.valueOf(new LLDBMemoryValue(pointer).computeAddress(0));
            if (bitOffset != 0 || bitSize != 0) {
                value = String.format("%s at offset %s in %s", LLDBSupport.toSizeString(bitSize), LLDBSupport.toSizeString(bitOffset), value);
            }
            return value;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return !pointer.isNull();
        }

        @Override
        @TruffleBoundary
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            if (canRead(bitOffset, bitSize)) {
                if (LLVMNativePointer.isInstance(pointer)) {
                    long asLong = LLVMNativePointer.cast(pointer).asNative();
                    if (bitOffset != 0) {
                        asLong >>>= bitOffset;
                    }

                    final int shift = LLVMDebugTypeConstants.DOUBLE_SIZE - bitSize;
                    if (shift > 0) {
                        asLong <<= shift;
                        asLong = signed ? asLong >> shift : asLong >>> shift;
                    }

                    if (signed) {
                        return BigInteger.valueOf(asLong);
                    } else {
                        return new BigInteger(Long.toUnsignedString(asLong));
                    }

                } else if (LLVMManagedPointer.isInstance(pointer)) {
                    return describeValue(bitOffset, bitSize);
                }
            }

            return super.readBigInteger(bitOffset, bitSize, signed);
        }

        @Override
        @TruffleBoundary
        public Object readAddress(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
                return pointer;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.ADDRESS_NAME, bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
            }
        }

        @Override
        public Object computeAddress(long bitOffset) {
            return new LLDBMemoryValue(pointer).computeAddress(bitOffset);
        }

        @Override
        public boolean isAlwaysSafeToDereference(long bitOffset) {
            if (LLDBSupport.isNestedManagedPointer(pointer) || LLDBSupport.pointsToObjectAccess(pointer)) {
                return true;
            }

            if (pointer.isNull()) {
                return false;
            }

            if (LLVMManagedPointer.isInstance(pointer)) {
                final LLVMManagedPointer managedPointer = LLVMManagedPointer.cast(pointer);

                // this is somewhat lazy, but saves us from actually handling these cases
                if (bitOffset != 0L || managedPointer.getOffset() != 0L) {
                    return false;
                }

                final Object target = managedPointer.getObject();
                return LLVMManagedPointer.isInstance(target);
            }

            return bitOffset == 0L && pointer.getExportType() != null;
        }

        @Override
        public LLVMDebugValue dereferencePointer(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
                return new LLDBMemoryValue(pointer);
            } else {
                return null;
            }
        }

        @Override
        public Object asInteropValue() {
            if (isInteropValue()) {
                Object foreign = null;

                if (LLVMNativePointer.isInstance(pointer)) {
                    long address = LLVMNativePointer.cast(pointer).asNative();
                    foreign = getHandleValue(address);
                } else if (LLVMManagedPointer.isInstance(pointer)) {
                    foreign = LLVMManagedPointer.cast(pointer).getObject();
                }

                if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(foreign)) {
                    return LLVMAsForeignLibrary.getFactory().getUncached().asForeign(foreign);
                }
            }
            return super.asInteropValue();
        }

        private static Object getHandleValue(long address) {
            LLVMContext context = LLVMLanguage.getContext();
            if (context.getHandleContainer().isHandle(address)) {
                LLVMManagedPointer value = context.getHandleContainer().getValue(null, address);
                if (value != null) {
                    return value.getObject();
                }
            }
            if (context.getDerefHandleContainer().isHandle(address)) {
                LLVMManagedPointer value = context.getDerefHandleContainer().getValue(null, address);
                if (value != null) {
                    return value.getObject();
                }
            }
            return null;
        }

        @Override
        @TruffleBoundary
        public boolean isInteropValue() {
            if (pointer.isNull()) {
                return false;

            } else if (LLVMNativePointer.isInstance(pointer)) {
                return getHandleValue(LLVMNativePointer.cast(pointer).asNative()) != null;

            } else if (LLVMManagedPointer.isInstance(pointer)) {
                final Object target = LLVMManagedPointer.cast(pointer).getObject();

                if (LLVMPointer.isInstance(target)) {
                    return false;

                } else if (LLDBSupport.pointsToObjectAccess(pointer)) {
                    return false;

                } else {
                    // Not sure how to replace this LLVMTypedForeignObject occurrence
                    return !(target instanceof LLVMTypedForeignObject);
                }
            } else {
                throw new IllegalStateException("Unsupported Pointer: " + pointer);
            }
        }

        @Override
        public boolean isManagedPointer() {
            return LLVMManagedPointer.isInstance(pointer);
        }

        @Override
        public Object getManagedPointerBase() {
            if (LLVMManagedPointer.isInstance(pointer)) {
                return LLVMManagedPointer.cast(pointer).getObject();
            } else {
                return null;
            }
        }

        @Override
        public long getManagedPointerOffset() {
            if (LLVMManagedPointer.isInstance(pointer)) {
                return LLVMManagedPointer.cast(pointer).getOffset();
            } else {
                return 0;
            }
        }
    }

    static final class Float extends LLDBConstant {

        private final float value;

        Float(float value) {
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset == 0 && bits == LLVMDebugTypeConstants.FLOAT_SIZE;
        }

        private int asInt(long bitOffset) {
            int valAsInt = java.lang.Float.floatToRawIntBits(value);
            if (bitOffset != 0) {
                valAsInt >>= bitOffset;
            }
            return valAsInt;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            if (bitOffset < LLVMDebugTypeConstants.FLOAT_SIZE) {
                final long asInt = asInt(bitOffset);
                return (asInt & 0x1) != 0;
            } else {
                return super.readBoolean(bitOffset);
            }
        }

        @Override
        public Object readFloat(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE)) {
                return value;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.FLOAT_NAME, bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
        }

        @Override
        @TruffleBoundary
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            if (LLVMDebugTypeConstants.FLOAT_SIZE - bitSize - bitOffset >= 0) {
                final int shift = LLVMDebugTypeConstants.FLOAT_SIZE - bitSize;
                int intVal = asInt(bitOffset);
                if (shift > 0) {
                    intVal <<= shift;
                    intVal = signed ? intVal >> shift : intVal >>> shift;
                }
                if (signed) {
                    return BigInteger.valueOf(intVal);
                } else {
                    return new BigInteger(Long.toUnsignedString(intVal));
                }

            } else {
                return super.readBigInteger(bitOffset, bitSize, signed);
            }
        }
    }

    static final class Double extends LLDBConstant {

        private final double value;

        Double(double value) {
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return LLVMDebugTypeConstants.DOUBLE_SIZE - bits - bitOffset >= 0;
        }

        private long asLong(long bitOffset) {
            long valAsLong = java.lang.Double.doubleToRawLongBits(value);
            if (bitOffset != 0) {
                valAsLong >>= bitOffset;
            }
            return valAsLong;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            if (bitOffset < LLVMDebugTypeConstants.DOUBLE_SIZE) {
                final long asLong = asLong(bitOffset);
                return (asLong & 0x1) != 0;
            } else {
                return super.readBoolean(bitOffset);
            }
        }

        @Override
        @TruffleBoundary
        public Object readFloat(long bitOffset) {
            if (LLVMDebugTypeConstants.DOUBLE_SIZE - bitOffset >= LLVMDebugTypeConstants.FLOAT_SIZE) {
                final int asInt = (int) asLong(bitOffset);
                return java.lang.Float.intBitsToFloat(asInt);
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.FLOAT_NAME, bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
        }

        @Override
        public Object readDouble(long bitOffset) {
            if (bitOffset == 0) {
                return value;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.DOUBLE_NAME, bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
            }
        }

        @Override
        @TruffleBoundary
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            if (LLVMDebugTypeConstants.DOUBLE_SIZE - bitSize - bitOffset >= 0) {
                final int shift = LLVMDebugTypeConstants.DOUBLE_SIZE - bitSize;
                long asLong = asLong(bitOffset);
                if (shift > 0) {
                    asLong <<= shift;
                    asLong = signed ? asLong >> shift : asLong >>> shift;
                }
                if (signed) {
                    return BigInteger.valueOf(asLong);
                } else {
                    return new BigInteger(Long.toUnsignedString(asLong));
                }

            } else {
                return super.readBigInteger(bitOffset, bitSize, signed);
            }
        }

        @Override
        public Object readAddress(long bitOffset) {
            if (bitOffset == 0) {
                return LLVMNativePointer.create(asLong(bitOffset));
            } else {
                return super.readAddress(bitOffset);
            }
        }
    }

    static final class BigFloat extends LLDBConstant {

        private static boolean isValidBitsize(int bits) {
            return bits == LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL || bits == LLVMDebugTypeConstants.LLVM80BIT_SIZE_SUGGESTED;
        }

        private final LLVM80BitFloat value;

        BigFloat(LLVM80BitFloat value) {
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return LLVM80BitFloat.toLLVMString(value);
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            // clang uses 80bit floats to represent the long double type which is indicated by
            // metadata to instead have 128 bits
            return bitOffset == 0 && isValidBitsize(bits);
        }

        @Override
        public Object read80BitFloat(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL)) {
                return LLVM80BitFloat.toLLVMString(value);
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.LLVM80BIT_NAME, bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
            }
        }
    }

    static final class Function extends LLDBConstant {

        private final LLVMFunctionDescriptor value;

        Function(LLVMFunctionDescriptor value) {
            this.value = value;
        }

        @Override
        protected Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            // this may only be used for function pointers
            return bitOffset == 0 && bits == LLVMDebugTypeConstants.ADDRESS_SIZE;
        }

        @Override
        public Object readAddress(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
                return getBaseValue();
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.ADDRESS_NAME, bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
            }
        }
    }

    static final class InteropValue extends LLDBConstant {

        private final Object value;

        private final long offset;

        InteropValue(Object value, long offset) {
            this.value = value;
            this.offset = offset;
        }

        @Override
        @TruffleBoundary
        protected Object getBaseValue() {
            if (offset > 0) {
                return String.format("offset %d in %s", offset, value);
            } else {
                return value;
            }
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return true;
        }

        @Override
        public boolean isInteropValue() {
            return true;
        }

        @Override
        public Object asInteropValue() {
            if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(value)) {
                return LLVMAsForeignLibrary.getFactory().getUncached().asForeign(value);
            }
            return value;
        }
    }
}
