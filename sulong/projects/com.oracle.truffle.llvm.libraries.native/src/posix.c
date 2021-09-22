/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
#define _GNU_SOURCE

#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <poll.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/param.h>
#include <sys/mount.h>

#ifdef __linux__
#include <sys/vfs.h>
#include <sys/klog.h>
#include <sys/syscall.h>
#include <sys/sendfile.h>
#endif

#ifdef __linux__
#define CALL(type, name, ...)                                                                                                                        \
    {                                                                                                                                                \
        int native_errno = errno;                                                                                                                    \
        type result = name(__VA_ARGS__);                                                                                                             \
        if (result == (type) -1) {                                                                                                                   \
            result = (type) (long) -errno;                                                                                                           \
        }                                                                                                                                            \
        errno = native_errno;                                                                                                                        \
        return result;                                                                                                                               \
    }
#else
#define CALL(type, name, ...)                                                                                                                        \
    {                                                                                                                                                \
        fprintf(stderr, "Syscalls (" #name ") not supported on this OS.\n");                                                                         \
        return (type) -ENOSYS;                                                                                                                       \
    }
#endif

int __sulong_posix_open(const char *pathname, int flags, mode_t mode) {
    CALL(int, open, pathname, flags, mode)
}

int __sulong_posix_close(int fd){ CALL(int, close, fd) }

ssize_t __sulong_posix_read(int fd, void *buf, size_t count){ CALL(ssize_t, read, fd, buf, count) }

ssize_t __sulong_posix_write(int fd, const void *buf, size_t count){ CALL(ssize_t, write, fd, buf, count) }

ssize_t __sulong_posix_readv(int fd, const struct iovec *iov, int iovcnt) {
    CALL(ssize_t, readv, fd, iov, iovcnt);
}

ssize_t __sulong_posix_writev(int fd, const struct iovec *iov, int iovcnt) {
    CALL(ssize_t, writev, fd, iov, iovcnt);
}

int __sulong_posix_dup(int oldfd) {
    CALL(int, dup, oldfd)
}

int __sulong_posix_dup2(int oldfd, int newfd) {
    CALL(int, dup2, oldfd, newfd)
}

int __sulong_posix_dup3(int oldfd, int newfd, int flags) {
    CALL(int, dup3, oldfd, newfd, flags)
}

int __sulong_posix_fcntl(int fd, int cmd, void *arg) {
    CALL(int, fcntl, fd, cmd, arg)
}

int __sulong_posix_ioctl(int fd, unsigned long request, void *argp) {
    CALL(int, ioctl, fd, request, argp)
}

int __sulong_posix_stat(const char *path, struct stat *statbuf) {
    CALL(int, stat, path, statbuf)
}

int __sulong_posix_fstat(int fd, struct stat *statbuf) {
    CALL(int, fstat, fd, statbuf)
}

int __sulong_posix_lstat(const char *path, struct stat *statbuf){ CALL(int, lstat, path, statbuf) }

ssize_t __sulong_posix_sendfile(int out_fd, int in_fd, off_t *offset, size_t count) {
    CALL(ssize_t, sendfile, out_fd, in_fd, offset, count);
}

void *__sulong_posix_mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
    CALL(void *, mmap, addr, length, prot, flags, fd, offset);
}

int __sulong_posix_munmap(void *addr, size_t length) {
    CALL(int, munmap, addr, length);
}

int __sulong_posix_unlink(const char *path) {
    CALL(int, unlink, path);
}

int __sulong_posix_chmod(const char *filename, mode_t mode) {
    CALL(int, chmod, filename, mode);
}

int __sulong_posix_chown(const char *filename, uid_t user, gid_t group) {
    CALL(int, chown, filename, user, group);
}

int __sulong_posix_socket(int domain, int type, int protocol) {
    CALL(int, socket, domain, type, protocol);
}

int __sulong_posix_pipe(int pipefd[2]) {
    CALL(int, pipe, pipefd);
}

int __sulong_posix_pipe2(int pipefd[2], int flags) {
    CALL(int, pipe2, pipefd, flags);
}

int __sulong_posix_utimensat(int dfd, const char *filename, struct timespec *utimes, int flags) {
    CALL(int, utimensat, dfd, filename, utimes, flags);
}

int __sulong_posix_bind(int sockfd, const struct sockaddr *addr, socklen_t addrlen) {
    CALL(int, bind, sockfd, addr, addrlen);
}

