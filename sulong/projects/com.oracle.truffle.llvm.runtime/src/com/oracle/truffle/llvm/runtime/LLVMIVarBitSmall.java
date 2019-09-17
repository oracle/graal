/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

/**
 * Efficient implementation of variable-width integers with <= 64 bits in size.
 */
@ValueType
public final class LLVMIVarBitSmall extends LLVMIVarBit {

    // see https://bugs.chromium.org/p/nativeclient/issues/detail?id=3360 for use cases where
    // variable ints arise

    public static final int MAX_SIZE = Long.SIZE;

    private static final long ALL_BITS = -1L;

    private final int bits;
    private final long value;

    LLVMIVarBitSmall(int bits, long value) {
        this.bits = bits;
        this.value = value;

        assert bits <= MAX_SIZE;
    }

    LLVMIVarBitSmall(int bits, byte[] arr, int arrBits, boolean signExtend) {
        this.bits = bits;

        long v = 0;
        for (int i = 0; i < arr.length; i++) {
            v = v << Byte.SIZE | (arr[i] & 0xff);
        }

        if (arrBits < bits) {
            v = getCleanedValue(v, arrBits, signExtend);
        }
        this.value = v;

        assert bits <= MAX_SIZE;
    }

    @Override
    public LLVMIVarBitSmall copy() {
        if (CompilerDirectives.inCompiledCode()) {
            return new LLVMIVarBitSmall(bits, value);
        } else {
            return this;
        }
    }

    private static long getCleanedValue(long value, int bits, boolean signed) {
        boolean oneExtend = signed && (value & (1 << (bits - 1))) != 0;

        if (oneExtend) {
            return value | (ALL_BITS << bits);
        } else {
            return value & (ALL_BITS >>> (Long.SIZE - bits));
        }
    }

    public long getCleanedValue(boolean signed) {
        return getCleanedValue(value, bits, signed);
    }

    @Override
    @TruffleBoundary
    public byte getByteValue() {
        return (byte) getCleanedValue(true);
    }

    @Override
    @TruffleBoundary
    public byte getZeroExtendedByteValue() {
        return (byte) getCleanedValue(false);
    }

    @Override
    @TruffleBoundary
    public short getShortValue() {
        return (short) getCleanedValue(true);
    }

    @Override
    @TruffleBoundary
    public short getZeroExtendedShortValue() {
        return (short) getCleanedValue(false);
    }

    @Override
    @TruffleBoundary
    public int getIntValue() {
        return (int) getCleanedValue(true);
    }

    @Override
    @TruffleBoundary
    public int getZeroExtendedIntValue() {
        return (int) getCleanedValue(false);
    }

    @Override
    @TruffleBoundary
    public long getLongValue() {
        return getCleanedValue(true);
    }

    @Override
    @TruffleBoundary
    public long getZeroExtendedLongValue() {
        return getCleanedValue(false);
    }

    @Override
    public int getBitSize() {
        return bits;
    }

    @Override
    public byte[] getBytes() {
        int byteSize = LLVMIVarBitLarge.getByteSize(bits);
        byte[] array = new byte[byteSize];

        long v = value;
        for (int i = array.length - 1; i >= 0; i--) {
            array[i] = (byte) v;
            v >>>= 8;
        }

        return array;
    }

    private static LLVMIVarBitSmall cast(LLVMIVarBit ivar) {
        return (LLVMIVarBitSmall) ivar;
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall add(LLVMIVarBit right) {
        return asIVar(value + cast(right).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall mul(LLVMIVarBit right) {
        return asIVar(value * cast(right).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall sub(LLVMIVarBit right) {
        return asIVar(value - cast(right).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall div(LLVMIVarBit right) {
        return asIVar(getCleanedValue(true) / cast(right).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall rem(LLVMIVarBit right) {
        return asIVar(getCleanedValue(true) % cast(right).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall unsignedRem(LLVMIVarBit right) {
        return asIVar(Long.remainderUnsigned(getCleanedValue(false), cast(right).getCleanedValue(false)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall unsignedDiv(LLVMIVarBit right) {
        return asIVar(Long.divideUnsigned(getCleanedValue(false), cast(right).getCleanedValue(false)));
    }

    @Override
    public boolean isEqual(LLVMIVarBit o) {
        return getCleanedValue(false) == cast(o).getCleanedValue(false);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall and(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, value & cast(right).value);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall or(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, value | cast(right).value);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall xor(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, value ^ cast(right).value);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall leftShift(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, value << cast(right).getCleanedValue(false));
    }

    @TruffleBoundary
    private LLVMIVarBitSmall asIVar(long result) {
        return new LLVMIVarBitSmall(bits, result);
    }

    static LLVMIVarBitSmall asIVar(int bitSize, BigInteger result) {
        int resultLengthIncludingSign = result.bitLength() + (result.signum() == -1 ? 1 : 0);
        long value = result.longValue();
        if (resultLengthIncludingSign < bitSize) {
            value = getCleanedValue(value, resultLengthIncludingSign, true);
        }
        return new LLVMIVarBitSmall(bitSize, value);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall logicalRightShift(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, getCleanedValue(false) >>> cast(right).getCleanedValue(false));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitSmall arithmeticRightShift(LLVMIVarBit right) {
        return new LLVMIVarBitSmall(bits, getCleanedValue(true) >> cast(right).getCleanedValue(false));
    }

    @Override
    @TruffleBoundary
    public int signedCompare(LLVMIVarBit other) {
        return Long.compare(getCleanedValue(true), cast(other).getCleanedValue(true));
    }

    @Override
    @TruffleBoundary
    public int unsignedCompare(LLVMIVarBit other) {
        return Long.compareUnsigned(getCleanedValue(false), cast(other).getCleanedValue(false));
    }

    @Override
    @TruffleBoundary
    public boolean isZero() {
        return getCleanedValue(false) == 0;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (isZero()) {
            return Integer.toString(0);
        }
        return String.format("i%d %s", getBitSize(), getDebugValue(true));
    }

    @Override
    @TruffleBoundary
    public BigInteger getDebugValue(boolean signed) {
        if (signed) {
            return BigInteger.valueOf(getCleanedValue(true));
        } else {
            long v = getCleanedValue(false);
            return BigInteger.valueOf(v >>> 1).shiftLeft(1).add(BigInteger.valueOf(v & 1));
        }
    }
}
