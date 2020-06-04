/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern const char *__progname;

__attribute__((weak)) void __assert_fail(const char *__assertion, const char *__file, unsigned int __line, const char *__function) {
    fprintf(stderr, "%s: %s:%d: %s: Assertion `%s' failed.\n", __progname, __file, __line, __function, __assertion);
    fflush(NULL);
    abort();
}

__attribute__((weak)) void __assert_perror_fail(int __errnum, const char *__file, unsigned int __line, const char *__function) {
    const char *str = strerror(__errnum);
    fprintf(stderr, "%s: %s:%d: %s: Assertion `%s' failed.\n", __progname, __file, __line, __function, str);
    fflush(NULL);
    abort();
}

__attribute__((weak)) void __assert(const char *__assertion, const char *__file, int __line) {
    fprintf(stderr, "%s: %s:%d: Assertion `%s' failed.\n", __progname, __file, __line, __assertion);
    fflush(NULL);
    abort();
}
