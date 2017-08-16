/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64UpdateFlagsNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI8RegisterNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild("left"), @NodeChild("right")})
public abstract class LLVMAMD64XaddNode extends LLVMExpressionNode {
    @Child LLVMAMD64UpdateFlagsNode flags;

    private LLVMAMD64XaddNode(LLVMAMD64UpdateFlagsNode flags) {
        this.flags = flags;
    }

    public abstract static class LLVMAMD64XaddbNode extends LLVMAMD64XaddNode {
        @Child LLVMAMD64WriteI8RegisterNode src;

        public LLVMAMD64XaddbNode(LLVMAMD64UpdateFlagsNode flags, LLVMAMD64WriteI8RegisterNode src) {
            super(flags);
            this.src = src;
        }

        @Specialization
        protected byte executeI8(VirtualFrame frame, byte left, byte right) {
            byte result = (byte) (left + right);
            boolean overflow = (result < 0 && left > 0 && right > 0) || (result > 0 && left < 0 && right < 0);
            boolean carry = ((left < 0 || right < 0) && result > 0) || (left < 0 && right < 0);
            flags.execute(frame, overflow, carry, result);
            src.execute(frame, right);
            return result;
        }
    }

    public abstract static class LLVMAMD64XaddwNode extends LLVMAMD64XaddNode {
        @Child LLVMAMD64WriteI16RegisterNode src;

        public LLVMAMD64XaddwNode(LLVMAMD64UpdateFlagsNode flags, LLVMAMD64WriteI16RegisterNode src) {
            super(flags);
            this.src = src;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short left, short right) {
            short result = (short) (left + right);
            boolean overflow = (result < 0 && left > 0 && right > 0) || (result > 0 && left < 0 && right < 0);
            boolean carry = ((left < 0 || right < 0) && result > 0) || (left < 0 && right < 0);
            flags.execute(frame, overflow, carry, result);
            src.execute(frame, right);
            return result;
        }
    }

    public abstract static class LLVMAMD64XaddlNode extends LLVMAMD64XaddNode {
        @Child LLVMAMD64WriteI32RegisterNode src;

        public LLVMAMD64XaddlNode(LLVMAMD64UpdateFlagsNode flags, LLVMAMD64WriteI32RegisterNode src) {
            super(flags);
            this.src = src;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int left, int right) {
            int result = left + right;
            boolean overflow = (result < 0 && left > 0 && right > 0) || (result > 0 && left < 0 && right < 0);
            boolean carry = ((left < 0 || right < 0) && result > 0) || (left < 0 && right < 0);
            flags.execute(frame, overflow, carry, result);
            src.execute(frame, right);
            return result;
        }
    }

    public abstract static class LLVMAMD64XaddqNode extends LLVMAMD64XaddNode {
        @Child LLVMAMD64WriteI64RegisterNode src;

        public LLVMAMD64XaddqNode(LLVMAMD64UpdateFlagsNode flags, LLVMAMD64WriteI64RegisterNode src) {
            super(flags);
            this.src = src;
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long left, long right) {
            long result = left + right;
            boolean overflow = (result < 0 && left > 0 && right > 0) || (result > 0 && left < 0 && right < 0);
            boolean carry = ((left < 0 || right < 0) && result > 0) || (left < 0 && right < 0);
            flags.execute(frame, overflow, carry, result);
            src.execute(frame, right);
            return result;
        }
    }
}
