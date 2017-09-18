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
package com.oracle.truffle.llvm.nodes.asm.support;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64AddressComputationNode extends LLVMExpressionNode {
    protected final int displacement;

    public LLVMAMD64AddressComputationNode(int displacement) {
        this.displacement = displacement;
    }

    @NodeChild(value = "base", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64AddressDisplacementComputationNode extends LLVMAMD64AddressComputationNode {
        public LLVMAMD64AddressDisplacementComputationNode(int displacement) {
            super(displacement);
        }

        @Specialization
        protected LLVMAddress executeLLVMAddress(LLVMAddress base) {
            return base.increment(displacement);
        }

        @Specialization
        protected int executeLLVMAddress(int base) {
            return base + displacement;
        }

        @Specialization
        protected long executeLLVMAddress(long base) {
            return base + displacement;
        }
    }

    @NodeChildren({@NodeChild(value = "base", type = LLVMExpressionNode.class), @NodeChild(value = "offset", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64AddressOffsetComputationNode extends LLVMAMD64AddressComputationNode {
        private final int shift;

        public LLVMAMD64AddressOffsetComputationNode(int displacement, int shift) {
            super(displacement);
            this.shift = shift;
        }

        @Specialization
        protected int executeI64(int base, int offset) {
            return base + displacement + (offset << shift);
        }

        @Specialization
        protected long executeI64(long base, int offset) {
            return base + displacement + (offset << shift);
        }

        @Specialization
        protected long executeI64(long base, long offset) {
            return base + displacement + (offset << shift);
        }

        @Specialization
        protected LLVMAddress executeLLVMAddress(LLVMAddress base, int offset) {
            return base.increment(displacement + (offset << shift));
        }

        @Specialization
        protected LLVMAddress executeLLVMAddress(LLVMAddress base, long offset) {
            return base.increment(displacement + (offset << shift));
        }
    }

    @NodeChild(value = "offset", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64AddressNoBaseOffsetComputationNode extends LLVMAMD64AddressComputationNode {
        private final int shift;

        public LLVMAMD64AddressNoBaseOffsetComputationNode(int displacement, int shift) {
            super(displacement);
            this.shift = shift;
        }

        @Specialization
        protected LLVMAddress executeLLVMAddress(int offset) {
            return LLVMAddress.fromLong(displacement + (offset << shift));
        }

        @Specialization
        protected long executeLLVMAddress(long offset) {
            return displacement + (offset << shift);
        }

        @Specialization
        protected LLVMAddress executeLLVMAddress(LLVMAddress offset) {
            return LLVMAddress.fromLong(displacement + (offset.getVal() << shift));
        }
    }
}
