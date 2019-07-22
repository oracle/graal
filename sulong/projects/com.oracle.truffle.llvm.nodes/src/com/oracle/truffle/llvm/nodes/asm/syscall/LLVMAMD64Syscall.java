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
package com.oracle.truffle.llvm.nodes.asm.syscall;

import com.oracle.truffle.api.CompilerDirectives;

public enum LLVMAMD64Syscall {
    SYS_read(0),
    SYS_write(1),
    SYS_open(2),
    SYS_close(3),
    SYS_stat(4),
    SYS_fstat(5),
    SYS_lstat(6),
    SYS_poll(7),
    SYS_lseek(8),
    SYS_mmap(9),
    SYS_munmap(11),
    SYS_brk(12),
    SYS_rt_sigaction(13),
    SYS_rt_sigprocmask(14),
    SYS_ioctl(16),
    SYS_readv(19),
    SYS_writev(20),
    SYS_access(21),
    SYS_pipe(22),
    SYS_dup(32),
    SYS_dup2(33),
    SYS_getpid(39),
    SYS_sendfile(40),
    SYS_socket(41),
    SYS_connect(42),
    SYS_accept(43),
    SYS_sendto(44),
    SYS_recvfrom(45),
    SYS_sendmsg(46),
    SYS_recvmsg(47),
    SYS_shutdown(48),
    SYS_bind(49),
    SYS_listen(50),
    SYS_getsockname(51),
    SYS_getpeername(52),
    SYS_socketpair(53),
    SYS_setsockopt(54),
    SYS_getsockopt(55),
    SYS_clone(56),
    SYS_fork(57),
    SYS_vfork(58),
    SYS_exit(60),
    SYS_uname(63),
    SYS_fcntl(72),
    SYS_ftruncate(77),
    SYS_getcwd(79),
    SYS_rename(82),
    SYS_rmdir(84),
    SYS_unlink(87),
    SYS_chmod(90),
    SYS_fchmod(91),
    SYS_chown(92),
    SYS_fchown(93),
    SYS_lchown(94),
    SYS_getuid(102),
    SYS_syslog(103),
    SYS_getgid(104),
    SYS_setuid(105),
    SYS_setgid(106),
    SYS_geteuid(107),
    SYS_getegid(108),
    SYS_getppid(110),
    SYS_getgroups(115),
    SYS_getpgid(121),
    SYS_capget(125),
    SYS_statfs(137),
    SYS_fstatfs(138),
    SYS_arch_prctl(158),
    SYS_gettid(186),
    SYS_futex(202),
    SYS_getdents64(217),
    SYS_set_tid_address(218),
    SYS_clock_gettime(228),
    SYS_exit_group(231),
    SYS_renameat(264),
    SYS_faccessat(269),
    SYS_utimensat(280),
    SYS_pipe2(293);

    public final int value;

    LLVMAMD64Syscall(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return super.toString() + " 0x" + Long.toHexString(value) + " (" + value + ")";
    }

    private static final LLVMAMD64Syscall[] valueToSysCall = new LLVMAMD64Syscall[294];

    static {
        for (LLVMAMD64Syscall syscall : values()) {
            valueToSysCall[syscall.value] = syscall;
        }
    }

    public static LLVMAMD64Syscall getSyscall(long value) {
        if (value >= 0 && value < valueToSysCall.length) {
            LLVMAMD64Syscall syscall = valueToSysCall[(int) value];
            if (syscall != null) {
                return syscall;
            }
        }
        throw error(value);
    }

    @CompilerDirectives.TruffleBoundary
    private static IllegalArgumentException error(long value) {
        return new IllegalArgumentException("Unknown syscall number: " + value);
    }
}
