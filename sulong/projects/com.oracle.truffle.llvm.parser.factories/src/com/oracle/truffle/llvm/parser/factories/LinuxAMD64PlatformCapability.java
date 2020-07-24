/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories;

import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallArchPrctlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallBrkNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallClockGetTimeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallExitNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallFutexNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallGetcwdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallMmapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallRtSigactionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallRtSigprocmaskNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallSetTidAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMUnknownSyscallNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.linux.amd64.LinuxAMD64Syscall;

final class LinuxAMD64PlatformCapability extends BasicPlatformCapability<LinuxAMD64Syscall> {

    LinuxAMD64PlatformCapability(boolean loadCxxLibraries) {
        super(LinuxAMD64Syscall.class, loadCxxLibraries);
    }

    @Override
    protected LLVMSyscallOperationNode createSyscallNode(LinuxAMD64Syscall syscall) {
        switch (syscall) {
            case SYS_mmap:
                return LLVMAMD64SyscallMmapNodeGen.create();
            case SYS_brk:
                return LLVMAMD64SyscallBrkNodeGen.create();
            case SYS_rt_sigaction:
                return LLVMAMD64SyscallRtSigactionNodeGen.create();
            case SYS_rt_sigprocmask:
                return LLVMAMD64SyscallRtSigprocmaskNodeGen.create();
            case SYS_exit:
            case SYS_exit_group: // TODO: implement difference to SYS_exit
                return new LLVMSyscallExitNode();
            case SYS_getcwd:
                return LLVMAMD64SyscallGetcwdNodeGen.create();
            case SYS_arch_prctl:
                return LLVMAMD64SyscallArchPrctlNodeGen.create();
            case SYS_futex:
                return LLVMAMD64SyscallFutexNodeGen.create();
            case SYS_set_tid_address:
                return LLVMAMD64SyscallSetTidAddressNodeGen.create();
            case SYS_clock_gettime:
                return LLVMAMD64SyscallClockGetTimeNodeGen.create();
            default:
                return new LLVMUnknownSyscallNode(syscall);
        }
    }
}
