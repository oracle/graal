/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import java.nio.ByteBuffer;

/**
 * Abstract type for variable width integers. Depending on the concrete bit width, either the
 * {@link LLVMIVarBitSmall} or {@link LLVMIVarBitLarge} types will be used.
 */
public abstract class LLVMIVarBit {

    public abstract LLVMIVarBit copy();

    public abstract byte getByteValue();

    public abstract byte getZeroExtendedByteValue();

    public abstract short getShortValue();

    public abstract short getZeroExtendedShortValue();

    public abstract int getIntValue();

    public abstract int getZeroExtendedIntValue();

    public abstract long getLongValue();

    public abstract long getZeroExtendedLongValue();

    public abstract int getBitSize();

    public abstract byte[] getBytes();

    public abstract LLVMIVarBit add(LLVMIVarBit right);

    public abstract LLVMIVarBit mul(LLVMIVarBit right);

    public abstract LLVMIVarBit sub(LLVMIVarBit right);

    public abstract LLVMIVarBit div(LLVMIVarBit right);

    public abstract LLVMIVarBit rem(LLVMIVarBit right);

    public abstract LLVMIVarBit unsignedRem(LLVMIVarBit right);

    public abstract LLVMIVarBit unsignedDiv(LLVMIVarBit right);

    public abstract boolean isEqual(LLVMIVarBit o);

    public abstract LLVMIVarBit and(LLVMIVarBit right);

    public abstract LLVMIVarBit or(LLVMIVarBit right);

    public abstract LLVMIVarBit xor(LLVMIVarBit right);

    public abstract LLVMIVarBit leftShift(LLVMIVarBit right);

    public abstract LLVMIVarBit logicalRightShift(LLVMIVarBit right);

    public abstract LLVMIVarBit arithmeticRightShift(LLVMIVarBit right);

    public abstract int signedCompare(LLVMIVarBit other);

    public abstract int unsignedCompare(LLVMIVarBit other);

    public abstract boolean isZero();

    public abstract BigInteger getDebugValue(boolean signed);

    public static LLVMIVarBit create(int bits, byte[] loadedBytes, int loadedArrBits, boolean signExtend) {
        if (bits <= LLVMIVarBitSmall.MAX_SIZE) {
            return new LLVMIVarBitSmall(bits, loadedBytes, loadedArrBits, signExtend);
        } else {
            return new LLVMIVarBitLarge(bits, loadedBytes, loadedArrBits, signExtend);
        }
    }

    public static LLVMIVarBit create(int bits, long loadedValue, int loadedArrBits, boolean signExtend) {
        if (bits <= LLVMIVarBitSmall.MAX_SIZE) {
            return new LLVMIVarBitSmall(bits, loadedValue);
        } else {
            return new LLVMIVarBitLarge(bits, longToArray(loadedValue), loadedArrBits, signExtend);
        }
    }

    public static LLVMIVarBit createZeroExt(int bits, byte from) {
        return create(bits, byteToArray(from), Byte.SIZE, false);
    }

    public static LLVMIVarBit createZeroExt(int bits, short from) {
        return create(bits, shortToArray(from), Short.SIZE, false);
    }

    public static LLVMIVarBit createZeroExt(int bits, int from) {
        return create(bits, intToArray(from), Integer.SIZE, false);
    }

    public static LLVMIVarBit createZeroExt(int bits, long from) {
        return create(bits, longToArray(from), Long.SIZE, false);
    }

    public static LLVMIVarBit fromBigInteger(int bits, BigInteger from) {
        assert bits > LLVMIVarBitSmall.MAX_SIZE;
        return LLVMIVarBitLarge.asIVar(bits, from);
    }

    public static LLVMIVarBit fromByte(int bits, byte from) {
        return create(bits, byteToArray(from), Byte.SIZE, true);
    }

    public static LLVMIVarBit fromShort(int bits, short from) {
        return create(bits, shortToArray(from), Short.SIZE, true);
    }

    public static LLVMIVarBit fromInt(int bits, int from) {
        return create(bits, intToArray(from), Integer.SIZE, true);
    }

    public static LLVMIVarBit fromLong(int bits, long from) {
        return create(bits, longToArray(from), Long.SIZE, true);
    }

    private static byte[] byteToArray(byte from) {
        return new byte[]{from};
    }

    private static byte[] shortToArray(short from) {
        byte[] array = new byte[Short.BYTES];
        ByteBuffer.wrap(array).putShort(from);
        return array;
    }

    private static byte[] intToArray(int from) {
        byte[] array = new byte[Integer.BYTES];
        ByteBuffer.wrap(array).putInt(from);
        return array;
    }

    private static byte[] longToArray(long from) {
        byte[] array = new byte[Long.BYTES];
        ByteBuffer.wrap(array).putLong(from);
        return array;
    }
}
