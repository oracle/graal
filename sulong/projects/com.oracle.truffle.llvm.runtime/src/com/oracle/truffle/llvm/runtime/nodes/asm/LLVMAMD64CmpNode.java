/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPAZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

@NodeChild(value = "left", type = LLVMExpressionNode.class)
@NodeChild(value = "right", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64CmpNode extends LLVMStatementNode {
    @Child protected LLVMAMD64UpdateCPAZSOFlagsNode flags;

    public LLVMAMD64CmpNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
        this.flags = flags;
    }

    public abstract static class LLVMAMD64CmpbNode extends LLVMAMD64CmpNode {
        public LLVMAMD64CmpbNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, byte left, byte right) {
            int result = left - right;
            boolean overflow = (byte) ((left ^ right) & (left ^ result)) < 0;
            boolean carry = Byte.toUnsignedInt(left) < Byte.toUnsignedInt(right);
            boolean adjust = (((left ^ right) ^ result) & 0x10) != 0;
            flags.execute(frame, overflow, carry, adjust, result);
        }
    }

    public abstract static class LLVMAMD64CmpwNode extends LLVMAMD64CmpNode {
        public LLVMAMD64CmpwNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, short left, short right) {
            int result = left - right;
            boolean overflow = (short) ((left ^ right) & (left ^ result)) < 0;
            boolean carry = Short.toUnsignedInt(left) < Short.toUnsignedInt(right);
            boolean adjust = (((left ^ right) ^ result) & 0x10) != 0;
            flags.execute(frame, overflow, carry, adjust, result);
        }
    }

    public abstract static class LLVMAMD64CmplNode extends LLVMAMD64CmpNode {
        public LLVMAMD64CmplNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, int left, int right) {
            int result = left - right;
            boolean overflow = ((left ^ right) & (left ^ result)) < 0;
            boolean carry = Integer.compareUnsigned(left, right) < 0;
            boolean adjust = (((left ^ right) ^ result) & 0x10) != 0;
            flags.execute(frame, overflow, carry, adjust, result);
        }
    }

    public abstract static class LLVMAMD64CmpqNode extends LLVMAMD64CmpNode {
        public LLVMAMD64CmpqNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, long left, long right) {
            long result = left - right;
            boolean overflow = ((left ^ right) & (left ^ result)) < 0;
            boolean carry = Long.compareUnsigned(left, right) < 0;
            boolean adjust = (((left ^ right) ^ result) & 0x10) != 0;
            flags.execute(frame, overflow, carry, adjust, result);
        }
    }
}
