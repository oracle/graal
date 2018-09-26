/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64Syscall;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallAcceptNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallAccessNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallArchPrctlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallBindNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallBrkNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallChmodNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallChownNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallClockGetTimeNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallCloseNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallConnectNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallDup2Node;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallDupNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallExitNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFaccessatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFcntlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFstatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFstatfsNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFtruncateNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallFutexNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetPpidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetcwdNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetdents64NodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetegidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGeteuidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetgidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetgroupsNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetpgidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetpidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetsocknameNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetsockoptNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGettidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallGetuidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallIoctlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallListenNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallLseekNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallLstatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallMmapNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallOpenNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallPipe2NodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallPipeNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallPollNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallReadNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallReadvNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRecvfromNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRecvmsgNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRenameNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRenameatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRtSigactionNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallRtSigprocmaskNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSendfileNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSendmsgNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSendtoNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSetTidAddressNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSetgidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSetsockoptNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSetuidNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSocketNode;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallStatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallStatfsNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallSyslogNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallUnameNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallUnlinkNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallUtimensatNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallWriteNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64SyscallWritevNodeGen;
import com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64UnknownSyscallNode;
import com.oracle.truffle.llvm.runtime.SystemContextExtension;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;

public class BasicSystemContextExtension extends SystemContextExtension {

    protected static final String LIBSULONG_FILENAME = "libsulong.bc";

    @Override
    public String[] getSulongDefaultLibraries() {
        return new String[]{LIBSULONG_FILENAME};
    }

    @Override
    public LLVMSyscallOperationNode createSyscallNode(long index) {
        switch ((int) index) {
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
            case LLVMAMD64Syscall.SYS_poll:
                return LLVMAMD64SyscallPollNodeGen.create();
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
            case LLVMAMD64Syscall.SYS_access:
                return LLVMAMD64SyscallAccessNodeGen.create();
            case LLVMAMD64Syscall.SYS_pipe:
                return LLVMAMD64SyscallPipeNodeGen.create();
            case LLVMAMD64Syscall.SYS_dup:
                return new LLVMAMD64SyscallDupNode();
            case LLVMAMD64Syscall.SYS_dup2:
                return new LLVMAMD64SyscallDup2Node();
            case LLVMAMD64Syscall.SYS_getpid:
                return new LLVMAMD64SyscallGetpidNode();
            case LLVMAMD64Syscall.SYS_sendfile:
                return LLVMAMD64SyscallSendfileNodeGen.create();
            case LLVMAMD64Syscall.SYS_socket:
                return new LLVMAMD64SyscallSocketNode();
            case LLVMAMD64Syscall.SYS_connect:
                return LLVMAMD64SyscallConnectNodeGen.create();
            case LLVMAMD64Syscall.SYS_accept:
                return LLVMAMD64SyscallAcceptNodeGen.create();
            case LLVMAMD64Syscall.SYS_sendto:
                return LLVMAMD64SyscallSendtoNodeGen.create();
            case LLVMAMD64Syscall.SYS_recvfrom:
                return LLVMAMD64SyscallRecvfromNodeGen.create();
            case LLVMAMD64Syscall.SYS_sendmsg:
                return LLVMAMD64SyscallSendmsgNodeGen.create();
            case LLVMAMD64Syscall.SYS_recvmsg:
                return LLVMAMD64SyscallRecvmsgNodeGen.create();
            case LLVMAMD64Syscall.SYS_bind:
                return LLVMAMD64SyscallBindNodeGen.create();
            case LLVMAMD64Syscall.SYS_listen:
                return new LLVMAMD64SyscallListenNode();
            case LLVMAMD64Syscall.SYS_getsockname:
                return LLVMAMD64SyscallGetsocknameNodeGen.create();
            case LLVMAMD64Syscall.SYS_setsockopt:
                return LLVMAMD64SyscallSetsockoptNodeGen.create();
            case LLVMAMD64Syscall.SYS_getsockopt:
                return LLVMAMD64SyscallGetsockoptNodeGen.create();
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
            case LLVMAMD64Syscall.SYS_rename:
                return LLVMAMD64SyscallRenameNodeGen.create();
            case LLVMAMD64Syscall.SYS_unlink:
                return LLVMAMD64SyscallUnlinkNodeGen.create();
            case LLVMAMD64Syscall.SYS_chmod:
                return LLVMAMD64SyscallChmodNodeGen.create();
            case LLVMAMD64Syscall.SYS_chown:
                return LLVMAMD64SyscallChownNodeGen.create();
            case LLVMAMD64Syscall.SYS_getuid:
                return new LLVMAMD64SyscallGetuidNode();
            case LLVMAMD64Syscall.SYS_syslog:
                return LLVMAMD64SyscallSyslogNodeGen.create();
            case LLVMAMD64Syscall.SYS_getgid:
                return new LLVMAMD64SyscallGetgidNode();
            case LLVMAMD64Syscall.SYS_setuid:
                return new LLVMAMD64SyscallSetuidNode();
            case LLVMAMD64Syscall.SYS_setgid:
                return new LLVMAMD64SyscallSetgidNode();
            case LLVMAMD64Syscall.SYS_geteuid:
                return new LLVMAMD64SyscallGeteuidNode();
            case LLVMAMD64Syscall.SYS_getegid:
                return new LLVMAMD64SyscallGetegidNode();
            case LLVMAMD64Syscall.SYS_getppid:
                return new LLVMAMD64SyscallGetPpidNode();
            case LLVMAMD64Syscall.SYS_getgroups:
                return LLVMAMD64SyscallGetgroupsNodeGen.create();
            case LLVMAMD64Syscall.SYS_getpgid:
                return new LLVMAMD64SyscallGetpgidNode();
            case LLVMAMD64Syscall.SYS_statfs:
                return LLVMAMD64SyscallStatfsNodeGen.create();
            case LLVMAMD64Syscall.SYS_fstatfs:
                return LLVMAMD64SyscallFstatfsNodeGen.create();
            case LLVMAMD64Syscall.SYS_arch_prctl:
                return LLVMAMD64SyscallArchPrctlNodeGen.create();
            case LLVMAMD64Syscall.SYS_gettid:
                return new LLVMAMD64SyscallGettidNode();
            case LLVMAMD64Syscall.SYS_futex:
                return LLVMAMD64SyscallFutexNodeGen.create();
            case LLVMAMD64Syscall.SYS_getdents64:
                return LLVMAMD64SyscallGetdents64NodeGen.create();
            case LLVMAMD64Syscall.SYS_set_tid_address:
                return LLVMAMD64SyscallSetTidAddressNodeGen.create();
            case LLVMAMD64Syscall.SYS_clock_gettime:
                return LLVMAMD64SyscallClockGetTimeNodeGen.create();
            case LLVMAMD64Syscall.SYS_renameat:
                return LLVMAMD64SyscallRenameatNodeGen.create();
            case LLVMAMD64Syscall.SYS_faccessat:
                return LLVMAMD64SyscallFaccessatNodeGen.create();
            case LLVMAMD64Syscall.SYS_utimensat:
                return LLVMAMD64SyscallUtimensatNodeGen.create();
            case LLVMAMD64Syscall.SYS_pipe2:
                return LLVMAMD64SyscallPipe2NodeGen.create();
            default:
                return new LLVMAMD64UnknownSyscallNode(index);
        }
    }
}
