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
package com.oracle.truffle.llvm.nodes.asm.syscall;

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

@NodeChildren({@NodeChild("rax"), @NodeChild("rdi"), @NodeChild("rsi"), @NodeChild("rdx"), @NodeChild("r10"), @NodeChild("r8"), @NodeChild("r9")})
public abstract class LLVMAMD64SyscallNode extends LLVMExpressionNode {
    protected static final int NUM_SYSCALLS = 332;

    protected static void exit(int code) {
        throw new LLVMExitException(code);
    }

    protected static LLVMAMD64SyscallOperationNode createNode(long rax) {
        switch ((int) rax) {
            case LLVMAMD64Syscall.SYS_read:
                return LLVMAMD64SyscallReadNodeGen.create();
            case LLVMAMD64Syscall.SYS_write:
                return LLVMAMD64SyscallWriteNodeGen.create();
            case LLVMAMD64Syscall.SYS_open:
                return LLVMAMD64SyscallOpenNodeGen.create();
            case LLVMAMD64Syscall.SYS_close:
                return new LLVMAMD64SyscallCloseNode();
            case LLVMAMD64Syscall.SYS_stat:
                return LLVMAMD64SyscallStatNodeGen.create();
            case LLVMAMD64Syscall.SYS_fstat:
                return LLVMAMD64SyscallFstatNodeGen.create();
            case LLVMAMD64Syscall.SYS_lstat:
                return LLVMAMD64SyscallLstatNodeGen.create();
            case LLVMAMD64Syscall.SYS_lseek:
                return new LLVMAMD64SyscallLseekNode();
            case LLVMAMD64Syscall.SYS_mmap:
                return LLVMAMD64SyscallMmapNodeGen.create();
            case LLVMAMD64Syscall.SYS_brk:
                return LLVMAMD64SyscallBrkNodeGen.create();
            case LLVMAMD64Syscall.SYS_rt_sigaction:
                return LLVMAMD64SyscallRtSigactionNodeGen.create();
            case LLVMAMD64Syscall.SYS_rt_sigprocmask:
                return LLVMAMD64SyscallRtSigprocmaskNodeGen.create();
            case LLVMAMD64Syscall.SYS_ioctl:
                return LLVMAMD64SyscallIoctlNodeGen.create();
            case LLVMAMD64Syscall.SYS_readv:
                return LLVMAMD64SyscallReadvNodeGen.create();
            case LLVMAMD64Syscall.SYS_writev:
                return LLVMAMD64SyscallWritevNodeGen.create();
            case LLVMAMD64Syscall.SYS_dup:
                return new LLVMAMD64SyscallDupNode();
            case LLVMAMD64Syscall.SYS_dup2:
                return new LLVMAMD64SyscallDup2Node();
            case LLVMAMD64Syscall.SYS_getpid:
                return new LLVMAMD64SyscallGetpidNode();
            case LLVMAMD64Syscall.SYS_sendfile:
                return LLVMAMD64SyscallSendfileNodeGen.create();
            case LLVMAMD64Syscall.SYS_exit:
            case LLVMAMD64Syscall.SYS_exit_group: // TODO: implement difference to SYS_exit
                return new LLVMAMD64SyscallExitNode();
            case LLVMAMD64Syscall.SYS_uname:
                return LLVMAMD64SyscallUnameNodeGen.create();
            case LLVMAMD64Syscall.SYS_fcntl:
                return LLVMAMD64SyscallFcntlNodeGen.create();
            case LLVMAMD64Syscall.SYS_ftruncate:
                return new LLVMAMD64SyscallFtruncateNode();
            case LLVMAMD64Syscall.SYS_getcwd:
                return LLVMAMD64SyscallGetcwdNodeGen.create();
            case LLVMAMD64Syscall.SYS_getuid:
                return new LLVMAMD64SyscallGetuidNode();
            case LLVMAMD64Syscall.SYS_getgid:
                return new LLVMAMD64SyscallGetgidNode();
            case LLVMAMD64Syscall.SYS_setuid:
                return new LLVMAMD64SyscallSetuidNode();
            case LLVMAMD64Syscall.SYS_setgid:
                return new LLVMAMD64SyscallSetgidNode();
            case LLVMAMD64Syscall.SYS_getppid:
                return new LLVMAMD64SyscallGetPpidNode();
            case LLVMAMD64Syscall.SYS_futex:
                return LLVMAMD64SyscallFutexNodeGen.create();
            case LLVMAMD64Syscall.SYS_clock_gettime:
                return LLVMAMD64SyscallClockGetTimeNodeGen.create();
            default:
                return new LLVMAMD64UnknownSyscallNode((int) rax);
        }
    }

    @Specialization(guards = "rax == cachedRax", limit = "NUM_SYSCALLS")
    protected long cachedSyscall(@SuppressWarnings("unused") long rax, Object rdi, Object rsi, Object rdx, Object r10, Object r8, Object r9,
                    @Cached("createNode(rax)") LLVMAMD64SyscallOperationNode node, @SuppressWarnings("unused") @Cached("rax") long cachedRax) {
        if (traceEnabled()) {
            trace(node);
        }
        return node.execute(rdi, rsi, rdx, r10, r8, r9);
    }

    @Specialization(replaces = "cachedSyscall")
    protected long executeI64(long rax, Object rdi, Object rsi, Object rdx, Object r10, Object r8, Object r9) {
        // TODO: implement big switch with type casts + logic + ...?
        CompilerDirectives.transferToInterpreter();
        return createNode(rax).execute(rdi, rsi, rdx, r10, r8, r9);
    }

    @CompilationFinal private boolean traceEnabledFlag;
    @CompilationFinal private PrintStream traceStream;

    private void cacheTrace() {
        if (traceStream == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            traceStream = SulongEngineOption.getStream(getContext().getEnv().getOptions().get(SulongEngineOption.DEBUG));
            traceEnabledFlag = SulongEngineOption.isTrue(getContext().getEnv().getOptions().get(SulongEngineOption.DEBUG));
        }
    }

    private boolean traceEnabled() {
        cacheTrace();
        return traceEnabledFlag;
    }

    private PrintStream traceStream() {
        cacheTrace();
        return traceStream;
    }

    @TruffleBoundary
    private void trace(LLVMAMD64SyscallOperationNode statement) {
        traceStream().println(("[sulong] syscall: " + statement.getName()));
    }
}
