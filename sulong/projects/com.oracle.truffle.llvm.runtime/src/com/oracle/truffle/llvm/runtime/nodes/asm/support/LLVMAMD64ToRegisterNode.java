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

public abstract class LLVMAMD64ToRegisterNode extends LLVMExpressionNode {
    @NodeChild(value = "reg", type = LLVMExpressionNode.class)
    @NodeChild(value = "from", type = LLVMExpressionNode.class)
    public abstract static class LLVMI8ToR64 extends LLVMAMD64ToRegisterNode {
        private final int shift;
        private final long mask;

        public LLVMI8ToR64(int shift) {
            this.shift = shift;
            this.mask = ~((long) LLVMExpressionNode.I8_MASK << shift);
        }

        @Specialization
        protected long doI64(long reg, byte from) {
            return (reg & mask) | Byte.toUnsignedLong(from) << shift;
        }

        @Specialization
        protected long doI64(long reg, short from) {
            return (reg & mask) | (from & ~mask) << shift;
        }

        @Specialization
        protected long doI64(long reg, int from) {
            return (reg & mask) | (from & ~mask) << shift;
        }

        @Specialization
        protected long doI64(long reg, long from) {
            return (reg & mask) | (from & ~mask) << shift;
        }
    }

    @NodeChild(value = "reg", type = LLVMExpressionNode.class)
    @NodeChild(value = "from", type = LLVMExpressionNode.class)
    public abstract static class LLVMI16ToR64 extends LLVMAMD64ToRegisterNode {
        private final long mask = ~(long) LLVMExpressionNode.I16_MASK;

        @Specialization
        protected long doI64(long reg, byte from) {
            return (reg & mask) | Byte.toUnsignedLong(from);
        }

        @Specialization
        protected long doI64(long reg, short from) {
            return (reg & mask) | Short.toUnsignedLong(from);
        }

        @Specialization
        protected long doI64(long reg, int from) {
            return (reg & mask) | (from & ~mask);
        }

        @Specialization
        protected long doI64(long reg, long from) {
            return (reg & mask) | (from & ~mask);
        }
    }

    @NodeChild(value = "from", type = LLVMExpressionNode.class)
    public abstract static class LLVMI32ToR64 extends LLVMAMD64ToRegisterNode {
        private static final long MASK = 0xFFFFFFFFL;

        @Specialization
        protected long doI64(byte from) {
            return Byte.toUnsignedLong(from);
        }

        @Specialization
        protected long doI64(short from) {
            return Short.toUnsignedLong(from);
        }

        @Specialization
        protected long doI64(int from) {
            return Integer.toUnsignedLong(from);
        }

        @Specialization
        protected long doI64(long from) {
            return from & MASK;
        }
    }
}