int __sulong_posix_getsockname(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
    CALL(int, getsockname, sockfd, addr, addrlen);
}

int __sulong_posix_getsockopt(int sockfd, int level, int optname, void *optval, socklen_t *optlen) {
    CALL(int, getsockopt, sockfd, level, optname, optval, optlen);
}

int __sulong_posix_setsockopt(int sockfd, int level, int optname, const void *optval, socklen_t optlen) {
    CALL(int, setsockopt, sockfd, level, optname, optval, optlen);
}

ssize_t __sulong_posix_sendto(int socket, const void *message, size_t length, int flags, const struct sockaddr *dest_addr, socklen_t dest_len) {
    CALL(ssize_t, sendto, socket, message, length, flags, dest_addr, dest_len);
}

ssize_t __sulong_posix_sendmsg(int socket, const struct msghdr *message, int flags) {
    CALL(ssize_t, sendmsg, socket, message, flags);
}

ssize_t __sulong_posix_recvfrom(int socket, void *restrict buffer, size_t length, int flags, struct sockaddr *restrict address,
                                socklen_t *restrict address_len) {
    CALL(ssize_t, recvfrom, socket, buffer, length, flags, address, address_len);
}

ssize_t __sulong_posix_recvmsg(int socket, struct msghdr *message, int flags) {
    CALL(ssize_t, recvmsg, socket, message, flags);
}

int __sulong_posix_listen(int socket, int backlog) {
    CALL(int, listen, socket, backlog);
}

int __sulong_posix_connect(int socket, const struct sockaddr *address, socklen_t address_len) {
    CALL(int, connect, socket, address, address_len);
}

int __sulong_posix_accept(int socket, struct sockaddr *restrict address, socklen_t *restrict address_len) {
    CALL(int, accept, socket, address, address_len);
}

int __sulong_posix_getuid(void) {
    CALL(int, getuid);
}

int __sulong_posix_getgid(void) {
    CALL(int, getgid);
}

int __sulong_posix_ftruncate(int fildes, off_t length) {
    CALL(int, ftruncate, fildes, length);
}

off_t __sulong_posix_lseek(int fildes, off_t offset, int whence) {
    CALL(off_t, lseek, fildes, offset, whence);
}

int __sulong_posix_setuid(uid_t uid) {
    CALL(int, setuid, uid);
}

int __sulong_posix_setgid(gid_t gid) {
    CALL(int, setgid, gid);
}

uid_t __sulong_posix_geteuid(void) {
    CALL(uid_t, geteuid);
}

gid_t __sulong_posix_getegid(void) {
    CALL(gid_t, getegid);
}

int __sulong_posix_access(const char *path, int amode) {
    CALL(int, access, path, amode);
}

int __sulong_posix_faccessat(int fd, const char *path, int amode, int flag) {
    CALL(int, faccessat, fd, path, amode, flag);
}

int __sulong_posix_rename(const char *old, const char *new) {
    CALL(int, rename, old, new);
}

int __sulong_posix_renameat(int oldfd, const char *old, int newfd, const char *new) {
    CALL(int, renameat, oldfd, old, newfd, new);
}

int __sulong_posix_getdents64(unsigned int fd, void *dirp, unsigned int count) {
    CALL(int, syscall, __NR_getdents64, fd, dirp, count);
}

int __sulong_posix_getgroups(int gidsetsize, gid_t grouplist[]) {
    CALL(int, getgroups, gidsetsize, grouplist);
}

int __sulong_posix_syslog(int type, char *bufp, int len) {
    CALL(int, klogctl, type, bufp, len);
}

int __sulong_posix_statfs(const char *path, struct statfs *buf) {
    CALL(int, statfs, path, buf);
}

int __sulong_posix_fstatfs(int fd, struct statfs *buf) {
    CALL(int, fstatfs, fd, buf);
}

int __sulong_posix_poll(struct pollfd *fds, nfds_t nfds, int timeout) {
    CALL(int, poll, fds, nfds, timeout);
}

pid_t __sulong_posix_getpgid(pid_t pid) {
    CALL(pid_t, getpgid, pid);
}

long __sulong_posix_syscall(long number, long rdi, long rsi, long rdx, long r10, long r8, long r9) {
    CALL(long, syscall, number, rdi, rsi, rdx, r10, r8, r9);
}
