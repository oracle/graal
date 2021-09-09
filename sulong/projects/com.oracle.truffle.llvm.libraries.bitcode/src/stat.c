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

/*
 * There are no external implementations of the stat() functions, so we need to manually map them
 * to valid native functions. This manual mapping is only used when LLVM optimizations are disabled
 * (i.e., when O0 is used), as LLVM uses extern_inline code for all stat() functions otherwise.
 */

struct stat;
struct stat64;

int __sulong_stat(const char *path, struct stat *buf);

int __sulong_fstat(int fd, struct stat *buf);

int __sulong_lstat(const char *path, struct stat *buf);

int __sulong_fstatat(int fd, const char *path, struct stat *buf, int flag);

int __sulong_stat64(const char *path, struct stat64 *buf);

int __sulong_fstat64(int fd, struct stat64 *buf);

int __sulong_lstat64(const char *path, struct stat64 *buf);

int __sulong_fstatat64(int fd, const char *path, struct stat64 *buf, int flag);

int stat(const char *path, struct stat *buf) {
    return __sulong_stat(path, buf);
}

int fstat(int fd, struct stat *buf) {
    return __sulong_fstat(fd, buf);
}

int lstat(const char *path, struct stat *buf) {
    return __sulong_lstat(path, buf);
}

int fstatat(int fd, const char *path, struct stat *buf, int flag) {
    return __sulong_fstatat(fd, path, buf, flag);
}

int stat64(const char *path, struct stat64 *buf) {
    return __sulong_stat64(path, buf);
}

int fstat64(int fd, struct stat64 *buf) {
    return __sulong_fstat64(fd, buf);
}

int lstat64(const char *path, struct stat64 *buf) {
    return __sulong_lstat64(path, buf);
}

int fstatat64(int fd, const char *path, struct stat64 *buf, int flag) {
    return __sulong_fstatat64(fd, path, buf, flag);
}
