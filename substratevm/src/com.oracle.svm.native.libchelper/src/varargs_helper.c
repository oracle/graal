/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef _WIN64

#ifdef __linux__
#define _GNU_SOURCE
#include <sys/mman.h>
#endif

#include <stdio.h>
#include <fcntl.h>

/* On some platforms the varargs calling convention doesn't match regular calls
 * (e.g. darwin-aarch64 or linux-riscv). Instead of implementing varargs
 * support for @CFunction we add C helpers so that the C compiler resolves the
 * ABI specifics for us.
 */

int fprintfSD(FILE *stream, const char *format, char *arg0, int arg1)
{
    return fprintf(stream, format, arg0, arg1);
}

/* open(2) has a variadic signature on POSIX:
 *
 *    int open(const char *path, int oflag, ...);
 */
int openSII(const char *pathname, int flags, int mode)
{
    return open(pathname, flags, mode);
}

int openatISII(int dirfd, const char *pathname, int flags, int mode)
{
    return openat(dirfd, pathname, flags, mode);
}

#ifdef __linux__
void *mremapP(void *old_address, size_t old_size, size_t new_size, int flags, void *new_address) {
    return mremap(old_address, old_size, new_size, flags, new_address);
}
#endif

#endif
