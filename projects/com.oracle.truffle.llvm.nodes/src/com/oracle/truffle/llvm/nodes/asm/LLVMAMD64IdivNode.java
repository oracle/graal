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
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64IdivNode extends LLVMExpressionNode {
    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64IdivbNode extends LLVMExpressionNode {
        @Specialization
        protected short executeI16(short left, byte right) {
            byte quotient = (byte) (left / right);
            byte remainder = (byte) (left % right);
            return (short) ((quotient & LLVMExpressionNode.I8_MASK) | ((remainder & LLVMExpressionNode.I8_MASK) << LLVMExpressionNode.I8_SIZE_IN_BITS));
        }
    }

    @NodeChildren({@NodeChild("high"), @NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64IdivwNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI16RegisterNode rem;

        public LLVMAMD64IdivwNode(LLVMAMD64WriteI16RegisterNode rem) {
            this.rem = rem;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short high, short left, short right) {
            int value = Short.toUnsignedInt(high) << LLVMExpressionNode.I16_SIZE_IN_BITS | Short.toUnsignedInt(left);
            short quotient = (short) (value / right);
            short remainder = (short) (value % right);
            rem.execute(frame, remainder);
            return quotient;
        }
    }

    @NodeChildren({@NodeChild("high"), @NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64IdivlNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI32RegisterNode rem;

        public LLVMAMD64IdivlNode(LLVMAMD64WriteI32RegisterNode rem) {
            this.rem = rem;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int high, int left, int right) {
            long value = Integer.toUnsignedLong(high) << LLVMExpressionNode.I32_SIZE_IN_BITS | Integer.toUnsignedLong(left);
            int quotient = (int) (value / right);
            int remainder = (int) (value % right);
            rem.execute(frame, remainder);
            return quotient;
        }
    }

    @NodeChildren({@NodeChild("high"), @NodeChild("left"), @NodeChild("right")})
    public abstract static class LLVMAMD64IdivqNode extends LLVMExpressionNode {
        private final LLVMAMD64WriteI64RegisterNode rem;

        public LLVMAMD64IdivqNode(LLVMAMD64WriteI64RegisterNode rem) {
            this.rem = rem;
        }

        // FIXME: implement properly
        @Specialization
        protected long executeI64(VirtualFrame frame, @SuppressWarnings("unused") long high, long left, long right) {
            long quotient = left / right;
            long remainder = left % right;
            rem.execute(frame, remainder);
            return quotient;
        }
    }
}
