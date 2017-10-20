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
package com.oracle.truffle.llvm.nodes.intrinsics.c;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMOptionalArgNode;
import com.oracle.truffle.llvm.nodes.func.LLVMOptionalArgNode.Converter;
import com.oracle.truffle.llvm.nodes.func.LLVMOptionalArgNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;

public class LLVMSyscall extends LLVMIntrinsic {
    @Child protected LLVMAMD64SyscallNode syscall;
    @Child protected LLVMOptionalArgNode rax;
    @Child protected LLVMOptionalArgNode rdi;
    @Child protected LLVMOptionalArgNode rsi;
    @Child protected LLVMOptionalArgNode rdx;
    @Child protected LLVMOptionalArgNode r10;
    @Child protected LLVMOptionalArgNode r8;
    @Child protected LLVMOptionalArgNode r9;

    public LLVMSyscall() {
        Converter conv = new SyscallArgConverter();
        rax = LLVMOptionalArgNodeGen.create(conv, 1, (long) 0);
        rdi = LLVMOptionalArgNodeGen.create(conv, 2, (long) 0);
        rsi = LLVMOptionalArgNodeGen.create(conv, 3, (long) 0);
        rdx = LLVMOptionalArgNodeGen.create(conv, 4, (long) 0);
        r10 = LLVMOptionalArgNodeGen.create(conv, 5, (long) 0);
        r8 = LLVMOptionalArgNodeGen.create(conv, 6, (long) 0);
        r9 = LLVMOptionalArgNodeGen.create(conv, 7, (long) 0);
        syscall = LLVMAMD64SyscallNodeGen.create(rax, rdi, rsi, rdx, r10, r8, r9);
    }

    private class SyscallArgConverter implements LLVMOptionalArgNode.Converter {
        // sign extend all integer types to long
        @Override
        public Object convert(Object o) {
            if (o == null) {
                return (long) 0;
            } else if (o instanceof Long) {
                return o; // already a long
            } else if (o instanceof Integer) {
                return (long) (int) o;
            } else if (o instanceof Short) {
                return (long) (short) o;
            } else if (o instanceof Byte) {
                return (long) (byte) o;
            } else {
                return o;
            }
        }
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return syscall.executeGeneric(frame);
    }
}
