/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitLarge;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitSmall;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMBitcastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMSignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMUnsignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
@NodeField(type = int.class, name = "bits")
public abstract class LLVMToVarINode extends LLVMExpressionNode {

    protected final boolean isRecursive;

    protected LLVMToVarINode() {
        this(false);
    }

    protected LLVMToVarINode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    public abstract int getBits();

    protected abstract LLVMIVarBit executeWith(long value);

    protected LLVMToVarINode createRecursive() {
        throw new IllegalStateException("abstract node LLVMToVarINode used");
    }

    /**
     * Converts the integral part of an IEEE-754 value directly to its two's-complement bit
     * representation. Using a Java integer cast here would first restrict the result to 64 bits,
     * while BigInteger is not available on Sulong's runtime-compilation path.
     */
    protected LLVMIVarBit floatingPointToIVar(double from) {
        long raw = Double.doubleToRawLongBits(from);
        int encodedExponent = (int) ((raw >>> 52) & 0x7ff);
        if (encodedExponent == 0x7ff) {
            // Converting NaN, infinity, or an out-of-range value is poison in LLVM IR.
            return LLVMIVarBit.fromLong(getBits(), 0);
        }

        long significand = raw & 0x000f_ffff_ffff_ffffL;
        int exponent;
        if (encodedExponent == 0) {
            exponent = -1022;
        } else {
            significand |= 0x0010_0000_0000_0000L;
            exponent = encodedExponent - 1023;
        }

        byte[] bytes = new byte[(getBits() + Byte.SIZE - 1) / Byte.SIZE];
        int shift = exponent - 52;
        for (int i = 0; i <= 52; i++) {
            int targetBit = i + shift;
            if (targetBit >= 0 && targetBit < getBits() && (significand & (1L << i)) != 0) {
                int byteIndex = bytes.length - 1 - targetBit / Byte.SIZE;
                bytes[byteIndex] |= (byte) (1 << (targetBit % Byte.SIZE));
            }
        }

        if (raw < 0 && significand != 0) {
            int carry = 1;
            for (int i = bytes.length - 1; i >= 0; i--) {
                int value = (~bytes[i] & 0xff) + carry;
                bytes[i] = (byte) value;
                carry = value >>> Byte.SIZE;
            }
            int unusedBits = bytes.length * Byte.SIZE - getBits();
            if (unusedBits != 0) {
                bytes[0] &= (byte) (0xff >>> unusedBits);
            }
        }
        return LLVMIVarBit.create(getBits(), bytes, getBits(), false);
    }

    @Specialization(guards = "!isRecursive")
    protected LLVMIVarBit doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMToVarINode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    public abstract static class LLVMSignedCastToIVarNode extends LLVMToVarINode {

        public LLVMSignedCastToIVarNode() {
        }

        public LLVMSignedCastToIVarNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMSignedCastToIVarNodeGen.create(true, null, getBits());
        }

        @Specialization
        protected LLVMIVarBit doBoolean(boolean from) {
            return from ? LLVMIVarBit.fromInt(getBits(), -1) : LLVMIVarBit.fromInt(getBits(), 0);
        }

        @Specialization
        protected LLVMIVarBit doI8(byte from) {
            return LLVMIVarBit.fromByte(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI16(short from) {
            return LLVMIVarBit.fromShort(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI32(int from) {
            return LLVMIVarBit.fromInt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI64(long from) {
            return LLVMIVarBit.fromLong(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doFloat(float from) {
            return floatingPointToIVar(from);
        }

        @Specialization
        protected LLVMIVarBit doDouble(double from) {
            return floatingPointToIVar(from);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBitLarge from) {
            return LLVMIVarBit.create(getBits(), from.getSignExtendedBytes(), from.getBitSize(), true);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBitSmall from) {
            return LLVMIVarBit.create(getBits(), from.getCleanedValue(true), from.getBitSize(), true);
        }

        @Specialization
        protected LLVMIVarBit do80BitFloat(LLVM80BitFloat from) {
            return LLVMIVarBit.create(getBits(), from.getBytesBigEndian(), LLVM80BitFloat.BIT_WIDTH, true);
        }

        @Specialization
        protected LLVMIVarBit do128BitFloat(LLVM128BitFloat from) {
            return LLVMIVarBit.create(getBits(), from.getBytesBigEndian(), LLVM128BitFloat.BIT_WIDTH, true);
        }
    }

    public abstract static class LLVMUnsignedCastToIVarNode extends LLVMToVarINode {

        public LLVMUnsignedCastToIVarNode() {
        }

        public LLVMUnsignedCastToIVarNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMUnsignedCastToIVarNodeGen.create(true, null, getBits());
        }

        @Specialization
        protected LLVMIVarBit doBoolean(boolean from) {
            return from ? LLVMIVarBit.createZeroExt(getBits(), 1) : LLVMIVarBit.createZeroExt(getBits(), 0);
        }

        @Specialization
        protected LLVMIVarBit doI8(byte from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI16(short from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI32(int from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI64(long from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doFloat(float from) {
            return floatingPointToIVar(from);
        }

        @Specialization
        protected LLVMIVarBit doDouble(double from) {
            return floatingPointToIVar(from);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBitLarge from) {
            return LLVMIVarBit.create(getBits(), from.getBytes(), from.getBitSize(), false);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBitSmall from) {
            return LLVMIVarBit.create(getBits(), from.getCleanedValue(false), from.getBitSize(), false);
        }
    }

    public abstract static class LLVMBitcastToIVarNode extends LLVMToVarINode {

        public LLVMBitcastToIVarNode() {
        }

        public LLVMBitcastToIVarNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMBitcastToIVarNodeGen.create(true, null, getBits());
        }

        @Specialization
        protected LLVMIVarBit doBoolean(boolean from) {
            return from ? LLVMIVarBit.fromInt(getBits(), 1) : LLVMIVarBit.fromInt(getBits(), 0);
        }

        @Specialization
        protected LLVMIVarBit doI8(byte from) {
            return LLVMIVarBit.fromByte(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI16(short from) {
            return LLVMIVarBit.fromShort(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI32(int from) {
            return LLVMIVarBit.fromInt(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doI64(long from) {
            return LLVMIVarBit.fromLong(getBits(), from);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBit from) {
            return from;
        }

        @Specialization
        protected LLVMIVarBit doFloat(float from) {
            return LLVMIVarBit.fromInt(getBits(), Float.floatToIntBits(from));
        }

        @Specialization
        protected LLVMIVarBit doDouble(double from) {
            return LLVMIVarBit.fromLong(getBits(), Double.doubleToLongBits(from));
        }

        @Specialization
        protected LLVMIVarBit do80BitFloat(LLVM80BitFloat from) {
            assert getBits() == LLVM80BitFloat.BIT_WIDTH;
            return LLVMIVarBit.create(getBits(), from.getBytesBigEndian(), LLVM80BitFloat.BIT_WIDTH, true);
        }

        @Specialization
        protected LLVMIVarBit do128BitFloat(LLVM128BitFloat from) {
            assert getBits() == LLVM128BitFloat.BIT_WIDTH;
            return LLVMIVarBit.create(getBits(), from.getBytesBigEndian(), LLVM128BitFloat.BIT_WIDTH, true);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMIVarBit doI1Vector(LLVMI1Vector from) {
            assert getBits() == from.getLength();
            return LLVMIVarBit.fromI1Vector(getBits(), from);
        }
    }
}
