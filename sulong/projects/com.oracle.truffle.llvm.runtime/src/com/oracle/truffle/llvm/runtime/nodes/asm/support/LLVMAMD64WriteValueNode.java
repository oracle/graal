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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMAMD64WriteValueNode extends LLVMNode {
    @Child protected LLVMExpressionNode data;
    @Child protected LLVMAMD64WriteNode write;

    public LLVMAMD64WriteValueNode(LLVMAMD64Target dst) {
        this.data = dst.createInput();
        this.write = dst.createTarget();
    }

    public abstract void execute(VirtualFrame frame, Object value);

    @Specialization
    protected void doI8(VirtualFrame frame, byte value) {
        Object info = data.executeGeneric(frame);
        write.execute(frame, info, value);
    }

    @Specialization
    protected void doI16(VirtualFrame frame, short value) {
        Object info = data.executeGeneric(frame);
        write.execute(frame, info, value);
    }

    @Specialization
    protected void doI32(VirtualFrame frame, int value) {
        Object info = data.executeGeneric(frame);
        write.execute(frame, info, value);
    }

    @Specialization
    protected void doI64(VirtualFrame frame, long value) {
        Object info = data.executeGeneric(frame);
        write.execute(frame, info, value);
    }

    @Specialization
    protected void doAddress(VirtualFrame frame, LLVMPointer value) {
        Object info = data.executeGeneric(frame);
        write.execute(frame, info, value);
    }
}
