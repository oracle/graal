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
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import java.math.BigInteger;

final class LLVMAddressValueProvider implements LLVMDebugValueProvider {

    private final LLVMAddress baseAddress;

    LLVMAddressValueProvider(LLVMAddress baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return !LLVMAddress.nullPointer().equals(baseAddress);
    }

    private static final int BOOLEAN_SIZE = 1;

    @Override
    public boolean readBoolean(long bitOffset) {
        if (!canRead(bitOffset, BOOLEAN_SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.getI1(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return readUnalignedBoolean(bitOffset);
        }
    }

    @TruffleBoundary
    private boolean readUnalignedBoolean(long bitOffset) {
        return readUnsignedInteger(bitOffset, BOOLEAN_SIZE).testBit(1);
    }

    @Override
    public Object readFloat(long bitOffset) {
        if (!canRead(bitOffset, Float.SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.getFloat(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return "Offset must be byte-aligned: " + bitOffset;
        }
    }

    @Override
    public Object readDouble(long bitOffset) {
        if (!canRead(bitOffset, Double.SIZE)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.getDouble(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return "Offset must be byte-aligned: " + bitOffset;
        }
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        if (!canRead(bitOffset, LLVM80BitFloat.BIT_WIDTH)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.get80BitFloat(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return "Offset must be byte-aligned: " + bitOffset;
        }
    }

    @Override
    public Object readAddress(long bitOffset) {
        if (!canRead(bitOffset, LLVMAddress.WORD_LENGTH_BIT)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);

        } else if (isByteAligned(bitOffset)) {
            return LLVMMemory.getAddress(baseAddress.increment(bitOffset / Byte.SIZE));

        } else {
            return "Offset must be byte-aligned: " + bitOffset;
        }
    }

    @Override
    @TruffleBoundary
    public Object readUnknown(long bitOffset, int bitSize) {
        if (!canRead(bitOffset, LLVMAddress.WORD_LENGTH_BIT)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        }

        final byte[] bytes = readUnsignedInteger(bitOffset, bitSize).toByteArray();
        final StringBuilder builder = new StringBuilder(bytes.length * 2 + 2);
        builder.append("0x");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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
    @TruffleBoundary
    public BigInteger readUnsignedInteger(long bitOffset, int bitSize) {
        if (!canRead(bitOffset, bitSize)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        }

        long byteOffset = bitOffset / Byte.SIZE;
        long address = baseAddress.increment(byteOffset).getVal();

        // the most common cases are byte-aligned integers
        if (isByteAligned(bitOffset)) {
            switch (bitSize) {
                case Byte.SIZE:
                    return BigInteger.valueOf(Byte.toUnsignedInt(LLVMMemory.getI8(address)));

                case Short.SIZE:
                    return BigInteger.valueOf(Short.toUnsignedInt(LLVMMemory.getI16(address)));

                case Integer.SIZE:
                    return BigInteger.valueOf(Integer.toUnsignedLong(LLVMMemory.getI32(address)));
            }
        }

        return readBigInt(bitOffset, bitSize, false);
    }

    @Override
    @TruffleBoundary
    public BigInteger readSignedInteger(long bitOffset, int bitSize) {
        if (!canRead(bitOffset, bitSize)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot read from " + baseAddress);
        }

        long byteOffset = bitOffset / Byte.SIZE;
        long address = baseAddress.increment(byteOffset).getVal();

        // the most common cases are byte-aligned integers
        if (isByteAligned(bitOffset)) {
            switch (bitSize) {
                case Byte.SIZE:
                    return BigInteger.valueOf(LLVMMemory.getI8(address));

                case Short.SIZE:
                    return BigInteger.valueOf(LLVMMemory.getI16(address));

                case Integer.SIZE:
                    return BigInteger.valueOf(LLVMMemory.getI32(address));

                case Long.SIZE:
                    return BigInteger.valueOf(LLVMMemory.getI64(address));
            }
        }

        return readBigInt(bitOffset, bitSize, true);
    }

    private BigInteger readBigInt(long bitOffset, int bitSize, boolean signed) {
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
