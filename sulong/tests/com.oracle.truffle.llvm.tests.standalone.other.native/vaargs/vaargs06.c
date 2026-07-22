/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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

/* Verify i128 register consumption and 16-byte-aligned overflow placement. */
#include <stdarg.h>
#include <stdio.h>

static volatile int zero_precision;

/* Passing the va_list to a native function forces its managed storage to be nativized. */
#if defined(__APPLE__) && defined(__aarch64__)
#define NATIVIZE(ap) ((void) printf("%.*s", zero_precision, (char *) (ap)))
#else
#define NATIVIZE(ap) ((void) printf("%.*s", zero_precision, (char *) &(ap)))
#endif

static int register_arguments(int ignored, ...) {
    va_list ap;
    va_start(ap, ignored);
    NATIVIZE(ap);
    __int128 value = va_arg(ap, __int128);
    long next = va_arg(ap, long);
    va_end(ap);
    return value == ((__int128) 1 << 100) && next == 42 ? 0 : 1;
}

static int overflow_arguments(int ignored, ...) {
    va_list ap;
    va_start(ap, ignored);
    NATIVIZE(ap);
    (void) va_arg(ap, long);
    (void) va_arg(ap, long);
    (void) va_arg(ap, long);
    (void) va_arg(ap, long);
    __int128 value = va_arg(ap, __int128);
    long next = va_arg(ap, long);
    va_end(ap);
    return value == ((__int128) 1 << 100) && next == 84 ? 0 : 1;
}

#if defined(__x86_64__) || defined(__aarch64__)
static int named_i128_does_not_consume_partial_register_area(long a, long b, long c, long d, long e, __int128 named, ...) {
    va_list ap;
    va_start(ap, named);
    long value = va_arg(ap, long);
    va_end(ap);
    return a == 1 && b == 2 && c == 3 && d == 4 && e == 5 && named == 6 && value == 126 ? 0 : 1;
}
#endif

int main(void) {
    int result = register_arguments(0, ((__int128) 1 << 100), 42L) | overflow_arguments(0, 1L, 2L, 3L, 4L, ((__int128) 1 << 100), 84L);
#if defined(__x86_64__) || defined(__aarch64__)
    result |= named_i128_does_not_consume_partial_register_area(1L, 2L, 3L, 4L, 5L, 6, 126L);
#endif
    return result;
}
