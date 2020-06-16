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
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

@NodeChild(value = "left", type = LLVMExpressionNode.class)
@NodeChild(value = "right", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64XchgNode extends LLVMStatementNode {
    @Child protected LLVMAMD64WriteTupelNode out;

    public LLVMAMD64XchgNode(LLVMAMD64WriteTupelNode out) {
        this.out = out;
    }

    public abstract static class LLVMAMD64XchgbNode extends LLVMAMD64XchgNode {
        public LLVMAMD64XchgbNode(LLVMAMD64WriteTupelNode out) {
            super(out);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, byte a, byte b) {
            out.execute(frame, b, a);
        }
    }

    public abstract static class LLVMAMD64XchgwNode extends LLVMAMD64XchgNode {
        public LLVMAMD64XchgwNode(LLVMAMD64WriteTupelNode out) {
            super(out);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, short a, short b) {
            out.execute(frame, b, a);
        }
    }

    public abstract static class LLVMAMD64XchglNode extends LLVMAMD64XchgNode {
        public LLVMAMD64XchglNode(LLVMAMD64WriteTupelNode out) {
            super(out);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, int a, int b) {
            out.execute(frame, b, a);
        }
    }

    public abstract static class LLVMAMD64XchgqNode extends LLVMAMD64XchgNode {
        public LLVMAMD64XchgqNode(LLVMAMD64WriteTupelNode out) {
            super(out);
        }

        @Specialization
        protected void doOp(VirtualFrame frame, long a, long b) {
            out.execute(frame, b, a);
        }
    }
}
