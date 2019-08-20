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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

/**
 * Implementation of variable-width integers with > 64 bits in size.
 */
@ValueType
public final class LLVMIVarBitLarge extends LLVMIVarBit {

    // see https://bugs.chromium.org/p/nativeclient/issues/detail?id=3360 for use cases where
    // variable ints arise

    private final int bits;

    // represents value as big-endian two's-complement
    @CompilationFinal(dimensions = 1) private final byte[] array;

    private LLVMIVarBitLarge(int bits, byte[] arr) {
        this.bits = bits;
        this.array = arr;

        assert bits > LLVMIVarBitSmall.MAX_SIZE;
        assert getByteSize() == arr.length;
    }

    LLVMIVarBitLarge(int bits, byte[] arr, int arrBits, boolean signExtend) {
        this.bits = bits;

        this.array = new byte[getByteSize()];
        if (getByteSize() >= arr.length) {
            System.arraycopy(arr, 0, this.array, getByteSize() - arr.length, arr.length);
        } else {
            System.arraycopy(arr, arr.length - getByteSize(), this.array, 0, this.array.length);
        }

        if (bits > arrBits && (bits % Byte.SIZE) != 0) {
            // we don't need to do sign/zero extension if we truncate bits

            boolean isNegative = signExtend && ((arr[0] & (1 << ((bits - 1) % Byte.SIZE))) != 0);
            if (isNegative) {
                this.array[0] |= 0xFF << (bits % Byte.SIZE);
            } else {
                this.array[0] &= 0xFF >>> (8 - (bits % Byte.SIZE));
            }
        }

        assert bits > LLVMIVarBitSmall.MAX_SIZE;
        assert getByteSize() == array.length;
    }

    @Override
    public LLVMIVarBitLarge copy() {
        if (CompilerDirectives.inCompiledCode()) {
            return new LLVMIVarBitLarge(bits, array);
        } else {
            return this;
        }
    }

    private int getByteSize() {
        return getByteSize(bits);
    }

    public static int getByteSize(int bits) {
        assert bits > 0;
        return (bits + Byte.SIZE - 1) / Byte.SIZE;
    }

    @TruffleBoundary
    private static BigInteger asBigInteger(LLVMIVarBit right) {
        return ((LLVMIVarBitLarge) right).asBigInteger();
    }

    @TruffleBoundary
    public BigInteger asUnsignedBigInteger() {
        if (array.length == 0) {
            return BigInteger.ZERO;
        }
        byte[] newArr = new byte[array.length + 1];
        System.arraycopy(array, 0, newArr, 1, array.length);
        return new BigInteger(newArr);
    }

    @TruffleBoundary
    public BigInteger asBigInteger() {
        if (array.length != 0) {
            return new BigInteger(array);
        } else {
            return BigInteger.ZERO;
        }
    }

    @TruffleBoundary
    private ByteBuffer getByteBuffer(int minSizeBytes, boolean signExtend) {
        int allocationSize = Math.max(minSizeBytes, getByteSize());
        ByteBuffer bb = ByteBuffer.allocate(allocationSize).order(ByteOrder.BIG_ENDIAN);
        boolean truncation = bits > minSizeBytes * Byte.SIZE;
        boolean shouldAddLeadingOnes = signExtend && mostSignificantBit();
        if (!truncation) {
            int bytesToFillUp = minSizeBytes - getByteSize();
            if (shouldAddLeadingOnes) {
                for (int i = 0; i < bytesToFillUp; i++) {
                    bb.put((byte) -1);
                }
            } else {
                for (int i = 0; i < bytesToFillUp; i++) {
                    bb.put((byte) 0);
                }
            }
        }
        if (bits % Byte.SIZE == 0) {
            bb.put(array, 0, getByteSize());
        } else {
            BitSet bitSet = new BitSet(Byte.SIZE);
            int bitsToSet = bits % Byte.SIZE;
            for (int i = 0; i < bitsToSet; i++) {
                boolean isBitSet = ((array[0] >> i) & 1) == 1;
                if (isBitSet) {
                    bitSet.set(i);
                }
            }

            if (shouldAddLeadingOnes) {
                for (int i = bitsToSet; i < Byte.SIZE; i++) {
                    bitSet.set(i);
                }
            }
            byte firstByteResult;
            if (bitSet.isEmpty()) {
                firstByteResult = 0;
            } else {
                firstByteResult = bitSet.toByteArray()[0];
            }
            // FIXME actually need to truncate or sign extend individual bits
            bb.put(firstByteResult);
            for (int i = 1; i < array.length; i++) {
                bb.put(array[i]);
            }
        }

        bb.position(Math.max(0, getByteSize() - minSizeBytes));
        return bb;
    }

