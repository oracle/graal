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

import java.security.SecureRandom;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64RdRandNode extends LLVMExpressionNode {
    private final SecureRandom random = new SecureRandom();

    @Child protected LLVMAMD64WriteBooleanNode writeCFNode;

    // TODO: OF, SF, ZF, AF, PF = 0

    @TruffleBoundary
    protected int nextInt() {
        return random.nextInt();
    }

    @TruffleBoundary
    protected long nextLong() {
        return random.nextLong();
    }

    public LLVMAMD64RdRandNode(LLVMAMD64WriteBooleanNode writeCFNode) {
        this.writeCFNode = writeCFNode;
    }

    public abstract static class LLVMAMD64RdRandwNode extends LLVMAMD64RdRandNode {
        public LLVMAMD64RdRandwNode(LLVMAMD64WriteBooleanNode writeCFNode) {
            super(writeCFNode);
        }

        @Specialization
        protected short doI16(VirtualFrame frame) {
            writeCFNode.execute(frame, true);
            return (short) nextInt();
        }
    }

    public abstract static class LLVMAMD64RdRandlNode extends LLVMAMD64RdRandNode {
        public LLVMAMD64RdRandlNode(LLVMAMD64WriteBooleanNode cf) {
            super(cf);
        }

        @Specialization
        protected int doI32(VirtualFrame frame) {
            writeCFNode.execute(frame, true);
            return nextInt();
        }
    }

    public abstract static class LLVMAMD64RdRandqNode extends LLVMAMD64RdRandNode {
        public LLVMAMD64RdRandqNode(LLVMAMD64WriteBooleanNode cf) {
            super(cf);
        }

        @Specialization
        protected long doI64(VirtualFrame frame) {
            writeCFNode.execute(frame, true);
            return nextLong();
        }
    }
}
