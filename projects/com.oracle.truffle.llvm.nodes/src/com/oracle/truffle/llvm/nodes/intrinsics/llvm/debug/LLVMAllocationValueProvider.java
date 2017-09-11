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
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import java.math.BigInteger;

final class LLVMAllocationValueProvider implements LLVMDebugValueProvider {

    private final LLVMAddress baseAddress;

    LLVMAllocationValueProvider(LLVMAddress baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    @TruffleBoundary
    public Object describeValue(long bitOffset, int bitSize) {
        return String.format("%s (%d bits at offset %d bits)", baseAddress, bitSize, bitOffset);
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return !LLVMAddress.nullPointer().equals(baseAddress);
    }

    @Override
    public Object readBoolean(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.getI1(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return readUnalignedBoolean(bitOffset);
        }
    }

    @TruffleBoundary
    private boolean readUnalignedBoolean(long bitOffset) {
        final Object integerObject = readBigInteger(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE, false);
        return integerObject instanceof BigInteger && !integerObject.equals(BigInteger.ZERO);
    }

    @Override
    public Object readFloat(long bitOffset) {
        if (canRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE) && isByteAligned(bitOffset)) {
            return LLVMMemory.getFloat(baseAddress.increment(bitOffset / Byte.SIZE));
        } else {
            return unavailable(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
        }
    }

    @Override
    public Object readDouble(long bitOffset) {
        if (canRead(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE) && isByteAligned(bitOffset)) {
            return LLVMMemory.getDouble(baseAddress.increment(bitOffset / Byte.SIZE));
        } else {
            return unavailable(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
        }
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        if (canRead(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL) && isByteAligned(bitOffset)) {
            return LLVMMemory.get80BitFloat(baseAddress.increment(bitOffset / Byte.SIZE));
        } else {
            return unavailable(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
        }
    }

    @Override
    public Object readAddress(long bitOffset) {
        if (canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE) && isByteAligned(bitOffset)) {
            return LLVMMemory.getAddress(baseAddress.increment(bitOffset / Byte.SIZE));
        } else {
            return unavailable(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
        }
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        if (canRead(bitOffset, bitSize)) {
            final Object integerObject = readBigInteger(bitOffset, bitSize, false);
            if (integerObject instanceof BigInteger) {
                return LLVMDebugValueProvider.toHexString((BigInteger) integerObject);
            }
        }
        return unavailable(bitOffset, bitSize);
    }

    @Override
    public Object computeAddress(long bitOffset) {
        if (LLVMAddress.nullPointer().equals(baseAddress)) {
            return baseAddress;
        } else {
            return baseAddress.increment(bitOffset / Byte.SIZE);
        }
    }

    @Override
    public String toString() {
        return baseAddress.toString();
    }

    @Override
    public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE) || !isByteAligned(bitOffset)) {
            return null;
        }

        final LLVMAddress address = LLVMMemory.getAddress(baseAddress.increment(bitOffset / Byte.SIZE));
        return new LLVMAllocationValueProvider(address);
    }

    @Override
    public boolean isInteropValue() {
        return false;
    }

    @Override
    public Object asInteropValue() {
        return null;
    }

    @Override
    @TruffleBoundary
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        if (!canRead(bitOffset, bitSize)) {
            return unavailable(bitOffset, bitSize);
        }

        // the most common cases are byte-aligned integers
        if (isByteAligned(bitOffset)) {
            final long byteOffset = bitOffset / Byte.SIZE;
            final long address = baseAddress.increment(byteOffset).getVal();
            if (signed) {
                switch (bitSize) {
                    case LLVMDebugTypeConstants.BYTE_SIZE:
                        return BigInteger.valueOf(LLVMMemory.getI8(address));

                    case LLVMDebugTypeConstants.SHORT_SIZE:
                        return BigInteger.valueOf(LLVMMemory.getI16(address));

                    case LLVMDebugTypeConstants.INTEGER_SIZE:
                        return BigInteger.valueOf(LLVMMemory.getI32(address));

                    case LLVMDebugTypeConstants.LONG_SIZE:
                        return BigInteger.valueOf(LLVMMemory.getI64(address));
                }

            } else {
                switch (bitSize) {
                    case LLVMDebugTypeConstants.BYTE_SIZE:
                        return BigInteger.valueOf(Byte.toUnsignedInt(LLVMMemory.getI8(address)));

                    case LLVMDebugTypeConstants.SHORT_SIZE:
                        return BigInteger.valueOf(Short.toUnsignedInt(LLVMMemory.getI16(address)));

                    case LLVMDebugTypeConstants.INTEGER_SIZE:
                        return BigInteger.valueOf(Integer.toUnsignedLong(LLVMMemory.getI32(address)));
                }
            }
        }

        final int paddingBefore = (int) (bitOffset % Byte.SIZE);
        int totalBitSize = bitSize + paddingBefore;

        int paddingAfter = totalBitSize % Byte.SIZE;
        if (paddingAfter != 0) {
            paddingAfter = Byte.SIZE - paddingAfter;
        }
        totalBitSize += paddingAfter;

        LLVMIVarBit var = LLVMMemory.getIVarBit(baseAddress.increment(bitOffset / Byte.SIZE), totalBitSize);

        if (paddingAfter != 0) {
            var = var.leftShift(LLVMIVarBit.fromInt(Integer.SIZE, paddingAfter));
        }

        final int totalPadding = paddingBefore + paddingAfter;
        final LLVMIVarBit shiftRight = LLVMIVarBit.fromInt(Integer.SIZE, totalPadding);
        if (totalPadding != 0) {
            var = signed ? var.arithmeticRightShift(shiftRight) : var.logicalRightShift(shiftRight);
        }
        return signed ? var.asBigInteger() : var.asUnsignedBigInteger();
    }

    private static boolean isByteAligned(long offset) {
        return (offset & (Byte.SIZE - 1)) == 0;
    }
}
