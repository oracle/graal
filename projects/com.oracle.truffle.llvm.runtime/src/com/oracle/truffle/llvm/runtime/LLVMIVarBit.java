/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

// see https://bugs.chromium.org/p/nativeclient/issues/detail?id=3360 for use cases where variable ints arise
@ValueType
public final class LLVMIVarBit {

    private final int bits;

    private final byte[] arr;

    LLVMIVarBit(int bits, byte[] arr) {
        this.bits = bits;
        if (CompilerDirectives.inInterpreter()) {
            LLVMLogger.performanceWarning("constructing a variable bit number!");
        }
        // TODO: what about sign extension?
        this.arr = new byte[getByteSize()];
        if (getByteSize() >= arr.length) {
            System.arraycopy(arr, 0, this.arr, getByteSize() - arr.length, arr.length);
        } else {
            System.arraycopy(arr, arr.length - getByteSize(), this.arr, 0, this.arr.length);
        }
        assert this.arr.length == getByteSize();
    }

    public static LLVMIVarBit create(int bitWidth, byte[] loadedBytes) {
        return new LLVMIVarBit(bitWidth, loadedBytes);
    }

    public static LLVMIVarBit createZeroExt(int bits, int from) {
        return create(bits, ByteBuffer.allocate(Integer.BYTES).putInt(from).array());
    }

    public static LLVMIVarBit createZeroExt(int bits, long from) {
        return create(bits, ByteBuffer.allocate(Long.BYTES).putLong(from).array());
    }

    public static LLVMIVarBit fromByte(int bits, byte from) {
        return new LLVMIVarBit(bits, ByteBuffer.allocate(Byte.BYTES).put(from).array());
    }

    public static LLVMIVarBit fromShort(int bits, short from) {
        return new LLVMIVarBit(bits, ByteBuffer.allocate(Short.BYTES).putShort(from).array());
    }

    public static LLVMIVarBit fromInt(int bits, int from) {
        return new LLVMIVarBit(bits, ByteBuffer.allocate(Integer.BYTES).putInt(from).array());
    }

    public static LLVMIVarBit fromLong(int bits, long from) {
        return create(bits, ByteBuffer.allocate(Long.BYTES).putLong(from).array());
    }

    private int getByteSize() {
        int nrFullBytes = bits / Byte.SIZE;
        if (bits % Byte.SIZE != 0) {
            return nrFullBytes + 1;
        } else {
            return nrFullBytes;
        }
    }

    @TruffleBoundary
    private static BigInteger bigInt(LLVMIVarBit right) {
        return new BigInteger(right.getBytes());
    }

    @TruffleBoundary
    private BigInteger unsignedBigInt() {
        byte[] newArr = new byte[arr.length + 1];
        System.arraycopy(arr, 0, newArr, 1, arr.length);
        return new BigInteger(newArr);
    }

    @TruffleBoundary
    private BigInteger bigInt() {
        return new BigInteger(arr);
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
            bb.put(arr, 0, getByteSize());
        } else {
            BitSet bitSet = new BitSet(Byte.SIZE);
            int bitsToSet = bits % Byte.SIZE;
            for (int i = 0; i < bitsToSet; i++) {
                boolean isBitSet = ((arr[0] >> i) & 1) == 1;
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
            for (int i = 1; i < arr.length; i++) {
                bb.put(arr[i]);
            }
        }
        bb.position(Math.max(0, getByteSize() - minSizeBytes));
        return bb;
    }

    public boolean mostSignificantBit() {
        return getBit(bits % Byte.SIZE);
    }

    public boolean getBit(int pos) {
        int selectedBytePos = pos / Byte.SIZE;
        byte selectedByte = arr[selectedBytePos];
        int selectedBitPos = pos % Byte.SIZE;
        return ((selectedByte >> selectedBitPos) & 1) == 1;
    }

    @TruffleBoundary
    public byte getByteValue() {
        return getByteBuffer(Byte.BYTES, true).get();
    }

    @TruffleBoundary
    public short getShortValue() {
        return getByteBuffer(Short.BYTES, true).getShort();
    }

    @TruffleBoundary
    public int getIntValue() {
        ByteBuffer byteBuffer = getByteBuffer(Integer.BYTES, true);
        return byteBuffer.getInt();
    }

    @TruffleBoundary
    public int getZeroExtendedIntValue() {
        return getByteBuffer(Integer.BYTES, false).getInt();
    }