    private boolean mostSignificantBit() {
        return getBit(bits - 1);
    }

    private boolean getBit(int pos) {
        int selectedBytePos = array.length - 1 - (pos / Byte.SIZE);
        byte selectedByte = array[selectedBytePos];
        int selectedBitPos = pos % Byte.SIZE;
        return ((selectedByte >> selectedBitPos) & 1) == 1;
    }

    @Override
    @TruffleBoundary
    public byte getByteValue() {
        return getByteBuffer(Byte.BYTES, true).get();
    }

    @Override
    @TruffleBoundary
    public byte getZeroExtendedByteValue() {
        return getByteBuffer(Byte.BYTES, false).get();
    }

    @Override
    @TruffleBoundary
    public short getShortValue() {
        return getByteBuffer(Short.BYTES, true).getShort();
    }

    @Override
    @TruffleBoundary
    public short getZeroExtendedShortValue() {
        return getByteBuffer(Short.BYTES, false).getShort();
    }

    @Override
    @TruffleBoundary
    public int getIntValue() {
        return getByteBuffer(Integer.BYTES, true).getInt();
    }

    @Override
    @TruffleBoundary
    public int getZeroExtendedIntValue() {
        return getByteBuffer(Integer.BYTES, false).getInt();
    }

    @Override
    @TruffleBoundary
    public long getLongValue() {
        return getByteBuffer(Long.BYTES, true).getLong();
    }

    @Override
    @TruffleBoundary
    public long getZeroExtendedLongValue() {
        return getByteBuffer(Long.BYTES, false).getLong();
    }

    @Override
    public int getBitSize() {
        return bits;
    }

    @Override
    public byte[] getBytes() {
        assert array.length == getByteSize() : array.length + " " + getByteSize();
        return array;
    }

