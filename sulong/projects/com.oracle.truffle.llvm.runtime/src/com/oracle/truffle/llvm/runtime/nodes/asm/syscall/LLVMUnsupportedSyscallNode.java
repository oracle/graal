/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm.syscall;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.LLVMSyscallEntry;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;

public final class LLVMUnsupportedSyscallNode extends LLVMSyscallOperationNode {

    private final LLVMSyscallEntry syscall;
    private final long nr;

    public static LLVMUnsupportedSyscallNode create(long nr) {
        return new LLVMUnsupportedSyscallNode(nr, null);
    }

    public static LLVMUnsupportedSyscallNode create(LLVMSyscallEntry syscall) {
        return new LLVMUnsupportedSyscallNode(syscall.value(), syscall);
    }

    private LLVMUnsupportedSyscallNode(long nr, LLVMSyscallEntry syscall) {
        this.nr = nr;
        this.syscall = syscall;
    }

    @Override
    public String getName() {
        if (syscall != null) {
            return syscall.toString();
        }
        return "unsupported(" + nr + ")";
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public long execute(Object rdi, Object rsi, Object rdx, Object r10, Object r8, Object r9) {
        String details = syscall != null ? syscall.toString() : "0x" + Long.toHexString(nr) + " (" + nr + ")";
        throw new LLVMUnsupportedException(this, LLVMUnsupportedException.UnsupportedReason.UNSUPPORTED_SYSCALL, String.format("%s (%s %s)", details, LLVMInfo.SYSNAME, LLVMInfo.MACHINE));
    }
}
