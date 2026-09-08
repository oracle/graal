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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesVectorNodeGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class CountTrailingZeroesNode {

    public static TypedBuiltinFactory getFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
                return TypedBuiltinFactory.vector2(CountTrailingZeroesI8NodeGen::create, CountTrailingZeroesNode::createVector);
            case I16:
                return TypedBuiltinFactory.vector2(CountTrailingZeroesI16NodeGen::create, CountTrailingZeroesNode::createVector);
            case I32:
                return TypedBuiltinFactory.vector2(CountTrailingZeroesI32NodeGen::create, CountTrailingZeroesNode::createVector);
            case I64:
                return TypedBuiltinFactory.vector2(CountTrailingZeroesI64NodeGen::create, CountTrailingZeroesNode::createVector);
            default:
                return null;
        }
    }

    /**
     * The vector form of {@code llvm.cttz} takes an {@code is_zero_poison} flag as its second
     * argument, just like the scalar form. Sulong's implementation always returns the full bit
     * width for a zero lane rather than poison, so the flag carries no information and is dropped.
     */
    private static LLVMExpressionNode createVector(int vectorSize, LLVMExpressionNode value, @SuppressWarnings("unused") LLVMExpressionNode isZeroPoison) {
        return CountTrailingZeroesVectorNodeGen.create(vectorSize, value);
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

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountTrailingZeroesVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        CountTrailingZeroesVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI8(LLVMI8Vector value) {
            assert value.getLength() == vectorLength;
            byte[] result = new byte[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                final int trailingZeroes = Integer.numberOfTrailingZeros(value.getValue(i));
                result[i] = (byte) (trailingZeroes > Byte.SIZE ? Byte.SIZE : trailingZeroes);
            }
            return LLVMI8Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI16(LLVMI16Vector value) {
            assert value.getLength() == vectorLength;
            short[] result = new short[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                final int trailingZeroes = Integer.numberOfTrailingZeros(value.getValue(i));
                result[i] = (short) (trailingZeroes > Short.SIZE ? Short.SIZE : trailingZeroes);
            }
            return LLVMI16Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI32(LLVMI32Vector value) {
            assert value.getLength() == vectorLength;
            int[] result = new int[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Integer.numberOfTrailingZeros(value.getValue(i));
            }
            return LLVMI32Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI64(LLVMI64Vector value) {
            assert value.getLength() == vectorLength;
            long[] result = new long[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Long.numberOfTrailingZeros(value.getValue(i));
            }
            return LLVMI64Vector.create(result);
        }
    }
}
