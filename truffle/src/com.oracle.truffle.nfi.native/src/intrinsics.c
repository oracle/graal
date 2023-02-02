/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#if !defined(_WIN32)
#include <dlfcn.h>
#endif

#include <errno.h>
#include "internal.h"

#if defined(__linux__)
#define ERRNO_LOCATION __errno_location
#elif defined(__APPLE__) && defined(__MACH__)
#define ERRNO_LOCATION __error
#elif defined(__sun) && defined(__SVR4)
#define ERRNO_LOCATION ___errno
#endif

#define STRINGIFY_IMPL(x) #x
#define STRINGIFY(x) STRINGIFY_IMPL(x)

void initialize_intrinsics(struct __TruffleContextInternal *context) {
#ifdef ERRNO_LOCATION
    context->__pthreads_errno_location = ERRNO_LOCATION;
    context->__libc_errno_location = dlsym(RTLD_NEXT, STRINGIFY(ERRNO_LOCATION));
#elif defined(_WIN32)
    context->__libc_errno_location = _errno;
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

    if (orig == context->__libc_errno_location
#if !defined(_WIN32)
        || orig == context->__pthreads_errno_location
#endif
    ) {
        return __errno_mirror_location;
    }

    return orig;
}
