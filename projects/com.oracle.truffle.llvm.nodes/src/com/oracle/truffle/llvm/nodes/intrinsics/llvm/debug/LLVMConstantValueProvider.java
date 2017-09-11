/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;

import java.math.BigInteger;

abstract class LLVMConstantValueProvider implements LLVMDebugValueProvider {

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
    public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
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

    abstract Object getBaseValue();

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        if (bitOffset >= 0 && bitSize >= 0) {
            return String.format("%d bits at offset %d in %s", bitSize, bitOffset, getBaseValue());
        } else {
            return String.valueOf(getBaseValue());
        }
    }

    static final class Integer extends LLVMConstantValueProvider {

        private final long size;
        private final long value;

        Integer(long size, long value) {
            this.size = size;
            this.value = value;
        }

        @Override
        Object getBaseValue() {
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
            } else {
                result >>>= Long.SIZE - bitSize;
            }

            return BigInteger.valueOf(result);
        }
    }

    static final class IVarBit extends LLVMConstantValueProvider {

        private final LLVMIVarBit value;

        IVarBit(LLVMIVarBit value) {
            this.value = value;
        }

        @Override
        Object getBaseValue() {
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

            if (signed) {
                return result.asBigInteger();

            } else {
                return result.asUnsignedBigInteger();
            }
        }
    }

    static final class Address extends LLVMConstantValueProvider {

        private final LLVMAddress address;

        Address(LLVMAddress address) {
            this.address = address;
        }

        @Override
        Object getBaseValue() {
            return address;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset == 0 && bits == LLVMDebugTypeConstants.ADDRESS_SIZE;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return !address.equals(LLVMAddress.nullPointer());
        }

        @Override
        @TruffleBoundary
        public Object readAddress(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
                return address;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.ADDRESS_NAME, bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
            }
        }

        @Override
        public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
                return new LLVMAllocationValueProvider(address);
            } else {
                return null;
            }
        }
    }

    static final class Float extends LLVMConstantValueProvider {

        private final float value;

        Float(float value) {
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset == 0 && bits == LLVMDebugTypeConstants.FLOAT_SIZE;
        }

        @Override
        public Object readFloat(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE)) {
                return value;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.FLOAT_NAME, bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
        }
    }

    static final class Double extends LLVMConstantValueProvider {

        private final double value;

        Double(double value) {
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return bitOffset == 0 && bits == LLVMDebugTypeConstants.DOUBLE_SIZE;
        }

        @Override
        public Object readDouble(long bitOffset) {
            if (canRead(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE)) {
                return value;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.DOUBLE_NAME, bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
            }
        }
    }

    static final class LLVM80BitFloat extends LLVMConstantValueProvider {

        private static boolean isValidBitsize(int bits) {
            return bits == LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL || bits == LLVMDebugTypeConstants.LLVM80BIT_SIZE_SUGGESTED;
        }

        private final com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat value;

        LLVM80BitFloat(com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat value) {
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
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
                return value;
            } else {
                return cannotInterpret(LLVMDebugTypeConstants.LLVM80BIT_NAME, bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
            }
        }
    }

    static final class Function extends LLVMConstantValueProvider {

        private final LLVMFunctionHandle value;
        private final LLVMContext context;

        Function(LLVMFunctionHandle value, LLVMContext context) {
            this.value = value;
            this.context = context;
        }

        @Override
        Object getBaseValue() {
            if (value.isNullFunction()) {
                return "NULL";
            } else {
                return context.getFunctionDescriptor(value);
            }
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

    static final class InteropValue extends LLVMConstantValueProvider {

        private final Object value;

        private final long offset;

        InteropValue(Object value, long offset) {
            this.value = value;
            this.offset = offset;
        }

        InteropValue(Object value) {
            this(value, 0L);
        }

        @Override
        @TruffleBoundary
        Object getBaseValue() {
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
            return value;
        }
    }
}
