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
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesVectorNodeGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class CountLeadingZeroesNode {

    public static TypedBuiltinFactory getFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
                return TypedBuiltinFactory.vector2(CountLeadingZeroesI8NodeGen::create, CountLeadingZeroesNode::createVector);
            case I16:
                return TypedBuiltinFactory.vector2(CountLeadingZeroesI16NodeGen::create, CountLeadingZeroesNode::createVector);
            case I32:
                return TypedBuiltinFactory.vector2(CountLeadingZeroesI32NodeGen::create, CountLeadingZeroesNode::createVector);
            case I64:
                return TypedBuiltinFactory.vector2(CountLeadingZeroesI64NodeGen::create, CountLeadingZeroesNode::createVector);
            default:
                return null;
        }
    }

    /**
     * The vector form of {@code llvm.ctlz} takes an {@code is_zero_poison} flag as its second
     * argument, just like the scalar form. Sulong's implementation always returns the full bit
     * width for a zero lane rather than poison, so the flag carries no information and is dropped.
     */
    private static LLVMExpressionNode createVector(int vectorSize, LLVMExpressionNode value, @SuppressWarnings("unused") LLVMExpressionNode isZeroPoison) {
        return CountLeadingZeroesVectorNodeGen.create(vectorSize, value);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountLeadingZeroesI8Node extends LLVMBuiltin {

        @Specialization
        protected byte doI8(byte val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return (byte) (Integer.numberOfLeadingZeros(val & 0xFF) - Integer.SIZE + Byte.SIZE);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountLeadingZeroesI16Node extends LLVMBuiltin {

        @Specialization
        protected short doI16(short val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return (byte) (Integer.numberOfLeadingZeros(val & 0xFFFF) - Integer.SIZE + Short.SIZE);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountLeadingZeroesI32Node extends LLVMBuiltin {

        @Specialization
        protected int doI32(int val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return Integer.numberOfLeadingZeros(val);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountLeadingZeroesI64Node extends LLVMBuiltin {

        @Specialization
        protected long doI64(long val, @SuppressWarnings("unused") boolean isZeroUndefined) {
            return Long.numberOfLeadingZeros(val);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class CountLeadingZeroesVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        CountLeadingZeroesVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI8(LLVMI8Vector value) {
            assert value.getLength() == vectorLength;
            byte[] result = new byte[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = (byte) (Integer.numberOfLeadingZeros(value.getValue(i) & 0xFF) - Integer.SIZE + Byte.SIZE);
            }
            return LLVMI8Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI16(LLVMI16Vector value) {
            assert value.getLength() == vectorLength;
            short[] result = new short[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = (short) (Integer.numberOfLeadingZeros(value.getValue(i) & 0xFFFF) - Integer.SIZE + Short.SIZE);
            }
            return LLVMI16Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI32(LLVMI32Vector value) {
            assert value.getLength() == vectorLength;
            int[] result = new int[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Integer.numberOfLeadingZeros(value.getValue(i));
            }
            return LLVMI32Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI64(LLVMI64Vector value) {
            assert value.getLength() == vectorLength;
            long[] result = new long[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Long.numberOfLeadingZeros(value.getValue(i));
            }
            return LLVMI64Vector.create(result);
        }
    }
}