    @TruffleBoundary
    public byte[] getSignExtendedBytes() {
        return getByteBuffer(array.length, true).array();
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge add(LLVMIVarBit right) {
        return asIVar(asBigInteger().add(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge mul(LLVMIVarBit right) {
        return asIVar(asBigInteger().multiply(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge sub(LLVMIVarBit right) {
        return asIVar(asBigInteger().subtract(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge div(LLVMIVarBit right) {
        return asIVar(asBigInteger().divide(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge rem(LLVMIVarBit right) {
        return asIVar(asBigInteger().remainder(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge unsignedRem(LLVMIVarBit right) {
        return asIVar(asUnsignedBigInteger().remainder(asBigInteger(right)));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge unsignedDiv(LLVMIVarBit right) {
        return asIVar(asUnsignedBigInteger().divide(asBigInteger(right)));
    }

    @Override
    public boolean isEqual(LLVMIVarBit o) {
        LLVMIVarBitLarge other = (LLVMIVarBitLarge) o;
        int thisWidth = bits;
        int otherWidth = other.bits;
        if (thisWidth != otherWidth) {
            return false;
        }
        byte[] otherArr = other.getBytes();
        for (int i = 0; i < getByteSize() - 1; i++) {
            int diff = array[i] - otherArr[i];
            if (diff != 0) {
                return false;
            }
        }
        byte thisByte = array[getByteSize() - 1];
        byte otherByte = otherArr[getByteSize() - 1];
        int maskLength = Byte.SIZE - (getByteSize() * Byte.SIZE - bits);
        byte mask = (byte) (((1 << maskLength) - 1) & 0xFF);
        return (thisByte & mask) == (otherByte & mask);
    }

    private interface SimpleOp {
        byte op(byte a, byte b);
    }

    private LLVMIVarBitLarge performOp(LLVMIVarBit r, SimpleOp op) {
        LLVMIVarBitLarge right = (LLVMIVarBitLarge) r;
        assert bits == right.bits;
        byte[] newArr = new byte[getByteSize()];
        byte[] other = right.getBytes();
        assert array.length == other.length : Arrays.toString(array) + " " + Arrays.toString(other);
        for (int i = 0; i < newArr.length; i++) {
            newArr[i] = op.op(array[i], other[i]);
        }
        return new LLVMIVarBitLarge(bits, newArr);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge and(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a & b));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge or(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a | b));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge xor(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a ^ b));
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBitLarge leftShift(LLVMIVarBit right) {
        BigInteger result = asBigInteger().shiftLeft(right.getIntValue());
        return asIVar(bits, result);
    }

    @TruffleBoundary
    private LLVMIVarBitLarge asIVar(BigInteger result) {
        return asIVar(bits, result);
    }

    static LLVMIVarBitLarge asIVar(int bitSize, BigInteger result) {
        byte[] newArr = new byte[getByteSize(bitSize)];
        byte[] bigIntArr = result.toByteArray();

        if (newArr.length > bigIntArr.length) {
            int diff = newArr.length - bigIntArr.length;
            for (int j = diff; j < newArr.length; j++) {
                newArr[j] = bigIntArr[j - diff];
            }
            for (int j = 0; j < diff; j++) {
                newArr[j] = bigIntArr[0] < 0 ? (byte) -1 : 0;
            }
        } else {
            int diff = bigIntArr.length - newArr.length;
            for (int j = 0; j < newArr.length; j++) {
                newArr[j] = bigIntArr[j + diff];
            }
        }
        int resultLengthIncludingSign = result.bitLength() + (result.signum() == -1 ? 1 : 0);
        return new LLVMIVarBitLarge(bitSize, newArr, resultLengthIncludingSign, result.signum() == -1);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBit logicalRightShift(LLVMIVarBit right) {
        int shiftAmount = right.getIntValue();
        BigInteger mask = BigInteger.valueOf(-1).shiftLeft(bits - shiftAmount).not();
        BigInteger result = new BigInteger(array).shiftRight(shiftAmount).and(mask);
        return asIVar(result);
    }

    @Override
    @TruffleBoundary
    public LLVMIVarBit arithmeticRightShift(LLVMIVarBit right) {
        BigInteger result = asBigInteger().shiftRight(right.getIntValue());
        return asIVar(result);
    }

    @Override
    @TruffleBoundary
    public int signedCompare(LLVMIVarBit other) {
        return asBigInteger().compareTo(((LLVMIVarBitLarge) other).asBigInteger());
    }

    @Override
    @TruffleBoundary
    public int unsignedCompare(LLVMIVarBit other) {
        return asUnsignedBigInteger().compareTo(((LLVMIVarBitLarge) other).asUnsignedBigInteger());
    }

    @Override
    @TruffleBoundary
    public boolean isZero() {
        return array.length == 0 || BigInteger.ZERO.equals(asBigInteger());
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (isZero()) {
            return Integer.toString(0);
        }
        return String.format("i%d %s", getBitSize(), asBigInteger().toString());
    }

    @Override
    public BigInteger getDebugValue(boolean signed) {
        if (signed) {
            return asBigInteger();
        } else {
            return asUnsignedBigInteger();
        }
    }
}
