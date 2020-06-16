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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("src")
@NodeChild("dst")
public abstract class LLVMAMD64BsrNode extends LLVMExpressionNode {
    @Child protected LLVMAMD64WriteBooleanNode writeZFNode;
    protected final ConditionProfile profile;

    public LLVMAMD64BsrNode(LLVMAMD64WriteBooleanNode writeZFNode) {
        this.writeZFNode = writeZFNode;
        profile = ConditionProfile.createCountingProfile();
    }

    public abstract static class LLVMAMD64BsrwNode extends LLVMAMD64BsrNode {
        public LLVMAMD64BsrwNode(LLVMAMD64WriteBooleanNode writeZFNode) {
            super(writeZFNode);
        }

        @Specialization
        protected short doI16(VirtualFrame frame, short src, short dst) {
            if (profile.profile(src == 0)) {
                writeZFNode.execute(frame, true);
                return dst;
            } else {
                writeZFNode.execute(frame, false);
                int val = Short.toUnsignedInt(src);
                int numberOfLeadingZeros = Integer.numberOfLeadingZeros(val) - LLVMExpressionNode.I32_SIZE_IN_BITS + LLVMExpressionNode.I16_SIZE_IN_BITS;
                return (short) (LLVMExpressionNode.I16_SIZE_IN_BITS - numberOfLeadingZeros - 1);
            }
        }
    }

    public abstract static class LLVMAMD64BsrlNode extends LLVMAMD64BsrNode {
        public LLVMAMD64BsrlNode(LLVMAMD64WriteBooleanNode writeZFNode) {
            super(writeZFNode);
        }

        @Specialization
        protected int doI32(VirtualFrame frame, int src, int dst) {
            if (profile.profile(src == 0)) {
                writeZFNode.execute(frame, true);
                return dst;
            } else {
                writeZFNode.execute(frame, false);
                int numberOfLeadingZeros = Integer.numberOfLeadingZeros(src);
                return LLVMExpressionNode.I32_SIZE_IN_BITS - numberOfLeadingZeros - 1;
            }
        }
    }

    public abstract static class LLVMAMD64BsrqNode extends LLVMAMD64BsrNode {
        public LLVMAMD64BsrqNode(LLVMAMD64WriteBooleanNode writeZFNode) {
            super(writeZFNode);
        }

        @Specialization
        protected long doI64(VirtualFrame frame, long src, long dst) {
            if (profile.profile(src == 0)) {
                writeZFNode.execute(frame, true);
                return dst;
            } else {
                writeZFNode.execute(frame, false);
                int numberOfLeadingZeros = Long.numberOfLeadingZeros(src);
                return LLVMExpressionNode.I64_SIZE_IN_BITS - numberOfLeadingZeros - 1;
            }
        }
    }
}
