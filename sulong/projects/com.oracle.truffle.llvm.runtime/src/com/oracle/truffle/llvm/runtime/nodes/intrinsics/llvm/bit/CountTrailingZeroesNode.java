/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI8NodeGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class CountTrailingZeroesNode {

    public static TypedBuiltinFactory getFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
                return TypedBuiltinFactory.simple2(CountTrailingZeroesI8NodeGen::create);
            case I16:
                return TypedBuiltinFactory.simple2(CountTrailingZeroesI16NodeGen::create);
            case I32:
                return TypedBuiltinFactory.simple2(CountTrailingZeroesI32NodeGen::create);
            case I64:
                return TypedBuiltinFactory.simple2(CountTrailingZeroesI64NodeGen::create);
            default:
                return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountTrailingZeroesI8Node extends LLVMBuiltin {

        @Specialization
        protected byte doI8(byte val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            final int trailingZeroes = Integer.numberOfTrailingZeros(val);
            return (byte) (trailingZeroes > Byte.SIZE ? Byte.SIZE : trailingZeroes);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountTrailingZeroesI16Node extends LLVMBuiltin {

        @Specialization
        protected short doI16(short val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            final int trailingZeroes = Integer.numberOfTrailingZeros(val);
            return (short) (trailingZeroes > Short.SIZE ? Short.SIZE : trailingZeroes);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountTrailingZeroesI32Node extends LLVMBuiltin {

        @Specialization
        protected int doI32(int val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return Integer.numberOfTrailingZeros(val);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountTrailingZeroesI64Node extends LLVMBuiltin {

        @Specialization
        protected long doI64(long val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return Long.numberOfTrailingZeros(val);
        }
    }
}
