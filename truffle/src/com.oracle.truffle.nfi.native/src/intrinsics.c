/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
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

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>

#include "internal.h"

#if defined(OS_linux)
#define ERRNO_LOCATION __errno_location
#elif defined(OS_darwin)
#define ERRNO_LOCATION __error
#elif defined(OS_solaris)
#define ERRNO_LOCATION ___errno
#endif

#define STRINGIFY_IMPL(x) #x
#define STRINGIFY(x) STRINGIFY_IMPL(x)


void initialize_intrinsics(struct __TruffleContextInternal *context) {
#ifdef ERRNO_LOCATION
    context->__pthreads_errno_location = ERRNO_LOCATION;
    context->__libc_errno_location = dlsym(RTLD_NEXT, STRINGIFY(ERRNO_LOCATION));
#else
#warning not intrinsifying __errno_location on this platform
    context->__pthreads_errno_location = NULL;
    context->__libc_errno_location = NULL;
#endif
}

static int *__errno_mirror_location() {
    return &errnoMirror;
}


void *check_intrinsify(struct __TruffleContextInternal *context, void *orig) {
    if (orig == NULL) {
        return NULL;
    }

    if (orig == context->__libc_errno_location || orig == context->__pthreads_errno_location) {
        return __errno_mirror_location;
    }

    return orig;
}
