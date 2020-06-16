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
package com.oracle.truffle.llvm.runtime.nodes.asm.support;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMAMD64AddressComputationNode extends LLVMExpressionNode {
    protected final int displacement;

    public LLVMAMD64AddressComputationNode(int displacement) {
        this.displacement = displacement;
    }

    protected static long toLong(int value) {
        // TODO: find out if signed cast is always correct
        return value;
    }

    @NodeChild(value = "base", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64AddressDisplacementComputationNode extends LLVMAMD64AddressComputationNode {
        public LLVMAMD64AddressDisplacementComputationNode(int displacement) {
            super(displacement);
        }

        @Specialization
        protected LLVMPointer doLLVMPointer(LLVMPointer base) {
            return base.increment(displacement);
        }

        @Specialization
        protected long doInt(int base) {
            return toLong(base) + displacement;
        }

        @Specialization
        protected long doLong(long base) {
            return base + displacement;
        }
    }

    @NodeChild(value = "base", type = LLVMExpressionNode.class)
    @NodeChild(value = "offset", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64AddressOffsetComputationNode extends LLVMAMD64AddressComputationNode {
        private final int shift;

        public LLVMAMD64AddressOffsetComputationNode(int displacement, int shift) {
            super(displacement);
            this.shift = shift;
        }

        @Specialization
        protected long doI64(int base, int offset) {
            return toLong(base) + displacement + (toLong(offset) << shift);
        }

        @Specialization
        protected long doI64(long base, int offset) {
            return base + displacement + (toLong(offset) << shift);
        }

        @Specialization
        protected long doI64(long base, long offset) {
            return base + displacement + (offset << shift);
        }

        @Specialization
        protected LLVMPointer doLLVMPointer(LLVMPointer base, int offset) {
            return base.increment(displacement + (toLong(offset) << shift));
        }

        @Specialization
        protected LLVMPointer doLLVMPointer(LLVMPointer base, long offset) {
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
        protected LLVMNativePointer doInt(int offset) {
            return LLVMNativePointer.create(displacement + (toLong(offset) << shift));
        }

        @Specialization
        protected long doLong(long offset) {
            return displacement + (offset << shift);
        }

        @Specialization
        protected LLVMNativePointer doLLVMNativePointer(LLVMNativePointer offset) {
            return LLVMNativePointer.create(displacement + (offset.asNative() << shift));
        }
    }

    @NodeChild(value = "base", type = LLVMExpressionNode.class)
    @NodeChild(value = "offset", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64AddressSegmentComputationNode extends LLVMAMD64AddressComputationNode {
        public LLVMAMD64AddressSegmentComputationNode() {
            super(0);
        }

        @Specialization
        protected LLVMPointer doLLVMPointer(LLVMPointer base, LLVMNativePointer offset) {
            return base.increment(offset.asNative());
        }

        @Specialization
        protected LLVMPointer doI64(LLVMPointer base, long offset) {
            return base.increment(offset);
        }
    }
}