    @TruffleBoundary
    public long getLongValue() {
        return getByteBuffer(Long.BYTES, true).getLong();
    }

    @TruffleBoundary
    public long getZeroExtendedLongValue() {
        return getByteBuffer(Long.BYTES, false).getLong();
    }

    public byte[] getBytes() {
        assert arr.length == getByteSize() : arr.length + " " + getByteSize();
        return arr;
    }

    @TruffleBoundary
    public byte[] getSignExtendedBytes() {
        return getByteBuffer(getByteValue(), true).array();
    }

    @TruffleBoundary
    public LLVMIVarBit add(LLVMIVarBit right) {
        return asIVar(bigInt().add(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit mul(LLVMIVarBit right) {
        return asIVar(bigInt().multiply(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit sub(LLVMIVarBit right) {
        return asIVar(bigInt().subtract(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit div(LLVMIVarBit right) {
        return asIVar(bigInt().divide(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit rem(LLVMIVarBit right) {
        return asIVar(bigInt().remainder(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit unsignedRem(LLVMIVarBit right) {
        return asIVar(unsignedBigInt().remainder(bigInt(right)));
    }

    @TruffleBoundary
    public LLVMIVarBit unsignedDiv(LLVMIVarBit right) {
        return asIVar(unsignedBigInt().divide(bigInt(right)));
    }

    public int compare(LLVMIVarBit other) {
        for (int i = 0; i < getByteSize(); i++) {
            int diff = arr[i] - other.getBytes()[i];
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    interface SimpleOp {
        byte op(byte a, byte b);
    }

    LLVMIVarBit performOp(LLVMIVarBit right, SimpleOp op) {
        assert bits == right.bits;
        byte[] newArr = new byte[getByteSize()];
        byte[] other = right.getBytes();
        assert arr.length == other.length : Arrays.toString(arr) + " " + Arrays.toString(other);
        for (int i = 0; i < newArr.length; i++) {
            newArr[i] = op.op(arr[i], other[i]);
        }
        return new LLVMIVarBit(bits, newArr);
    }

    @TruffleBoundary
    public LLVMIVarBit and(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a & b));
    }

    @TruffleBoundary
    public LLVMIVarBit or(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a | b));
    }

    @TruffleBoundary
    public LLVMIVarBit xor(LLVMIVarBit right) {
        return performOp(right, (byte a, byte b) -> (byte) (a ^ b));
    }

    @TruffleBoundary
    public LLVMIVarBit leftShift(LLVMIVarBit right) {
        BigInteger result = bigInt().shiftLeft(right.getIntValue());
        return asIVar(bits, result);
    }

    @TruffleBoundary
    private LLVMIVarBit asIVar(BigInteger result) {
        return asIVar(bits, result);
    }

    static LLVMIVarBit asIVar(int bitSize, BigInteger result) {
        int i = Math.max(Byte.BYTES, bitSize / Byte.SIZE);
        byte[] newArr = new byte[i];
        byte[] bigIntArr = result.toByteArray();
        if (bigIntArr.length == newArr.length + 1) {
            System.arraycopy(bigIntArr, 1, newArr, 0, newArr.length);
        } else {
            int destPos = newArr.length - bigIntArr.length;
            System.arraycopy(bigIntArr, 0, newArr, destPos, bigIntArr.length);
            if (bigIntArr[0] < 0) {
                Arrays.fill(newArr, 0, destPos, (byte) -1);
            }
        }
        return new LLVMIVarBit(bitSize, newArr);
    }

    @TruffleBoundary
    public LLVMIVarBit logicalRightShift(LLVMIVarBit right) {
        int shiftAmount = right.getIntValue();
        BigInteger mask = BigInteger.valueOf(-1).shiftLeft(bits - shiftAmount).not();
        BigInteger result = new BigInteger(arr).shiftRight(shiftAmount).and(mask);
        return asIVar(result);
    }

    @TruffleBoundary
    public LLVMIVarBit arithmeticRightShift(LLVMIVarBit right) {
        BigInteger result = bigInt().shiftRight(right.getIntValue());
        return asIVar(result);
    }

    @TruffleBoundary
    public static LLVMIVarBit fromString(String stringValue, int bits) {
        BigInteger constAsBigInteger = new BigInteger(stringValue);
        return asIVar(bits, constAsBigInteger);
    }

}
