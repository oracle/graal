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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64RdtscNodeGen.LLVMAMD64RdtscReadNodeGen;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;

public abstract class LLVMAMD64RdtscNode extends LLVMExpressionNode {
    private final LLVMAMD64WriteI64RegisterNode low;
    private final LLVMAMD64WriteI64RegisterNode high;
    private final LLVMExpressionNode rdtsc;

    public LLVMAMD64RdtscNode(LLVMAMD64WriteI64RegisterNode low, LLVMAMD64WriteI64RegisterNode high) {
        this.low = low;
        this.high = high;
        rdtsc = LLVMAMD64RdtscReadNodeGen.create();
    }

    @Specialization
    public Object execute(VirtualFrame frame) {
        long value = rdtsc.executeI64(frame);
        long lo = value & LLVMExpressionNode.I32_MASK;
        long hi = (value >> LLVMExpressionNode.I32_SIZE_IN_BITS) & LLVMExpressionNode.I32_MASK;
        low.execute(frame, lo);
        high.execute(frame, hi);
        return null;
    }

    public abstract static class LLVMAMD64RdtscReadNode extends LLVMExpressionNode {
        @TruffleBoundary
        @Specialization
        public long executeRdtsc() {
            return System.currentTimeMillis();
        }
    }
}
