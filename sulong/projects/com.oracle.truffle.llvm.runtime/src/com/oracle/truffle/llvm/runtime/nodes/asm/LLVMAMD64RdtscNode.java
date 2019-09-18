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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdtscNodeGen.LLVMAMD64RdtscReadNodeGen;

public abstract class LLVMAMD64RdtscNode extends LLVMStatementNode {
    @Child private LLVMExpressionNode rdtsc;
    @Child private LLVMAMD64WriteTupelNode out;

    public LLVMAMD64RdtscNode(LLVMAMD64WriteTupelNode out) {
        this.out = out;
        rdtsc = LLVMAMD64RdtscReadNodeGen.create();
    }

    @Specialization
    protected void doOp(VirtualFrame frame) {
        long value = LLVMTypesGen.asLong(rdtsc.executeGeneric(frame));
        long lo = value & LLVMExpressionNode.I32_MASK;
        long hi = (value >> LLVMExpressionNode.I32_SIZE_IN_BITS) & LLVMExpressionNode.I32_MASK;
        out.execute(frame, lo, hi);
    }

    public abstract static class LLVMAMD64RdtscReadNode extends LLVMExpressionNode {
        @TruffleBoundary
        @Specialization
        protected long doRdtsc() {
            return System.currentTimeMillis();
        }
    }
}
