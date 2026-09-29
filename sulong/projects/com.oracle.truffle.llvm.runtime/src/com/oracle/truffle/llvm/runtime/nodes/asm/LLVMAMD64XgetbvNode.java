/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

@NodeChild(value = "xcr", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64XgetbvNode extends LLVMStatementNode {
    /*
     * XCR0 state-component bits. The values reported here must stay consistent with the OSXSAVE
     * and AVX feature bits reported by LLVMAMD64CpuidNode: feature-detection code (e.g. glibc's
     * __builtin_cpu_supports) only trusts CPUID's AVX bits after confirming via XGETBV(0) that
     * the OS keeps x87/SSE/AVX state enabled.
     */
    public static final int XCR0_X87 = 1 << 0;
    public static final int XCR0_SSE = 1 << 1;
    public static final int XCR0_AVX = 1 << 2;

    @Child private LLVMAMD64WriteValueNode eax;
    @Child private LLVMAMD64WriteValueNode edx;

    public LLVMAMD64XgetbvNode(LLVMAMD64WriteValueNode eax, LLVMAMD64WriteValueNode edx) {
        this.eax = eax;
        this.edx = edx;
    }

    @Specialization
    protected void doOp(VirtualFrame frame, int xcr) {
        int low;
        if (xcr == 0) {
            low = XCR0_X87 | XCR0_SSE | XCR0_AVX;
        } else {
            // unsupported XCR register: report no state components rather than faulting
            low = 0;
        }
        eax.execute(frame, low);
        edx.execute(frame, 0);
    }
}
