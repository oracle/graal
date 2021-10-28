/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates.
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
#include <sys/stat.h>
#include <sys/types.h>

struct stat64;

int __sulong_stat(const char *path, struct stat *buf) {
    return stat(path, buf);
}

int __sulong_fstat(int fd, struct stat *buf) {
    return fstat(fd, buf);
}

int __sulong_lstat(const char *path, struct stat *buf) {
    return lstat(path, buf);
}

int __sulong_fstatat(int fd, const char *path, struct stat *buf, int flag) {
    return fstatat(fd, path, buf, flag);
}

int __sulong_stat64(const char *path, struct stat64 *buf) {
    return stat64(path, buf);
}

int __sulong_fstat64(int fd, struct stat64 *buf) {
    return fstat64(fd, buf);
}

int __sulong_lstat64(const char *path, struct stat64 *buf) {
    return lstat64(path, buf);
}

int __sulong_fstatat64(int fd, const char *path, struct stat64 *buf, int flag) {
    return fstatat64(fd, path, buf, flag);
}
