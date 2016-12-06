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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LongMultiplication;

public abstract class LLVMAMD64MulNode extends LLVMExpressionNode {
    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64MulbNode extends LLVMExpressionNode {
        @Specialization
        protected short executeI16(byte left, byte right) {
            return (short) (Byte.toUnsignedInt(left) * Byte.toUnsignedInt(right));
        }
    }

    @NodeChildren({@NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64MulwNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI16RegisterNode high;

        public LLVMAMD64MulwNode(LLVMAMD64WriteI16RegisterNode high) {
            this.high = high;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short left, short right) {
            int value = Short.toUnsignedInt(left) * Short.toUnsignedInt(right);
            short hi = (short) (value >> LLVMExpressionNode.I16_SIZE_IN_BITS);
            high.execute(frame, hi);
            return (short) value;
        }
    }

    @NodeChildren({@NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64MullNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI32RegisterNode high;

        public LLVMAMD64MullNode(LLVMAMD64WriteI32RegisterNode high) {
            this.high = high;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int left, int right) {
            long value = Integer.toUnsignedLong(left) * Integer.toUnsignedLong(right);
            int hi = (int) (value >> LLVMExpressionNode.I32_SIZE_IN_BITS);
            high.execute(frame, hi);
            return (int) value;
        }
    }

    @NodeChildren({@NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64MulqNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI64RegisterNode high;

        public LLVMAMD64MulqNode(LLVMAMD64WriteI64RegisterNode high) {
            this.high = high;
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long left, long right) {
            // FIXME: implement as unsigned operation
            long hi = LongMultiplication.multiplyHigh(left, right);
            high.execute(frame, hi);
            return left * right;
        }
    }
}
