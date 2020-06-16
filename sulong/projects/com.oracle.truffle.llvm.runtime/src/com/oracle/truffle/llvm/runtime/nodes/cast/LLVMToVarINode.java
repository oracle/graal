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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitLarge;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitSmall;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMBitcastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMSignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMUnsignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
@NodeField(type = int.class, name = "bits")
public abstract class LLVMToVarINode extends LLVMExpressionNode {

    public abstract int getBits();

    protected abstract LLVMIVarBit executeWith(long value);

    protected LLVMToVarINode createRecursive() {
        throw new IllegalStateException("abstract node LLVMToVarINode used");
    }

    @Specialization
    protected LLVMIVarBit doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMToVarINode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    public abstract static class LLVMSignedCastToIVarNode extends LLVMToVarINode {

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMSignedCastToIVarNodeGen.create(null, getBits());
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
    }

    public abstract static class LLVMUnsignedCastToIVarNode extends LLVMToVarINode {

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMUnsignedCastToIVarNodeGen.create(null, getBits());
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
        protected LLVMIVarBit doIVarBit(LLVMIVarBitLarge from) {
            return LLVMIVarBit.create(getBits(), from.getBytes(), from.getBitSize(), false);
        }

        @Specialization
        protected LLVMIVarBit doIVarBit(LLVMIVarBitSmall from) {
            return LLVMIVarBit.create(getBits(), from.getCleanedValue(false), from.getBitSize(), false);
        }
    }

    public abstract static class LLVMBitcastToIVarNode extends LLVMToVarINode {

        @Override
        protected LLVMToVarINode createRecursive() {
            return LLVMBitcastToIVarNodeGen.create(null, getBits());
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
    }
}
