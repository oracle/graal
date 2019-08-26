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
package com.oracle.truffle.llvm.runtime.nodes.asm.support;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMAMD64WriteTupelNode extends LLVMNode {
    @Child private LLVMAMD64WriteValueNode write1;
    @Child private LLVMAMD64WriteValueNode write2;

    public LLVMAMD64WriteTupelNode(LLVMAMD64WriteValueNode write1, LLVMAMD64WriteValueNode write2) {
        this.write1 = write1;
        this.write2 = write2;
    }

    public abstract void execute(VirtualFrame frame, Object value1, Object value2);

    @Specialization
    protected void doOp(VirtualFrame frame, byte value1, byte value2) {
        write1.execute(frame, value1);
        write2.execute(frame, value2);
    }

    @Specialization
    protected void doOp(VirtualFrame frame, short value1, short value2) {
        write1.execute(frame, value1);
        write2.execute(frame, value2);
    }

    @Specialization
    protected void doOp(VirtualFrame frame, int value1, int value2) {
        write1.execute(frame, value1);
        write2.execute(frame, value2);
    }

    @Specialization
    protected void doOp(VirtualFrame frame, long value1, long value2) {
        write1.execute(frame, value1);
        write2.execute(frame, value2);
    }
}
