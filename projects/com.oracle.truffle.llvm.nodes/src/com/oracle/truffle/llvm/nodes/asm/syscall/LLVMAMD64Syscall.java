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

public class LLVMAMD64Syscall {
    public static final int SYS_read = 0;
    public static final int SYS_write = 1;
    public static final int SYS_open = 2;
    public static final int SYS_close = 3;
    public static final int SYS_stat = 4;
    public static final int SYS_fstat = 5;
    public static final int SYS_lstat = 6;
    public static final int SYS_lseek = 8;
    public static final int SYS_mmap = 9;
    public static final int SYS_munmap = 11;
    public static final int SYS_brk = 12;
    public static final int SYS_rt_sigaction = 13;
    public static final int SYS_rt_sigprocmask = 14;
    public static final int SYS_ioctl = 16;
    public static final int SYS_readv = 19;
    public static final int SYS_writev = 20;
    public static final int SYS_dup = 32;
    public static final int SYS_dup2 = 33;
    public static final int SYS_getpid = 39;
    public static final int SYS_sendfile = 40;
    public static final int SYS_socket = 41;
    public static final int SYS_connect = 42;
    public static final int SYS_accept = 43;
    public static final int SYS_sendto = 44;
    public static final int SYS_recvfrom = 45;
    public static final int SYS_sendmsg = 46;
    public static final int SYS_recvmsg = 47;
    public static final int SYS_shutdown = 48;
    public static final int SYS_bind = 49;
    public static final int SYS_listen = 50;
    public static final int SYS_getsockname = 51;
    public static final int SYS_getpeername = 52;
    public static final int SYS_socketpair = 53;
    public static final int SYS_setsockopt = 54;
    public static final int SYS_getsockopt = 55;
    public static final int SYS_exit = 60;
    public static final int SYS_uname = 63;
    public static final int SYS_fcntl = 72;
    public static final int SYS_ftruncate = 77;
    public static final int SYS_getcwd = 79;
    public static final int SYS_getuid = 102;
    public static final int SYS_getgid = 104;
    public static final int SYS_setuid = 105;
    public static final int SYS_setgid = 106;
    public static final int SYS_getppid = 110;
    public static final int SYS_futex = 202;
    public static final int SYS_clock_gettime = 228;
    public static final int SYS_exit_group = 231;
}
