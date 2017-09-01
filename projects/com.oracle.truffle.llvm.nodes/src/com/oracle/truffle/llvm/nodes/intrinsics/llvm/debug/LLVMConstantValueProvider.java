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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;

import java.math.BigInteger;
import java.util.Objects;

abstract class LLVMConstantValueProvider implements LLVMDebugValueProvider {

    @Override
    public boolean readBoolean(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public Object readFloat(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public Object readDouble(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public Object readAddress(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        return getBaseValue();
    }

    @Override
    public Object computeAddress(long bitOffset) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
    }

    @Override
    public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("Cannot read value at offset " + bitOffset + "bits from " + getBaseValue());
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
    public String toString() {
        return Objects.toString(getBaseValue());
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
        public boolean readBoolean(long bitOffset) {
            return value != 0;
        }

        @Override
        @TruffleBoundary
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("Cannot read " + bitSize + " + bits at offset " + bitOffset + " from integer value " + value);
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
        public boolean readBoolean(long bitOffset) {
            return !value.isZero();
        }

        @Override
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException("Cannot read " + bitSize + " + bits at offset " + bitOffset + " from integer value " + value);
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
            return bitOffset == 0 && bits == LLVMAddress.WORD_LENGTH_BIT;
        }

        @Override
        public boolean readBoolean(long bitOffset) {
            return !address.equals(LLVMAddress.nullPointer());
        }

        @Override
        @TruffleBoundary
        public Object readAddress(long bitOffset) {
            if (bitOffset != 0) {
                return "Cannot read address from offset " + bitOffset + " in address value " + address;
            }
            return address;
        }

        @Override
        public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
            if (bitOffset != 0) {
                return null;
            }
            return new LLVMAddressValueProvider(address);
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
            return bitOffset == 0 && bits == java.lang.Float.SIZE;
        }

        @Override
        public Object readFloat(long bitOffset) {
            return value;
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
            return bitOffset == 0 && bits == java.lang.Double.SIZE;
        }

        @Override
        public Object readDouble(long bitOffset) {
            return value;
        }
    }

    static final class LLVM80BitFloat extends LLVMConstantValueProvider {

        private static boolean isValidBitsize(int bits) {
            return bits == 80 || bits == 128;
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
            return value;
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
            return bitOffset == 0 && bits == LLVMAddress.WORD_LENGTH_BIT;
        }

        @Override
        public Object readAddress(long bitOffset) {
            return getBaseValue();
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
        Object getBaseValue() {
            if (offset == 0) {
                return value;
            } else {
                return "Offset " + offset + " in " + value;
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
