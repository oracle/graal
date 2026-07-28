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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsIVarNodeGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class CountSetBitsNode {

    public static TypedBuiltinFactory getFactory(PrimitiveKind type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case I8:
                return TypedBuiltinFactory.simple1(CountSetBitsI8NodeGen::create);
            case I16:
                return TypedBuiltinFactory.simple1(CountSetBitsI16NodeGen::create);
            case I32:
                return TypedBuiltinFactory.simple1(CountSetBitsI32NodeGen::create);
            case I64:
                return TypedBuiltinFactory.simple1(CountSetBitsI64NodeGen::create);
            default:
                return null;
        }
    }

    public static LLVMBuiltin createIVar(int bitWidth, LLVMExpressionNode value) {
        return CountSetBitsIVarNodeGen.create(value, bitWidth);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class CountSetBitsIVarNode extends LLVMBuiltin {

        protected abstract int getBitWidth();

        @Specialization
        protected LLVMIVarBit doIVar(LLVMIVarBit value) {
            int count = 0;
            byte[] bytes = value.getBytes();
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                if (i == 0 && value.getBitSize() % Byte.SIZE != 0) {
                    b &= (byte) (0xFF >>> (Byte.SIZE - value.getBitSize() % Byte.SIZE));
                }
                count += Integer.bitCount(Byte.toUnsignedInt(b));
            }
            return LLVMIVarBit.createZeroExt(getBitWidth(), count);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountSetBitsI8Node extends LLVMBuiltin {

        @Specialization
        protected byte doI8(byte val) {
            return (byte) Integer.bitCount(Byte.toUnsignedInt(val));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountSetBitsI16Node extends LLVMBuiltin {

        @Specialization
        protected short doI16(short val) {
            return (short) Integer.bitCount(Short.toUnsignedInt(val));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountSetBitsI32Node extends LLVMBuiltin {

        @Specialization
        protected int doI32(int val) {
            return Integer.bitCount(val);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountSetBitsI64Node extends LLVMBuiltin {

        @Specialization
        protected long doOp(long val) {
            return Long.bitCount(val);
        }
    }
}
