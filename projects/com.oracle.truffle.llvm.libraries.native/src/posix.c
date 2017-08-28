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
#define _GNU_SOURCE

#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/sendfile.h>
#include <sys/uio.h>
#include <sys/mman.h>

#define CALL(type, name, ...) { \
	int native_errno = errno; \
	type result = name(__VA_ARGS__); \
	if(result == (type) -1) { \
		result = (type) (long) -errno; \
	} \
	errno = native_errno; \
	return result; \
}

int __sulong_posix_open(const char* pathname, int flags, mode_t mode)
{
	CALL(int, open, pathname, flags, mode)
}

int __sulong_posix_close(int fd)
{
	CALL(int, close, fd)
}

ssize_t __sulong_posix_read(int fd, void* buf, size_t count)
{
	CALL(ssize_t, read, fd, buf, count)
}

ssize_t __sulong_posix_write(int fd, const void* buf, size_t count)
{
	CALL(ssize_t, write, fd, buf, count)
}

ssize_t __sulong_posix_readv(int fd, const struct iovec* iov, int iovcnt)
{
	CALL(ssize_t, readv, fd, iov, iovcnt);
}

ssize_t __sulong_posix_writev(int fd, const struct iovec* iov, int iovcnt)
{
	CALL(ssize_t, writev, fd, iov, iovcnt);
}

int __sulong_posix_dup(int oldfd)
{
	CALL(int, dup, oldfd)
}

int __sulong_posix_dup2(int oldfd, int newfd)
{
	CALL(int, dup2, oldfd, newfd)
}

int __sulong_posix_dup3(int oldfd, int newfd, int flags)
{
#ifdef __linux__
	CALL(int, dup3, oldfd, newfd, flags)
#else
	CALL(int, dup2, oldfd, newfd)
#endif
}

int __sulong_posix_fcntl(int fd, int cmd, void* arg)
{
	CALL(int, fcntl, fd, cmd, arg)
}

int __sulong_posix_ioctl(int fd, unsigned long request, void* argp)
{
	CALL(int, ioctl, fd, request, argp)
}

int __sulong_posix_stat(const char* path, struct stat* statbuf)
{
	CALL(int, stat, path, statbuf)
}

int __sulong_posix_fstat(int fd, struct stat* statbuf)
{
	CALL(int, fstat, fd, statbuf)
}

int __sulong_posix_lstat(const char* path, struct stat* statbuf)
{
	CALL(int, lstat, path, statbuf)
}

ssize_t __sulong_posix_sendfile(int out_fd, int in_fd, off_t* offset, size_t count)
{
	CALL(ssize_t, sendfile, out_fd, in_fd, offset, count);
}

void* __sulong_posix_mmap(void* addr, size_t length, int prot, int flags, int fd, off_t offset)
{
	CALL(void*, mmap, addr, length, prot, flags, fd, offset);
}

int __sulong_posix_munmap(void* addr, size_t length)
{
	CALL(int, munmap, addr, length);
}
