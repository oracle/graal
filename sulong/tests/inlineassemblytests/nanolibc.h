/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
#ifndef __NANOLIBC_H__
#define __NANOLIBC_H__

// nanolibc: implementation of various posix functions/syscall wrappers

#define _GNU_SOURCE
#include <unistd.h>
#include <stdint.h>
#include <stdarg.h>
#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <sys/uio.h>
#include <sys/utsname.h>
#include <sys/syscall.h>

// avoid name conflicts
#define getcwd __syscall_getcwd
#define read __syscall_read
#define write __syscall_write
#define open __syscall_open
#define close __syscall_close
#define lseek __syscall_lseek
#define readv __syscall_readv
#define writev __syscall_writev
#define exit __syscall_exit
#define _Exit __syscall_exit_group
#define mkdir __syscall_mkdir
#define rmdir __syscall_rmdir
#define uname __syscall_uname
#define getuid __syscall_getuid
#define getgid __syscall_getgid
#define syscall __syscall__

#define strlen __nanolibc_strlen

#define asm __asm__
#define inline __inline__

// syscall helpers
#define __SYSCALL_0(result, id) __asm__ volatile("syscall" : "=a"(result) : "a"(id) : "memory", "rcx", "r11");

#define __SYSCALL_1(result, id, a1) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1) : "memory", "rcx", "r11");

#define __SYSCALL_2(result, id, a1, a2) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2) : "memory", "rcx", "r11");

#define __SYSCALL_3(result, id, a1, a2, a3) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2), "d"(a3) : "memory", "rcx", "r11");

#define __SYSCALL_6(result, id, a1, a2, a3, a4, a5, a6)                                                                                              \
    {                                                                                                                                                \
        register int64_t r10 asm("r10") = a4;                                                                                                        \
        register int64_t r8 asm("r8") = a5;                                                                                                          \
        register int64_t r9 asm("r9") = a6;                                                                                                          \
        __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2), "d"(a3), "r"(r10), "r"(r8), "r"(r9) : "memory", "rcx", "r11");        \
    }

#define __SYSCALL_RET(result)                                                                                                                        \
    {                                                                                                                                                \
        if (result < 0) {                                                                                                                            \
            errno = -result;                                                                                                                         \
            return -1;                                                                                                                               \
        }                                                                                                                                            \
        return result;                                                                                                                               \
    }

#define __SYSCALL_0P(id)                                                                                                                             \
    {                                                                                                                                                \
        int64_t result;                                                                                                                              \
        __SYSCALL_0(result, id);                                                                                                                     \
        __SYSCALL_RET(result);                                                                                                                       \
    }

#define __SYSCALL_1P(id, a1)                                                                                                                         \
    {                                                                                                                                                \
        int64_t result;                                                                                                                              \
        __SYSCALL_1(result, id, a1);                                                                                                                 \
        __SYSCALL_RET(result);                                                                                                                       \
    }

#define __SYSCALL_2P(id, a1, a2)                                                                                                                     \
    {                                                                                                                                                \
        int64_t result;                                                                                                                              \
        __SYSCALL_2(result, id, a1, a2);                                                                                                             \
        __SYSCALL_RET(result);                                                                                                                       \
    }

#define __SYSCALL_3P(id, a1, a2, a3)                                                                                                                 \
    {                                                                                                                                                \
        int64_t result;                                                                                                                              \
        __SYSCALL_3(result, id, a1, a2, a3);                                                                                                         \
        __SYSCALL_RET(result);                                                                                                                       \
    }

#define __SYSCALL_6P(id, a1, a2, a3, a4, a5, a6)                                                                                                     \
    {                                                                                                                                                \
        int64_t result;                                                                                                                              \
        __SYSCALL_6(result, id, a1, a2, a3, a4, a5, a6);                                                                                             \
        __SYSCALL_RET(result);                                                                                                                       \
    }

// posix/libc functions
static inline ssize_t read(int fd, void *buf, size_t count) {
    __SYSCALL_3P(SYS_read, fd, buf, count);
}

static inline ssize_t write(int fd, const void *buf, size_t count) {
    __SYSCALL_3P(SYS_write, fd, buf, count);
}

static inline int open(const char *filename, int flags, mode_t mode) {
    __SYSCALL_3P(SYS_open, filename, flags, mode);
}

static inline int close(int fd) {
    __SYSCALL_1P(SYS_close, fd);
}

static inline long lseek(int fd, off_t offset, int whence) {
    __SYSCALL_3P(SYS_lseek, fd, offset, whence);
}

static inline ssize_t readv(int fd, const struct iovec *iov, int iovcnt) {
    __SYSCALL_3P(SYS_readv, fd, iov, iovcnt);
}

static inline ssize_t writev(int fd, const struct iovec *iov, int iovcnt) {
    __SYSCALL_3P(SYS_writev, fd, iov, iovcnt);
}

static inline char *getcwd(char *buf, size_t size) {
    int64_t result;
    __SYSCALL_2(result, SYS_getcwd, buf, size);
    if (result < 0) {
        errno = -result;
        return NULL;
    }
    return buf;
}

static inline void _Exit(int ec) {
    int64_t result;
    __SYSCALL_1(result, SYS_exit_group, ec);
}

static inline void exit(int ec) {
    int64_t result;
    __SYSCALL_1(result, SYS_exit, ec);
}

static inline int mkdir(const char *path, mode_t mode) {
    __SYSCALL_2P(SYS_mkdir, path, mode);
}

static inline int rmdir(const char *path, mode_t mode) {
    __SYSCALL_2P(SYS_rmdir, path, mode);
}

static inline int uname(struct utsname *buf) {
    __SYSCALL_1P(SYS_uname, buf);
}

static inline int getuid(void) {
    __SYSCALL_0P(SYS_getuid);
}

static inline int getgid(void) {
    __SYSCALL_0P(SYS_getgid);
}

// syscall functions
static inline int64_t syscall(int64_t n, ...) {
    va_list ap;
    int64_t a, b, c, d, e, f;
    va_start(ap, n);
    a = va_arg(ap, int64_t);
    b = va_arg(ap, int64_t);
    c = va_arg(ap, int64_t);
    d = va_arg(ap, int64_t);
    e = va_arg(ap, int64_t);
    f = va_arg(ap, int64_t);
    va_end(ap);
    __SYSCALL_6P(n, a, b, c, d, e, f);
}

// pure userspace functions
int strlen(char *s) {
    char *p = s;
    for (; *p; p++)
        ;
    return p - s;
}

#endif
